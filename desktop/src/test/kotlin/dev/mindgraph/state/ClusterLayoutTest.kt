package dev.mindgraph.state

import dev.mindgraph.model.Edge
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Grouping by where a node came from, and the placement that makes the grouping visible. */
class ClusterLayoutTest {

    private fun node(id: String, originProject: String? = null) = Node(
        id = NodeId(id),
        title = id,
        body = "",
        originProject = originProject,
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = id,
    )

    @Test
    fun aNodeIsGroupedByTheRepositoryItCameFrom() {
        val groups = Clustering.groups(
            listOf(
                node("a", "-home-iago-workspace-agni"),
                node("b", "-home-iago-workspace-agni"),
                node("c", "-home-iago-workspace-tally"),
            ),
        )
        assertEquals(mapOf("a" to "agni", "b" to "agni", "c" to "tally"), groups)
    }

    @Test
    fun nodesWrittenHereAreTheirOwnGroup() {
        // The majority of a vault has no origin; leaving them in a group named after nothing
        // would be the wrong reading of what they are.
        assertEquals(Clustering.LOCAL, Clustering.label(null))
        assertEquals(Clustering.LOCAL, Clustering.label("  "))
    }

    @Test
    fun aHyphenatedProjectKeepsItsWholeName() {
        assertEquals("geo-resolution-rag", Clustering.label("-home-iago-workspace-geo-resolution-rag"))
        assertEquals("algorithm-solutions", Clustering.label("-home-iago-workspace-algorithm-solutions"))
    }

    @Test
    fun anUnrecognisedShapeIsUsedAsIsRatherThanBlanked() {
        assertEquals("elsewhere", Clustering.label("elsewhere"))
    }

    @Test
    fun membersOfAGroupLandNearerEachOtherThanNodesOfAnother() {
        val engine = GraphLayoutEngine()
        val nodes = listOf(
            node("a1", "-home-iago-workspace-agni"),
            node("a2", "-home-iago-workspace-agni"),
            node("t1", "-home-iago-workspace-tally"),
            node("t2", "-home-iago-workspace-tally"),
        )
        engine.sync(nodes.map { it.id.value }, emptyList<Edge>())
        val groups = Clustering.groups(nodes)
        engine.setClusters(groups)
        engine.mode = LayoutMode.Cluster
        repeat(200) { engine.step() }

        fun at(id: String) = engine.positions.getValue(id)
        fun distance(a: String, b: String) = hypot(at(a).x - at(b).x, at(a).y - at(b).y)

        assertTrue(
            distance("a1", "a2") < distance("a1", "t1"),
            "same project should sit closer than different projects",
        )
        assertTrue(distance("t1", "t2") < distance("t1", "a1"))
    }

    @Test
    fun aLoneGroupIsCentredRatherThanPushedAside() {
        val engine = GraphLayoutEngine()
        val nodes = listOf(node("a", "-home-iago-workspace-agni"), node("b", "-home-iago-workspace-agni"))
        engine.sync(nodes.map { it.id.value }, emptyList<Edge>())
        engine.setClusters(Clustering.groups(nodes))
        engine.mode = LayoutMode.Cluster
        repeat(200) { engine.step() }

        val centres = engine.clusterCentres(Clustering.groups(nodes))
        assertEquals(1, centres.size)
        assertEquals(0f, centres.getValue("agni").x)
        assertEquals(0f, centres.getValue("agni").y)
    }

    @Test
    fun aBiggerGroupGetsABiggerRingSoItDoesNotSwallowItsNeighbours() {
        val engine = GraphLayoutEngine()
        val small = listOf(node("s1", "-home-iago-workspace-blog"), node("s2", "-home-iago-workspace-blog"))
        val big = (1..12).map { node("b$it", "-home-iago-workspace-agni") }
        val nodes = small + big
        engine.sync(nodes.map { it.id.value }, emptyList<Edge>())
        val groups = Clustering.groups(nodes)
        engine.setClusters(groups)
        engine.mode = LayoutMode.Cluster
        repeat(300) { engine.step() }

        val centres = engine.clusterCentres(groups)
        fun spread(ids: List<String>, centre: Vec2) =
            ids.maxOf { hypot(engine.positions.getValue(it).x - centre.x, engine.positions.getValue(it).y - centre.y) }

        assertTrue(
            spread(big.map { it.id.value }, centres.getValue("agni")) >
                spread(small.map { it.id.value }, centres.getValue("blog")),
        )
    }

    @Test
    fun clusteringDoesNotMoveAPinnedNode() {
        val engine = GraphLayoutEngine()
        val nodes = listOf(node("a", "-home-iago-workspace-agni"), node("b", "-home-iago-workspace-tally"))
        engine.sync(nodes.map { it.id.value }, emptyList<Edge>())
        engine.setPinned("a", true)
        val before = engine.positions.getValue("a")
        engine.setClusters(Clustering.groups(nodes))
        engine.mode = LayoutMode.Cluster
        repeat(100) { engine.step() }

        assertEquals(before, engine.positions.getValue("a"), "a pinned node is where you put it")
    }
}
