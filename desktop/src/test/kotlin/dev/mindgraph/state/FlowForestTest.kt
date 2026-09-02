package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskFacet
import dev.mindgraph.model.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Flow mode's subject: the dependency trees among tasks, and nothing else. */
class FlowForestTest {

    private fun task(
        id: String,
        status: TaskStatus = TaskStatus.Todo,
        dependsOn: List<String> = emptyList(),
        archived: Boolean = false,
    ) = Node(
        id = NodeId(id),
        title = id,
        body = "",
        task = TaskFacet(status = status),
        archived = archived,
        dependsOn = dependsOn.map(::NodeId),
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = id,
    )

    private fun note(id: String) = Node(
        id = NodeId(id),
        title = id,
        body = "",
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = id,
    )

    private fun ids(layout: FlowForest.Layout) = layout.nodes.map { it.id.value }.toSet()

    @Test
    fun onlyTasksInADependencyAreDrawn() {
        // The defect this view exists to fix: 55 unconnected tasks and 34 notes were laid out
        // by dependency depth, so the real chains were lost among them.
        val layout = FlowForest.build(
            listOf(
                task("a"),
                task("b", dependsOn = listOf("a")),
                task("loose"),
                note("plain-note"),
            ),
        )
        assertEquals(setOf("a", "b"), ids(layout))
    }

    @Test
    fun looseTasksAreCountedRatherThanSilentlyDropped() {
        val layout = FlowForest.build(
            listOf(task("a"), task("b", dependsOn = listOf("a")), task("x"), task("y"), task("z")),
        )
        assertEquals(3, layout.looseTaskCount)
    }

    @Test
    fun aNoteIsNeverCountedAsALooseTask() {
        val layout = FlowForest.build(listOf(note("n1"), note("n2")))
        assertEquals(0, layout.looseTaskCount)
        assertTrue(layout.isEmpty)
    }

    @Test
    fun aPrerequisiteSitsAboveTheWorkWaitingOnIt() {
        val layout = FlowForest.build(listOf(task("root"), task("child", dependsOn = listOf("root"))))
        val root = layout.positions.getValue("root")
        val child = layout.positions.getValue("child")
        assertTrue(root.y < child.y, "the prerequisite should be above its dependent")
    }

    @Test
    fun aPrerequisiteIsCentredOverItsDependents() {
        val layout = FlowForest.build(
            listOf(
                task("root"),
                task("l", dependsOn = listOf("root")),
                task("r", dependsOn = listOf("root")),
            ),
        )
        val root = layout.positions.getValue("root")
        val left = layout.positions.getValue("l")
        val right = layout.positions.getValue("r")
        assertEquals((left.x + right.x) / 2f, root.x, 0.01f)
    }

    @Test
    fun aSharedPrerequisiteIsPlacedOnce() {
        // A diamond: two tasks both waiting on one, then one waiting on both.
        val layout = FlowForest.build(
            listOf(
                task("top"),
                task("l", dependsOn = listOf("top")),
                task("r", dependsOn = listOf("top")),
                task("bottom", dependsOn = listOf("l", "r")),
            ),
        )
        assertEquals(4, layout.nodes.size)
        val bottom = layout.positions.getValue("bottom")
        val top = layout.positions.getValue("top")
        assertTrue(bottom.y > top.y)
        assertEquals(top.x, bottom.x, 0.01f, "a diamond should close, not fork apart")
    }

    @Test
    fun hidingDoneKeepsChainInteriorsAsGhosts() {
        // The real failure: 7 of the 9 nodes in the vault's one real tree are done, so hiding
        // them left the live tasks as orphans with no visible reason to be where they are.
        val layout = FlowForest.build(
            listOf(
                task("finished", status = TaskStatus.Done),
                task("middle", status = TaskStatus.Done, dependsOn = listOf("finished")),
                task("live", dependsOn = listOf("middle")),
            ),
            includeDone = false,
        )
        assertEquals(setOf("finished", "middle", "live"), ids(layout))
        assertEquals(setOf(NodeId("finished"), NodeId("middle")), layout.ghosts)
    }

    @Test
    fun nothingIsAGhostWhileDoneWorkIsShown() {
        val layout = FlowForest.build(
            listOf(task("a", status = TaskStatus.Done), task("b", dependsOn = listOf("a"))),
            includeDone = true,
        )
        assertTrue(layout.ghosts.isEmpty())
    }

    @Test
    fun aDoneTaskWithNoDependenciesStillDoesNotAppear() {
        // Ghosting is for holding a tree together, not for bringing finished work back.
        val layout = FlowForest.build(
            listOf(task("lonely-done", status = TaskStatus.Done), task("x"), task("y")),
            includeDone = false,
        )
        assertTrue(layout.isEmpty)
    }

