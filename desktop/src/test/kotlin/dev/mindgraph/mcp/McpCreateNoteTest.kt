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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Creating something that is *not* work. Before this, an agent capturing a finding had to mint
 * a task, which then sat in the ready queue forever as work nobody would ever pick up.
 */
class McpCreateNoteTest {

    private lateinit var store: NodeStore
    private lateinit var dispatcher: McpDispatcher

    private fun newVault() {
        val vault = Vault(Files.createTempDirectory("mindgraph-note"))
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

    @Test
    fun aCreatedNoteRoundTripsThroughTheVault() {
        newVault()
        val result = call(
            "create_note",
            buildJsonObject {
                put("title", "Gradle overwrites classes under the running app")
                put("body", "Restart after every rebuild.")
            },
        )
        assertFalse(result.isError())

        val saved = runBlocking { store.load() }.single()
        assertEquals("Gradle overwrites classes under the running app", saved.title)
        assertEquals("Restart after every rebuild.", saved.body.trim())
        assertTrue(result.text().contains(saved.id.value))
    }

    @Test
    fun aNoteHasNoTaskFacetAndNeverEntersReadyWork() {
        newVault()
        call("create_note", buildJsonObject { put("title", "A finding") })

        // The whole point: no status, so nothing to do, so it cannot be ranked as ready.
        assertNull(runBlocking { store.load() }.single().task)

        val ready = call("list_ready_tasks", buildJsonObject { })
        assertFalse(ready.text().contains("A finding"))
    }

    @Test
    fun eachKindRoundTripsAsItself() {
        newVault()
        NodeKind.entries.forEach { kind ->
            call(
                "create_note",
                buildJsonObject { put("title", "About ${kind.slug}"); put("kind", kind.slug) },
            )
        }

        val byTitle = runBlocking { store.load() }.associateBy { it.title }
        NodeKind.entries.forEach { kind ->
            assertEquals(kind, byTitle.getValue("About ${kind.slug}").kind)
        }
    }

    @Test
    fun anOmittedKindDefaultsToNote() {
        newVault()
        call("create_note", buildJsonObject { put("title", "Unlabelled") })
        assertEquals(NodeKind.Note, runBlocking { store.load() }.single().kind)
    }

    @Test
    fun anUnknownKindIsRefusedAndCreatesNothing() {
        newVault()
        val result = call(
            "create_note",
            buildJsonObject { put("title", "Nope"); put("kind", "task") },
        )
        assertTrue(result.isError())
        assertTrue(result.text().contains("kind must be one of"))
        assertTrue(runBlocking { store.load() }.isEmpty())
    }

    @Test
    fun aNoteCanBeAssignedLikeAnyOtherNode() {
        newVault()
        call(
            "create_note",
            buildJsonObject { put("title", "For review"); put("assignee", "iago") },
        )
        assertEquals("iago", runBlocking { store.load() }.single().assignee)
    }
}
