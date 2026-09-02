package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** One repository's Codex instructions, read into the shape MindGraph stores. */
data class CodexInstruction(
    val title: String,
    val body: String,
    val origin: String,
    val originProject: String,
)

/** Reads repository-level `AGENTS.md` files without modifying the upstream projects. */
object CodexImport {

    const val KEY_ORIGIN = MemoryImport.KEY_ORIGIN
    const val KEY_ORIGIN_PROJECT = MemoryImport.KEY_ORIGIN_PROJECT
    const val KEY_ORIGIN_AGENT = "originAgent"

    /** Finds all instruction files below [workspaceRoot] in stable path order. */
    fun scan(workspaceRoot: Path): List<Path> {
        if (!Files.isDirectory(workspaceRoot)) return emptyList()
        return Files.walk(workspaceRoot).use { files ->
            files.filter { Files.isRegularFile(it) && it.fileName.name == "AGENTS.md" }.toList()
        }.sortedBy { workspaceRoot.relativize(it).toString() }
    }

    fun read(file: Path, workspaceRoot: Path): CodexInstruction? =
        runCatching { Files.readString(file) }.getOrNull()?.let { parse(file, workspaceRoot, it) }

    fun parse(file: Path, workspaceRoot: Path, content: String): CodexInstruction? {
        val normalized = content.replace("\r\n", "\n")
        if (normalized.isBlank()) return null

        val title = normalized.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "AGENTS.md — ${file.parent?.name ?: file.name}"

        return CodexInstruction(
            title = title,
            body = normalized.trim(),
            origin = file.toAbsolutePath().toString(),
            originProject = projectNameOf(file, workspaceRoot),
        )
    }

    fun extrasFor(instruction: CodexInstruction): Map<String, String> = mapOf(
        KEY_ORIGIN to instruction.origin,
        KEY_ORIGIN_PROJECT to instruction.originProject,
        KEY_ORIGIN_AGENT to "codex",
    )

    private fun projectNameOf(file: Path, workspaceRoot: Path): String {
        val relative = runCatching { workspaceRoot.toAbsolutePath().relativize(file.toAbsolutePath()) }
            .getOrNull()
        return relative?.firstOrNull()?.toString() ?: file.parent?.name ?: "unknown"
    }
}
