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

    companion object {
        fun default(): Vault = Vault(defaultRoot())

        fun defaultRoot(): Path {
            System.getenv("MINDGRAPH_HOME")?.let { return Paths.get(it) }
            System.getenv("HOME")?.let { return Paths.get(it, ".config", "mindgraph", "vault") }
            return Paths.get(".mindgraph-vault")
        }
    }
}
