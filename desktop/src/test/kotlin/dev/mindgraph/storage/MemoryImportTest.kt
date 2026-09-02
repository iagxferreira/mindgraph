package dev.mindgraph.storage

import dev.mindgraph.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading somebody else's markdown. Fixtures are copied from the shape real memory files have. */
class MemoryImportTest {

    private fun projectsRoot(): Path = Files.createTempDirectory("claude-projects")

    private fun writeMemory(root: Path, project: String, file: String, content: String): Path {
        val dir = root.resolve(project).resolve("memory")
        Files.createDirectories(dir)
        return Files.writeString(dir.resolve(file), content)
    }

    private val realShape = """
        ---
        name: pr-workflow-kotlin-migration
        description: PR granularity convention for the agni Rust-to-Kotlin migration
        metadata: 
          node_type: memory
          type: feedback
          originSessionId: cd8bd04e-f61a-49fa-9e94-6b07ce6922ab
          modified: 2026-08-20T00:39:17.423Z
        ---

        Use one PR per unit of work via worktrees, see [[kotlin-migration-roadmap]].

        **Why:** the user's explicit call.
    """.trimIndent()

    @Test
    fun theDescriptionBecomesTheTitleAndNestedMetadataIsRead() {
        val root = projectsRoot()
        val file = writeMemory(root, "-home-iago-workspace-agni", "pr_workflow.md", realShape)

        val note = MemoryImport.read(file)!!

        assertEquals("PR granularity convention for the agni Rust-to-Kotlin migration", note.title)
        assertEquals("pr-workflow-kotlin-migration", note.memoryName)
        assertEquals("feedback", note.memoryType)
        assertEquals("cd8bd04e-f61a-49fa-9e94-6b07ce6922ab", note.originSessionId)
        assertEquals("-home-iago-workspace-agni", note.originProject)
        assertEquals(file.toAbsolutePath().toString(), note.origin)
        assertTrue(note.body.startsWith("Use one PR per unit of work"))
        assertTrue(note.body.contains("[[kotlin-migration-roadmap]]"), "wikilinks survive verbatim")
    }

    @Test
    fun aQuotedDescriptionLosesItsQuotes() {
        val root = projectsRoot()
        val file = writeMemory(
            root, "-home-iago-workspace-agni", "quoted.md",
            "---\nname: x\ndescription: \"Agni is moving from Kotlin to Go\"\nmetadata:\n  type: project\n---\n\nbody\n",
        )
        assertEquals("Agni is moving from Kotlin to Go", MemoryImport.read(file)!!.title)
    }

    @Test
    fun onlyReferenceCrossesIntoTheKindAxis() {
        // The two vocabularies overlap on exactly one word; the rest describe a different axis
        // and are kept as data instead.
        assertEquals(NodeKind.Reference, MemoryImport.kindFor("reference"))
        assertEquals(NodeKind.Note, MemoryImport.kindFor("feedback"))
        assertEquals(NodeKind.Note, MemoryImport.kindFor("project"))
        assertEquals(NodeKind.Note, MemoryImport.kindFor("user"))
        assertEquals(NodeKind.Note, MemoryImport.kindFor(null))
    }

    @Test
    fun aFileWithNoDescriptionFallsBackToItsName() {
        val root = projectsRoot()
        val file = writeMemory(
            root, "-home-iago-workspace-blog", "no_desc.md",
            "---\nname: some-fact-here\nmetadata:\n  type: project\n---\n\nthe body\n",
        )
        assertEquals("Some fact here", MemoryImport.read(file)!!.title)
    }

    @Test
    fun aFileWithNoFrontmatterIsStillReadable() {
        val root = projectsRoot()
        val file = writeMemory(root, "-home-iago-workspace-blog", "bare.md", "just a body, no frontmatter\n")

        val note = MemoryImport.read(file)!!
        assertEquals("bare", note.title)
        assertEquals(NodeKind.Note, note.kind)
        assertNull(note.memoryName)
    }

    @Test
    fun anEmptyBodyIsNotWorthANode() {
        val root = projectsRoot()
        val file = writeMemory(root, "-home-iago-workspace-blog", "empty.md", "---\nname: x\n---\n\n\n")
        assertNull(MemoryImport.read(file))
    }

    @Test
    fun scanFindsEveryProjectAndSkipsTheIndexes() {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-agni", "a.md", "---\nname: a\n---\n\nbody")
        writeMemory(root, "-home-iago-workspace-agni", "MEMORY.md", "# index\n\n- [a](a.md)")
        writeMemory(root, "-home-iago-workspace-blog", "b.md", "---\nname: b\n---\n\nbody")
        writeMemory(root, "-home-iago-workspace-blog", "notes.txt", "not markdown")
        Files.createDirectories(root.resolve("-home-iago-workspace-empty"))

        val found = MemoryImport.scan(root).map { it.fileName.toString() }

        assertEquals(listOf("a.md", "b.md"), found)
    }

    @Test
    fun aHyphenatedProjectNameSurvivesWhole() {
        // Shortening a mangled path is guesswork: taking the last segment turned
        // geo-resolution-rag into "rag" against the real directories.
        val root = projectsRoot()
        val file = writeMemory(
            root, "-home-iago-workspace-geo-resolution-rag", "a.md",
            "---\nname: a\n---\n\nbody",
        )
        assertEquals("-home-iago-workspace-geo-resolution-rag", MemoryImport.read(file)!!.originProject)
    }

    @Test
    fun scanningSomewhereThatDoesNotExistIsEmptyRatherThanAnError() {
        assertEquals(emptyList(), MemoryImport.scan(Path.of("/nonexistent/claude/projects")))
    }

    @Test
    fun theExtrasCarryEnoughToResolveWikilinksLater() {
        val root = projectsRoot()
        val file = writeMemory(root, "-home-iago-workspace-agni", "pr_workflow.md", realShape)

        val extras = MemoryImport.extrasFor(MemoryImport.read(file)!!)

        // memoryName is what siblings write inside [[...]], so losing it would make resolving
        // imported links into edges impossible after the fact.
        assertEquals("pr-workflow-kotlin-migration", extras[MemoryImport.KEY_MEMORY_NAME])
        assertEquals("feedback", extras[MemoryImport.KEY_MEMORY_TYPE])
        assertEquals("-home-iago-workspace-agni", extras[MemoryImport.KEY_ORIGIN_PROJECT])
        assertTrue(extras.getValue(MemoryImport.KEY_ORIGIN).endsWith("pr_workflow.md"))
    }
}
