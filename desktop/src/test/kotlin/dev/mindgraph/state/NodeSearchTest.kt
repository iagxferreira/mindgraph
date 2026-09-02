package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeSearchTest {

    private fun node(
        id: String,
        title: String,
        body: String = "",
        aliases: List<String> = emptyList(),
    ) = Node(
        id = NodeId(id),
        title = title,
        body = body,
        aliases = aliases,
        createdAt = Instant.EPOCH.toString(),
        updatedAt = Instant.EPOCH.toString(),
        slug = id,
    )

    @Test
    fun titleMatchesRankBeforeAliasAndBodyMatches() {
        val title = node("01", "Commit conventions")
        val alias = node("02", "Project workflow", aliases = listOf("commit-style"))
        val body = node("03", "Release process", "Use conventional commits for each change.")

        val matches = NodeSearch.search(listOf(body, alias, title), "commit")

        assertEquals(listOf("01", "02", "03"), matches.map { it.node.id.value })
    }

    @Test
    fun bodySnippetIsBoundedAroundTheMatchingText() {
        val body = "x".repeat(100) + " The important SEARCH phrase is here. " + "y".repeat(160)

        val snippet = NodeSearch.search(listOf(node("01", "Context", body)), "search").single().snippet

        assertTrue(snippet.startsWith("…"), snippet)
        assertTrue(snippet.contains("SEARCH phrase"), snippet)
        assertTrue(snippet.endsWith("…"), snippet)
    }

    @Test
    fun aliasMatchExplainsTheAlternateName() {
        val match = NodeSearch.search(
            listOf(node("01", "Long descriptive title", aliases = listOf("short-name"))),
            "short",
        ).single()

        assertEquals("Also known as: short-name", match.snippet)
    }

    @Test
    fun matchingIsCaseInsensitiveAndRespectsTheLimit() {
        val nodes = (1..3).map { node("0$it", "Note $it", "shared context") }

        assertEquals(listOf("01", "02"), NodeSearch.search(nodes, "CONTEXT", limit = 2).map { it.node.id.value })
    }

    @Test
    fun blankQueriesAreRefused() {
        assertFailsWith<IllegalArgumentException> { NodeSearch.search(emptyList(), "  ") }
    }
}
