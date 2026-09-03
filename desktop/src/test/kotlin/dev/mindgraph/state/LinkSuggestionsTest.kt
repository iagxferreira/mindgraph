package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Edges the vault has evidence for and does not have. */
class LinkSuggestionsTest {

    private fun node(
        id: String,
        title: String = id,
        body: String = "",
        aliases: List<String> = emptyList(),
        relatesTo: List<String> = emptyList(),
        archived: Boolean = false,
    ) = Node(
        id = NodeId(id),
        title = title,
        body = body,
        aliases = aliases,
        archived = archived,
        relatesTo = relatesTo.map(::NodeId),
        createdAt = "2026-09-03T00:00:00Z",
        updatedAt = "2026-09-03T00:00:00Z",
        slug = id,
    )

    @Test
    fun aNoteThatNamesAnotherIsSuggested() {
        val nodes = listOf(
            node("a", "Money representation"),
            node("b", "The ledger", "We settled this in Money representation last week."),
        )
        val found = LinkSuggestions.across(nodes)
        assertEquals(1, found.size)
        assertEquals("The ledger", found.single().from.title)
        assertEquals("Money representation", found.single().to.title)
        assertEquals(LinkSuggestions.Reason.UnlinkedMention, found.single().reason)
    }

    @Test
    fun anExistingLinkIsNotSuggested() {
        val nodes = listOf(
            node("a", "Money representation"),
            node("b", "The ledger", "See Money representation.", relatesTo = listOf("a")),
        )
        assertTrue(LinkSuggestions.across(nodes).isEmpty())
    }

    @Test
    fun aWikilinkAlreadyCountsAsLinked() {
        // It becomes an edge on load, so offering it would be offering what is already there.
        val nodes = listOf(
            node("a", "Money representation"),
            node("b", "The ledger", "See [[Money representation]] for the reasoning."),
        )
        assertTrue(LinkSuggestions.across(nodes).isEmpty())
    }

    @Test
    fun aDanglingLinkThatNearlyMatchesIsTheStrongestSignal() {
        // Someone wrote the link and it did not land - a stated intention beats a mention.
        val nodes = listOf(
            node("a", "Money representation"),
            node("b", "The ledger", "See [[money-representation]]."),
            node("c", "A third note", "Money representation came up again."),
        )
        val found = LinkSuggestions.across(nodes)
        assertEquals(LinkSuggestions.Reason.DanglingLink, found.first().reason)
        assertEquals("money-representation", found.first().evidence)
    }

    @Test
    fun anAmbiguousNameIsNeverSuggested() {
        // Forty-nine notes are called `index` in the real vault. An offer that cannot say which
        // node it means is not an offer.
        val nodes = listOf(
            node("a", "Elixir roadmap"),
            node("b", "Elixir roadmap"),
            node("c", "Planning", "The Elixir roadmap says otherwise."),
        )
        assertTrue(LinkSuggestions.across(nodes).isEmpty())
    }

    @Test
    fun aShortNameIsNotMatchedByCoincidence() {
        val nodes = listOf(node("a", "CLI"), node("b", "Notes", "Run the CLI first."))
        assertTrue(LinkSuggestions.across(nodes).isEmpty(), "three characters is a coincidence")
    }

    @Test
    fun aNameInsideALongerWordIsNotAMention() {
        val nodes = listOf(
            node("a", "Threading"),
            node("b", "Notes", "Discussion of Threadings and other plurals."),
        )
        assertTrue(LinkSuggestions.across(nodes).none { it.to.title == "Threading" })
    }

    @Test
    fun anAliasCountsAsAName() {
        val nodes = listOf(
            node("a", "How we represent money", aliases = listOf("money-representation")),
            node("b", "The ledger", "Discussed under money-representation."),
        )
        assertEquals("How we represent money", LinkSuggestions.across(nodes).single().to.title)
    }

    @Test
    fun archivedNodesAreNotOffered() {
        val nodes = listOf(
            node("a", "Money representation", archived = true),
            node("b", "The ledger", "See Money representation."),
        )
        assertTrue(LinkSuggestions.across(nodes).isEmpty())
    }

    @Test
    fun aNodeIsNotSuggestedToItself() {
        val nodes = listOf(node("a", "Money representation", "Money representation, again."))
        assertTrue(LinkSuggestions.across(nodes).isEmpty())
    }

    @Test
    fun scopingToANodeFindsBothDirections() {
        // What it mentions, and what mentions it. The second is the half a person cannot find by
        // reading the note in front of them.
        val nodes = listOf(
            node("subject", "Money representation", "This builds on Account identity."),
            node("mentions-it", "The ledger", "See Money representation."),
            node("mentioned", "Account identity"),
            node("unrelated", "Something else entirely", "No names here."),
        )
        val found = LinkSuggestions.forNode(nodes, nodes.first { it.id.value == "subject" })
        assertEquals(
            setOf("The ledger", "Account identity"),
            found.map { if (it.from.title == "Money representation") it.to.title else it.from.title }.toSet(),
        )
    }

    @Test
    fun theLimitIsHonoured() {
        val nodes = listOf(node("target", "Money representation")) +
            (1..30).map { node("n$it", "Note $it", "About Money representation.") }
        assertEquals(5, LinkSuggestions.across(nodes, limit = 5).size)
    }

    @Test
    fun theSameVaultAlwaysSuggestsTheSameThings() {
        val nodes = listOf(
            node("a", "Money representation"),
            node("b", "Account identity"),
            node("c", "The ledger", "Money representation and Account identity both apply."),
        )
        assertEquals(
            LinkSuggestions.across(nodes).map { it.to.title },
            LinkSuggestions.across(nodes.reversed()).map { it.to.title },
        )
    }
}
