package dev.mindgraph.mcp

import dev.mindgraph.model.Node
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

/** The loop an agent actually runs: ask what is ready, do it, close it, ask again. */
class McpWorkflowTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-loop"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun task(title: String): Node =
        runBlocking { store.create(title, "", TaskFacet(TaskStatus.Todo)) }

    private fun call(name: String, arguments: JsonObject): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put(
                    "params",
                    buildJsonObject {
                        put("name", name)
                        put("arguments", arguments)
                    },
                )
            },
        )!!["result"]!!.jsonObject

    private fun readyTasks(): JsonObject = call("list_ready_tasks", buildJsonObject {})

    private fun setStatus(node: String, status: String): JsonObject =
        call("update_status", buildJsonObject { put("node", node); put("status", status) })

    private fun link(from: String, to: String): JsonObject =
        call(
            "link_nodes",
            buildJsonObject { put("from", from); put("to", to); put("kind", "depends_on") },
        )

    private fun JsonObject.isError(): Boolean = this["isError"]!!.jsonPrimitive.content.toBoolean()

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun anEmptyVaultSaysSoRatherThanReturningNothing() {
        newVault()

        assertEquals("No open tasks.", readyTasks().text())
    }

    @Test
    fun blockedTasksAreNotListedAsReady() {
        newVault()
        val foundation = task("Add a kind field")
        val dependent = task("Import the memory notes")
        link(dependent.id.value, foundation.id.value)

        val text = readyTasks().text()

        assertTrue("Add a kind field" in text, text)
        assertFalse("Import the memory notes" in text, text)
        assertTrue("1 blocked" in text, text)
    }

    @Test
    fun readyTasksAreRankedByHowMuchTheyUnblock() {
        newVault()
        val keystone = task("Add a kind field")
        val alsoReady = task("Watch the vault")
        repeat(2) { i -> link(task("Dependent $i").id.value, keystone.id.value) }

        val lines = readyTasks().text().lines().filter { it.startsWith("- ") }

        assertTrue(keystone.title in lines[0], "expected the keystone first, got: $lines")
        assertTrue("unblocks 2" in lines[0], lines[0])
        assertTrue(alsoReady.title in lines[1], lines[1])
    }

    @Test
    fun closingATaskReportsWhatItUnblocked() {
        newVault()
        val foundation = task("Add a kind field")
        task("Import the memory notes").also { link(it.id.value, foundation.id.value) }
        task("Import the plans").also { link(it.id.value, foundation.id.value) }

        val result = setStatus(foundation.id.value, "done")

        assertFalse(result.isError(), result.text())
        val text = result.text()
        assertTrue("is now done" in text, text)
        assertTrue("unblocked 2" in text, text)
        assertTrue("Import the memory notes" in text && "Import the plans" in text, text)
    }

    @Test
    fun droppingATaskUnblocksItsDependentsToo() {
        newVault()
        val abandoned = task("An approach we gave up on")
        task("The work after it").also { link(it.id.value, abandoned.id.value) }

        val text = setStatus("An approach we gave up on", "dropped").text()

        assertTrue("unblocked 1" in text, text)
        assertTrue("The work after it" in readyTasks().text())
    }

    @Test
    fun closingATaskThatFreesNothingSaysOnlyThat() {
        newVault()
        task("A standalone task")

        val text = setStatus("A standalone task", "done").text()

        assertEquals("\"A standalone task\" is now done.", text)
    }

    @Test
    fun theFullLoopEndsWithNothingLeft() {
        newVault()
        val first = task("First")
        val second = task("Second")
        link(second.id.value, first.id.value)

        setStatus(first.id.value, "done")
        assertTrue("Second" in readyTasks().text())
        setStatus(second.id.value, "done")

        assertEquals("No open tasks.", readyTasks().text())
    }

    @Test
    fun everythingBlockedIsReportedAsSuchRatherThanAsEmpty() {
        newVault()
        // A hand-edited vault can contain a cycle even though the tools refuse to create one.
        val a = runBlocking { store.create("A", "", TaskFacet(TaskStatus.Todo)) }
        val b = runBlocking { store.create("B", "", TaskFacet(TaskStatus.Todo)) }
        runBlocking {
            store.save(store.load().first { it.id == a.id }.copy(dependsOn = listOf(b.id)))
            store.save(store.load().first { it.id == b.id }.copy(dependsOn = listOf(a.id)))
        }

        val text = readyTasks().text()

        assertTrue(text.startsWith("Nothing is ready"), text)
        assertTrue("2 open tasks are blocked" in text, text)
    }

    @Test
    fun anInvalidStatusIsRejected() {
        newVault()
        task("A task")

        val result = setStatus("A task", "finished")

        assertTrue(result.isError())
        assertTrue("done" in result.text(), result.text())
    }

    @Test
    fun aNoteGivenAStatusBecomesATask() {
        newVault()
        runBlocking { store.create("Just a note", "no task facet") }

        val result = setStatus("Just a note", "doing")

        assertFalse(result.isError(), result.text())
        assertEquals(TaskStatus.Doing, runBlocking { store.load() }.single().task?.status)
    }
}
