package dev.mindgraph.state

import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.Workspace
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Looking through a workspace: what the app shows, and what it must not do to the vault. */
class WorkspaceScopeTest {

    private fun newModel(): Pair<AppViewModel, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-scope")).also { it.prepare() }
        val store = NodeStore(vault)
        return AppViewModel(store, SessionLog(vault)) to store
    }

    private suspend fun imported(store: NodeStore, title: String, origin: String) =
        store.create(title = title, body = "", extras = mapOf("origin" to origin))

    @Test
    fun noWorkspaceShowsTheWholeVault() = runBlocking {
        val (model, store) = newModel()
        imported(store, "a", "/v/estudos/a.md")
        imported(store, "b", "/v/blog/b.md")
        model.refresh()

        assertEquals(2, model.visibleNodes.size)
    }

    @Test
    fun aWorkspaceNarrowsWhatIsShown() = runBlocking {
        val (model, store) = newModel()
        imported(store, "study", "/v/estudos/a.md")
        imported(store, "post", "/v/blog/b.md")
        val ws = store.create("Estudos", "", kind = NodeKind.Reference)
        store.save(ws.copy(workspace = Workspace(Workspace.Rule.OriginUnder("/v/estudos"))))
        model.refresh()

        model.selectWorkspace(ws.id)
        assertEquals(listOf("study"), model.visibleNodes.map { it.title })
    }

    @Test
    fun aWorkspaceDoesNotListItself() = runBlocking {
        // A saved view among the things it selects is noise, and on the canvas it would sit
        // inside the group it describes.
        val (model, store) = newModel()
        val ws = store.create("Every RFC", "", kind = NodeKind.Reference)
        store.save(ws.copy(workspace = Workspace(Workspace.Rule.OfKind(NodeKind.Reference))))
        model.refresh()

        model.selectWorkspace(ws.id)
        assertTrue(model.visibleNodes.none { it.id == ws.id }, model.visibleNodes.map { it.title }.toString())
    }

    @Test
    fun narrowingChangesNothingOnDisk() = runBlocking {
        // A workspace is a lens. Nothing is moved, copied or deleted by looking through one.
        val (model, store) = newModel()
        imported(store, "study", "/v/estudos/a.md")
        imported(store, "post", "/v/blog/b.md")
        val ws = store.create("Estudos", "", kind = NodeKind.Reference)
        store.save(ws.copy(workspace = Workspace(Workspace.Rule.OriginUnder("/v/estudos"))))
        model.refresh()
        val before = store.load().map { it.id }.toSet()

        model.selectWorkspace(ws.id)

        assertEquals(before, store.load().map { it.id }.toSet())
        assertEquals(3, model.nodes.size, "the vault is untouched behind the lens")
    }

    @Test
    fun clearingTheWorkspaceRestoresEverything() = runBlocking {
        val (model, store) = newModel()
        imported(store, "study", "/v/estudos/a.md")
        val ws = store.create("Estudos", "", kind = NodeKind.Reference)
        store.save(ws.copy(workspace = Workspace(Workspace.Rule.OriginUnder("/v/estudos"))))
        model.refresh()

        model.selectWorkspace(ws.id)
        model.selectWorkspace(null)
        assertEquals(2, model.visibleNodes.size)
    }

    @Test
    fun theFoldersOfAnImportAreOffered() = runBlocking {
        // The obsidian-main problem: one label for nine folders someone actually maintained.
        val (model, store) = newModel()
        repeat(4) { imported(store, "e$it", "/home/x/vault/estudos/$it.md") }
        repeat(3) { imported(store, "b$it", "/home/x/vault/blog/$it.md") }
        store.load().forEach { store.save(it.copy(originProject = "obsidian-main")) }
        model.refresh()

        val offered = model.suggestedWorkspaces()
        assertEquals(listOf("estudos" to 4, "blog" to 3), offered.map { it.first to it.third })
        assertEquals("/home/x/vault/estudos", offered.first().second)
    }

    @Test
    fun creatingAWorkspaceSelectsIt() = runBlocking {
        val (model, store) = newModel()
        imported(store, "study", "/v/estudos/a.md")
        model.refresh()

        model.createWorkspace("Estudos", Workspace.Rule.OriginUnder("/v/estudos"))
        // The coroutine writes and reloads; wait for the node to appear.
        repeat(50) { if (model.workspaces.isEmpty()) Thread.sleep(20) }

        assertEquals(1, model.workspaces.size)
        assertEquals("Estudos", model.activeWorkspace?.title)
        assertEquals(listOf("study"), model.visibleNodes.map { it.title })
    }
}
