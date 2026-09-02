package dev.mindgraph.state

import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import dev.mindgraph.storage.VaultWatcher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The done-when for watching the vault, asserted where the user would see it: a file touched
 * on disk changes what the app holds, with nobody asking it to reload.
 */
class VaultReloadTest {

    private fun writeNodeFile(dir: Path, slug: String, id: String, title: String) {
        Files.writeString(
            dir.resolve("$slug.md"),
            """
            ---
            id: $id
            title: "$title"
            kind: note
            created: 2026-09-02T00:00:00Z
            updated: 2026-09-02T00:00:00Z
            ---

            body
            """.trimIndent(),
        )
    }

    /** Waits for the view model to catch up, rather than assuming a fixed delay is enough. */
    private suspend fun awaitNodes(model: AppViewModel, predicate: (AppViewModel) -> Boolean): Boolean? =
        withTimeoutOrNull(10_000L) {
            while (!predicate(model)) delay(50)
            true
        }

    @Test
    fun aNodeFileWrittenOutsideTheAppAppearsWithoutAReload() = runBlocking {
        val vault = Vault(Files.createTempDirectory("mindgraph-reload")).also { it.prepare() }
        val model = AppViewModel(NodeStore(vault), SessionLog(vault), VaultWatcher(vault))

        assertEquals(true, awaitNodes(model) { it.nodes.isEmpty() }, "started from an empty vault")
        delay(600) // let the watch registration take effect

        // Exactly what a second process — an agent, an editor, a git checkout — does.
        writeNodeFile(vault.nodesDir, "outside-edit", "01M1FMFEDMZXFNXMT6TX0D6FX0", "Written outside")

        assertEquals(
            true,
            awaitNodes(model) { m -> m.nodes.any { it.title == "Written outside" } },
            "the node written on disk reached the view model on its own",
        )
        model.scope.cancel()
    }

    @Test
    fun aNodeFileDeletedOutsideTheAppLeavesTheGraph() = runBlocking {
        val vault = Vault(Files.createTempDirectory("mindgraph-reload-del")).also { it.prepare() }
        writeNodeFile(vault.nodesDir, "doomed", "01M1FMFEDMZXFNXMT6TX0D6FX1", "Doomed")
        val model = AppViewModel(NodeStore(vault), SessionLog(vault), VaultWatcher(vault))

        assertEquals(true, awaitNodes(model) { it.nodes.size == 1 }, "loaded the existing node")
        delay(600)

        Files.delete(vault.nodesDir.resolve("doomed.md"))

        assertEquals(true, awaitNodes(model) { it.nodes.isEmpty() }, "the deletion reached the graph")
        model.scope.cancel()
    }

    @Test
    fun aViewModelWithNoWatcherStillLoadsNormally() = runBlocking {
        val vault = Vault(Files.createTempDirectory("mindgraph-nowatch")).also { it.prepare() }
        writeNodeFile(vault.nodesDir, "there", "01M1FMFEDMZXFNXMT6TX0D6FX2", "Already there")

        // The watcher is optional; every other test constructs the view model without one.
        val model = AppViewModel(NodeStore(vault), SessionLog(vault))

        assertEquals(true, awaitNodes(model) { it.nodes.size == 1 })
        assertTrue(model.nodes.single().title == "Already there")
        model.scope.cancel()
    }
}
