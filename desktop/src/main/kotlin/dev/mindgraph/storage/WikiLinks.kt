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
        val byTitle = nodes.associateBy { it.title.trim().lowercase() }
        val bySlug = nodes.associateBy { it.slug }
        return titlesIn(body)
            .mapNotNull { title -> byTitle[title.lowercase()] ?: bySlug[title.lowercase()] }
            .map { it.id }
            .distinct()
    }

    /** Titles with no node behind them — the raw material for "create the note you just linked". */
    fun unresolved(body: String, nodes: List<Node>): List<String> {
        val byTitle = nodes.mapTo(HashSet()) { it.title.trim().lowercase() }
        val bySlug = nodes.mapTo(HashSet()) { it.slug }
        return titlesIn(body)
            .filter { it.lowercase() !in byTitle && it.lowercase() !in bySlug }
            .distinct()
    }
}
