package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId

/**
 * Assembling the context for a piece of work: walk out from a starting node and render the
 * neighbourhood as one document, cut to a budget.
 *
 * Two things make this different from search. Traversal follows edges rather than matching
 * strings, so a note that never repeats your words still arrives. And the result is a document
 * rather than a list of hits — a list of titles is not context, it is a second search the caller
 * now has to run.
 *
 * The budget is the point rather than a safeguard. A context window is finite, so a retrieval
 * tool that returns everything relevant has answered the wrong question; this one answers "what
 * would you load first, given room for this much".
 */
object Retrieval {

    /** Why a node is in the bundle. Ordered: a chosen edge outranks an inferred one. */
    enum class Reason { Seed, Curated, Dependency, Association }

    data class Included(
        val node: Node,
        val distance: Int,
        val reason: Reason,
        /** False when the body was cut to fit the budget. */
        val whole: Boolean = true,
    )

    data class Bundle(
        val seed: Node,
        val included: List<Included>,
        /** Reached by the walk but left out for want of budget. Reported, never silent. */
        val omitted: List<Node>,
        val charactersUsed: Int,
    )

    /**
     * Neighbours of [seed] within [hops], nearest first, with a chosen edge beating an inferred
     * one at the same distance.
     *
     * Edges are walked in **both** directions. `context_for` points from the note to the work it
     * serves, so a bundle assembled by only following outgoing edges would find nothing at all —
     * the curated case is entirely incoming.
     */
    fun gather(nodes: List<Node>, seed: Node, hops: Int = DEFAULT_HOPS): List<Included> {
        val byId = nodes.associateBy { it.id }
        val best = HashMap<NodeId, Pair<Int, Reason>>()
        best[seed.id] = 0 to Reason.Seed

        var frontier = listOf(seed.id)
        for (distance in 1..hops.coerceIn(1, MAX_HOPS)) {
            val next = mutableListOf<NodeId>()
            for (fromId in frontier) {
                for ((neighbourId, reason) in neighbours(byId, nodes, fromId)) {
                    if (neighbourId == seed.id) continue
                    val existing = best[neighbourId]
                    val candidate = distance to reason
                    // A shorter walk wins; at equal distance, the better reason wins.
                    if (existing == null || candidate.lessThan(existing)) {
                        if (existing == null) next.add(neighbourId)
                        best[neighbourId] = candidate
                    }
                }
            }
            frontier = next
            if (frontier.isEmpty()) break
        }

        return best.entries
            .filter { it.key != seed.id }
            .mapNotNull { (id, rank) -> byId[id]?.let { Included(it, rank.first, rank.second) } }
            .filterNot { it.node.archived }
            // Nearest first; at equal distance a chosen edge beats an inferred one; then title,
            // so the same vault always renders the same document.
            .sortedWith(
                compareBy({ it.distance }, { it.reason.ordinal }, { it.node.title.lowercase() }),
            )
    }

    private fun Pair<Int, Reason>.lessThan(other: Pair<Int, Reason>): Boolean =
        first < other.first || (first == other.first && second.ordinal < other.second.ordinal)

    /** Both directions of every edge kind, tagged with why the neighbour is relevant. */
    private fun neighbours(
        byId: Map<NodeId, Node>,
        nodes: List<Node>,
        id: NodeId,
    ): List<Pair<NodeId, Reason>> {
        val node = byId[id] ?: return emptyList()
        val out = mutableListOf<Pair<NodeId, Reason>>()
        node.contextFor.forEach { out.add(it to Reason.Curated) }
        node.dependsOn.forEach { out.add(it to Reason.Dependency) }
        node.relatesTo.forEach { out.add(it to Reason.Association) }
        for (other in nodes) {
            if (other.id == id) continue
            if (id in other.contextFor) out.add(other.id to Reason.Curated)
            if (id in other.dependsOn) out.add(other.id to Reason.Dependency)
            if (id in other.relatesTo) out.add(other.id to Reason.Association)
        }
        return out.filter { it.first in byId }
    }

