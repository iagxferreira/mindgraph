package dev.mindgraph.state

import dev.mindgraph.model.Edge
import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphFilterTest {

    private fun node(title: String, kind: NodeKind = NodeKind.Note) = Node(
        id = NodeId(title),
        title = title,
        body = "",
        kind = kind,
        createdAt = "",
        updatedAt = "",
        slug = title,
    )

    private fun edge(from: Node, to: Node) = Edge(from.id, to.id, EdgeKind.RelatesTo)

    private val rfc = node("RFC-001", NodeKind.Rfc)
    private val note = node("A note")
    private val reference = node("A reference", NodeKind.Reference)
    private val otherRfc = node("RFC-002", NodeKind.Rfc)

    private val nodes = listOf(rfc, note, reference, otherRfc)
    private val edges = listOf(edge(rfc, note), edge(rfc, otherRfc), edge(note, reference))

    @Test
    fun noFilterChangesNothing() {
        val visible = GraphFilter.apply(nodes, edges, null)

        assertEquals(nodes, visible.nodes)
        assertEquals(edges, visible.edges)
    }

    @Test
    fun onlyTheChosenKindSurvives() {
        val visible = GraphFilter.apply(nodes, edges, NodeKind.Rfc)

        assertEquals(listOf("RFC-001", "RFC-002"), visible.nodes.map { it.title })
    }

    @Test
    fun anEdgeWithOneEndHiddenIsHiddenToo() {
        val visible = GraphFilter.apply(nodes, edges, NodeKind.Rfc)

        // RFC-001 → A note would otherwise be a line trailing off to nothing.
        assertEquals(listOf(edge(rfc, otherRfc)), visible.edges)
    }

    @Test
    fun aKindWithNothingInItLeavesAnEmptyCanvasNotAnError() {
        val visible = GraphFilter.apply(listOf(note), emptyList(), NodeKind.Rfc)

        assertTrue(visible.nodes.isEmpty())
        assertTrue(visible.edges.isEmpty())
    }

    @Test
    fun filteringDoesNotReorderWhatRemains() {
        // Order is the list's own; the filter must not become a second opinion about it.
        val visible = GraphFilter.apply(nodes, edges, NodeKind.Rfc)

        assertEquals(
            nodes.filter { it.kind == NodeKind.Rfc }.map { it.id },
            visible.nodes.map { it.id },
        )
    }
}
