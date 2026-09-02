package dev.mindgraph.mcp

import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Handing work to a person or an agent, and asking only for your own. */
class McpAssigneeTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-assign"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun call(name: String, arguments: JsonObject): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", buildJsonObject { put("name", name); put("arguments", arguments) })
            },
        )!!["result"]!!.jsonObject

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    private fun ready(assignee: String? = null): String =
        call(
            "list_ready_tasks",
            buildJsonObject { assignee?.let { put("assignee", it) } },
        ).text()

    private fun task(title: String, assignee: String? = null) = runBlocking {
        store.create(title, "", TaskFacet(TaskStatus.Todo), assignee = assignee)
    }

    @Test
    fun createTaskCanHandItToSomeone() {
        newVault()

        val text = call(
            "create_task",
            buildJsonObject { put("title", "Retake the screenshots"); put("assignee", "iago") },
        ).text()

        assertTrue("Assigned to iago" in text, text)
        assertEquals("iago", runBlocking { store.load() }.single().assignee)
    }

    @Test
    fun anAgentCanClaimATaskAsItStartsIt() {
        newVault()
        task("Write the importer")

        call(
            "update_status",
            buildJsonObject {
                put("node", "Write the importer")
                put("status", "doing")
                put("agent", "claude-code")
                put("assignee", "claude-code")
            },
        )

        assertEquals("claude-code", runBlocking { store.load() }.single().assignee)
    }

    @Test
    fun closingATaskDoesNotUnassignIt() {
        newVault()
        task("Owned work", assignee = "iago")

        call(
            "update_status",
            buildJsonObject { put("node", "Owned work"); put("status", "done") },
        )

        assertEquals("iago", runBlocking { store.load() }.single().assignee)
    }

    @Test
    fun anAgentCanAskForOnlyItsOwnWork() {
        newVault()
        task("Mine", assignee = "claude-code")
        task("Yours", assignee = "iago")
        task("Nobody's")

        val text = ready(assignee = "claude-code")

        assertTrue("Mine" in text, text)
        assertFalse("Yours" in text, text)
        assertFalse("Nobody's" in text, text)
    }

    @Test
    fun theUnfilteredListStillShowsEverythingWithItsOwner() {
        newVault()
        task("Mine", assignee = "claude-code")
        task("Nobody's")

        val text = ready()

        assertTrue("@claude-code" in text, text)
        assertTrue("Nobody's" in text, text)
    }

    @Test
    fun assignmentFiltersItNeverGates() {
        newVault()
        val blocker = task("Blocker", assignee = "iago")
        val dependent = task("Dependent", assignee = "claude-code")
        call(
            "link_nodes",
            buildJsonObject {
                put("from", dependent.id.value)
                put("to", blocker.id.value)
                put("kind", "depends_on")
            },
        )

        // Somebody else owning the blocker does not make the dependent any less blocked,
        // and owning a ready task does not make it any more ready.
        assertTrue("Blocker" in ready(assignee = "iago"))
        assertTrue("Nothing ready is assigned to claude-code" in ready(assignee = "claude-code"))
    }

    @Test
    fun anEmptyResultSaysWhoItLookedFor() {
        newVault()
        task("Someone else's", assignee = "iago")

        assertEquals("Nothing ready is assigned to nobody.", ready(assignee = "nobody"))
    }

    @Test
    fun namesMatchWithoutRegardToCase() {
        newVault()
        task("Mine", assignee = "Claude-Code")

        assertTrue("Mine" in ready(assignee = "claude-code"))
    }

    @Test
    fun aBlankAssigneeLeavesTheNodeUnowned() {
        newVault()

        call("create_task", buildJsonObject { put("title", "Loose end"); put("assignee", "   ") })

        assertNull(runBlocking { store.load() }.single().assignee)
    }
}
