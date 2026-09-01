package dev.mindgraph.storage

import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeStoreTest {
    private fun newStore(): NodeStore =
        NodeStore(Vault(Files.createTempDirectory("mindgraph-store")))

    @Test
    fun createdNodeSurvivesAReload() = runTest {
        val store = newStore()
        val created = store.create("First note", "Some **body** text")

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(created.id, loaded[0].id)
        assertEquals("First note", loaded[0].title)
        assertEquals("Some **body** text", loaded[0].body.trim())
        assertNull(loaded[0].task)
    }

    @Test
    fun taskFacetRoundTrips() = runTest {
        val store = newStore()
        store.create("A task", "", TaskFacet(status = TaskStatus.Doing, due = "2026-09-01"))

        val loaded = store.load().single()
        assertEquals(TaskStatus.Doing, loaded.task?.status)
        assertEquals("2026-09-01", loaded.task?.due)
    }

    @Test
    fun renamingKeepsTheIdAndMovesTheFile() = runTest {
        val store = newStore()
        val created = store.create("Original title")

        val renamed = store.save(created.copy(title = "A completely different title"))
        assertEquals(created.id, renamed.id)
        assertEquals("a-completely-different-title", renamed.slug)

        val loaded = store.load()
        assertEquals(1, loaded.size, "the old file should not be left behind")
        assertEquals(created.id, loaded[0].id)
    }

    @Test
    fun notesSharingATitleGetDistinctFiles() = runTest {
        val store = newStore()
        val first = store.create("Untitled")
        val second = store.create("Untitled")

        assertTrue(first.slug != second.slug, "slugs must not collide")
        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals(2, loaded.map { it.id }.distinct().size)
    }

    @Test
    fun edgesProjectFromFrontmatter() = runTest {
        val store = newStore()
        val target = store.create("Dependency")
        val source = store.create("Dependent")
        store.save(source.copy(dependsOn = listOf(target.id), relatesTo = listOf(target.id)))

        val edges = store.load().toEdges()
        assertEquals(2, edges.size)
        assertTrue(edges.any { it.kind == dev.mindgraph.model.EdgeKind.DependsOn })
        assertTrue(edges.any { it.kind == dev.mindgraph.model.EdgeKind.RelatesTo })
    }

    @Test
    fun deletingANodeClearsEdgesPointingAtIt() = runTest {
        val store = newStore()
        val target = store.create("Doomed")
        val source = store.create("Survivor")
        store.save(source.copy(dependsOn = listOf(target.id)))

        store.delete(target.id)

        val survivors = store.load()
        assertEquals(1, survivors.size)
        assertTrue(survivors[0].dependsOn.isEmpty(), "dangling dependency should be dropped")
        assertTrue(survivors.toEdges().isEmpty())
    }

    @Test
    fun handEditedFieldsAreNotDestroyedOnSave() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-extras"))
        val store = NodeStore(vault)
        val created = store.create("Keeps extras")

        val path = vault.nodesDir.resolve("${created.slug}.md")
        val edited = Files.readString(path).replaceFirst("---\n", "---\nauthor: iago\n")
        Files.writeString(path, edited)

        store.save(store.load().single().copy(title = "Keeps extras"))

        val front = Frontmatter.split(Files.readString(vault.nodesDir.resolve("keeps-extras.md"))).first
        assertEquals("iago", front.string("author"))
    }

    @Test
    fun filesWithoutAValidIdAreSkippedRatherThanCrashing() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-junk"))
        vault.prepare()
        Files.writeString(vault.nodesDir.resolve("stray.md"), "just a markdown file someone dropped in")

        val loaded = NodeStore(vault).load()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun bodyIsPreservedExactlyAcrossSaves() = runTest {
        val store = newStore()
        val body = "line one\n\n- bullet\n- another\n\n```kotlin\nval x = 1\n```"
        val created = store.create("Body test", body)

        val reloaded = store.load().single()
        assertEquals(body, reloaded.body.trim())

        store.save(reloaded.copy(title = "Body test renamed"))
        assertEquals(body, store.load().single().body.trim())
    }

    @Test
    fun wikilinksBecomeEdgesWithoutBeingWrittenToFrontmatter() = runTest {
        val store = newStore()
        val target = store.create("Target note")
        store.create("Source note", "text mentioning [[Target note]] inline")

        val nodes = store.load()
        val source = nodes.first { it.title == "Source note" }

        assertTrue(source.relatesTo.isEmpty(), "an inline link must not be copied into frontmatter")
        assertEquals(
            listOf(target.id),
            nodes.toEdges().filter { it.sourceId == source.id }.map { it.targetId },
        )
    }

    @Test
    fun removingAWikilinkRemovesItsEdge() = runTest {
        val store = newStore()
        store.create("Target note")
        val source = store.create("Source note", "linking [[Target note]] here")
        assertEquals(1, store.load().toEdges().size)

        store.save(store.load().first { it.id == source.id }.copy(body = "no links any more"))

        assertTrue(store.load().toEdges().isEmpty(), "the edge should die with the link")
    }

    @Test
    fun anInlineLinkIsNotDuplicatedByAnExplicitOne() = runTest {
        val store = newStore()
        val target = store.create("Target note")
        val source = store.create("Source note", "see [[Target note]]")
        store.save(store.load().first { it.id == source.id }.copy(relatesTo = listOf(target.id)))

        val edges = store.load().toEdges().filter { it.sourceId == source.id }
        assertEquals(1, edges.size, "the same relationship must not appear twice")
    }

    @Test
    fun ulidsSortChronologically() {
        val earlier = Ulid.generate(atMillis = 1_000_000L)
        val later = Ulid.generate(atMillis = 2_000_000L)
        assertTrue(earlier < later)
        assertTrue(Ulid.looksValid(earlier))
        assertNotNull(Ulid.looksValid(later).takeIf { it })
    }

    @Test
    fun kindDefaultsToNoteAndRoundTrips() = runTest {
        val store = newStore()
        store.create("A plain thought")

        assertEquals(NodeKind.Note, store.load().single().kind)
    }

    @Test
    fun anRfcKeepsItsKindAcrossAReload() = runTest {
        val store = newStore()
        store.create("RFC-001: the MCP surface", "Context and decision.", kind = NodeKind.Rfc)

        assertEquals(NodeKind.Rfc, store.load().single().kind)
    }

    @Test
    fun kindAndTheTaskFacetAreIndependent() = runTest {
        val store = newStore()
        // The point of keeping the axes apart: writing an RFC can itself be tracked work.
        store.create(
            "RFC-002: the import",
            "",
            TaskFacet(status = TaskStatus.Doing),
            NodeKind.Rfc,
        )

        val loaded = store.load().single()
        assertEquals(NodeKind.Rfc, loaded.kind)
        assertEquals(TaskStatus.Doing, loaded.task?.status)
        assertTrue(loaded.isTask)
    }

    @Test
    fun aFileWithNoKindIsReadAsANote() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-legacy"))
        vault.prepare()
        // A vault written before `kind` existed, or a file typed by hand.
        Files.writeString(
            vault.nodesDir.resolve("older.md"),
            """
            ---
            id: 01M0V4BQMAJ000RTB5PNFK2P5N
            title: Written before kind existed
            ---

            Still a perfectly good note.
            """.trimIndent(),
        )

        assertEquals(NodeKind.Note, NodeStore(vault).load().single().kind)
    }

    @Test
    fun anUnrecognisedKindCostsTheLabelNotTheFile() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-typo"))
        vault.prepare()
        Files.writeString(
            vault.nodesDir.resolve("typo.md"),
            """
            ---
            id: 01M0V4BQMAJ000RTB5PNFK2P5N
            title: Kind with a typo
            kind: refrence
            ---

            Body survives.
            """.trimIndent(),
        )

        val loaded = NodeStore(vault).load().single()
        assertEquals(NodeKind.Note, loaded.kind)
        assertEquals("Kind with a typo", loaded.title)
    }

    @Test
    fun changingKindSurvivesASave() = runTest {
        val store = newStore()
        val created = store.create("Promote me")

        store.save(created.copy(kind = NodeKind.Reference))

        assertEquals(NodeKind.Reference, store.load().single().kind)
    }
}
