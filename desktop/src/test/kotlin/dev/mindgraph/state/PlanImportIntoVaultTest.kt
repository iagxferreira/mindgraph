package dev.mindgraph.state

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.NodeKind
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.PlanImport
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import dev.mindgraph.storage.toEdges
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/** Plans landing in the vault as RFCs, and linking only where the link is actually known. */
class PlanImportIntoVaultTest {

    private fun newModel(): Pair<AppViewModel, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-plans")).also { it.prepare() }
        val store = NodeStore(vault)
        return AppViewModel(store, SessionLog(vault)) to store
    }

    private fun projectsRoot(): Path = Files.createTempDirectory("claude-projects")
    private fun plansRoot(): Path = Files.createTempDirectory("claude-plans")

    private fun writeMemory(root: Path, project: String, file: String, name: String, description: String) {
        val dir = root.resolve(project).resolve("memory")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve(file),
            "---\nname: $name\ndescription: \"$description\"\nmetadata:\n  type: project\n---\n\nbody\n",
        )
    }

    private fun writePlan(root: Path, file: String, content: String): Path =
        Files.writeString(root.resolve(file), content)

    @Test
    fun aPlanLandsAsAnRfcNodeWithItsHeadingAsTheTitle() = runBlocking {
        val plans = plansRoot()
        writePlan(plans, "eager-beaming-leaf.md", "# Compose Desktop pivot\n\n## Context\n\nThe reasoning.\n")
        val (model, store) = newModel()

        val result = model.importClaudePlansNow(plans)

        assertEquals(1, result.imported)
        val node = store.load().single()
        assertEquals("Compose Desktop pivot", node.title)
        assertEquals(NodeKind.Rfc, node.kind)
        assertNull(node.task, "a design document is not work to do")
        assertTrue(node.body.contains("## Context"))
        model.scope.cancel()
    }

    @Test
    fun aPlanNamingAKnownProjectIsLinkedToWhatTheVaultKnowsAboutIt() = runBlocking {
        val projects = projectsRoot()
        writeMemory(projects, "-home-iago-workspace-mindgraph", "goal.md", "goal", "MindGraph's product goal")
        val plans = plansRoot()
        writePlan(plans, "a.md", "# Compose Desktop pivot for MindGraph\n\nbody\n")
        val (model, store) = newModel()

        model.importClaudeMemoryNow(projects)
        val result = model.importClaudePlansNow(plans)

        assertEquals(1, result.imported)
        assertEquals(1, result.linked)

        val nodes = store.load()
        val plan = nodes.single { it.kind == NodeKind.Rfc }
        val memory = nodes.single { it.title == "MindGraph's product goal" }
        assertTrue(
            nodes.toEdges().any {
                it.sourceId == plan.id && it.targetId == memory.id && it.kind == EdgeKind.RelatesTo
            },
            "the plan relates to the project it is about",
        )
        model.scope.cancel()
    }

    @Test
    fun aProjectNamedOnlyInTheBodyDoesNotProduceAnEdge() = runBlocking {
        val projects = projectsRoot()
        writeMemory(projects, "-home-iago-workspace-blog", "b.md", "blog", "What the blog is")
        val plans = plansRoot()
        // The real plan this rule exists for: about sqnc.cloud, mentions the blog once as the
        // pattern it copies. Body matching linked it to the blog, wrongly.
        writePlan(
            plans, "c.md",
            "# i18n: /en + /pt routing, translate homepage\n\nMirroring the pattern in the blog repo.\n",
        )
        val (model, store) = newModel()

        model.importClaudeMemoryNow(projects)
        val result = model.importClaudePlansNow(plans)

        assertEquals(1, result.imported)
        assertEquals(0, result.linked)
        val plan = store.load().single { it.kind == NodeKind.Rfc }
        assertTrue(plan.dependsOn.isEmpty() && plan.relatesTo.isEmpty(), "no guessed edge")
        model.scope.cancel()
    }

    @Test
    fun aPlanAboutAProjectTheVaultDoesNotKnowStillImports() = runBlocking {
        val plans = plansRoot()
        writePlan(plans, "d.md", "# Migrate anychain from Rust to Kotlin\n\nbody\n")
        val (model, store) = newModel()

        val result = model.importClaudePlansNow(plans)

        // Unlinked is not unimported: the document is worth having either way.
        assertEquals(1, result.imported)
        assertEquals(0, result.linked)
        assertEquals(1, store.load().size)
        model.scope.cancel()
    }

    @Test
    fun runningItTwiceImportsNothingTheSecondTime() = runBlocking {
        val plans = plansRoot()
        writePlan(plans, "a.md", "# A plan\n\nbody\n")
        val (model, store) = newModel()

        assertEquals(1, model.importClaudePlansNow(plans).imported)
        val second = model.importClaudePlansNow(plans)

        assertEquals(0, second.imported)
        assertEquals(1, second.skipped)
        assertEquals(1, store.load().size)
        model.scope.cancel()
    }

    @Test
    fun theOriginIsRecordedSoTheSkipWorks() = runBlocking {
        val plans = plansRoot()
        val file = writePlan(plans, "a.md", "# A plan\n\nbody\n")
        val (model, store) = newModel()

        model.importClaudePlansNow(plans)

        assertEquals(
            setOf(file.toAbsolutePath().toString()),
            store.frontmatterValues(PlanImport.KEY_ORIGIN),
        )
        model.scope.cancel()
    }

    @Test
    fun anAbsentPlansDirectoryIsReportedRatherThanThrown() = runBlocking {
        val (model, store) = newModel()

        val result = model.importClaudePlansNow(Path.of("/nonexistent/claude/plans"))

        assertEquals(0, result.filesFound)
        assertTrue(store.load().isEmpty())
        model.scope.cancel()
    }

    @Test
    fun theSummarySaysHowManyLinked() {
        assertEquals(
            "Imported 4 plan(s) as RFCs, 2 linked to a project",
            PlanImportResult(4, 0, 2, 4).summary(),
        )
        assertEquals("Plans already imported (4)", PlanImportResult(0, 4, 0, 4).summary())
        assertEquals("No Claude plans found", PlanImportResult(0, 0, 0, 0).summary())
    }
}
