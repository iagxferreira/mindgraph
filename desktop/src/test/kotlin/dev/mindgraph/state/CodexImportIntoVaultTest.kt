package dev.mindgraph.state

import dev.mindgraph.storage.CodexImport
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

class CodexImportIntoVaultTest {

    private fun newModel(): Pair<AppViewModel, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-codex-import")).also { it.prepare() }
        val store = NodeStore(vault)
        return AppViewModel(store, SessionLog(vault)) to store
    }

    @Test
    fun importsAgentsFilesAsNonWorkNotesAndSkipsThemOnSecondRun() = runBlocking {
        val root = Files.createTempDirectory("codex-workspace")
        val first = root.resolve("app/AGENTS.md").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "# App rules\n\nUse tests.")
        }
        val (model, store) = newModel()

        val result = model.importCodexAgentsNow(root)
        val second = model.importCodexAgentsNow(root)
        val node = store.load().single()

        assertEquals(CodexImportResult(1, 0, 0, 1), result)
        assertEquals(CodexImportResult(0, 1, 0, 1), second)
        assertEquals("App rules", node.title)
        assertTrue(node.task == null, "instruction files are context, not work")
        assertEquals(setOf(first.toAbsolutePath().toString()), store.frontmatterValues(CodexImport.KEY_ORIGIN))
        assertEquals("Imported 1 Codex instruction(s)", result.summary())
        model.scope.cancel()
    }

    @Test
    fun aBlankAgentsFileIsReportedAsUnreadable() = runBlocking {
        val root = Files.createTempDirectory("codex-workspace")
        Files.writeString(root.resolve("AGENTS.md"), "\n")
        val (model, store) = newModel()

        val result = model.importCodexAgentsNow(root)

        assertEquals(CodexImportResult(0, 0, 1, 1), result)
        assertTrue(store.load().isEmpty())
        model.scope.cancel()
    }

    @Test
    fun aMissingWorkspaceIsReportedRatherThanThrown() = runBlocking {
        val (model, store) = newModel()

        val result = model.importCodexAgentsNow(Path.of("/nonexistent/codex-workspace"))

        assertEquals(CodexImportResult(0, 0, 0, 0), result)
        assertTrue(store.load().isEmpty())
        model.scope.cancel()
    }
}
