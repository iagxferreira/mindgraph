package dev.mindgraph.mcp

import dev.mindgraph.model.Node
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
import kotlin.test.assertTrue

/** Asking the vault what it should be linked to. */
class McpSuggestLinksTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-suggest"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun note(title: String, body: String = ""): Node = runBlocking { store.create(title, body) }

    private fun call(args: Map<String, String>): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/call")
                put(
                    "params",
                    buildJsonObject {
                        put("name", "suggest_links")
                        put("arguments", buildJsonObject { args.forEach { (k, v) -> put(k, v) } })
                    },
                )
            },
        )!!["result"]!!.jsonObject

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    @Test
    fun theToolIsOffered() {
        newVault()
        val listed = dispatcher.handle(
            buildJsonObject { put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/list") },
        )!!["result"]!!.jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("suggest_links" in listed, listed.toString())
    }

    @Test
    fun itNamesBothNodesAndSaysWhy() {
        newVault()
        note("Money representation", "")
        note("The ledger", "We settled this in Money representation last week.")

        val text = call(emptyMap()).text()

        assertTrue(text.contains("The ledger"), text)
        assertTrue(text.contains("Money representation"), text)
        assertTrue(text.contains("without linking"), "the reason must be readable: $text")
    }

    @Test
    fun itSaysItLinkedNothing() {
        // The tool proposes; an edge is a claim about meaning and someone has to make it.
        newVault()
        note("Money representation", "")
        val ledger = note("The ledger", "See Money representation.")

        val text = call(emptyMap()).text()

        assertTrue(text.contains("Nothing was linked"), text)
        assertTrue(runBlocking { store.load() }.single { it.id == ledger.id }.relatesTo.isEmpty())
    }

    @Test
    fun scopingToANodeNarrowsIt() {
        newVault()
        note("Money representation", "")
        note("The ledger", "See Money representation.")
        note("Somewhere else", "Nothing relevant in here at all.")

        val text = call(mapOf("node" to "Money representation")).text()
        assertTrue(text.contains("for \"Money representation\""), text)
        assertTrue(!text.contains("Somewhere else"), text)
    }

    @Test
    fun anEmptyResultExplainsItself() {
        newVault()
        note("One note", "Nothing to see.")
        note("Another note", "Also nothing.")

        val text = call(emptyMap()).text()
        assertTrue(text.contains("No unlinked connections"), text)
    }
}
