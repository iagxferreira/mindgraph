package dev.mindgraph.mcp

import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.model.Worker
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
import kotlin.test.assertTrue

/** An agent moving a task through doing and out again, and the time that leaves behind. */
class McpMachineLabourTest {

    private lateinit var store: NodeStore
    private lateinit var log: SessionLog
    private lateinit var dispatcher: McpDispatcher

    /** Stretches are pinned to 25 minutes so the assertions don't depend on wall-clock speed. */
    private fun newVault(elapsed: Long = 1500) {
        val vault = Vault(Files.createTempDirectory("mindgraph-labour"))
        store = NodeStore(vault)
        log = SessionLog(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, log, elapsedOverride = elapsed)))
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

    private fun status(node: String, status: String, agent: String? = null): JsonObject =
        call(
            "update_status",
            buildJsonObject {
                put("node", node)
                put("status", status)
                agent?.let { put("agent", it) }
            },
        )

    private fun task(title: String) =
        runBlocking { store.create(title, "", TaskFacet(TaskStatus.Todo)) }

    @Test
    fun timeBetweenDoingAndDoneIsLoggedAsMachineLabour() {
        newVault()
        task("Write the importer")

        status("Write the importer", "doing", agent = "claude-code")
        status("Write the importer", "done", agent = "claude-code")

        val session = runBlocking { log.load() }.single()
        assertEquals(Worker.Agent, session.worker)
        assertEquals("claude-code", session.agent)
        assertEquals(1500, session.seconds)
    }

    @Test
    fun theAgentIsToldTheClockStarted() {
        newVault()
        task("A task")

        val text = status("A task", "doing", agent = "claude-code").text()

        assertTrue("clock is running" in text, text)
        assertTrue("claude-code" in text, text)
    }

    @Test
    fun closingReportsTheTimeItTook() {
        newVault()
        task("A task")
        status("A task", "doing", agent = "claude-code")

        val text = status("A task", "done", agent = "claude-code").text()

        assertTrue("25m tracked on it" in text, text)
    }

    @Test
    fun anAgentThatDoesNotNameItselfIsStillMachineLabour() {
        newVault()
        task("A task")

        val text = status("A task", "doing").text()
        status("A task", "done")

        assertTrue("against you" in text, text)
        val session = runBlocking { log.load() }.single()
        assertEquals(Worker.Agent, session.worker)
        assertEquals(null, session.agent)
    }

    @Test
    fun abandoningATaskStillRecordsWhatWasSpentOnIt() {
        newVault()
        task("A dead end")
        status("A dead end", "doing", agent = "claude-code")

        status("A dead end", "dropped", agent = "claude-code")

        assertEquals(1500, runBlocking { log.load() }.single().seconds)
    }

    @Test
    fun aTaskNeverStartedLogsNothing() {
        newVault()
        task("Straight to done")

        status("Straight to done", "done", agent = "claude-code")

        assertTrue(runBlocking { log.load() }.isEmpty())
    }
}
