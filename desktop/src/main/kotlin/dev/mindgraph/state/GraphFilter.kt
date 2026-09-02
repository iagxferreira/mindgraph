package dev.mindgraph.state

import dev.mindgraph.model.Edge
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeKind

/** What the canvas draws once a filter is applied. */
data class VisibleGraph(val nodes: List<Node>, val edges: List<Edge>)

/**
 * Narrowing the graph to one kind.
 *
 * A filter rather than a cluster: gathering RFCs into a corner would pull them away from the
 * work they describe, which is the relationship the layout exists to show. Hiding the rest
 * answers the same question and leaves the surviving edges meaning what they meant.
 */
object GraphFilter {

    fun apply(nodes: List<Node>, edges: List<Edge>, kind: NodeKind?): VisibleGraph {
        if (kind == null) return VisibleGraph(nodes, edges)

        val visible = nodes.filter { it.kind == kind }
        val ids = visible.mapTo(HashSet()) { it.id }
        // An edge needs both ends: half an edge is a line trailing off to nothing.
        return VisibleGraph(visible, edges.filter { it.sourceId in ids && it.targetId in ids })
    }
}
