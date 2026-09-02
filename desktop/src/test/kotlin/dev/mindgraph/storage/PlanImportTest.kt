package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading bare markdown: no frontmatter, and a filename that tells you nothing. */
class PlanImportTest {

    private fun plansRoot(): Path = Files.createTempDirectory("claude-plans")

    private fun writePlan(root: Path, file: String, content: String): Path =
        Files.writeString(root.resolve(file), content)

    private val projects = setOf(
        "-home-iago-workspace-mindgraph",
        "-home-iago-workspace-blog",
        "-home-iago-workspace-geo-resolution-rag",
    )

    @Test
    fun theFirstHeadingBecomesTheTitle() {
        val root = plansRoot()
        val file = writePlan(
            root, "eager-beaming-leaf.md",
            "# Compose Desktop pivot for MindGraph\n\n## Context\n\nThe body.\n",
        )

        val plan = PlanImport.read(file, projects)!!
        assertEquals("Compose Desktop pivot for MindGraph", plan.title)
        assertTrue(plan.body.contains("## Context"), "the document is kept whole")
        assertEquals(file.toAbsolutePath().toString(), plan.origin)
    }

    @Test
    fun aTitleNamingOneProjectLinksToIt() {
        val root = plansRoot()
        val file = writePlan(root, "a.md", "# Compose Desktop pivot for MindGraph\n\nbody\n")
        assertEquals("-home-iago-workspace-mindgraph", PlanImport.read(file, projects)!!.subject)
    }

    @Test
    fun aHyphenatedProjectNameIsMatchedWhole() {
        val root = plansRoot()
        val file = writePlan(root, "b.md", "# Professionalize geo-resolution-rag: uv, conventions\n\nbody\n")
        assertEquals(
            "-home-iago-workspace-geo-resolution-rag",
            PlanImport.read(file, projects)!!.subject,
        )
    }

    @Test
    fun aProjectMentionedOnlyInTheBodyIsNotTheSubject() {
        val root = plansRoot()
        // The real case this rule exists for: an i18n plan for sqnc.cloud names the blog repo
        // once, as the pattern it copies. Matching the body linked it to the blog, wrongly.
        val file = writePlan(
            root, "c.md",
            """
            # i18n: /en + /pt routing, translate homepage + /work

            Mirroring the routing pattern already used in the blog repo
            (`~/workspace/blog/app/[locale]/`).
            """.trimIndent(),
        )

        assertNull(PlanImport.read(file, projects)!!.subject)
    }

    @Test
    fun aTitleNamingNoKnownProjectLinksToNothing() {
        val root = plansRoot()
        val file = writePlan(root, "d.md", "# Migrate anychain from Rust to Kotlin\n\nbody\n")
        assertNull(PlanImport.read(file, projects)!!.subject)
    }

    @Test
    fun anAmbiguousTitleLinksToNothingRatherThanGuessing() {
        val root = plansRoot()
        val file = writePlan(root, "e.md", "# Share the blog layout with mindgraph\n\nbody\n")

        // Two subjects is not a subject. Picking one would be a coin flip written as an edge.
        assertNull(PlanImport.read(file, projects)!!.subject)
    }

    @Test
    fun aPlanWithNoHeadingFallsBackToItsFilename() {
        val root = plansRoot()
        val file = writePlan(root, "eager-beaming-leaf.md", "No heading, just prose.\n")
        assertEquals("Eager beaming leaf", PlanImport.read(file, projects)!!.title)
    }

    @Test
    fun anEmptyPlanIsNotWorthANode() {
        val root = plansRoot()
        assertNull(PlanImport.read(writePlan(root, "empty.md", "   \n"), projects))
    }

    @Test
    fun scanFindsMarkdownInAStableOrder() {
        val root = plansRoot()
        writePlan(root, "b.md", "# B\n\nbody")
        writePlan(root, "a.md", "# A\n\nbody")
        writePlan(root, "notes.txt", "not markdown")

        assertEquals(listOf("a.md", "b.md"), PlanImport.scan(root).map { it.fileName.toString() })
    }

    @Test
    fun scanningSomewhereThatDoesNotExistIsEmptyRatherThanAnError() {
        assertEquals(emptyList(), PlanImport.scan(Path.of("/nonexistent/claude/plans")))
    }
}
