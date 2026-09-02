package dev.mindgraph.storage

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.NodeId
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `context_for` through the vault: written, read back, and projected as an edge. */
class ContextEdgeTest {

    private fun newStore(): NodeStore = NodeStore(Vault(Files.createTempDirectory("mindgraph-context")))

    @Test
    fun contextForRoundTripsThroughFrontmatter() = runTest {
        val store = newStore()
        val project = store.create("A new project", "")
        val note = store.create("A convention worth loading", "")

        store.save(note.copy(contextFor = listOf(project.id)))

        val reloaded = store.load().single { it.id == note.id }
        assertEquals(listOf(project.id), reloaded.contextFor)
    }

    @Test
    fun contextForIsWrittenUnderItsOwnKey() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-context-raw"))
        val store = NodeStore(vault)
        val project = store.create("Project", "")
        val note = store.create("Note", "")
        store.save(note.copy(contextFor = listOf(project.id)))

        // Read the raw markdown: the point of the vault is that the file describes itself, so
        // the key has to be there in plain text and not only in the parsed model.
        val raw = Files.list(vault.nodesDir).use { files ->
            files.toList().map { Files.readString(it) }
        }.single { it.contains("title: Note") }
        assertTrue(raw.contains("context_for: [${project.id.value}]"), "frontmatter was:\n$raw")
    }

    @Test
    fun contextBecomesAnEdgeOfItsOwnKind() = runTest {
        val store = newStore()
        val project = store.create("Project", "")
        val note = store.create("Note", "")
        store.save(note.copy(contextFor = listOf(project.id)))

        val edges = store.load().toEdges()
        val edge = edges.single { it.kind == EdgeKind.ContextFor }
        assertEquals(note.id, edge.sourceId, "the context node points at what it serves")
        assertEquals(project.id, edge.targetId)
    }

    @Test
    fun contextIsNeverInferredFromAWikilink() = runTest {
        // A mention in prose must not quietly enlarge what an agent is asked to load.
        val store = newStore()
        val project = store.create("Project", "")
        val note = store.create("Note", "see [[Project]] for background")

        val edges = store.load().toEdges()
        assertTrue(edges.none { it.kind == EdgeKind.ContextFor }, "a wikilink is association, not context")
        assertTrue(edges.any { it.kind == EdgeKind.RelatesTo && it.sourceId == note.id })
        assertEquals(project.id, edges.first { it.kind == EdgeKind.RelatesTo }.targetId)
    }

    @Test
    fun anEdgeToAMissingNodeIsDropped() = runTest {
        val store = newStore()
        val note = store.create("Note", "")
        store.save(note.copy(contextFor = listOf(NodeId("01M1J8GJR3T1KZWB8C7KPZ5YE7"))))

        assertTrue(store.load().toEdges().none { it.kind == EdgeKind.ContextFor })
    }

    @Test
    fun oneNoteCanBeContextForSeveralProjects() = runTest {
        val store = newStore()
        val a = store.create("Project A", "")
        val b = store.create("Project B", "")
        val note = store.create("Shared convention", "")
        store.save(note.copy(contextFor = listOf(a.id, b.id)))

        val edges = store.load().toEdges().filter { it.kind == EdgeKind.ContextFor }
        assertEquals(setOf(a.id, b.id), edges.map { it.targetId }.toSet())
    }
}
