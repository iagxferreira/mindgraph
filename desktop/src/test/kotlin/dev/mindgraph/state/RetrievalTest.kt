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

/** Walking the graph to assemble a context document, and cutting it to a budget. */
class RetrievalTest {

    private fun node(
        id: String,
        body: String = "body of $id",
        contextFor: List<String> = emptyList(),
        dependsOn: List<String> = emptyList(),
        relatesTo: List<String> = emptyList(),
        archived: Boolean = false,
        kind: NodeKind = NodeKind.Note,
    ) = Node(
        id = NodeId(id),
        title = id,
        body = body,
        kind = kind,
        task = TaskFacet(TaskStatus.Todo),
        archived = archived,
        dependsOn = dependsOn.map(::NodeId),
        relatesTo = relatesTo.map(::NodeId),
        contextFor = contextFor.map(::NodeId),
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = id,
    )

    private fun seed(nodes: List<Node>, id: String) = nodes.single { it.id.value == id }

    @Test
    fun curatedContextIsFoundEvenThoughItsEdgePointsTheOtherWay() {
        // The case the whole feature exists for. `context_for` points from the note to the work
        // it serves, so a walk that only followed outgoing edges would find nothing at all.
        val nodes = listOf(node("project"), node("convention", contextFor = listOf("project")))
        val found = Retrieval.gather(nodes, seed(nodes, "project"))

        assertEquals(listOf("convention"), found.map { it.node.id.value })
        assertEquals(Retrieval.Reason.Curated, found.single().reason)
    }

    @Test
    fun aChosenEdgeOutranksAnInferredOneAtTheSameDistance() {
        val nodes = listOf(
            node("project", relatesTo = listOf("mentioned")),
            node("mentioned"),
            node("chosen", contextFor = listOf("project")),
        )
        val found = Retrieval.gather(nodes, seed(nodes, "project"))
        assertEquals(listOf("chosen", "mentioned"), found.map { it.node.id.value })
    }

    @Test
    fun nearerBeatsBetterReason() {
        // Distance first: a loose association next door is still more relevant than a curated
        // note three rooms away.
        val nodes = listOf(
            node("project", relatesTo = listOf("near")),
            node("near", relatesTo = listOf("middle")),
            node("middle"),
            node("far", contextFor = listOf("middle")),
        )
        val found = Retrieval.gather(nodes, seed(nodes, "project"), hops = 3)
        assertEquals("near", found.first().node.id.value)
        assertTrue(found.map { it.node.id.value }.indexOf("far") > found.map { it.node.id.value }.indexOf("near"))
    }

    @Test
    fun theWalkStopsAtTheHopLimit() {
        val nodes = listOf(
            node("a", relatesTo = listOf("b")),
            node("b", relatesTo = listOf("c")),
            node("c", relatesTo = listOf("d")),
            node("d"),
        )
        assertEquals(setOf("b"), Retrieval.gather(nodes, seed(nodes, "a"), hops = 1).map { it.node.id.value }.toSet())
        assertEquals(setOf("b", "c"), Retrieval.gather(nodes, seed(nodes, "a"), hops = 2).map { it.node.id.value }.toSet())
    }

    @Test
    fun archivedNotesAreNotContext() {
        val nodes = listOf(node("project"), node("old", contextFor = listOf("project"), archived = true))
        assertTrue(Retrieval.gather(nodes, seed(nodes, "project")).isEmpty())
    }

    @Test
    fun aCycleDoesNotLoopForever() {
        val nodes = listOf(node("a", relatesTo = listOf("b")), node("b", relatesTo = listOf("a")))
        assertEquals(listOf("b"), Retrieval.gather(nodes, seed(nodes, "a"), hops = 4).map { it.node.id.value })
    }

    @Test
    fun theSeedIsNeverListedAsItsOwnContext() {
        val nodes = listOf(node("a", relatesTo = listOf("b")), node("b", relatesTo = listOf("a")))
        assertTrue(Retrieval.gather(nodes, seed(nodes, "a")).none { it.node.id.value == "a" })
    }

    @Test
    fun theBundleIsADocumentNotAListOfTitles() {
        val nodes = listOf(node("project", body = "Build the thing"), node("note", body = "A useful convention", contextFor = listOf("project")))
        val text = Retrieval.markdown(Retrieval.bundle(nodes, seed(nodes, "project")))

        assertTrue(text.contains("# Context for: project"), text)
        assertTrue(text.contains("Build the thing"), "the seed's body must be there")
        assertTrue(text.contains("A useful convention"), "the neighbour's body, not just its title")
        assertTrue(text.contains("chosen as context"), "the document says why each piece is in it")
    }

    @Test
    fun theBudgetIsRespectedAndWhatDidNotFitIsReported() {
        val big = "x".repeat(3_000)
        val nodes = listOf(
            node("project", body = "seed"),
            node("a", body = big, contextFor = listOf("project")),
            node("b", body = big, contextFor = listOf("project")),
            node("c", body = big, contextFor = listOf("project")),
        )
        val bundle = Retrieval.bundle(nodes, seed(nodes, "project"), budgetCharacters = 4_000)

        assertTrue(bundle.charactersUsed <= 4_000, "used ${bundle.charactersUsed}")
        assertTrue(bundle.omitted.isNotEmpty(), "something had to be left out")
        val text = Retrieval.markdown(bundle)
        assertTrue(text.contains("Not included (${bundle.omitted.size})"), text.takeLast(400))
    }

    @Test
    fun theSeedIsAlwaysWholeEvenWhenTheBudgetIsTight() {
        // Truncating the thing you asked about spends the budget on the one document the caller
        // already knew it needed.
        val nodes = listOf(
            node("project", body = "the important seed body"),
            node("other", body = "y".repeat(5_000), contextFor = listOf("project")),
        )
        val text = Retrieval.markdown(Retrieval.bundle(nodes, seed(nodes, "project"), budgetCharacters = 600))
        assertTrue(text.contains("the important seed body"), text)
    }

    @Test
    fun nothingIsHalfIncludedWithoutSayingSo() {
        val nodes = listOf(
            node("project", body = "seed"),
            node("long", body = "z".repeat(4_000), contextFor = listOf("project")),
        )
        val bundle = Retrieval.bundle(nodes, seed(nodes, "project"), budgetCharacters = 1_500)
        val cut = bundle.included.singleOrNull { !it.whole }
        if (cut != null) {
            assertTrue(Retrieval.markdown(bundle).contains("excerpt"), "a cut body must say it was cut")
        }
        assertFalse(bundle.omitted.isEmpty() && cut == null, "the budget must have bitten somewhere")
    }

    @Test
    fun theSameVaultAlwaysRendersTheSameDocument() {
        val nodes = listOf(
            node("project"),
            node("b", contextFor = listOf("project")),
            node("a", contextFor = listOf("project")),
        )
        val once = Retrieval.markdown(Retrieval.bundle(nodes, seed(nodes, "project")))
        val twice = Retrieval.markdown(Retrieval.bundle(nodes.reversed(), seed(nodes, "project")))
        assertEquals(once, twice, "ordering must not depend on vault iteration order")
    }

    @Test
    fun aNodeWithNoNeighboursStillProducesAUsableDocument() {
        val nodes = listOf(node("lonely", body = "all alone"))
        val text = Retrieval.markdown(Retrieval.bundle(nodes, seed(nodes, "lonely")))
        assertTrue(text.contains("all alone"))
        assertFalse(text.contains("Not included"))
    }
}
