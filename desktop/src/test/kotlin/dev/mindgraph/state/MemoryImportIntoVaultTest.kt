package dev.mindgraph.state

import dev.mindgraph.model.NodeKind
import dev.mindgraph.storage.MemoryImport
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/** The import as a person would use it: run it, run it again, and see nothing duplicated. */
class MemoryImportIntoVaultTest {

    private fun projectsRoot(): Path = Files.createTempDirectory("claude-projects")

    private fun writeMemory(root: Path, project: String, file: String, content: String): Path {
        val dir = root.resolve(project).resolve("memory")
        Files.createDirectories(dir)
        return Files.writeString(dir.resolve(file), content)
    }

    private fun memoryFile(name: String, type: String, description: String, body: String) = """
        ---
        name: $name
        description: "$description"
        metadata:
          node_type: memory
          type: $type
          originSessionId: session-$name
        ---

        $body
    """.trimIndent()

    private fun newModel(): Pair<AppViewModel, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-import")).also { it.prepare() }
        val store = NodeStore(vault)
        return AppViewModel(store, SessionLog(vault)) to store
    }

    @Test
    fun everyMemoryFileBecomesANodeInTheVault() = runBlocking {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-agni", "a.md", memoryFile("a", "project", "About agni", "Agni body"))
        writeMemory(root, "-home-iago-workspace-blog", "b.md", memoryFile("b", "feedback", "About the blog", "Blog body"))
        writeMemory(root, "-home-iago-workspace-blog", "MEMORY.md", "# index")
        val (model, store) = newModel()

        val result = model.importClaudeMemoryNow(root)

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        val titles = store.load().map { it.title }.toSet()
        assertEquals(setOf("About agni", "About the blog"), titles)
        model.scope.cancel()
    }

    @Test
    fun runningItTwiceImportsNothingTheSecondTime() = runBlocking {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-agni", "a.md", memoryFile("a", "project", "About agni", "Body"))
        val (model, store) = newModel()

        assertEquals(1, model.importClaudeMemoryNow(root).imported)
        val second = model.importClaudeMemoryNow(root)

        // The whole point of recording origin: this is re-runnable, not one-shot.
        assertEquals(0, second.imported)
        assertEquals(1, second.skipped)
        assertEquals(1, store.load().size)
        model.scope.cancel()
    }

    @Test
    fun aSecondRunPicksUpOnlyWhatIsNew() = runBlocking {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-agni", "a.md", memoryFile("a", "project", "First", "Body"))
        val (model, store) = newModel()
        model.importClaudeMemoryNow(root)

        // Claude keeps writing memory files; this is the case that matters in practice.
        writeMemory(root, "-home-iago-workspace-agni", "b.md", memoryFile("b", "feedback", "Second", "Body"))
        val result = model.importClaudeMemoryNow(root)

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(setOf("First", "Second"), store.load().map { it.title }.toSet())
        model.scope.cancel()
    }

    @Test
    fun theOriginAndMemoryTypeSurviveOnDisk() = runBlocking {
        val root = projectsRoot()
        val source = writeMemory(
            root, "-home-iago-workspace-agni", "a.md",
            memoryFile("pr-workflow", "feedback", "A convention", "Body with [[a-link]]"),
        )
        val (model, store) = newModel()
        model.importClaudeMemoryNow(root)

        // Written as extras, so they must survive the round trip that Node never models.
        assertEquals(setOf(source.toAbsolutePath().toString()), store.frontmatterValues(MemoryImport.KEY_ORIGIN))
        assertEquals(setOf("feedback"), store.frontmatterValues(MemoryImport.KEY_MEMORY_TYPE))
        assertEquals(setOf("pr-workflow"), store.frontmatterValues(MemoryImport.KEY_MEMORY_NAME))
        assertEquals(setOf("session-pr-workflow"), store.frontmatterValues(MemoryImport.KEY_ORIGIN_SESSION))
        assertTrue(store.load().single().body.contains("[[a-link]]"), "wikilinks are carried verbatim")
        model.scope.cancel()
    }

    @Test
    fun anImportedNoteIsNotWorkToDo() = runBlocking {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-agni", "a.md", memoryFile("a", "project", "A fact", "Body"))
        val (model, store) = newModel()

        model.importClaudeMemoryNow(root)

        // A memory note is something known, not something to do; a task facet would put every
        // imported file into the ready queue.
        assertNull(store.load().single().task)
        model.scope.cancel()
    }

    @Test
    fun onlyAReferenceBecomesAReference() = runBlocking {
        val root = projectsRoot()
        writeMemory(root, "-home-iago-workspace-a", "r.md", memoryFile("r", "reference", "A reference", "Body"))
        writeMemory(root, "-home-iago-workspace-a", "f.md", memoryFile("f", "feedback", "Some feedback", "Body"))
        val (model, store) = newModel()

        model.importClaudeMemoryNow(root)

        val byTitle = store.load().associateBy { it.title }
        assertEquals(NodeKind.Reference, byTitle.getValue("A reference").kind)
        assertEquals(NodeKind.Note, byTitle.getValue("Some feedback").kind)
        model.scope.cancel()
    }

    @Test
    fun anAbsentClaudeDirectoryIsReportedRatherThanThrown() = runBlocking {
        val (model, store) = newModel()

        val result = model.importClaudeMemoryNow(Path.of("/nonexistent/claude/projects"))

        assertEquals(0, result.filesFound)
        assertEquals(0, result.imported)
        assertTrue(store.load().isEmpty())
        model.scope.cancel()
    }

    @Test
    fun theSummaryReadsLikeAnAnswer() {
        assertEquals("Imported 3 note(s)", MemoryImportResult(3, 0, 0, 3).summary())
        assertEquals("Imported 2 note(s), 5 already there", MemoryImportResult(2, 5, 0, 7).summary())
        assertEquals("Already imported (7 notes)", MemoryImportResult(0, 7, 0, 7).summary())
        assertEquals("No Claude memory files found", MemoryImportResult(0, 0, 0, 0).summary())
    }
}
