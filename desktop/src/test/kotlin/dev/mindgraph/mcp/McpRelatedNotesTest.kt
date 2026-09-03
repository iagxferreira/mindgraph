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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `related_notes`: the context an agent loads before starting work. */
class McpRelatedNotesTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-related"))
        store = NodeStore(vault)
        dispatcher = McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault))))
    }

    private fun note(title: String, body: String = ""): Node = runBlocking { store.create(title, body) }

    private fun save(node: Node) = runBlocking { store.save(node) }

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
    fun theToolIsOffered() {
        newVault()
        val listed = dispatcher.handle(
            buildJsonObject {
                put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/list")
            },
        )!!["result"]!!.jsonObject["tools"]!!.jsonArray
            .map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("related_notes" in listed, listed.toString())
    }

    @Test
    fun theGoldfishTest() {
        // The article's acceptance criterion: hand a blank agent only this document and it
        // should be able to start. So the bundle must carry the bodies of the things it names,
        // not merely their titles - a title is a second lookup, which a goldfish cannot do.
        newVault()
        val project = note("Rewrite the importer", "Split the importers into one module per agent.")
        val convention = note("Commit style", "One layer per commit, Conventional Commits.")
        val gotcha = note("Gradle overwrites classes", "Restart the app after a rebuild.")
        save(convention.copy(contextFor = listOf(project.id)))
        save(gotcha.copy(contextFor = listOf(project.id)))

        val text = call("related_notes", mapOf("topic" to "Rewrite the importer")).text()

        assertTrue(text.contains("Split the importers into one module per agent."), "seed body missing")
        assertTrue(text.contains("One layer per commit, Conventional Commits."), "curated body missing")
        assertTrue(text.contains("Restart the app after a rebuild."), "curated body missing")
    }

    @Test
    fun aTopicNeedNotBeAnExactTitle() {
        newVault()
        note("Resolve imported wikilinks into edges", "Aliases let a node answer to more names.")

        val text = call("related_notes", mapOf("topic" to "wikilinks")).text()
        assertTrue(text.contains("Resolve imported wikilinks into edges"), text.take(200))
    }

    @Test
    fun itCrossesProjectsWhichIsTheWholePoint() {
        // The node's own "done when": a note recorded in one repository's session must surface
        // for work in another.
        newVault()
        val work = note("Set up a new service", "")
        val elsewhere = note("Commit style feedback from leadfinder", "Small atomic commits.")
        save(elsewhere.copy(contextFor = listOf(work.id)))

        val text = call("related_notes", mapOf("topic" to "Set up a new service")).text()
        assertTrue(text.contains("Small atomic commits."), text)
    }

    @Test
    fun theDocumentSaysWhyEachPieceIsInIt() {
        newVault()
        val project = note("Project", "")
        val chosen = note("Chosen", "chosen body")
        save(chosen.copy(contextFor = listOf(project.id)))

        val text = call("related_notes", mapOf("topic" to "Project")).text()
        assertTrue(text.contains("chosen as context"), text)
    }

    @Test
    fun theBudgetIsHonouredAndReported() {
        newVault()
        val project = note("Project", "seed")
        repeat(6) { i ->
            val big = note("Big $i", "x".repeat(2_000))
            save(big.copy(contextFor = listOf(project.id)))
        }

        val text = call("related_notes", mapOf("topic" to "Project", "budget_tokens" to "500")).text()
        assertTrue(text.length <= 500 * 4 + 800, "document was ${text.length} chars")
        assertTrue(text.contains("Not included"), "what did not fit must be listed")
    }

    @Test
    fun hopsNarrowsAndWidensTheWalk() {
        newVault()
        val a = note("A", "")
        val b = note("B", "body of B")
        val c = note("C", "body of C")
        save(b.copy(relatesTo = listOf(a.id)))
        save(c.copy(relatesTo = listOf(b.id)))

        val near = call("related_notes", mapOf("topic" to "A", "hops" to "1")).text()
        assertTrue(near.contains("body of B"))
        assertFalse(near.contains("body of C"), "one hop should not reach C")

        val far = call("related_notes", mapOf("topic" to "A", "hops" to "2")).text()
        assertTrue(far.contains("body of C"))
    }

    @Test
    fun anUnmatchedTopicSaysWhatToDoNext() {
        newVault()
        note("Something", "")
        val result = call("related_notes", mapOf("topic" to "zzzzz-nothing-like-this"))
        assertTrue(result.isError())
        assertTrue(result.text().contains("search_notes"), result.text())
    }

    @Test
    fun anEmptyVaultFailsClearly() {
        newVault()
        val result = call("related_notes", mapOf("topic" to "anything"))
        assertTrue(result.isError())
        assertTrue(result.text().contains("empty"), result.text())
    }

    @Test
    fun aLoneNodeStillReturnsItsOwnDocument() {
        newVault()
        note("Alone", "the only thing here")
        val text = call("related_notes", mapOf("topic" to "Alone")).text()
        assertTrue(text.contains("the only thing here"))
        assertEquals(false, text.contains("Not included"))
    }
}