    @Test
    fun archivedTasksAreLeftOut() {
        val layout = FlowForest.build(
            listOf(task("a"), task("b", dependsOn = listOf("a"), archived = true)),
        )
        assertTrue(layout.isEmpty, "one live task with an archived dependent is not a chain")
    }

    @Test
    fun archivedChainMembersComeBackWhenArchivedWorkIsShown() {
        val nodes = listOf(task("a"), task("b", dependsOn = listOf("a"), archived = true))
        assertEquals(setOf("a", "b"), ids(FlowForest.build(nodes, includeArchived = true)))
    }

    @Test
    fun aDependencyOnANonTaskDoesNotDragTheNoteIn() {
        val layout = FlowForest.build(listOf(note("spec"), task("work", dependsOn = listOf("spec"))))
        assertTrue(layout.isEmpty)
    }

    @Test
    fun separateChainsDoNotShareAColumn() {
        // Two independent pairs must read as two pictures, not one interleaved row.
        val layout = FlowForest.build(
            listOf(
                task("a1"), task("a2", dependsOn = listOf("a1")),
                task("b1"), task("b2", dependsOn = listOf("b1")),
            ),
        )
        val a = layout.positions.getValue("a1").x
        val b = layout.positions.getValue("b1").x
        assertTrue(a != b, "two trees should be packed apart, not stacked on each other")
    }

    @Test
    fun manyChainsWrapInsteadOfFormingOneLongRow() {
        // The original bug in one assertion: 40 pairs used to be 8,000px of unbroken row.
        val nodes = (0 until 40).flatMap { listOf(task("r$it"), task("c$it", dependsOn = listOf("r$it"))) }
        val layout = FlowForest.build(nodes)

        val width = layout.positions.values.let { ps -> ps.maxOf { it.x } - ps.minOf { it.x } }
        val height = layout.positions.values.let { ps -> ps.maxOf { it.y } - ps.minOf { it.y } }
        assertTrue(width <= 1600f, "expected the trees to wrap, got a row ${width}px wide")
        assertTrue(height > 0f, "wrapping should use vertical space")
    }

    @Test
    fun theLayoutIsCentredOnTheOrigin() {
        val layout = FlowForest.build(listOf(task("a"), task("b", dependsOn = listOf("a"))))
        val xs = layout.positions.values.map { it.x }
        val ys = layout.positions.values.map { it.y }
        assertEquals(0f, (xs.min() + xs.max()) / 2f, 0.01f)
        assertEquals(0f, (ys.min() + ys.max()) / 2f, 0.01f)
    }

    @Test
    fun aCycleDoesNotHangOrLoseNodes() {
        // Defensive: the vault is hand-editable markdown.
        val layout = FlowForest.build(
            listOf(task("a", dependsOn = listOf("b")), task("b", dependsOn = listOf("a"))),
        )
        assertEquals(setOf("a", "b"), ids(layout))
        assertEquals(2, layout.positions.size)
    }

    @Test
    fun theLayoutIsStableAcrossRuns() {
        val nodes = listOf(
            task("root"),
            task("x", dependsOn = listOf("root")),
            task("y", dependsOn = listOf("root")),
        )
        assertEquals(FlowForest.build(nodes).positions, FlowForest.build(nodes).positions)
    }

    @Test
    fun theRealVaultShapeComesOutAsOneTree() {
        // The vault's actual 9-node chain, which the old layout rendered as scattered dots.
        val layout = FlowForest.build(
            listOf(
                task("kind-field", status = TaskStatus.Done),
                task("memory-import", status = TaskStatus.Done, dependsOn = listOf("kind-field")),
                task("plans-import", status = TaskStatus.Done, dependsOn = listOf("kind-field")),
                task("kind-filter", status = TaskStatus.Done, dependsOn = listOf("kind-field")),
                task("wikilinks", status = TaskStatus.Done, dependsOn = listOf("memory-import")),
                task("cluster", status = TaskStatus.Doing, dependsOn = listOf("memory-import")),
                task("search-notes", status = TaskStatus.Done),
                task("related-notes", dependsOn = listOf("wikilinks", "search-notes")),
                task("unrelated-loose"),
            ),
            includeDone = false,
        )
        assertEquals(8, layout.nodes.size, "all eight chain members should be drawn")
        assertEquals(1, layout.looseTaskCount)
        assertFalse(NodeId("cluster") in layout.ghosts, "live work is not a ghost")
        assertTrue(NodeId("kind-field") in layout.ghosts, "finished prerequisites are ghosts")

        // The live task at the bottom must sit below the finished work it came out of.
        assertTrue(
            layout.positions.getValue("related-notes").y > layout.positions.getValue("kind-field").y,
        )
    }
}
