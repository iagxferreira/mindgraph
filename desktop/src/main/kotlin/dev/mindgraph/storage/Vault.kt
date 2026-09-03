package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A vault is just a directory of markdown. `nodes/` holds the content — the part you would
 * put in git — and `.mindgraph/` holds machine-written logs that content shouldn't mix with.
 */
class Vault(val root: Path) {
    val nodesDir: Path get() = root.resolve("nodes")
    val internalDir: Path get() = root.resolve(".mindgraph")

    fun prepare() {
        Files.createDirectories(nodesDir)
        Files.createDirectories(internalDir)
    }

    /** What to call this vault on screen: its own folder, or its parent when that says more. */
    val displayName: String
        get() {
            val name = root.fileName?.toString().orEmpty()
            // `~/projects/my-app/vault` is my-app's, and a switcher full of entries called
            // "vault" tells you nothing about which is which.
            val parent = root.parent?.fileName?.toString()
            return if (name in GENERIC_NAMES && !parent.isNullOrEmpty()) "$parent/$name" else name
        }

    companion object {
        private val GENERIC_NAMES = setOf("vault", "vaults", "mindgraph", ".mindgraph")

        fun default(): Vault = Vault(defaultRoot())

        /**
         * Whether a directory already holds a vault.
         *
         * `nodes/` is the test rather than the directory merely existing, so pointing at an
         * empty folder offers to create one instead of silently opening nothing.
         */
        fun exists(root: Path): Boolean = Files.isDirectory(root.resolve("nodes"))

        fun defaultRoot(): Path {
            System.getenv("MINDGRAPH_HOME")?.let { return Paths.get(it) }
            System.getenv("HOME")?.let { return Paths.get(it, ".config", "mindgraph", "vault") }
            return Paths.get(".mindgraph-vault")
        }
    }
}
