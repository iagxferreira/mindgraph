package dev.mindgraph.storage

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId

/**
 * `[[Some title]]` typed in a body is an associative link. Frontmatter stays canonical for
 * dependencies; this exists so linking while writing costs nothing but two brackets.
 */
object WikiLinks {
    private val PATTERN = Regex("\\[\\[([^\\[\\]|]+)(?:\\|[^\\[\\]]*)?]]")

    fun titlesIn(body: String): List<String> =
        PATTERN.findAll(body).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

    /** Unresolvable links are dropped rather than erroring — you may link ahead of writing. */
    fun resolve(body: String, nodes: List<Node>): List<NodeId> {
        if (nodes.isEmpty()) return emptyList()
        val index = index(nodes)
        return titlesIn(body)
            .mapNotNull { index[it.trim().lowercase()] }
            .distinct()
    }

    /** Titles with no node behind them — the raw material for "create the note you just linked". */
    fun unresolved(body: String, nodes: List<Node>): List<String> {
        val index = index(nodes)
        return titlesIn(body)
            .filter { it.trim().lowercase() !in index }
            .distinct()
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
