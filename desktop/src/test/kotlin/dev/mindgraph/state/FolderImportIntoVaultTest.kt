package dev.mindgraph.state

import dev.mindgraph.model.NodeKind
import dev.mindgraph.storage.FolderImport
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Copying a folder into the vault, and what the copy owes to the file it came from. */
class FolderImportIntoVaultTest {

    private fun newModel(): Pair<AppViewModel, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-folder-import")).also { it.prepare() }
        val store = NodeStore(vault)
        return AppViewModel(store, SessionLog(vault)) to store
    }

    private fun write(root: Path, relative: String, text: String): Path {
        val file = root.resolve(relative)
        file.parent?.createDirectories()
        file.writeText(text)
        return file
    }

    @Test
    fun decisionRecordsLandAsRfcNodesAttributedToTheirProject() = runBlocking {
        // The real case: ~/workspace/tally/docs/adr.
        val root = Files.createTempDirectory("repo").resolve("tally/docs/adr").also { it.createDirectories() }
        write(root, "001-money-representation.md", "# ADR 001 — Money representation\n\n- **Status:** Accepted\n")
        write(root, "002-crate-layout.md", "# ADR 002 — Crate and module layout\n\nContext.\n")
        val (model, store) = newModel()

        val result = model.importFolderNow(root, NodeKind.Rfc, "tally")

        assertEquals(2, result.imported)
        val nodes = store.load()
        assertEquals(setOf(NodeKind.Rfc), nodes.map { it.kind }.toSet())
        assertEquals(setOf("tally"), nodes.mapNotNull { it.originProject }.toSet())
        assertTrue(nodes.all { it.task == null }, "a decision record is context, not work")
        assertTrue(nodes.any { it.title == "ADR 001 — Money representation" }, nodes.map { it.title }.toString())
    }

    @Test
    fun aSecondRunImportsNothingNew() = runBlocking {
        val root = Files.createTempDirectory("adr")
        write(root, "001.md", "# One")
        val (model, store) = newModel()

        model.importFolderNow(root, NodeKind.Rfc, "tally")
        val second = model.importFolderNow(root, NodeKind.Rfc, "tally")

        assertEquals(0, second.imported)
        assertEquals(1, second.skipped)
        assertEquals(1, store.load().size)
    }

    @Test
    fun aNewFileInAnAlreadyImportedFolderIsPickedUp() = runBlocking {
        val root = Files.createTempDirectory("adr")
        write(root, "001.md", "# One")
        val (model, store) = newModel()
        model.importFolderNow(root, NodeKind.Rfc, "tally")

        write(root, "002.md", "# Two")
        val second = model.importFolderNow(root, NodeKind.Rfc, "tally")

        assertEquals(1, second.imported)
        assertEquals(1, second.skipped)
        assertEquals(2, store.load().size)
    }

    @Test
    fun theUpstreamFolderIsNeverWrittenTo() = runBlocking {
        val root = Files.createTempDirectory("adr")
        val file = write(root, "001.md", "# One\n\nBody.\n")
        val before = Files.readString(file)
        val (model, _) = newModel()

        model.importFolderNow(root, NodeKind.Rfc, "tally")

        assertEquals(before, Files.readString(file), "the source must be left exactly as it was")
        assertEquals(listOf("001.md"), Files.list(root).use { it.toList() }.map { it.fileName.toString() })
    }

    @Test
    fun editingUpstreamAfterwardsDoesNotReachTheCopy() = runBlocking {
        // The ownership rule, asserted rather than assumed: this is a snapshot, so an agent's
        // additions to the copy can never be overwritten by a later edit at the source.
        val root = Files.createTempDirectory("adr")
        val file = write(root, "001.md", "# One\n\nOriginal.\n")
        val (model, store) = newModel()
        model.importFolderNow(root, NodeKind.Rfc, "tally")

        file.writeText("# One\n\nRewritten upstream.\n")
        model.importFolderNow(root, NodeKind.Rfc, "tally")

        val node = store.load().single()
        assertTrue(node.body.contains("Original"), "the copy is the vault's own")
        assertTrue(!node.body.contains("Rewritten upstream"))
    }

    @Test
    fun theOriginIsRecordedSoTheSourceCanBeFound() = runBlocking {
        val root = Files.createTempDirectory("adr")
        val file = write(root, "001.md", "# One")
        val (model, store) = newModel()

        model.importFolderNow(root, NodeKind.Rfc, "tally")

        assertEquals(
            setOf(file.toAbsolutePath().toString()),
            store.frontmatterValues(FolderImport.KEY_ORIGIN),
        )
    }

    @Test
    fun vendoredFilesNeverBecomeNodes() = runBlocking {
        val root = Files.createTempDirectory("repo")
        write(root, "docs/real.md", "# Real")
        write(root, "node_modules/next/AGENTS.md", "# This is NOT the Next.js you know")
        val (model, store) = newModel()

        model.importFolderNow(root, NodeKind.Note, "repo")

        assertEquals(listOf("Real"), store.load().map { it.title })
    }

    @Test
    fun anEmptyFolderReportsRatherThanFails() = runBlocking {
        val (model, store) = newModel()
        val result = model.importFolderNow(Files.createTempDirectory("empty"), NodeKind.Note, "x")

        assertEquals(0, result.filesFound)
        assertTrue(result.summary().contains("No markdown"), result.summary())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun defaultsAreDerivedFromTheFolder() = runBlocking {
        val root = Files.createTempDirectory("repo").resolve("tally/docs/adr").also { it.createDirectories() }
        write(root, "001.md", "# One")
        val (model, store) = newModel()

        // No kind or project given: the folder says what it holds and what it belongs to.
        model.importFolderNow(root)

        val node = store.load().single()
        assertEquals(NodeKind.Rfc, node.kind)
        assertEquals("tally", node.originProject)
    }
}
