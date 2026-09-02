package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What putting a node away does to the work still in front of you. */
class ArchiveTest {

    private fun task(
        title: String,
        status: TaskStatus = TaskStatus.Todo,
        archived: Boolean = false,
        dependsOn: List<NodeId> = emptyList(),
    ) = Node(
        id = NodeId(title),
        title = title,
        body = "",
        task = TaskFacet(status = status),
        archived = archived,
        dependsOn = dependsOn,
        createdAt = "",
        updatedAt = "",
        slug = title,
    )

    @Test
    fun anArchivedTaskIsNotReadyWork() {
        val put = task("Put away", archived = true)

        assertTrue(TaskGraph(listOf(put)).readyTasks().isEmpty())
    }

    @Test
    fun archivingAnUnfinishedBlockerReleasesWhatWaitedOnIt() {
        val blocker = task("Abandoned groundwork", archived = true)
        val waiting = task("The work behind it", dependsOn = listOf(blocker.id))
        val graph = TaskGraph(listOf(blocker, waiting))

        // Archiving must not strand its dependents; that is the trap a cycle sets.
        assertFalse(graph.isBlocked(waiting.id))
        assertEquals(listOf("The work behind it"), graph.readyTasks().map { it.title })
    }

    @Test
    fun anArchivedTaskNoLongerCountsAsWorkYouWouldUnblock() {
        val keystone = task("Keystone")
        val live = task("Still wanted", dependsOn = listOf(keystone.id))
        val shelved = task("Shelved", archived = true, dependsOn = listOf(keystone.id))

        assertEquals(1, TaskGraph(listOf(keystone, live, shelved)).unblockedCount(keystone.id))
    }

    @Test
    fun archivingDoesNotChangeWhetherTheWorkWasDoneOrDropped() {
        val done = task("Finished", status = TaskStatus.Done, archived = true)
        val dropped = task("Abandoned", status = TaskStatus.Dropped, archived = true)

        // The whole reason this is not a fifth status: the outcome survives being put away.
        assertEquals(TaskStatus.Done, done.task?.status)
        assertEquals(TaskStatus.Dropped, dropped.task?.status)
    }

    @Test
    fun aPlainNoteCanBeArchivedToo() {
        val note = Node(
            id = NodeId("n"),
            title = "An old note",
            body = "",
            kind = NodeKind.Reference,
            archived = true,
            createdAt = "",
            updatedAt = "",
            slug = "n",
        )

        assertFalse(note.isTask)
        assertFalse(note.isLiveWork)
    }

    @Test
    fun archivedNodesLeaveTheCanvasUnlessAskedFor() {
        val live = task("Live")
        val shelved = task("Shelved", archived = true)
        val nodes = listOf(live, shelved)

        assertEquals(
            listOf("Live"),
            GraphFilter.apply(nodes, emptyList(), kind = null).nodes.map { it.title },
        )
        assertEquals(
            listOf("Live", "Shelved"),
            GraphFilter.apply(nodes, emptyList(), kind = null, includeArchived = true)
                .nodes.map { it.title },
        )
    }

    @Test
    fun theKindFilterAndTheArchiveToggleCompose() {
        val liveRfc = task("Live RFC").copy(kind = NodeKind.Rfc)
        val shelvedRfc = task("Shelved RFC", archived = true).copy(kind = NodeKind.Rfc)
        val note = task("A note")
        val nodes = listOf(liveRfc, shelvedRfc, note)

        val visible = GraphFilter.apply(nodes, emptyList(), NodeKind.Rfc, includeArchived = true)

        assertEquals(listOf("Live RFC", "Shelved RFC"), visible.nodes.map { it.title })
    }
}
