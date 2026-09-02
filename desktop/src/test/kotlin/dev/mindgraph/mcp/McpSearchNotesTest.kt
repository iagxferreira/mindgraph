package dev.mindgraph.mcp

import dev.mindgraph.model.NodeKind
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

class McpSearchNotesTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-search"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun call(arguments: JsonObject): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", buildJsonObject { put("name", "search_notes"); put("arguments", arguments) })
            },
        )!!["result"]!!.jsonObject

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun returnsIdTitleKindAndContextForEachMatchingNode() {
        newVault()
        val note = runBlocking {
            store.create("Commit conventions", "Use conventional commits in small atomic steps.")
        }
        runBlocking { store.create("Unrelated", "Nothing about history.", kind = NodeKind.Rfc) }

        val result = call(buildJsonObject { put("query", "commit") })
        val text = result.text()

        assertFalse(result["isError"]!!.jsonPrimitive.content.toBoolean(), text)
        assertTrue(note.id.value in text, text)
        assertTrue("Commit conventions" in text, text)
        assertTrue("note" in text, text)
        assertTrue("Use conventional commits" in text, text)
    }

    @Test
    fun searchesAliasesAndReportsNoMatchesClearly() {
        newVault()
        runBlocking {
            val node = store.create("The long title", "Body")
            store.save(node.copy(aliases = listOf("short-memory-name")))
        }

        assertTrue("Also known as: short-memory-name" in call(buildJsonObject { put("query", "memory") }).text())
        assertEquals("No nodes match \"missing\".", call(buildJsonObject { put("query", "missing") }).text())
    }

    @Test
    fun blankQueriesAreRejected() {
        newVault()

        val result = call(buildJsonObject { put("query", "   ") })

        assertTrue(result["isError"]!!.jsonPrimitive.content.toBoolean())
        assertTrue("query is required" in result.text())
    }

    @Test
    fun theToolIsAdvertisedWithItsRequiredQuery() {
        newVault()

        val tools = dispatcher.handle(
            buildJsonObject { put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/list") },
        )!!["result"]!!.jsonObject["tools"]!!.jsonArray
        val tool = tools.map { it.jsonObject }
            .single { it["name"]!!.jsonPrimitive.content == "search_notes" }

        assertEquals(listOf("query"), tool["inputSchema"]!!.jsonObject["required"]!!.jsonArray
            .map { it.jsonPrimitive.content })
    }
}
