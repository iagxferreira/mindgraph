package dev.mindgraph.storage

import dev.mindgraph.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading an arbitrary folder of markdown into the vault. */
class FolderImportTest {

    private fun root(): Path = Files.createTempDirectory("mindgraph-folder")

    private fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        file.parent?.createDirectories()
        file.writeText(text)
        return file
    }

    @Test
    fun markdownIsFoundInStableOrder() {
        val root = root()
        write(root, "b.md", "# B")
        write(root, "a.md", "# A")
        write(root, "nested/c.md", "# C")

        assertEquals(listOf("a.md", "b.md", "nested/c.md"), FolderImport.scan(root).map { root.relativize(it).toString() })
    }

    @Test
    fun vendoredAndToolDirectoriesAreSkipped() {
        // The same missing idea that put Next.js's own AGENTS.md in the vault, plus the two an
        // Obsidian vault brings: its configuration directory and its trash.
        val root = root()
        write(root, "keep.md", "# Keep")
        write(root, "node_modules/pkg/AGENTS.md", "# Vendored")
        write(root, ".obsidian/plugins/notes.md", "# Config")
        write(root, ".trash/deleted.md", "# Deleted")
        write(root, "build/generated.md", "# Generated")

        assertEquals(listOf("keep.md"), FolderImport.scan(root).map { root.relativize(it).toString() })
    }

    @Test
    fun excalidrawDrawingsAreNotNotes() {
        // Megabytes of JSON wearing a .md extension.
        val root = root()
        write(root, "real.md", "# Real")
        write(root, "sketch.excalidraw.md", "# Sketch\n\n```json\n{}\n```")

        assertEquals(listOf("real.md"), FolderImport.scan(root).map { root.relativize(it).toString() })
    }

    @Test
    fun theTitleComesFromTheHeading() {
        val root = root()
        val file = write(root, "001-money.md", "# ADR 001 — Money representation\n\nContext…")
        val doc = FolderImport.parse(file, root, NodeKind.Rfc, "tally", Files.readString(file))!!
        assertEquals("ADR 001 — Money representation", doc.title)
        assertEquals(NodeKind.Rfc, doc.kind)
        assertEquals("tally", doc.originProject)
    }

    @Test
    fun theFilenameIsKeptSoWikilinksCanResolve() {
        // Obsidian links by filename while titles are headings; without this the links between
        // imported notes resolve to nothing.
        val root = root()
        val file = write(root, "money-representation.md", "# Money representation")
        val doc = FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file))!!
        assertEquals("money-representation", doc.documentName)
        assertEquals("money-representation", FolderImport.extrasFor(doc)[FolderImport.KEY_DOCUMENT_NAME])
    }

    @Test
    fun aFileWithNoHeadingFallsBackToItsName() {
        val root = root()
        val file = write(root, "loose-thought.md", "just some prose, no heading")
        val doc = FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file))!!
        assertEquals("loose-thought", doc.title)
    }

    @Test
    fun frontmatterTitleWinsOverTheHeading() {
        val root = root()
        val file = write(root, "n.md", "---\ntitle: The real title\n---\n\n# A different heading\n")
        val doc = FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file))!!
        assertEquals("The real title", doc.title)
    }

    @Test
    fun anExistingMindGraphNodeIsNotImported() {
        // It carries its own id and belongs to a vault already; copying it would mint a second
        // node with the same content under a different identity.
        val root = root()
        val file = write(root, "n.md", "---\nid: 01M0V4BQMAJ000RTB5PNFK2P5N\ntitle: Already a node\n---\n\nBody\n")
        assertNull(FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file)))
    }

    @Test
    fun anEmptyFileIsNotANote() {
        val root = root()
        val file = write(root, "blank.md", "   \n\n")
        assertNull(FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file)))
    }

    @Test
    fun theOriginIsTheAbsolutePath() {
        val root = root()
        val file = write(root, "n.md", "# N")
        val doc = FolderImport.parse(file, root, NodeKind.Note, "vault", Files.readString(file))!!
        assertEquals(file.toAbsolutePath().toString(), doc.origin)
        assertEquals(doc.origin, FolderImport.extrasFor(doc)[FolderImport.KEY_ORIGIN])
    }

    @Test
    fun aProjectNameSkipsTheConventionalDocsWrapper() {
        // ~/workspace/tally/docs/adr belongs to tally. The leaf says what kind of document it
        // is, not what it is about.
        assertEquals("tally", FolderImport.projectNameFor(Path.of("/home/iago/workspace/tally/docs/adr")))
        assertEquals("tally", FolderImport.projectNameFor(Path.of("/home/iago/workspace/tally/docs")))
        assertEquals("main", FolderImport.projectNameFor(Path.of("/home/iago/workspace/vaults/main")))
    }

    @Test
    fun aDecisionFolderIsSuggestedAsRfc() {
        assertEquals(NodeKind.Rfc, FolderImport.suggestedKind(Path.of("/x/docs/adr")))
        assertEquals(NodeKind.Rfc, FolderImport.suggestedKind(Path.of("/x/rfcs")))
        assertEquals(NodeKind.Note, FolderImport.suggestedKind(Path.of("/x/estudos")))
    }

    @Test
    fun aMissingFolderIsEmptyRatherThanAFailure() {
        assertTrue(FolderImport.scan(Path.of("/definitely/not/here")).isEmpty())
    }
}
