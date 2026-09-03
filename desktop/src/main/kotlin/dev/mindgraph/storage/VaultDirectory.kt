package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences

/**
 * Which vaults this machine knows about, and which was open last.
 *
 * Kept in [Preferences] rather than in a vault, because it is the one piece of state that cannot
 * live inside a vault: it is the list of them. `java.prefs` is in the packaged runtime, so this
 * works in an installed build as well as from Gradle.
 */
class VaultDirectory(private val preferences: Preferences = Preferences.userRoot().node(NODE)) {

    /**
     * The vault to open on launch: the last one used, if it is still there.
     *
     * A vault that has been deleted or moved falls back to the default rather than failing to
     * start — the app is more useful pointing somewhere than refusing to open.
     */
    fun lastOpened(): Path {
        val stored = preferences.get(KEY_LAST, null)?.let(Paths::get)
        return stored?.takeIf { Files.isDirectory(it) } ?: Vault.defaultRoot()
    }

    /** Vaults opened before, most recent first, minus any that have since gone. */
    fun recent(): List<Path> = preferences.get(KEY_RECENT, "")
        .split(SEPARATOR)
        .filter { it.isNotBlank() }
        .map(Paths::get)
        .filter { Files.isDirectory(it) }
        .distinct()

    fun remember(root: Path) {
        val normalised = root.toAbsolutePath().normalize()
        preferences.put(KEY_LAST, normalised.toString())
        val updated = (listOf(normalised) + recent()).distinct().take(MAX_RECENT)
        preferences.put(KEY_RECENT, updated.joinToString(SEPARATOR))
        preferences.flush()
    }

    private companion object {
        const val NODE = "dev/mindgraph/vaults"
        const val KEY_LAST = "last"
        const val KEY_RECENT = "recent"
        /** A path may contain anything but a newline, so a newline is the safe separator. */
        const val SEPARATOR = "\n"
        /** Enough to switch between the projects in flight, few enough to read as a menu. */
        const val MAX_RECENT = 8
    }
}
