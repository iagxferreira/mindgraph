package dev.mindgraph.mcp

import dev.mindgraph.model.Node
import dev.mindgraph.state.TaskGraph
import dev.mindgraph.storage.NodeStore
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

class McpLinkToolTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault(): StoreVault {
        store = NodeStore(Vault(Files.createTempDirectory("mindgraph-link")))
        val vault = StoreVault(store)
        dispatcher = McpDispatcher(mindGraphTools(vault))
        return vault
    }

    private fun task(title: String): Node = runBlocking { newTask(title) }

    private suspend fun newTask(title: String): Node = store.create(
        title,
        "",
        dev.mindgraph.model.TaskFacet(dev.mindgraph.model.TaskStatus.Todo),
    )

    private fun link(from: String, to: String, kind: String): JsonObject =
        dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put(
                    "params",
                    buildJsonObject {
                        put("name", "link_nodes")
                        put(
                            "arguments",
                            buildJsonObject {
                                put("from", from)
                                put("to", to)
                                put("kind", kind)
                            },
                        )
                    },
                )
            },
        )!!["result"]!!.jsonObject

    private fun JsonObject.isError(): Boolean = this["isError"]!!.jsonPrimitive.content.toBoolean()

    private fun JsonObject.text(): String =
        this["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content

    private fun reload(): List<Node> = runBlocking { store.load() }

    @Test
    fun aDependencyIsWrittenToTheVault() {
        newVault()
        val server = task("Serve MCP over HTTP")
        val tools = task("Add link tools")

        val result = link(tools.id.value, server.id.value, "depends_on")

        assertFalse(result.isError(), result.text())
        val reloaded = reload().single { it.id == tools.id }
        assertEquals(listOf(server.id), reloaded.dependsOn)
    }

    @Test
    fun nodesCanBeNamedByTitleBecauseThatIsWhatAModelHas() {
        newVault()
        task("Import the memory notes")
        task("Add a kind field")

        val result = link("Import the memory notes", "Add a kind field", "depends_on")

        assertFalse(result.isError(), result.text())
        assertTrue(reload().single { it.title == "Import the memory notes" }.dependsOn.isNotEmpty())
    }

    @Test
    fun theGraphImmediatelyTreatsTheDependentTaskAsBlocked() {
        newVault()
        val first = task("Add a kind field")
        val second = task("Import the memory notes")

        link(second.id.value, first.id.value, "depends_on")

        // The point of the edge: readiness is computed, never declared.
        val graph = TaskGraph(reload())
        assertTrue(graph.isBlocked(second.id))
        assertEquals(listOf(first.id), graph.readyTasks().map { it.id })
    }

    @Test
    fun aCycleIsRefusedAndNothingIsWritten() {
        newVault()
        val a = task("A")
        val b = task("B")
        link(b.id.value, a.id.value, "depends_on")

        val result = link(a.id.value, b.id.value, "depends_on")

        assertTrue(result.isError())
        assertTrue("cycle" in result.text(), result.text())
        assertTrue(reload().single { it.id == a.id }.dependsOn.isEmpty())
    }

    @Test
    fun aRepeatedLinkChangesNothingAndIsNotAnError() {
        newVault()
        val a = task("A")
        val b = task("B")
        link(a.id.value, b.id.value, "relates_to")

        val result = link(a.id.value, b.id.value, "relates_to")

        assertFalse(result.isError())
        assertEquals(1, reload().single { it.id == a.id }.relatesTo.size)
    }

    @Test
    fun aNodeCannotDependOnItself() {
        newVault()
        val a = task("A")

        val result = link(a.id.value, a.id.value, "depends_on")

        assertTrue(result.isError())
    }

    @Test
    fun anAmbiguousTitleReportsTheIdsToDisambiguateWith() {
        newVault()
        val first = task("Same title")
        val second = task("Same title")
        task("Other")

        val result = link("Same title", "Other", "depends_on")

        assertTrue(result.isError())
        assertTrue(first.id.value in result.text() && second.id.value in result.text())
    }

    @Test
    fun anUnknownNodeSaysSoRatherThanFailingSilently() {
        newVault()
        task("Only node")

        val result = link("Only node", "A node that does not exist", "depends_on")

        assertTrue(result.isError())
        assertTrue("No node matches" in result.text(), result.text())
    }

    @Test
    fun anInvalidKindIsRejected() {
        newVault()
        task("A")
        task("B")

        val result = link("A", "B", "blocks")

        assertTrue(result.isError())
        assertTrue("depends_on" in result.text())
    }
}
