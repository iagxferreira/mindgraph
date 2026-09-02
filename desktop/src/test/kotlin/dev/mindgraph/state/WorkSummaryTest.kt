package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.WorkSession
import dev.mindgraph.model.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The arithmetic behind the Work screen: how much of this was done by a machine. */
class WorkSummaryTest {

    private fun node(title: String) = Node(
        id = NodeId(title),
        title = title,
        body = "",
        createdAt = "",
        updatedAt = "",
        slug = title,
    )

    private fun human(node: Node, seconds: Long) =
        WorkSession(node.id, 0, seconds, seconds, Worker.Human)

    private fun agent(node: Node, seconds: Long, name: String? = "claude-code") =
        WorkSession(node.id, 0, seconds, seconds, Worker.Agent, name)

    @Test
    fun theSplitSeparatesYourTimeFromTheMachines() {
        val task = node("A task")

        val split = WorkSummary.splitOf(listOf(human(task, 600), agent(task, 1800)))

        assertEquals(600, split.human)
        assertEquals(1800, split.agent)
        assertEquals(2400, split.total)
        assertEquals(0.75f, split.agentShare)
    }

    @Test
    fun nothingTrackedIsNotAllMachine() {
        // A share computed from an empty total must not read as 100% machine.
        assertEquals(0f, WorkSummary.splitOf(emptyList()).agentShare)
        assertEquals(0L, WorkSummary.splitOf(emptyList()).total)
    }

    @Test
    fun nodesAreRankedByTotalTimeNotByWhoSpentIt() {
        val big = node("Mostly machine")
        val small = node("All yours")
        val sessions = listOf(agent(big, 3600), human(small, 600))

        val ranked = WorkSummary.byNode(listOf(small, big), sessions)

        assertEquals(listOf("Mostly machine", "All yours"), ranked.map { it.node.title })
    }

    @Test
    fun aNodeWithNoTrackedTimeIsLeftOut() {
        val worked = node("Worked")
        val untouched = node("Untouched")

        val ranked = WorkSummary.byNode(listOf(worked, untouched), listOf(human(worked, 60)))

        assertEquals(listOf("Worked"), ranked.map { it.node.title })
    }

    @Test
    fun sessionsForADeletedNodeDoNotInventARow() {
        val gone = node("Deleted")

        val ranked = WorkSummary.byNode(emptyList(), listOf(agent(gone, 600)))

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun agentTimeIsGroupedByName() {
        val task = node("A task")
        val sessions = listOf(
            agent(task, 600, "claude-code"),
            agent(task, 1200, "claude-code"),
            agent(task, 300, "cursor"),
            human(task, 9000),
        )

        val agents = WorkSummary.byAgent(sessions)

        assertEquals(listOf("claude-code", "cursor"), agents.map { it.name })
        assertEquals(1800, agents.first().seconds)
    }

    @Test
    fun yourOwnWorkIsNeverListedAsAnAgent() {
        val task = node("A task")

        assertTrue(WorkSummary.byAgent(listOf(human(task, 3600))).isEmpty())
    }

    @Test
    fun anAgentThatNeverNamedItselfStillAppears() {
        val task = node("A task")

        val agents = WorkSummary.byAgent(listOf(agent(task, 600, name = null)))

        assertEquals(1, agents.size)
        assertEquals(null, agents.single().name)
        assertEquals(600, agents.single().seconds)
    }

    @Test
    fun nodesTiedOnTimeFallBackToTheirTitles() {
        val zebra = node("Zebra")
        val aardvark = node("Aardvark")
        val sessions = listOf(human(zebra, 600), human(aardvark, 600))

        val ranked = WorkSummary.byNode(listOf(zebra, aardvark), sessions)

        assertEquals(listOf("Aardvark", "Zebra"), ranked.map { it.node.title })
    }
}
