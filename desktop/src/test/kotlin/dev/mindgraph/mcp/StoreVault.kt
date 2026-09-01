package dev.mindgraph.mcp

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.LinkOutcome
import dev.mindgraph.state.Linking
import dev.mindgraph.storage.NodeStore

/**
 * A [VaultAccess] over a real [NodeStore]. It goes through [Linking] exactly as the view model
 * does, so these tests exercise the real rules rather than a permissive stand-in.
 */
class StoreVault(private val store: NodeStore) : VaultAccess {

    override suspend fun createTask(title: String, body: String): Node =
        store.create(title, body, TaskFacet(TaskStatus.Todo))

    override suspend fun nodes(): List<Node> = store.load()

    override suspend fun link(
        sourceId: NodeId,
        targetId: NodeId,
        kind: EdgeKind,
    ): LinkOutcome {
        val nodes = store.load()
        val outcome = Linking.evaluate(nodes, sourceId, targetId, kind)
        if (outcome == LinkOutcome.Linked) {
            store.save(Linking.applied(nodes.first { it.id == sourceId }, targetId, kind))
        }
        return outcome
    }
}
