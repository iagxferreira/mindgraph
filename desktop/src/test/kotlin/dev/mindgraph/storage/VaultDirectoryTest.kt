package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Paths
import java.util.prefs.Preferences
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which vaults this machine knows about. */
class VaultDirectoryTest {

    private val node = Preferences.userRoot().node("dev/mindgraph/test-${System.nanoTime()}")
    private val directory = VaultDirectory(node)

    @AfterTest
    fun cleanUp() = node.removeNode()

    private fun tempVault(): java.nio.file.Path =
        Files.createTempDirectory("vault-dir").also { it.resolve("nodes").createDirectories() }

    @Test
    fun theLastOpenedVaultComesBack() {
        val vault = tempVault()
        directory.remember(vault)
        assertEquals(vault.toAbsolutePath().normalize(), directory.lastOpened())
    }

    @Test
    fun aVaultThatHasGoneFallsBackToTheDefault() {
        // The app is more useful pointing somewhere than refusing to start.
        val vault = tempVault()
        directory.remember(vault)
        vault.toFile().deleteRecursively()
        assertEquals(Vault.defaultRoot(), directory.lastOpened())
    }

    @Test
    fun recentsAreMostRecentFirst() {
        val first = tempVault()
        val second = tempVault()
        directory.remember(first)
        directory.remember(second)
        assertEquals(listOf(second, first).map { it.toAbsolutePath().normalize() }, directory.recent())
    }

    @Test
    fun openingTheSameVaultAgainDoesNotDuplicateIt() {
        val vault = tempVault()
        directory.remember(vault)
        directory.remember(vault)
        assertEquals(1, directory.recent().size)
    }

    @Test
    fun deletedVaultsDropOutOfRecents() {
        val kept = tempVault()
        val gone = tempVault()
        directory.remember(kept)
        directory.remember(gone)
        gone.toFile().deleteRecursively()
        assertEquals(listOf(kept.toAbsolutePath().normalize()), directory.recent())
    }

    @Test
    fun anEmptyDirectoryIsNotAVault() {
        // Pointing at an empty folder should offer to create one, not silently open nothing.
        val empty = Files.createTempDirectory("not-a-vault")
        assertTrue(!Vault.exists(empty))
        assertTrue(Vault.exists(tempVault()))
    }

    @Test
    fun aGenericallyNamedVaultIsShownWithItsParent() {
        // A switcher full of entries called "vault" says nothing about which is which.
        assertEquals("my-app/vault", Vault(Paths.get("/home/x/projects/my-app/vault")).displayName)
        assertEquals("second-brain", Vault(Paths.get("/home/x/second-brain")).displayName)
    }
}
