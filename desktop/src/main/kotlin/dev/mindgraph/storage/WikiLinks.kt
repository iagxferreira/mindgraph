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
        val index = index(nodes)
        return titlesIn(body)
            .mapNotNull { lookup(index, it) }
            .distinct()
    }

    /** Titles with no node behind them — the raw material for "create the note you just linked". */
    fun unresolved(body: String, nodes: List<Node>): List<String> {
        val index = index(nodes)
        return titlesIn(body)
            .filter { lookup(index, it) == null }
            .distinct()
    }

    /**
     * A name, then its last path segment.
     *
     * Obsidian writes the path when a name is ambiguous across folders —
     * `[[estudos/linguagens/elixir/roadmap]]` — and matching the whole string misses the note
     * whose name is sitting right there at the end of it. The full string is tried first, so a
     * note genuinely called `a/b` still wins over the leaf of some other link.
     */
    private fun lookup(index: Map<String, NodeId>, target: String): NodeId? {
        val name = target.trim().lowercase()
        index[name]?.let { return it }
        return name.substringAfterLast('/').takeIf { it != name && it.isNotEmpty() }?.let { index[it] }
    }

    /**
     * Every name a node answers to, mapped to it: title, slug, and any alias.
     *
     * Earlier names win, so a node's own title always beats another node's alias — an alias is
     * a nickname, and it should never quietly shadow something's real name. Building the index
     * once also means [resolve] and [unresolved] cannot disagree about what is resolvable.
     */
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
