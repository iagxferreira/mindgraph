package dev.mindgraph.storage

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Links between imported notes, in the dialects an Obsidian vault actually writes.
 *
 * These go through the store rather than calling [WikiLinks] directly, because the defect they
 * cover was exactly a field that was written, documented as the thing that makes links resolve,
 * and never read on the way back in.
 */
class ObsidianLinkTest {

    private fun newStore(): Pair<NodeStore, Path> {
        val vault = Vault(Files.createTempDirectory("mindgraph-obsidian"))
        return NodeStore(vault) to Files.createTempDirectory("obsidian-source")
    }

    private fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        file.parent?.createDirectories()
        file.writeText(text)
        return file
    }

    private suspend fun importAll(store: NodeStore, root: Path) {
        FolderImport.scan(root).forEach { file ->
            FolderImport.read(file, root, NodeKind.Note, "obsidian")?.let { doc ->
                store.create(
                    title = doc.title,
                    body = doc.body,
                    kind = doc.kind,
                    extras = FolderImport.extrasFor(doc),
                )
            }
        }
    }

    @Test
    fun aLinkByFilenameResolvesEvenThoughTheTitleIsAHeading() = runTest {
        // The whole reason documentName is recorded. Obsidian links by filename; the title comes
        // from the heading, so these two names are never the same.
        val (store, root) = newStore()
        write(root, "money-representation.md", "# How we represent money\n\nDetail.")
        write(root, "ledger.md", "# The ledger\n\nSee [[money-representation]].")
        importAll(store, root)

        val nodes = store.load()
        val edges = nodes.toEdges()
        val target = nodes.single { it.title == "How we represent money" }
        assertTrue(
            edges.any { it.kind == EdgeKind.RelatesTo && it.targetId == target.id },
            "linked by filename but resolved to nothing: ${nodes.map { it.title }}",
        )
    }

    @Test
    fun anEmbeddedImageIsNotALink() = runTest {
        val (store, root) = newStore()
        write(root, "note.md", "# Note\n\n![[20220412011351.png]]\n")
        importAll(store, root)

        val nodes = store.load()
        assertTrue(nodes.toEdges().isEmpty(), "an embed is an attachment, not a reference")
        assertTrue(
            WikiLinks.unresolved(nodes.single().body, nodes).isEmpty(),
            "an embed must not be offered as a note to create",
        )
    }

    @Test
    fun anEmbedAndALinkInOneBodyKeepOnlyTheLink() = runTest {
        val (store, root) = newStore()
        write(root, "target.md", "# Target")
        write(root, "note.md", "# Note\n\n![[diagram.png]] and [[target]].")
        importAll(store, root)

        val nodes = store.load()
        val edges = nodes.toEdges()
        assertEquals(1, edges.size, "one link, one edge")
        assertEquals(nodes.single { it.title == "Target" }.id, edges.single().targetId)
    }

    @Test
    fun aPathStyleLinkResolvesByItsLastSegment() = runTest {
        val (store, root) = newStore()
        write(root, "estudos/elixir/roadmap.md", "# Elixir roadmap")
        write(root, "index.md", "# Index\n\nSee [[estudos/elixir/roadmap]].")
        importAll(store, root)

        val nodes = store.load()
        val target = nodes.single { it.title == "Elixir roadmap" }
        assertTrue(
            nodes.toEdges().any { it.targetId == target.id },
            "a path-style link must find the note at the end of the path",
        )
    }

    @Test
    fun anExactNameStillBeatsTheLeafOfAPath() = runTest {
        // The full string is tried first, so a note genuinely called "a/b" is not shadowed.
        val (store, root) = newStore()
        write(root, "roadmap.md", "# Roadmap")
        write(root, "index.md", "# Index\n\n[[roadmap]]")
        importAll(store, root)

        val nodes = store.load()
        assertEquals(
            nodes.single { it.title == "Roadmap" }.id,
            nodes.toEdges().single().targetId,
        )
    }

    @Test
    fun aLinkToANoteThatDoesNotExistIsStillDangling() = runTest {
        // Obsidian allows linking ahead of writing, and that must keep working.
        val (store, root) = newStore()
        write(root, "note.md", "# Note\n\n[[not written yet]]")
        importAll(store, root)

        val nodes = store.load()
        assertTrue(nodes.toEdges().isEmpty())
        assertEquals(listOf("not written yet"), WikiLinks.unresolved(nodes.single().body, nodes))
    }
}
