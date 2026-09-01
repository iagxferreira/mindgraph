package dev.mindgraph.state

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId

/** What adding an edge did, or would do. */
enum class LinkOutcome { Linked, AlreadyLinked, WouldCycle, SelfLink, UnknownNode }

/**
 * The decision half of adding an edge, separated from writing it so the UI and the MCP tools
 * refuse the same edges for the same reasons — an agent must not be able to create a graph the
 * app would have rejected.
 */
object Linking {

    fun evaluate(
        nodes: List<Node>,
        sourceId: NodeId,
        targetId: NodeId,
        kind: EdgeKind,
    ): LinkOutcome {
        if (sourceId == targetId) return LinkOutcome.SelfLink
        val source = nodes.find { it.id == sourceId } ?: return LinkOutcome.UnknownNode
        if (nodes.none { it.id == targetId }) return LinkOutcome.UnknownNode

        val existing = when (kind) {
            EdgeKind.DependsOn -> source.dependsOn
            EdgeKind.RelatesTo -> source.relatesTo
        }
        if (targetId in existing) return LinkOutcome.AlreadyLinked

        // Only dependencies can cycle meaningfully — a mutual `relates_to` is just two notes
        // pointing at each other, which is fine and often correct.
        if (kind == EdgeKind.DependsOn && TaskGraph(nodes).wouldCycle(sourceId, targetId)) {
            return LinkOutcome.WouldCycle
        }
        return LinkOutcome.Linked
    }

    /** The node as it should be saved, once [evaluate] has returned [LinkOutcome.Linked]. */
    fun applied(source: Node, targetId: NodeId, kind: EdgeKind): Node = when (kind) {
        EdgeKind.DependsOn -> source.copy(dependsOn = source.dependsOn + targetId)
        EdgeKind.RelatesTo -> source.copy(relatesTo = source.relatesTo + targetId)
    }
}
