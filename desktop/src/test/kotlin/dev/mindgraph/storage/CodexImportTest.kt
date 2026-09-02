package dev.mindgraph.storage

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodexImportTest {

    @Test
    fun scanFindsNestedAgentsFilesInStableOrder() {
        val root = Files.createTempDirectory("codex-workspace")
        Files.createDirectories(root.resolve("z/project"))
        Files.createDirectories(root.resolve("a/project"))
        Files.writeString(root.resolve("z/project/AGENTS.md"), "# Z\n\nz")
        Files.writeString(root.resolve("a/project/AGENTS.md"), "# A\n\na")

        assertEquals(
            listOf("a/project/AGENTS.md", "z/project/AGENTS.md"),
            CodexImport.scan(root).map { root.relativize(it).toString() },
        )
    }

    @Test
    fun headingBecomesTitleAndProjectRootIsPreserved() {
        val root = Files.createTempDirectory("codex-workspace")
        val file = root.resolve("mindgraph/AGENTS.md").also {
            Files.createDirectories(it.parent)
            Files.writeString(it, "# Repository Guidelines\n\nKeep changes small.")
        }

        val instruction = CodexImport.read(file, root)!!

        assertEquals("Repository Guidelines", instruction.title)
        assertEquals("mindgraph", instruction.originProject)
        assertTrue(instruction.body.contains("Keep changes small."))
        assertEquals(file.toAbsolutePath().toString(), instruction.origin)
    }

    @Test
    fun blankFileIsIgnoredAndMissingRootIsEmpty() {
        val root = Files.createTempDirectory("codex-workspace")
        val file = root.resolve("AGENTS.md")
        Files.writeString(file, " \n")

        assertNull(CodexImport.read(file, root))
        assertEquals(emptyList(), CodexImport.scan(Path.of("/nonexistent/codex-workspace")))
    }

    @Test
    fun extrasIdentifyCodexInstructions() {
        val instruction = CodexInstruction("Title", "Body", "/workspace/app/AGENTS.md", "app")

        assertEquals(
            mapOf(
                "origin" to "/workspace/app/AGENTS.md",
                "originProject" to "app",
                "originAgent" to "codex",
            ),
            CodexImport.extrasFor(instruction),
        )
    }
}
