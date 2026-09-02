package dev.mindgraph.storage

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * These use real files and a real WatchService rather than a fake: the thing under test *is*
 * the filesystem integration, and a stubbed one would prove only that the stub works.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultWatcherTest {

    private fun newVault(): Vault =
        Vault(Files.createTempDirectory("mindgraph-watch")).also { it.prepare() }

    @Test
    fun aFileAppearingInTheVaultIsReported() = runBlocking {
        val vault = newVault()
        val watcher = VaultWatcher(vault)

        val seen = async { withTimeout(TIMEOUT) { watcher.changes().first() } }
        settle()
        Files.writeString(vault.nodesDir.resolve("a-note.md"), "---\nid: x\n---\nbody")

        seen.await()
        assertTrue(true, "the watcher emitted before the timeout")
    }

    @Test
    fun anEditToAnExistingFileIsReported() = runBlocking {
        val vault = newVault()
        val file = vault.nodesDir.resolve("existing.md")
        Files.writeString(file, "before")
        val watcher = VaultWatcher(vault)

        val seen = async { withTimeout(TIMEOUT) { watcher.changes().first() } }
        settle()
        Files.writeString(file, "after")

        seen.await()
        assertEquals("after", Files.readString(file))
    }

    @Test
    fun aDeletedFileIsReported() = runBlocking {
        val vault = newVault()
        val file = vault.nodesDir.resolve("doomed.md")
        Files.writeString(file, "here")
        val watcher = VaultWatcher(vault)

        val seen = async { withTimeout(TIMEOUT) { watcher.changes().first() } }
        settle()
        Files.delete(file)

        seen.await()
        assertTrue(Files.notExists(file))
    }

    @Test
    fun aBurstOfWritesSettlesIntoASingleEmission() = runBlocking {
        val vault = newVault()
        val watcher = VaultWatcher(vault, settleMillis = 200)

        val collected = async {
            withTimeoutOrNull(QUIET) { watcher.changes().take(2).toList() }
        }
        settle()
        // What one save looks like on disk: several events in quick succession, plus the
        // delete-then-create of a renamed slug.
        repeat(5) { Files.writeString(vault.nodesDir.resolve("busy.md"), "revision $it") }
        Files.writeString(vault.nodesDir.resolve("renamed.md"), "moved")
        Files.delete(vault.nodesDir.resolve("busy.md"))

        // Waiting for a *second* emission times out, which is the assertion: the burst
        // coalesced into one.
        assertEquals(null, collected.await())
    }

    @Test
    fun writesOutsideTheNodesDirectoryAreIgnored() = runBlocking {
        val vault = newVault()
        val watcher = VaultWatcher(vault)

        val seen = async { withTimeoutOrNull(QUIET) { watcher.changes().first() } }
        settle()
        // The session log lives in .mindgraph/ and is written constantly while time is
        // tracked. Reloading the whole vault for it would be a refresh per tick.
        Files.writeString(vault.internalDir.resolve("sessions.jsonl"), "{}\n")

        assertEquals(null, seen.await())
    }

    @Test
    fun aNonMarkdownFileIsIgnored() = runBlocking {
        val vault = newVault()
        val watcher = VaultWatcher(vault)

        val seen = async { withTimeoutOrNull(QUIET) { watcher.changes().first() } }
        settle()
        // Editors drop swap and backup files beside the real ones; they are not nodes.
        Files.writeString(vault.nodesDir.resolve(".a-note.md.swp"), "vim")
        Files.writeString(vault.nodesDir.resolve("notes.txt"), "not a node")

        assertEquals(null, seen.await())
    }

    /** Gives the watch registration time to take effect before the test writes anything. */
    private suspend fun settle() = kotlinx.coroutines.delay(600)

    private companion object {
        const val TIMEOUT = 10_000L
        const val QUIET = 2_500L
    }
}
