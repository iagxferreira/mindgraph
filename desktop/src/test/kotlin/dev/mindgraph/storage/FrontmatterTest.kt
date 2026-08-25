package dev.mindgraph.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrontmatterTest {
    @Test
    fun splitsFrontmatterFromBody() {
        val (front, body) = Frontmatter.split(
            """
            ---
            id: 01J8X2QF7K3M9ZABCDEFGHJKMN
            title: Hello there
            ---

            # Body starts here
            """.trimIndent(),
        )

        assertEquals("01J8X2QF7K3M9ZABCDEFGHJKMN", front.string("id"))
        assertEquals("Hello there", front.string("title"))
        assertEquals("# Body starts here", body.trim())
    }

    @Test
    fun documentWithoutFrontmatterIsAllBody() {
        val (front, body) = Frontmatter.split("just some text")
        assertEquals(null, front.string("id"))
        assertEquals("just some text", body)
    }

    @Test
    fun readsInlineAndBlockSequences() {
        val front = Frontmatter.parse(
            """
            depends_on: [AAA, BBB]
            relates_to:
              - CCC
              - DDD
            """.trimIndent(),
        )

        assertEquals(listOf("AAA", "BBB"), front.list("depends_on"))
        assertEquals(listOf("CCC", "DDD"), front.list("relates_to"))
    }

    @Test
    fun preservesTitlesThatWouldOtherwiseBreakParsing() {
        val tricky = "Refactor: the storage layer"
        val rendered = "title: ${Frontmatter.quote(tricky)}"
        assertEquals(tricky, Frontmatter.parse(rendered).string("title"))
    }

    @Test
    fun keepsUnknownKeysForRoundTripping() {
        val front = Frontmatter.parse("id: X\nmy_custom_field: kept")
        val extras = front.extras(setOf("id"))
        assertTrue("my_custom_field" in extras)
        assertEquals("kept", (extras["my_custom_field"] as Frontmatter.Value.Scalar).text)
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val front = Frontmatter.parse("# a comment\n\ntitle: Real\n")
        assertEquals("Real", front.string("title"))
    }
}
