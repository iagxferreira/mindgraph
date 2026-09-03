package dev.mindgraph.storage

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId

/**
 * `[[Some title]]` typed in a body is an associative link. Frontmatter stays canonical for
 * dependencies; this exists so linking while writing costs nothing but two brackets.
 */
object WikiLinks {
    // The leading `!` is captured rather than excluded so an embed can be recognised and dropped.
    private val PATTERN = Regex("(!?)\\[\\[([^\\[\\]|]+)(?:\\|[^\\[\\]]*)?]]")

    /**
     * The names linked in a body.
     *
     * `![[picture.png]]` is an embed - an image or an attachment placed in the text, not a
     * reference to a note. Counting those as links puts an edge to nothing in the graph for
     * every image in a vault, and offers to create a note called `20220412011351.png`.
     */
    fun titlesIn(body: String): List<String> =
        PATTERN.findAll(body)
            .filter { it.groupValues[1].isEmpty() }
            .map { it.groupValues[2].trim() }
            .filter { it.isNotEmpty() }
            .toList()

    /** Unresolvable links are dropped rather than erroring — you may link ahead of writing. */
    fun resolve(body: String, nodes: List<Node>): List<NodeId> {
        if (nodes.isEmpty()) return emptyList()
        val paths = paths(nodes)
        return titlesIn(body)
            .mapNotNull { lookup(paths, it) }
            .distinct()
    }

    /** Titles with no node behind them — the raw material for "create the note you just linked". */
    fun unresolved(body: String, nodes: List<Node>): List<String> {
        val paths = paths(nodes)
        return titlesIn(body)
            .filter { lookup(paths, it) == null }
            .distinct()
    }

    /**
     * A name, then the file at that path, and only then the bare last segment.
     *
     * Obsidian writes a path precisely when a name is ambiguous across folders, so resolving
     * `[[estudos/elixir/roadmap]]` to whichever note called `roadmap` happens to load first
     * answers the one question the path was written to settle. This vault has three notes named
     * `roadmap` and forty-nine named `index`.
     *
     * So a path is matched against where the file actually came from, which is exact. The bare
     * leaf is kept as a last resort but only when nothing else shares that name: a guess is
     * better than nothing where there is nothing to be wrong about, and worse than nothing where
     * there is.
     */
    private fun lookup(paths: Paths, target: String): NodeId? {
        val name = target.trim().lowercase()
        paths.byName[name]?.let { return it }
        if ('/' !in name) return null

        paths.byOrigin[name]?.let { return it }
        val leaf = name.substringAfterLast('/')
        return if (paths.ambiguous.contains(leaf)) null else paths.byName[leaf]
    }

    /** The three ways a link can name a node, built once so every lookup agrees. */
    private class Paths(
        val byName: Map<String, NodeId>,
        val byOrigin: Map<String, NodeId>,
        val ambiguous: Set<String>,
    )

    /**
     * Where each imported node came from, keyed by every trailing run of its path.
     *
     * A link writes as much of the path as it needs to be unambiguous, so `elixir/roadmap` and
     * `estudos/linguagens/elixir/roadmap` must both find the same file. Indexing every suffix
     * costs one entry per folder depth and makes both exact.
     */
    private fun originSuffixes(nodes: List<Node>): Map<String, NodeId> {
        val index = HashMap<String, NodeId>()
        nodes.forEach { node ->
            val origin = node.origin?.trim()?.removeSuffix(".md")?.removeSuffix(".MD") ?: return@forEach
            val segments = origin.split('/').filter { it.isNotEmpty() }
            for (start in segments.indices) {
                index.putIfAbsent(segments.subList(start, segments.size).joinToString("/").lowercase(), node.id)
            }
        }
        return index
    }

    /** Names held by more than one node, where a bare guess would pick one at random. */
    private fun ambiguousNames(nodes: List<Node>): Set<String> {
        val seen = HashSet<String>(nodes.size)
        val duplicated = HashSet<String>()
        nodes.forEach { node ->
            val names = (listOf(node.title, node.slug) + node.aliases).map { it.trim().lowercase() }
            names.filter { it.isNotEmpty() }.distinct().forEach { if (!seen.add(it)) duplicated.add(it) }
        }
        return duplicated
    }

    /**
     * Every name a node answers to, mapped to it: title, slug, and any alias.
     *
     * Earlier names win, so a node's own title always beats another node's alias — an alias is
     * a nickname, and it should never quietly shadow something's real name. Building the index
     * once also means [resolve] and [unresolved] cannot disagree about what is resolvable.
     */
    private fun paths(nodes: List<Node>): Paths =
        Paths(index(nodes), originSuffixes(nodes), ambiguousNames(nodes))

    private fun index(nodes: List<Node>): Map<String, NodeId> {
        val index = HashMap<String, NodeId>(nodes.size * 3)
        nodes.forEach { node -> index.putIfAbsent(node.title.trim().lowercase(), node.id) }
        nodes.forEach { node -> index.putIfAbsent(node.slug.trim().lowercase(), node.id) }
        nodes.forEach { node ->
            node.aliases.forEach { alias -> index.putIfAbsent(alias.trim().lowercase(), node.id) }
        }
        return index
    }
}
