package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The order ready work is offered in. Today is fixed so the ranking is not a moving target. */
class RankedReadyTasksTest {

    private val today = LocalDate.of(2026, 9, 1)

    private fun task(
        title: String,
        due: String? = null,
        dependsOn: List<NodeId> = emptyList(),
        status: TaskStatus = TaskStatus.Todo,
    ) = Node(
        id = NodeId(title),
        title = title,
        body = "",
        task = TaskFacet(status = status, due = due),
        dependsOn = dependsOn,
        createdAt = "",
        updatedAt = "",
        slug = title,
    )

    private fun order(vararg nodes: Node): List<String> =
        TaskGraph(nodes.toList()).rankedReadyTasks(today).map { it.title }

    @Test
    fun anOverdueTaskOutranksEvenAKeystone() {
        val keystone = task("Keystone")
        val overdue = task("Overdue", due = "2026-08-30")
        val alsoBlocked = task("Depends on keystone", dependsOn = listOf(keystone.id))

        // The keystone unblocks work; the overdue task unblocks nothing. The deadline still wins,
        // because it is the one claim on your time somebody else may be holding you to.
        assertEquals(listOf("Overdue", "Keystone"), order(keystone, overdue, alsoBlocked).take(2))
    }

    @Test
    fun dueTodayCountsAsDueSoonNotOverdue() {
        val order = order(task("Due today", due = "2026-09-01"), task("Overdue", due = "2026-08-31"))

        assertEquals(listOf("Overdue", "Due today"), order)
    }

    @Test
    fun aDeadlineJustOutsideTheWindowDoesNotJumpTheQueue() {
        val keystone = task("Keystone")
        val dependent = task("Dependent", dependsOn = listOf(keystone.id))
        val distant = task("Due in a fortnight", due = "2026-09-15")

        // Three days is "soon"; two weeks is a plan, and leverage should still lead.
        assertEquals(listOf("Keystone", "Due in a fortnight"), order(keystone, dependent, distant))
    }

    @Test
    fun withinTheSameUrgencyTheMostUnblockingComesFirst() {
        val keystone = task("Unblocks two")
        val lesser = task("Unblocks one")
        val order = order(
            keystone,
            lesser,
            task("A", dependsOn = listOf(keystone.id)),
            task("B", dependsOn = listOf(keystone.id)),
            task("C", dependsOn = listOf(lesser.id)),
        )

        assertEquals(listOf("Unblocks two", "Unblocks one"), order.take(2))
    }

    @Test
    fun equalUrgencyAndLeverageFallsBackToTheEarlierDeadline() {
        val order = order(
            task("Later", due = "2026-10-01"),
            task("Sooner", due = "2026-09-20"),
        )

        assertEquals(listOf("Sooner", "Later"), order)
    }

    @Test
    fun titlesAreOnlyEverTheLastResort() {
        val order = order(task("Zebra"), task("Aardvark"))

        assertEquals(listOf("Aardvark", "Zebra"), order)
    }

    @Test
    fun aDeadlineNobodyCanParseIsTreatedAsNoDeadline() {
        val vague = task("Vague", due = "next tuesday")

        assertNull(vague.task?.dueDate)
        // It must still be offered, just without urgency it did not earn.
        assertEquals(listOf("Overdue", "Vague"), order(vague, task("Overdue", due = "2026-08-01")))
    }

    @Test
    fun blockedWorkIsNeverRankedNoMatterHowOverdue() {
        val blocker = task("Unfinished blocker")
        val overdueButBlocked = task("Overdue", due = "2026-01-01", dependsOn = listOf(blocker.id))

        assertEquals(listOf("Unfinished blocker"), order(blocker, overdueButBlocked))
    }

    @Test
    fun aFinishedTaskIsNotReadyWorkEvenWhenOverdue() {
        val done = task("Done", due = "2026-01-01", status = TaskStatus.Done)

        assertEquals(emptyList(), order(done))
    }
}
