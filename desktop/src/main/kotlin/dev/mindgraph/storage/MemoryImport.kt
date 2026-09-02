package dev.mindgraph.storage

import dev.mindgraph.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * One of Claude Code's per-project memory files, read into the shape MindGraph stores.
 *
 * These notes are already MindGraph's format in everything but name — markdown, YAML
 * frontmatter, one fact per file, `[[wikilinks]]` between them — but they are sealed in a
 * directory per project, so nothing can read them as a single graph.
 */
data class MemoryNote(
    val title: String,
    val body: String,
    val kind: NodeKind,
    /**
     * The file's `name`, which is what its siblings write inside `[[...]]`. Kept because
     * resolving those links into edges is separate work, and without this the link target
     * could not be recovered afterwards — the title is the description, not the slug.
     */
    val memoryName: String?,
    /** `user`, `feedback`, `project` or `reference`, kept verbatim. See [MemoryImport.kindFor]. */
    val memoryType: String?,
    /** Absolute source path. The identity an import uses to know it already ran. */
    val origin: String,
    /** The project directory the note came from, which is the grouping worth having later. */
    val originProject: String,
    val originSessionId: String?,
)

/**
 * Reads Claude Code's memory directories. Read-only upstream, always: notes are copied into
 * the vault and `~/.claude` is never written back to.
 */
object MemoryImport {

    /** Frontmatter keys written onto imported nodes. Unknown to [NodeStore], so preserved as extras. */
    const val KEY_ORIGIN = "origin"
    const val KEY_ORIGIN_PROJECT = "originProject"
    const val KEY_ORIGIN_SESSION = "originSessionId"
    const val KEY_MEMORY_NAME = "memoryName"
    const val KEY_MEMORY_TYPE = "memoryType"

    /**
     * `metadata.type` describes what a fact is *about* — who you are, how an agent should work,
     * what a project is — while [NodeKind] describes what a document *is*. They are different
     * axes, so only `reference`, which means the same thing in both, is carried across; the rest
     * are notes, and their original type is kept in frontmatter rather than forced into a shape.
     */
    fun kindFor(memoryType: String?): NodeKind =
        if (memoryType?.trim()?.lowercase() == "reference") NodeKind.Reference else NodeKind.Note

    /** Every importable memory file under a `~/.claude/projects` root, in a stable order. */
    fun scan(projectsRoot: Path): List<Path> {
        if (!Files.isDirectory(projectsRoot)) return emptyList()
        return Files.list(projectsRoot).use { projects ->
            projects.filter { Files.isDirectory(it) }.toList()
        }
            .sortedBy { it.name }
            .flatMap { project ->
                val memory = project.resolve("memory")
                if (!Files.isDirectory(memory)) return@flatMap emptyList()
                Files.list(memory).use { files ->
                    files.filter { Files.isRegularFile(it) && it.name.endsWith(".md") }.toList()
                }
                    // MEMORY.md is a table of contents pointing at the others, not a fact.
                    // Importing it would add a node whose every link duplicates a real one.
                    .filter { it.name != "MEMORY.md" }
                    .sortedBy { it.name }
            }
    }

    fun read(file: Path): MemoryNote? =
        runCatching { Files.readString(file) }.getOrNull()?.let { parse(file, it) }

    fun parse(file: Path, content: String): MemoryNote? {
        val (frontmatter, body) = splitDocument(content)
        if (body.isBlank()) return null

        val name = frontmatter["name"]
        val memoryType = frontmatter["type"]

        return MemoryNote(
            // The description is a written one-line summary — the best title available. Falling
            // back to the name reads worse but is never empty, which matters more.
            title = frontmatter["description"]?.takeIf { it.isNotBlank() }
                ?: name?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
                ?: file.name.removeSuffix(".md").replace('_', ' ').replace('-', ' '),
            body = body.trim(),
            kind = kindFor(memoryType),
            memoryName = name,
            memoryType = memoryType,
            origin = file.toAbsolutePath().toString(),
            originProject = projectNameOf(file),
            originSessionId = frontmatter["originSessionId"],
        )
    }

    /** The frontmatter keys an imported node carries, ready to hand to `NodeStore.create`. */
    fun extrasFor(note: MemoryNote): Map<String, String> = buildMap {
        put(KEY_ORIGIN, note.origin)
        put(KEY_ORIGIN_PROJECT, note.originProject)
        note.memoryName?.let { put(KEY_MEMORY_NAME, it) }
        note.memoryType?.let { put(KEY_MEMORY_TYPE, it) }
        note.originSessionId?.let { put(KEY_ORIGIN_SESSION, it) }
    }

    /**
     * The project directory's name, verbatim: `-home-iago-workspace-geo-resolution-rag`.
     *
     * It is an absolute path with the separators replaced by `-`, which makes shortening it
     * guesswork — nothing distinguishes the `-` in `workspace-geo` from the one in
     * `geo-resolution`, and taking the last segment turns `geo-resolution-rag` into `rag` and
     * `algorithm-solutions` into `solutions`. Both were wrong when this was tried against the
     * real directories. Kept whole because it only has to group reliably; making it read
     * nicely belongs to whatever displays it.
     */
    private fun projectNameOf(file: Path): String = file.parent?.parent?.name ?: "unknown"

    /**
     * A deliberately separate reader from [Frontmatter]: this is somebody else's format, and
     * nesting is the difference that matters — `type` and `originSessionId` live under
     * `metadata:`. Flattening the nesting is exactly right here and would be wrong there.
     */
    private fun splitDocument(content: String): Pair<Map<String, String>, String> {
        val normalized = content.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return emptyMap<String, String>() to normalized

        val closing = normalized.indexOf("\n---", startIndex = 3)
        if (closing < 0) return emptyMap<String, String>() to normalized

        val block = normalized.substring(4, closing)
        val body = normalized.substring(closing + 4).removePrefix("\n")

        val values = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) continue
            val separator = trimmed.indexOf(':')
            if (separator <= 0) continue
            val key = trimmed.substring(0, separator).trim()
            val value = unquote(trimmed.substring(separator + 1).trim())
            // Nested keys are flattened, and a parent like `metadata:` carries no value of its
            // own — so an empty value must not overwrite a key already read.
            if (value.isNotEmpty()) values[key] = value
        }
        return values to body
    }

    private fun unquote(raw: String): String {
        val quoted = raw.length >= 2 &&
            ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'")))
        return if (quoted) raw.substring(1, raw.length - 1) else raw
    }
}