    /**
     * Fills the budget nearest-first and stops, rather than trimming every node a little.
     *
     * Half of each of ten documents is ten documents nobody can act on. A whole nearest note and
     * an honest count of what did not fit is more useful than a uniformly truncated smear — and
     * the caller can raise the budget or narrow the topic once it knows what it is missing.
     */
    fun bundle(
        nodes: List<Node>,
        seed: Node,
        hops: Int = DEFAULT_HOPS,
        budgetCharacters: Int = DEFAULT_BUDGET_CHARACTERS,
    ): Bundle {
        val budget = budgetCharacters.coerceIn(MIN_BUDGET_CHARACTERS, MAX_BUDGET_CHARACTERS)
        val candidates = gather(nodes, seed, hops)

        // The seed is always whole: a bundle that truncates the thing you asked about has
        // spent its budget on the one document the caller already knew it needed.
        var used = render(seed, Included(seed, 0, Reason.Seed)).length
        val included = mutableListOf<Included>()
        val omitted = mutableListOf<Node>()

        for (candidate in candidates) {
            if (omitted.isNotEmpty()) {
                omitted.add(candidate.node)
                continue
            }
            val whole = render(seed, candidate)
            if (used + whole.length <= budget) {
                included.add(candidate)
                used += whole.length
                continue
            }
            // Room for a useful piece of it? Otherwise stop — and everything after is omitted.
            val remaining = budget - used
            if (remaining >= MIN_USEFUL_EXCERPT) {
                val cut = candidate.copy(whole = false)
                included.add(cut)
                used += render(seed, cut).length
            }
            omitted.add(candidate.node)
        }
        return Bundle(seed, included, omitted, used)
    }

    /** The bundle as one markdown document, which is the thing an agent actually consumes. */
    fun markdown(bundle: Bundle): String = buildString {
        append("# Context for: ${bundle.seed.title}\n\n")
        append(
            "Assembled by walking the graph out from this node. Nearest first; a curated " +
                "`context_for` link outranks an inferred one at the same distance.\n\n",
        )
        append(render(bundle.seed, Included(bundle.seed, 0, Reason.Seed)))
        bundle.included.forEach { append(render(bundle.seed, it)) }

        if (bundle.omitted.isNotEmpty()) {
            append("\n---\n\n")
            append("## Not included (${bundle.omitted.size})\n\n")
            append("Reached by the walk but left out for want of budget. ")
            append("Raise the budget or narrow the topic to see them.\n\n")
            bundle.omitted.take(OMITTED_LISTED).forEach {
                append("- ${it.title} (${it.id.value})\n")
            }
            if (bundle.omitted.size > OMITTED_LISTED) {
                append("- …and ${bundle.omitted.size - OMITTED_LISTED} more\n")
            }
        }
    }

    private fun render(seed: Node, included: Included): String = buildString {
        val node = included.node
        append("\n---\n\n")
        append("## ${node.title}\n")
        append("id: ${node.id.value} · kind: ${node.kind.slug}")
        node.task?.let { append(" · status: ${it.status.name.lowercase()}") }
        if (included.reason != Reason.Seed) {
            append(" · ${why(included)}")
        }
        append("\n\n")

        val body = node.body.trim().ifEmpty { "(no body)" }
        if (included.whole) {
            append(body)
            append("\n")
        } else {
            append(body.take(EXCERPT_CHARACTERS).trimEnd())
            append("\n\n_(excerpt — raise the budget or open ${node.id.value} for the rest)_\n")
        }
    }

    private fun why(included: Included): String {
        val hop = if (included.distance == 1) "1 hop" else "${included.distance} hops"
        return when (included.reason) {
            Reason.Seed -> "the node asked about"
            Reason.Curated -> "chosen as context · $hop"
            Reason.Dependency -> "dependency · $hop"
            Reason.Association -> "related · $hop"
        }
    }

    const val DEFAULT_HOPS = 2
    const val MAX_HOPS = 4
    /** Roughly this many characters to a token; used only to talk to callers in tokens. */
    const val CHARACTERS_PER_TOKEN = 4
    const val DEFAULT_BUDGET_CHARACTERS = 8_000
    const val MIN_BUDGET_CHARACTERS = 500
    const val MAX_BUDGET_CHARACTERS = 200_000
    private const val MIN_USEFUL_EXCERPT = 400
    private const val EXCERPT_CHARACTERS = 600
    private const val OMITTED_LISTED = 20
}
