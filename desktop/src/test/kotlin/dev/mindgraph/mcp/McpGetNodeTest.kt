package dev.mindgraph.mcp

import dev.mindgraph.model.NodeKind
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
import kotlin.test.assertTrue

/** Reading one node in full — the half of the loop that was missing. */
class McpGetNodeTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-get"))
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

    private fun JsonObject.isError(): Boolean = this["isError"]!!.jsonPrimitive.content.toBoolean()

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    private fun get(reference: String): JsonObject =
        call("get_node", buildJsonObject { put("node", reference) })

    @Test
    fun theBodyIsReturnedInFull() {
        newVault()
        val body = "The reason this work exists, at length.\n\nWith a second paragraph."
        runBlocking { store.create("A considered note", body) }

        val text = get("A considered note").text()

        // The whole point: list_ready_tasks gives a line, some nodes are a page.
        assertTrue(body in text, text)
        assertTrue("# A considered note" in text, text)
    }

    @Test
    fun aNodeWithNoBodySaysSoRatherThanTrailingOff() {
        newVault()
        runBlocking { store.create("Empty") }

        assertTrue("(no body)" in get("Empty").text())
    }

    @Test
    fun derivedReadinessIsReportedBecauseItIsNotStored() {
        newVault()
        val blocker = runBlocking { store.create("Blocker", "", TaskFacet(TaskStatus.Todo)) }
        val blocked = runBlocking { store.create("Blocked", "", TaskFacet(TaskStatus.Todo)) }
        runBlocking { store.save(store.load().first { it.id == blocked.id }.copy(dependsOn = listOf(blocker.id))) }

        assertTrue("status: todo (blocked)" in get("Blocked").text())
        assertTrue("status: todo (ready)" in get("Blocker").text())
    }

    @Test
    fun itNamesWhatTheNodeIsWaitingOnAndWhatItHoldsUp() {
        newVault()
        val blocker = runBlocking { store.create("Groundwork", "", TaskFacet(TaskStatus.Todo)) }
        val blocked = runBlocking { store.create("The work after", "", TaskFacet(TaskStatus.Todo)) }
        runBlocking { store.save(store.load().first { it.id == blocked.id }.copy(dependsOn = listOf(blocker.id))) }

        assertTrue("waiting on: \"Groundwork\"" in get("The work after").text())
        assertTrue("blocking: \"The work after\"" in get("Groundwork").text())
    }

    @Test
    fun aFinishedDependencyIsNotReportedAsSomethingToWaitFor() {
        newVault()
        val done = runBlocking { store.create("Already done", "", TaskFacet(TaskStatus.Done)) }
        val next = runBlocking { store.create("Next", "", TaskFacet(TaskStatus.Todo)) }
        runBlocking { store.save(store.load().first { it.id == next.id }.copy(dependsOn = listOf(done.id))) }

        val text = get("Next").text()
        assertFalse("waiting on" in text, text)
        assertTrue("depends on (finished): \"Already done\"" in text, text)
    }

    @Test
    fun kindStatusDeadlineAndAssigneeAllComeBack() {
        newVault()
        runBlocking {
            store.create(
                "RFC-001",
                "Context.",
                TaskFacet(TaskStatus.Doing, due = "2026-09-04"),
                NodeKind.Rfc,
                assignee = "iago",
            )
        }

        val text = get("RFC-001").text()

        assertTrue("kind: rfc" in text, text)
        assertTrue("status: doing" in text, text)
        assertTrue("due: 2026-09-04" in text, text)
        assertTrue("assignee: iago" in text, text)
    }

    @Test
    fun anArchivedNodeSaysSoAndClaimsNeitherReadyNorBlocked() {
        newVault()
        val node = runBlocking { store.create("Put away", "", TaskFacet(TaskStatus.Todo)) }
        runBlocking { store.save(store.load().single { it.id == node.id }.copy(archived = true)) }

        val text = get("Put away").text()

        assertTrue("archived: yes" in text, text)
        assertFalse("(ready)" in text, text)
        assertFalse("(blocked)" in text, text)
    }

    @Test
    fun aNodeCanBeFetchedByIdAsWellAsByTitle() {
        newVault()
        val node = runBlocking { store.create("By id") }

        assertTrue("# By id" in get(node.id.value).text())
    }

    @Test
    fun askingForSomethingThatIsNotThereSaysSo() {
        newVault()
        runBlocking { store.create("The only node") }

        val result = get("A node that does not exist")

        assertTrue(result.isError())
        assertTrue("No node matches" in result.text(), result.text())
    }

    @Test
    fun theToolIsAdvertisedAlongsideTheOthers() {
        newVault()
        val tools = dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/list")
            },
        )!!["result"]!!.jsonObject["tools"]!!.jsonArray

        assertEquals(
            listOf(
                "list_ready_tasks", "get_node", "create_task", "create_note",
                "append_node_body", "link_nodes", "update_status",
            ),
            tools.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
    }
}
