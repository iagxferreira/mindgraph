package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker

/** Tracked seconds split by who spent them. */
data class WorkerSplit(val human: Long = 0, val agent: Long = 0) {
    val total: Long get() = human + agent

    /** How much of this was the machine, 0f..1f. Zero when nothing was tracked at all. */
    val agentShare: Float get() = if (total == 0L) 0f else agent.toFloat() / total

    operator fun plus(other: WorkerSplit) = WorkerSplit(human + other.human, agent + other.agent)
}

data class NodeWork(val node: Node, val split: WorkerSplit)

/** Time spent by one agent. A null [name] is an agent that never introduced itself. */
data class AgentWork(val name: String?, val seconds: Long)

/**
 * Reads the session log into the shapes the Work screen draws.
 *
 * Kept out of the composable so the arithmetic can be tested — the interesting question this
 * screen answers, how much of a body of work was done by a machine, is not one you want to
 * verify by looking at a bar.
 */
object WorkSummary {

    fun splitOf(sessions: List<WorkSession>): WorkerSplit =
        sessions.fold(WorkerSplit()) { acc, session ->
            when (session.worker) {
                Worker.Human -> acc + WorkerSplit(human = session.seconds)
                Worker.Agent -> acc + WorkerSplit(agent = session.seconds)
            }
        }

    /** Every node with tracked time, most-worked first. */
    fun byNode(nodes: List<Node>, sessions: List<WorkSession>): List<NodeWork> {
        val byId = sessions.groupBy { it.nodeId }
        return nodes.mapNotNull { node ->
            val split = splitOf(byId[node.id].orEmpty())
            if (split.total > 0) NodeWork(node, split) else null
        }.sortedWith(compareByDescending<NodeWork> { it.split.total }.thenBy { it.node.title })
    }

    /** Machine time per agent, busiest first. Your own work is not an agent and is excluded. */
    fun byAgent(sessions: List<WorkSession>): List<AgentWork> =
        sessions.filter { it.worker == Worker.Agent }
            .groupBy { it.agent }
            .map { (name, its) -> AgentWork(name, its.sumOf { it.seconds }) }
            .sortedWith(compareByDescending<AgentWork> { it.seconds }.thenBy { it.name ?: "" })
}
