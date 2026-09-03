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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Agents building and reading a context bundle over MCP. */
class McpContextLinkTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-context-mcp"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun note(title: String): Node = runBlocking { store.create(title, "") }

    private fun call(tool: String, args: Map<String, String>): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put(
                    "params",
                    buildJsonObject {
                        put("name", tool)
                        put("arguments", buildJsonObject { args.forEach { (k, v) -> put(k, v) } })
                    },
                )
            },
        )!!["result"]!!.jsonObject

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    private fun JsonObject.isError(): Boolean =
        this["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false

    @Test
    fun anAgentCanMarkANoteAsContextForAProject() {
        newVault()
        val project = note("Build the thing")
        val convention = note("Commit style we settled on")

        val result = call("link_nodes", mapOf("from" to convention.title, "to" to project.title, "kind" to "context_for"))

        assertTrue(!result.isError(), result.text())
        assertTrue(result.text().contains("is now context for"), result.text())
        val saved = runBlocking { store.load() }.single { it.id == convention.id }
        assertEquals(listOf(project.id), saved.contextFor)
    }

    @Test
    fun contextDoesNotBlockTheWorkItServes() {
        // The distinction that justifies a third kind: context is loaded, not waited for.
        newVault()
        val project = note("Build the thing")
        val convention = note("Commit style we settled on")
        call("link_nodes", mapOf("from" to convention.title, "to" to project.title, "kind" to "context_for"))

        val saved = runBlocking { store.load() }.single { it.id == convention.id }
        assertTrue(saved.dependsOn.isEmpty())
        assertTrue(saved.relatesTo.isEmpty())
    }

    @Test
    fun getNodeReportsTheBundleToLoad() {
        newVault()
        val project = note("Build the thing")
        val a = note("Commit style")
        val b = note("How the vault is laid out")
        call("link_nodes", mapOf("from" to a.title, "to" to project.title, "kind" to "context_for"))
        call("link_nodes", mapOf("from" to b.title, "to" to project.title, "kind" to "context_for"))

        val text = call("get_node", mapOf("node" to project.title)).text()

        assertTrue(text.contains("context to load (2)"), text)
        assertTrue(text.contains("Commit style"), text)
        assertTrue(text.contains("How the vault is laid out"), text)
    }

    @Test
    fun getNodeReportsWhatANoteServes() {
        newVault()
        val project = note("Build the thing")
        val a = note("Commit style")
        call("link_nodes", mapOf("from" to a.title, "to" to project.title, "kind" to "context_for"))

        val text = call("get_node", mapOf("node" to a.title)).text()
        assertTrue(text.contains("is context for: \"Build the thing\""), text)
    }

    @Test
    fun anUnknownKindSaysWhatIsAllowed() {
        newVault()
        val a = note("A")
        val b = note("B")

        val result = call("link_nodes", mapOf("from" to a.title, "to" to b.title, "kind" to "context"))

        assertTrue(result.isError())
        assertTrue(result.text().contains("context_for"), result.text())
    }
}
