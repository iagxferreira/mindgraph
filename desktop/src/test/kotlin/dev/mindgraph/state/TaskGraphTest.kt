package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskGraphTest {
    private fun node(
        id: String,
        status: TaskStatus? = TaskStatus.Todo,
        dependsOn: List<String> = emptyList(),
    ) = Node(
        id = NodeId(id),
        title = id,
        body = "",
        task = status?.let { TaskFacet(status = it) },
        dependsOn = dependsOn.map(::NodeId),
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        slug = id.lowercase(),
    )

    @Test
    fun aTaskWithUnfinishedDependenciesIsBlocked() {
        val graph = TaskGraph(listOf(node("A"), node("B", dependsOn = listOf("A"))))

        assertTrue(graph.isBlocked(NodeId("B")))
        assertFalse(graph.isBlocked(NodeId("A")), "a task with no dependencies is never blocked")
    }

    @Test
    fun finishingTheDependencyMakesTheDependentReady() {
        val graph = TaskGraph(
            listOf(node("A", status = TaskStatus.Done), node("B", dependsOn = listOf("A"))),
        )

        assertFalse(graph.isBlocked(NodeId("B")))
        assertEquals(listOf(NodeId("B")), graph.readyTasks().map { it.id })
    }

    @Test
    fun plainNotesNeverBlockATask() {
        val reference = node("Reference", status = null)
        val graph = TaskGraph(listOf(reference, node("B", dependsOn = listOf("Reference"))))

        assertFalse(graph.isBlocked(NodeId("B")), "a reference note is not a gate")
    }

    @Test
    fun blockedStatePropagatesDownAChain() {
        val graph = TaskGraph(
            listOf(
                node("A"),
                node("B", dependsOn = listOf("A")),
                node("C", dependsOn = listOf("B")),
            ),
        )

        assertTrue(graph.isBlocked(NodeId("B")))
        assertTrue(graph.isBlocked(NodeId("C")))
        assertEquals(listOf(NodeId("A")), graph.readyTasks().map { it.id })
    }

    @Test
    fun unblockedCountReachesTheWholeDownstream() {
        val graph = TaskGraph(
            listOf(
                node("root"),
                node("mid", dependsOn = listOf("root")),
                node("leafOne", dependsOn = listOf("mid")),
                node("leafTwo", dependsOn = listOf("mid")),
            ),
        )

        assertEquals(3, graph.unblockedCount(NodeId("root")))
        assertEquals(2, graph.unblockedCount(NodeId("mid")))
        assertEquals(0, graph.unblockedCount(NodeId("leafOne")))
    }

    @Test
    fun cyclesAreDetectedBeforeTheyAreWritten() {
        val graph = TaskGraph(
            listOf(node("A"), node("B", dependsOn = listOf("A"))),
        )

        assertTrue(graph.wouldCycle(NodeId("A"), NodeId("B")), "A depending on B closes the loop")
        assertTrue(graph.wouldCycle(NodeId("A"), NodeId("A")), "self-dependency is a cycle")
        assertFalse(graph.wouldCycle(NodeId("B"), NodeId("A")), "this edge already exists and is fine")
    }

    @Test
    fun longerCyclesAreAlsoRejected() {
        val graph = TaskGraph(
            listOf(
                node("A"),
                node("B", dependsOn = listOf("A")),
                node("C", dependsOn = listOf("B")),
            ),
        )

        assertTrue(graph.wouldCycle(NodeId("A"), NodeId("C")))
    }

    @Test
    fun ranksPlaceEachNodePastItsDeepestDependency() {
        val graph = TaskGraph(
            listOf(
                node("A"),
                node("B", dependsOn = listOf("A")),
                node("C", dependsOn = listOf("A", "B")),
            ),
        )

        val ranks = graph.ranks()
        assertEquals(0, ranks[NodeId("A")])
        assertEquals(1, ranks[NodeId("B")])
        assertEquals(2, ranks[NodeId("C")], "C sits past its deepest dependency, not its first")
    }

    @Test
    fun rankingSurvivesAHandEditedCycle() {
        val graph = TaskGraph(
            listOf(
                node("A", dependsOn = listOf("B")),
                node("B", dependsOn = listOf("A")),
            ),
        )

        assertEquals(2, graph.ranks().size, "a cycle must not hang the layout")
    }
}
