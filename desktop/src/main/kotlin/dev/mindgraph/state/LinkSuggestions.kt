package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.storage.WikiLinks

/**
 * Edges the vault has evidence for and does not have.
 *
 * An imported vault arrives with its notes and almost none of its connections — 465 nodes and 56
 * edges here — and a graph whose value is its edges cannot be filled in by hand at that size.
 * What the notes do contain is evidence: they name each other in prose without linking, and they
 * link to names that never resolved.
 *
 * This suggests and never links. The edge is a claim about meaning, so a person makes it, or an
 * agent makes it deliberately through `link_nodes`.
 */
object LinkSuggestions {

    /** Why a pair is being offered. Ordered: a written intention beats an incidental mention. */
    enum class Reason { DanglingLink, UnlinkedMention }

    data class Suggestion(
        val from: Node,
        val to: Node,
        val reason: Reason,
        /** The text that caused the suggestion, so the reason can be read rather than trusted. */
        val evidence: String,
    )

    /**
     * Suggestions touching [node], in either direction.
     *
     * Both directions matter and they are different questions: what this note mentions, and what
     * mentions it. The second is the one a person cannot find by reading the note in front of
     * them, which makes it the more valuable half.
     */
    fun forNode(
        nodes: List<Node>,
        node: Node,
        limit: Int = DEFAULT_LIMIT,
        minimumNameLength: Int = DEFAULT_MINIMUM_NAME_LENGTH,
    ): List<Suggestion> = all(nodes, minimumNameLength)
        .filter { it.from.id == node.id || it.to.id == node.id }
        .take(limit.coerceIn(1, MAX_LIMIT))

    /**
     * The whole vault's suggestions, ranked.
     *
     * Capped because the sweep is large by nature: measured on a real vault, a four character
     * name threshold offers 1010 pairs and eight offers 362. A list nobody can read is the same
     * as no list.
     */
    fun across(
        nodes: List<Node>,
        limit: Int = DEFAULT_LIMIT,
        minimumNameLength: Int = DEFAULT_MINIMUM_NAME_LENGTH,
    ): List<Suggestion> = all(nodes, minimumNameLength).take(limit.coerceIn(1, MAX_LIMIT))

    private fun all(nodes: List<Node>, minimumNameLength: Int): List<Suggestion> {
        val live = nodes.filterNot { it.archived }
        if (live.size < 2) return emptyList()

        val linked = existingPairs(live)
        val names = unambiguousNames(live, minimumNameLength)

        val suggestions = mutableListOf<Pair<Suggestion, Int>>()
        for (source in live) {
            val body = source.body.lowercase()

            for ((name, targetId) in names) {
                if (targetId == source.id) continue
                if (pairOf(source.id, targetId) in linked) continue
                // Word boundaries, or every short name matches inside a longer word - "index"
                // would match "indexing" and a vault full of them becomes a vault full of noise.
                if (!mentions(body, name)) continue
                val target = live.first { it.id == targetId }
                suggestions.add(
                    Suggestion(source, target, Reason.UnlinkedMention, name) to name.length,
                )
            }

            for (dangling in WikiLinks.unresolved(source.body, live)) {
                val normalised = normalise(dangling)
                if (normalised.length < minimumNameLength) continue
                val targetId = names.entries.firstOrNull { normalise(it.key) == normalised }?.value
                    ?: continue
                if (targetId == source.id || pairOf(source.id, targetId) in linked) continue
                val target = live.first { it.id == targetId }
                suggestions.add(
                    // Ranked above any mention: someone wrote the link and it did not land.
                    Suggestion(source, target, Reason.DanglingLink, dangling) to RANK_DANGLING,
                )
            }
        }

        return suggestions
            .distinctBy { Triple(it.first.from.id, it.first.to.id, it.first.reason) }
            .sortedWith(
                compareByDescending<Pair<Suggestion, Int>> { it.second }
                    .thenBy { it.first.from.title.lowercase() }
                    .thenBy { it.first.to.title.lowercase() },
            )
            .map { it.first }
    }

    /**
     * Every name that identifies exactly one node.
     *
     * A name two nodes answer to cannot be a suggestion: this vault has forty-nine notes called
     * `index` and three called `roadmap`, and an offer that cannot say which one it means is not
     * an offer. Short names are dropped for the same reason in weaker form — the shorter the
     * name, the more likely the match is a coincidence.
     */
    private fun unambiguousNames(nodes: List<Node>, minimumLength: Int): Map<String, NodeId> {
        val owners = HashMap<String, MutableSet<NodeId>>()
        nodes.forEach { node ->
            (listOf(node.title) + node.aliases)
                .map { it.trim().lowercase() }
                .filter { it.length >= minimumLength }
                .distinct()
                .forEach { owners.getOrPut(it) { mutableSetOf() }.add(node.id) }
        }
        return owners.filterValues { it.size == 1 }.mapValues { it.value.single() }
    }

    /** Existing edges of any kind, undirected: a link either way means the pair is not missing. */
    private fun existingPairs(nodes: List<Node>): Set<Pair<String, String>> {
        val pairs = HashSet<Pair<String, String>>()
        nodes.forEach { node ->
            (node.dependsOn + node.relatesTo + node.contextFor).forEach { other ->
                pairs.add(pairOf(node.id, other))
            }
            // A wikilink already becomes an edge, so a note that links in prose is not missing
            // one - suggesting it would be offering to add what is already there.
            WikiLinks.resolve(node.body, nodes).forEach { other -> pairs.add(pairOf(node.id, other)) }
        }
        return pairs
    }

    private fun pairOf(a: NodeId, b: NodeId): Pair<String, String> =
        if (a.value <= b.value) a.value to b.value else b.value to a.value

    private fun mentions(body: String, name: String): Boolean {
        var from = body.indexOf(name)
        while (from >= 0) {
            val before = body.getOrNull(from - 1)
            val after = body.getOrNull(from + name.length)
            if (before?.isLetterOrDigit() != true && after?.isLetterOrDigit() != true) return true
            from = body.indexOf(name, from + 1)
        }
        return false
    }

    /** Compares names past the punctuation and spacing a link and a title disagree about. */
    private fun normalise(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    const val DEFAULT_LIMIT = 20
    const val MAX_LIMIT = 200
    /**
     * Short names match by coincidence. Measured on a real vault: a four character floor offers
     * 1010 pairs, six offers 507, eight offers 362 - and the ones the floor removes are the ones
     * nobody would have accepted.
     */
    const val DEFAULT_MINIMUM_NAME_LENGTH = 8
    private const val RANK_DANGLING = 1_000
}
