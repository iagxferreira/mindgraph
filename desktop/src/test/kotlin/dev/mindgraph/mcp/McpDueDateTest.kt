package dev.mindgraph.mcp

import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import java.time.LocalDate
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

/** Setting and seeing deadlines over MCP. */
class McpDueDateTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-due"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

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

    private fun JsonObject.isError(): Boolean = this["isError"]!!.jsonPrimitive.content.toBoolean()

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun createTaskStoresTheDeadline() {
        newVault()

        val result = call(
            "create_task",
            buildJsonObject { put("title", "Ship the demo"); put("due", "2026-09-04") },
        )

        assertFalse(result.isError(), result.text())
        assertEquals("2026-09-04", runBlocking { store.load() }.single().task?.due)
    }

    @Test
    fun aDateNobodyCanParseIsRejectedAtTheBoundary() {
        newVault()

        val result = call(
            "create_task",
            buildJsonObject { put("title", "Vague"); put("due", "next tuesday") },
        )

        // Storing it would leave a task that silently never becomes urgent.
        assertTrue(result.isError())
        assertTrue("2026-09-04" in result.text(), result.text())
        assertTrue(runBlocking { store.load() }.isEmpty())
    }

    @Test
    fun updateStatusCanSetADeadline() {
        newVault()
        runBlocking { store.create("Existing", "", TaskFacet(TaskStatus.Todo)) }

        call(
            "update_status",
            buildJsonObject {
                put("node", "Existing")
                put("status", "doing")
                put("due", "2026-09-10")
            },
        )

        assertEquals("2026-09-10", runBlocking { store.load() }.single().task?.due)
    }

    @Test
    fun closingATaskDoesNotEraseTheDeadlineItWasClosedAgainst() {
        newVault()
        runBlocking { store.create("Has a deadline", "", TaskFacet(TaskStatus.Todo, due = "2026-09-04")) }

        call("update_status", buildJsonObject { put("node", "Has a deadline"); put("status", "done") })

        assertEquals("2026-09-04", runBlocking { store.load() }.single().task?.due)
    }

    @Test
    fun listReadyTasksMarksOverdueWorkAsOverdue() {
        newVault()
        val past = LocalDate.now().minusDays(5).toString()
        runBlocking { store.create("Late thing", "", TaskFacet(TaskStatus.Todo, due = past)) }

        val text = call("list_ready_tasks", buildJsonObject {}).text()

        assertTrue("OVERDUE" in text, text)
        assertTrue(past in text, text)
    }

    @Test
    fun listReadyTasksShowsAFutureDeadlineWithoutShouting() {
        newVault()
        val future = LocalDate.now().plusDays(10).toString()
        runBlocking { store.create("Later thing", "", TaskFacet(TaskStatus.Todo, due = future)) }

        val text = call("list_ready_tasks", buildJsonObject {}).text()

        assertTrue("due $future" in text, text)
        assertFalse("OVERDUE" in text, text)
    }

    @Test
    fun theOverdueTaskIsListedFirst() {
        newVault()
        runBlocking {
            store.create("Not urgent", "", TaskFacet(TaskStatus.Todo))
            store.create(
                "Late thing",
                "",
                TaskFacet(TaskStatus.Todo, due = LocalDate.now().minusDays(1).toString()),
            )
        }

        val lines = call("list_ready_tasks", buildJsonObject {}).text().lines()
            .filter { it.startsWith("- ") }

        assertTrue("Late thing" in lines.first(), lines.toString())
    }
}
