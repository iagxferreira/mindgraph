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

/**
 * Appending is the one carve-out from "agents do not rewrite nodes", so what these tests are
 * really protecting is the *narrowness* of it: text goes on the end, and nothing else moves.
 */
class McpAppendBodyTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-append"))
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

    private fun append(node: String, content: String): JsonObject =
        call(
            "append_node_body",
            buildJsonObject { put("node", node); put("content", content) },
        )

    @Test
    fun appendedContentLandsAfterWhatWasAlreadyThere() {
        newVault()
        runBlocking { store.create("Notes", "The original paragraph.") }

        val result = append("Notes", "What I found later.")
        assertFalse(result.isError())

        assertEquals(
            "The original paragraph.\n\nWhat I found later.",
            runBlocking { store.load() }.single().body.trim(),
        )
    }

    @Test
    fun repeatedAppendsAccumulateInOrder() {
        newVault()
        runBlocking { store.create("Log", "First.") }

        append("Log", "Second.")
        append("Log", "Third.")

        assertEquals(
            "First.\n\nSecond.\n\nThird.",
            runBlocking { store.load() }.single().body.trim(),
        )
    }

    @Test
    fun appendingToAnEmptyBodyDoesNotLeaveLeadingBlankLines() {
        newVault()
        runBlocking { store.create("Fresh", "") }

        append("Fresh", "The only entry.")

        assertEquals("The only entry.", runBlocking { store.load() }.single().body.trim())
    }

    @Test
    fun appendingChangesNothingButTheBody() {
        newVault()
        val before = runBlocking {
            store.create(
                "A tracked task",
                "Why it matters.",
                TaskFacet(TaskStatus.Doing, due = "2026-09-30"),
                kind = NodeKind.Rfc,
                assignee = "iago",
            )
        }

        append(before.id.value, "A note from the agent.")

        // The whole promise of the tool: every other field survives untouched.
        val after = runBlocking { store.load() }.single()
        assertEquals(before.id, after.id)
        assertEquals(before.title, after.title)
        assertEquals(NodeKind.Rfc, after.kind)
        assertEquals(TaskStatus.Doing, after.task!!.status)
        assertEquals("2026-09-30", after.task!!.due)
        assertEquals("iago", after.assignee)
        assertEquals(before.archived, after.archived)
        assertTrue(after.body.contains("Why it matters."))
    }

    @Test
    fun emptyContentIsRefusedAndTheBodyIsUntouched() {
        newVault()
        runBlocking { store.create("Notes", "Do not touch this.") }

        val result = append("Notes", "   ")

        assertTrue(result.isError())
        assertEquals("Do not touch this.", runBlocking { store.load() }.single().body.trim())
    }

    @Test
    fun anUnknownNodeIsRefused() {
        newVault()
        val result = append("Nothing by that name", "Some content.")

        assertTrue(result.isError())
        assertTrue(result.text().contains("No node matches"))
    }

    @Test
    fun theToolIsAdvertisedWithItsSchema() {
        newVault()
        val tools = dispatcher.handle(
            buildJsonObject { put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/list") },
        )!!["result"]!!.jsonObject["tools"]!!.jsonArray

        val tool = tools.map { it.jsonObject }
            .single { it["name"]!!.jsonPrimitive.content == "append_node_body" }
        val required = tool["inputSchema"]!!.jsonObject["required"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(listOf("node", "content"), required)
    }
}
