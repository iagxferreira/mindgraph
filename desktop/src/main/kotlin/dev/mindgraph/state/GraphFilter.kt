package dev.mindgraph.state

import dev.mindgraph.model.Edge
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskStatus

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

    fun apply(
        nodes: List<Node>,
        edges: List<Edge>,
        kind: NodeKind?,
        includeArchived: Boolean = false,
        includeDone: Boolean = true,
    ): VisibleGraph {
        if (kind == null && includeArchived && includeDone) return VisibleGraph(nodes, edges)

        val visible = nodes.filter {
            (kind == null || it.kind == kind) &&
                (includeArchived || !it.archived) &&
                (includeDone || it.task?.status != TaskStatus.Done)
        }
        val ids = visible.mapTo(HashSet()) { it.id }
        // An edge needs both ends: half an edge is a line trailing off to nothing.
        return VisibleGraph(visible, edges.filter { it.sourceId in ids && it.targetId in ids })
    }
}
