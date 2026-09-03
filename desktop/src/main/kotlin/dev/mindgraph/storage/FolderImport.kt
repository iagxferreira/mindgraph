package dev.mindgraph.storage

import dev.mindgraph.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** One markdown file from a chosen folder, read into the shape MindGraph stores. */
data class FolderDocument(
    val title: String,
    val body: String,
    val kind: NodeKind,
    /** Absolute source path. The identity an import uses to know it already ran. */
    val origin: String,
    /** What the document is attributed to, so it groups with the rest of that project. */
    val originProject: String,
    /**
     * The filename without its extension, which is what a sibling writes inside `[[...]]`.
     * An Obsidian vault links by filename while titles are headings, so without this the
     * links between imported notes resolve to nothing.
     */
    val documentName: String,
)

/**
 * Reads a folder of markdown into the vault. Read-only upstream, always.
 *
 * Deliberately not written for one source. The three importers that came before hardcoded four
 * decisions each - what to include, what to skip, what kind to assign, which project to
 * attribute it to - and a fourth copy of that would be thrown away the moment another folder
 * needs importing.
 *
 * The imported copy is the vault's own. `origin` records where it came from and the upstream
 * file is never written to, so a later edit there does not reach the copy. That is a snapshot
 * rather than a mirror, on purpose: agents append to nodes, and a re-sync would have to either
 * discard what an agent added or refuse to run.
 */
object FolderImport {

    const val KEY_ORIGIN = MemoryImport.KEY_ORIGIN
    const val KEY_ORIGIN_PROJECT = MemoryImport.KEY_ORIGIN_PROJECT
    const val KEY_DOCUMENT_NAME = "documentName"

    /**
     * Directories never worth importing.
     *
     * The Codex importer had no such notion and walked into `node_modules`, so the vault holds
     * Next.js's own instructions to its contributors. An Obsidian vault brings the same problem
     * in its own dialect: `.obsidian` is configuration and `excalidraw` files are drawings
     * stored as megabytes of JSON wearing a `.md` extension.
     */
    val SKIPPED_DIRECTORIES = setOf(
        "node_modules", ".git", ".obsidian", ".trash", "vendor", "target", "build", "dist",
        ".gradle", ".idea", "__pycache__", ".venv",
    )

    /** Markdown by extension, minus the file types that are markdown only by accident. */
    private fun isImportable(file: Path): Boolean {
        val name = file.fileName.name
        if (!name.endsWith(".md", ignoreCase = true)) return false
        // An Excalidraw drawing is a JSON blob in a .md wrapper. Importing one adds a megabyte
        // of coordinates to the graph and nothing a person or an agent can read.
        if (name.endsWith(".excalidraw.md", ignoreCase = true)) return false
        return !name.startsWith(".")
    }

    /** Markdown below [root] in stable path order, skipping directories nobody meant to import. */
    fun scan(root: Path): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path) &&
                    isImportable(path) &&
                    root.relativize(path).none { segment -> segment.name in SKIPPED_DIRECTORIES }
            }.toList()
        }.sortedBy { root.relativize(it).toString() }
    }

    fun read(file: Path, root: Path, kind: NodeKind, project: String): FolderDocument? =
        runCatching { Files.readString(file) }.getOrNull()
            ?.let { parse(file, root, kind, project, it) }

    fun parse(
        file: Path,
        root: Path,
        kind: NodeKind,
        project: String,
        content: String,
    ): FolderDocument? {
        val normalized = content.replace("\r\n", "\n")
        if (normalized.isBlank()) return null

        // An existing MindGraph node carries its own id and belongs to a vault already. Copying
        // one in would mint a second node with the same content and a different identity.
        val (frontmatter, body) = Frontmatter.split(normalized)
        if (frontmatter.string(NodeStore.KEY_ID) != null) return null

        val documentName = file.fileName.name.removeSuffix(".md").removeSuffix(".MD")
        val title = frontmatter.string(NodeStore.KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: headingOf(body)
            ?: documentName

        return FolderDocument(
            title = title,
            // The frontmatter is dropped and the body kept: its keys belong to whatever tool
            // wrote the file, and `origin` records where to look if they are ever wanted.
            body = body.trim(),
            kind = kind,
            origin = file.toAbsolutePath().toString(),
            originProject = project,
            documentName = documentName,
        )
    }

    private fun headingOf(body: String): String? = body.lineSequence()
        .firstOrNull { it.startsWith("# ") }
        ?.removePrefix("# ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun extrasFor(document: FolderDocument): Map<String, String> = mapOf(
        KEY_ORIGIN to document.origin,
        KEY_ORIGIN_PROJECT to document.originProject,
        KEY_DOCUMENT_NAME to document.documentName,
    )

    /**
     * A sensible project name for a folder: the repository it sits in.
     *
     * `~/workspace/tally/docs/adr` is tally's, not `adr`'s - the leaf names the kind of document,
     * and the directory above the conventional `docs`/`doc` wrapper names the thing it is about.
     */
    fun projectNameFor(root: Path): String {
        val absolute = root.toAbsolutePath().normalize()
        val segments = absolute.map { it.name }.filter { it.isNotEmpty() }
        val wrappers = setOf("docs", "doc", "adr", "adrs", "rfc", "rfcs", "decisions", "notes")
        val meaningful = segments.dropLastWhile { it.lowercase() in wrappers }
        return meaningful.lastOrNull() ?: segments.lastOrNull() ?: "unknown"
    }

    /** The kind a folder most likely holds, offered as a default the person can override. */
    fun suggestedKind(root: Path): NodeKind {
        val name = root.fileName?.name?.lowercase().orEmpty()
        return if (name in setOf("adr", "adrs", "rfc", "rfcs", "decisions", "design")) {
            NodeKind.Rfc
        } else {
            NodeKind.Note
        }
    }
}
