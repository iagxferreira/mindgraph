package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** One of Claude Code's plan documents, read into the shape MindGraph stores. */
data class PlanDocument(
    val title: String,
    val body: String,
    /** Absolute source path. The identity an import uses to know it already ran. */
    val origin: String,
    /**
     * The project this plan is *about*, when its title names one — see
     * [PlanImport.subjectOf] for why the title and not the body.
     */
    val subject: String?,
)

/**
 * Reads `~/.claude/plans`. Read-only upstream: plans are copied into the vault and
 * `~/.claude` is never written back to.
 *
 * Plans arrive as bare markdown — no frontmatter, and a generated filename like
 * `eager-beaming-leaf.md` that says nothing about the contents. Everything worth knowing has
 * to come out of the prose.
 */
object PlanImport {

    /** Frontmatter keys written onto imported plans. Unknown to [NodeStore], so preserved. */
    const val KEY_ORIGIN = MemoryImport.KEY_ORIGIN
    const val KEY_ORIGIN_PROJECT = MemoryImport.KEY_ORIGIN_PROJECT

    fun scan(plansRoot: Path): List<Path> {
        if (!Files.isDirectory(plansRoot)) return emptyList()
        return Files.list(plansRoot).use { files ->
            files.filter { Files.isRegularFile(it) && it.name.endsWith(".md") }.toList()
        }.sortedBy { it.name }
    }

    fun read(file: Path, knownProjects: Set<String> = emptySet()): PlanDocument? =
        runCatching { Files.readString(file) }.getOrNull()?.let { parse(file, it, knownProjects) }

    fun parse(file: Path, content: String, knownProjects: Set<String> = emptySet()): PlanDocument? {
        val normalized = content.replace("\r\n", "\n")
        if (normalized.isBlank()) return null

        val heading = normalized.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        // The generated filename is three random words, so it is a last resort rather than a
        // fallback worth preferring — but it is never empty, which the heading might be.
        val title = heading ?: file.name.removeSuffix(".md").replace('-', ' ')
            .replaceFirstChar { it.uppercase() }

        return PlanDocument(
            title = title,
            body = normalized.trim(),
            origin = file.toAbsolutePath().toString(),
            subject = subjectOf(title, knownProjects),
        )
    }

    /**
     * Which project a plan is about, decided from its **title** alone.
     *
     * Matching the body finds every project a plan merely mentions. Checked against the real
     * plans, that is actively wrong: an i18n plan for `sqnc.cloud` names the blog repository
     * once, as the routing pattern it copies, and body matching linked the plan to the blog.
     * A wrong edge is worse than a missing one here, because the graph is what an agent trusts.
     *
     * Titles do not have that problem — a plan's title names its subject — so a title that
     * names exactly one known project links, and anything else links to nothing.
     */
    fun subjectOf(title: String, knownProjects: Set<String>): String? {
        val matches = knownProjects.filter { project ->
            val key = projectKey(project)
            key.isNotEmpty() && title.contains(key, ignoreCase = true)
        }
        return matches.singleOrNull()
    }

    /** `-home-iago-workspace-geo-resolution-rag` reads as `geo-resolution-rag` in prose. */
    fun projectKey(originProject: String): String =
        originProject.substringAfterLast("-workspace-", originProject).trim('-')

    fun extrasFor(plan: PlanDocument): Map<String, String> = buildMap {
        put(KEY_ORIGIN, plan.origin)
        plan.subject?.let { put(KEY_ORIGIN_PROJECT, it) }
    }
}
