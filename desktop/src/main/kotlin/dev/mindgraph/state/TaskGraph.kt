package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskStatus
import java.time.LocalDate

/**
 * Derived task state. Status is what you declared; readiness is what the graph computes —
 * a `Todo` whose dependencies are unfinished is not actually startable, and that distinction
 * is the reason dependency edges are worth storing at all.
 */
class TaskGraph(private val nodes: List<Node>) {
    private val byId: Map<NodeId, Node> = nodes.associateBy { it.id }

    private companion object {
        const val SOON_DAYS = 3L
        const val OVERDUE = 0
        const val DUE_SOON = 1
        const val NOT_URGENT = 2
    }

    fun dependencies(nodeId: NodeId): List<Node> =
        byId[nodeId]?.dependsOn?.mapNotNull(byId::get).orEmpty()

    fun dependents(nodeId: NodeId): List<Node> =
        nodes.filter { nodeId in it.dependsOn }

    /** Open work upstream of this node. Non-tasks never block — a reference note isn't a gate. */
    fun blockers(nodeId: NodeId): List<Node> =
        dependencies(nodeId).filter { it.isLiveWork }

    fun isBlocked(nodeId: NodeId): Boolean = blockers(nodeId).isNotEmpty()

    fun isReady(node: Node): Boolean =
        !node.archived && node.task?.status == TaskStatus.Todo && !isBlocked(node.id)

    fun readyTasks(): List<Node> = nodes.filter(::isReady)

    /**
     * Ready work in the order it is worth doing.
     *
     * Deadlines come first because they are the one claim on your time that someone else may
     * be holding you to, and because a date decays on its own — unlike a declared priority,
     * which only ever inflates. Leverage decides everything after that: with nothing due, the
     * task that frees the most work is the one worth starting.
     *
     * Without this, tasks that unblock nothing tie and fall back on their titles, so ordering
     * comes down to spelling.
     */
    fun rankedReadyTasks(today: LocalDate = LocalDate.now()): List<Node> =
        readyTasks().sortedWith(
            compareBy<Node> { urgency(it, today) }
                .thenByDescending { unblockedCount(it.id) }
                .thenBy { it.task?.dueDate ?: LocalDate.MAX }
                .thenBy { it.title },
        )

    /** 0 overdue, 1 due within [SOON_DAYS], 2 everything else. */
    private fun urgency(node: Node, today: LocalDate): Int {
        val due = node.task?.dueDate ?: return NOT_URGENT
        return when {
            due.isBefore(today) -> OVERDUE
            !due.isAfter(today.plusDays(SOON_DAYS)) -> DUE_SOON
            else -> NOT_URGENT
        }
    }

    /**
     * How much becomes startable if this node is finished — every task reachable downstream.
     * Ranking by this is the difference between a to-do list and a plan.
     */
    fun unblockedCount(nodeId: NodeId): Int {
        val seen = HashSet<NodeId>()
        val queue = ArrayDeque(dependents(nodeId).map { it.id })
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (!seen.add(next)) continue
            dependents(next).forEach { queue.addLast(it.id) }
        }
        return seen.count { byId[it]?.isLiveWork == true }
    }

    /**
     * Whether adding `source depends on target` would close a cycle. Checked before the edge
     * is written: a dependency cycle makes every node in it permanently blocked, and the
     * symptom shows up far from the cause.
     */
    fun wouldCycle(sourceId: NodeId, targetId: NodeId): Boolean {
        if (sourceId == targetId) return true
        val seen = HashSet<NodeId>()
        val queue = ArrayDeque(listOf(targetId))
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (next == sourceId) return true
            if (!seen.add(next)) continue
            byId[next]?.dependsOn?.forEach { queue.addLast(it) }
        }
        return false
    }

    /**
     * Layered ranks for the flow layout: a node sits one level past its deepest dependency,
     * so edges point consistently backwards and the eye can follow the order of work.
     */
    fun ranks(): Map<NodeId, Int> {
        val ranks = HashMap<NodeId, Int>()
        val visiting = HashSet<NodeId>()

        fun rankOf(id: NodeId): Int {
            ranks[id]?.let { return it }
            if (!visiting.add(id)) return 0 // Defensive: a hand-edited file could still cycle.
            val depth = (byId[id]?.dependsOn?.mapNotNull { byId[it]?.id } ?: emptyList())
                .maxOfOrNull { rankOf(it) + 1 } ?: 0
            visiting.remove(id)
            ranks[id] = depth
            return depth
        }

        nodes.forEach { rankOf(it.id) }
        return ranks
    }
}
