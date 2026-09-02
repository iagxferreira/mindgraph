package dev.mindgraph.mcp

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.LinkOutcome
import dev.mindgraph.state.Linking
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker
import dev.mindgraph.model.currentUnixTimestamp
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog

/**
 * A [VaultAccess] over a real [NodeStore]. It goes through [Linking] exactly as the view model
 * does, so these tests exercise the real rules rather than a permissive stand-in.
 */
class StoreVault(
    private val store: NodeStore,
    private val log: SessionLog,
    /** Fixes the length of a tracked stretch so a test doesn't depend on how fast it runs. */
    private val elapsedOverride: Long? = null,
) : VaultAccess {

    private var startedAtUnix: Long? = null
    private var runningAgent: String? = null

    override suspend fun createTask(
        title: String,
        body: String,
        due: String?,
        assignee: String?,
    ): Node = store.create(title, body, TaskFacet(TaskStatus.Todo, due = due), assignee = assignee)

    override suspend fun createNote(
        title: String,
        body: String,
        kind: NodeKind,
        assignee: String?,
    ): Node = store.create(title, body, task = null, kind = kind, assignee = assignee)

    override suspend fun nodes(): List<Node> = store.load()

    override suspend fun setStatus(
        nodeId: NodeId,
        status: TaskStatus,
        due: String?,
        agent: String?,
        assignee: String?,
    ): Node? {
        val node = store.load().find { it.id == nodeId } ?: return null
        val facet = node.task ?: TaskFacet(status = status)
        val saved = store.save(
            node.copy(
                assignee = assignee ?: node.assignee,
                task = facet.copy(status = status, due = due ?: facet.due),
            ),
        )

        // The clock the view model runs, reduced to what a test needs: doing opens a stretch,
        // anything else closes it and logs who spent the time.
        if (status == TaskStatus.Doing) {
            startedAtUnix = currentUnixTimestamp()
            runningAgent = agent
        } else {
            startedAtUnix?.let { started ->
                val now = currentUnixTimestamp()
                log.append(
                    WorkSession(nodeId, started, now, elapsedOverride ?: (now - started), Worker.Agent, runningAgent),
                )
                startedAtUnix = null
            }
        }
        return saved
    }

    override suspend fun trackedSeconds(nodeId: NodeId): Long =
        log.load().filter { it.nodeId == nodeId }.sumOf { it.seconds }

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
