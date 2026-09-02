package dev.mindgraph.state

import dev.mindgraph.model.Node

/** A matching node and the small piece of context that explains why it matched. */
data class NodeMatch(
    val node: Node,
    val snippet: String,
)

/**
 * Finds nodes by the words an agent can see: title, aliases, and body.
 *
 * The vault is deliberately scanned on each call. At the scale where a person can use a graph,
 * a cache would make outside edits stale for less work than it saves.
 */
object NodeSearch {

    fun search(nodes: List<Node>, query: String, limit: Int = DEFAULT_LIMIT): List<NodeMatch> {
        val needle = query.trim()
        require(needle.isNotEmpty()) { "query is required and must not be blank" }

        return nodes.mapNotNull { node ->
            val titleMatches = node.title.contains(needle, ignoreCase = true)
            val aliasMatches = node.aliases.any { it.contains(needle, ignoreCase = true) }
            val bodyMatches = node.body.contains(needle, ignoreCase = true)
            if (!titleMatches && !aliasMatches && !bodyMatches) return@mapNotNull null

            NodeMatch(node, snippetFor(node, needle, bodyMatches)) to rank(titleMatches, aliasMatches)
        }
            .sortedWith(compareBy<Pair<NodeMatch, Int>>({ it.second }, { it.first.node.title.lowercase() }, { it.first.node.id.value }))
            .take(limit.coerceIn(1, MAX_LIMIT))
            .map { it.first }
    }

    private fun rank(titleMatches: Boolean, aliasMatches: Boolean): Int = when {
        titleMatches -> 0
        aliasMatches -> 1
        else -> 2
    }

    private fun snippetFor(node: Node, query: String, bodyMatches: Boolean): String = when {
        bodyMatches -> snippet(node.body, query)
        node.aliases.any { it.contains(query, ignoreCase = true) } ->
            "Also known as: ${node.aliases.joinToString(", ")}"
        else -> node.title
    }

    private fun snippet(text: String, query: String): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        val index = compact.indexOf(query, ignoreCase = true)
        if (index < 0) return compact.take(SNIPPET_LENGTH)

        val start = (index - CONTEXT_BEFORE).coerceAtLeast(0)
        val end = (index + query.length + CONTEXT_AFTER).coerceAtMost(compact.length)
        return buildString {
            if (start > 0) append('…')
            append(compact.substring(start, end).trim())
            if (end < compact.length) append('…')
        }
    }

    private const val DEFAULT_LIMIT = 10
    private const val MAX_LIMIT = 100
    private const val SNIPPET_LENGTH = 180
    private const val CONTEXT_BEFORE = 60
    private const val CONTEXT_AFTER = 120
}
