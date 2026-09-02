package dev.mindgraph.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which field an edge kind lives in is the model's business, and every kind must have one. */
class NodeLinksTest {

    private fun node() = Node(
        id = NodeId("n"),
        title = "n",
        body = "",
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = "n",
    )

    @Test
    fun everyKindHasSomewhereToLive() {
        // The guard against adding a fourth kind and forgetting to give it a field: this fails
        // to compile if `links` misses a branch, and fails here if it maps two kinds together.
        val target = NodeId("t")
        val fields = EdgeKind.entries.map { kind -> kind to node().withLink(kind, target).links(kind) }
        for ((kind, links) in fields) {
            assertEquals(listOf(target), links, "$kind did not store its own link")
        }
    }

    @Test
    fun aKindDoesNotLeakIntoAnother() {
        val linked = node().withLink(EdgeKind.ContextFor, NodeId("project"))
        assertEquals(listOf(NodeId("project")), linked.contextFor)
        assertTrue(linked.relatesTo.isEmpty(), "context is not association")
        assertTrue(linked.dependsOn.isEmpty(), "context does not order work")
    }

    @Test
    fun contextForIsAdditiveSoOneNoteCanBriefSeveralProjects() {
        val linked = node()
            .withLink(EdgeKind.ContextFor, NodeId("a"))
            .withLink(EdgeKind.ContextFor, NodeId("b"))
        assertEquals(listOf(NodeId("a"), NodeId("b")), linked.contextFor)
    }
}
