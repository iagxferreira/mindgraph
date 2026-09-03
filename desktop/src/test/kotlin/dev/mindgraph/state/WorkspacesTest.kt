package dev.mindgraph.state

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Resolving a saved selection, and finding the selections a vault already implies. */
class WorkspacesTest {

    private fun node(
        id: String,
        origin: String? = null,
        project: String? = null,
        kind: NodeKind = NodeKind.Note,
        contextFor: List<String> = emptyList(),
    ) = Node(
        id = NodeId(id),
        title = id,
        body = "",
        kind = kind,
        originProject = project,
        origin = origin,
        contextFor = contextFor.map(::NodeId),
        createdAt = "2026-09-03T00:00:00Z",
        updatedAt = "2026-09-03T00:00:00Z",
        slug = id,
    )

    private fun ids(nodes: List<Node>) = nodes.map { it.id.value }

    @Test
    fun aFolderRuleGathersWhatWasImportedBelowIt() {
        val nodes = listOf(
            node("a", origin = "/vaults/main/estudos/one.md"),
            node("b", origin = "/vaults/main/estudos/deep/two.md"),
            node("c", origin = "/vaults/main/blog/three.md"),
            node("d"),
        )
        val found = Workspaces.resolve(nodes, Workspace(Workspace.Rule.OriginUnder("/vaults/main/estudos")))
        assertEquals(listOf("a", "b"), ids(found))
    }

    @Test
    fun aFolderDoesNotSwallowASimilarlyNamedSibling() {
        // `/estudos` must not match `/estudos-antigos`.
        val nodes = listOf(
            node("inside", origin = "/vaults/main/estudos/one.md"),
            node("sibling", origin = "/vaults/main/estudos-antigos/two.md"),
        )
        val found = Workspaces.resolve(nodes, Workspace(Workspace.Rule.OriginUnder("/vaults/main/estudos")))
        assertEquals(listOf("inside"), ids(found))
    }

    @Test
    fun aTrailingSlashMakesNoDifference() {
        val nodes = listOf(node("a", origin = "/vaults/main/estudos/one.md"))
        assertEquals(
            Workspaces.resolve(nodes, Workspace(Workspace.Rule.OriginUnder("/vaults/main/estudos"))),
            Workspaces.resolve(nodes, Workspace(Workspace.Rule.OriginUnder("/vaults/main/estudos/"))),
        )
    }

    @Test
    fun includeAddsWhatTheRuleMissed() {
        val nodes = listOf(
            node("a", origin = "/vaults/main/estudos/one.md"),
            node("hand-written"),
        )
        val workspace = Workspace(
            rule = Workspace.Rule.OriginUnder("/vaults/main/estudos"),
            include = listOf(NodeId("hand-written")),
        )
        assertEquals(listOf("a", "hand-written"), ids(Workspaces.resolve(nodes, workspace)))
    }

    @Test
    fun excludeWinsOverBothTheRuleAndInclude() {
        // A workspace reads as "this, except that", and an exception a rule could overrule would
        // be no exception at all.
        val nodes = listOf(
            node("a", origin = "/vaults/main/estudos/one.md"),
            node("b", origin = "/vaults/main/estudos/two.md"),
        )
        val workspace = Workspace(
            rule = Workspace.Rule.OriginUnder("/vaults/main/estudos"),
            include = listOf(NodeId("b")),
            exclude = listOf(NodeId("b")),
        )
        assertEquals(listOf("a"), ids(Workspaces.resolve(nodes, workspace)))
    }

    @Test
    fun aProjectRuleGathersByAttribution() {
        val nodes = listOf(node("a", project = "tally"), node("b", project = "agni"), node("c"))
        assertEquals(listOf("a"), ids(Workspaces.resolve(nodes, Workspace(Workspace.Rule.InProject("tally")))))
    }

    @Test
    fun aKindRuleGathersEveryRfc() {
        val nodes = listOf(node("a", kind = NodeKind.Rfc), node("b"), node("c", kind = NodeKind.Rfc))
        assertEquals(listOf("a", "c"), ids(Workspaces.resolve(nodes, Workspace(Workspace.Rule.OfKind(NodeKind.Rfc)))))
    }

    @Test
    fun theCuratedCaseIsJustAnotherRule() {
        // context_for expressed as a rule, so it composes with include and exclude like the rest.
        val nodes = listOf(
            node("project"),
            node("briefing", contextFor = listOf("project")),
            node("unrelated"),
        )
        val found = Workspaces.resolve(nodes, Workspace(Workspace.Rule.ContextFor(NodeId("project"))))
        assertEquals(listOf("briefing"), ids(found))
    }

    @Test
    fun anEmptyRuleSelectsExactlyWhatIsListed() {
        val nodes = listOf(node("a"), node("b"), node("c"))
        val workspace = Workspace(Workspace.Rule.Nothing, include = listOf(NodeId("b")))
        assertEquals(listOf("b"), ids(Workspaces.resolve(nodes, workspace)))
    }

    @Test
    fun selectionKeepsTheVaultsOrder() {
        // A workspace narrows what is shown; it must not reorder it, or the same nodes read
        // differently inside a workspace and outside one.
        val nodes = listOf(node("a", project = "p"), node("b"), node("c", project = "p"))
        val workspace = Workspace(Workspace.Rule.InProject("p"), include = listOf(NodeId("b")))
        assertEquals(listOf("a", "b", "c"), ids(Workspaces.resolve(nodes, workspace)))
    }

    @Test
    fun aFolderIsRecoveredFromAStoredOrigin() {
        // The whole reason nothing needs re-importing.
        val node = node("a", origin = "/home/iago/workspace/vaults/main/estudos/linguagens/elixir.md")
        assertEquals("estudos", Workspaces.folderUnder(node, "/home/iago/workspace/vaults/main"))
    }

    @Test
    fun aFileDirectlyInTheRootHasNoFolder() {
        val node = node("a", origin = "/vaults/main/loose.md")
        assertNull(Workspaces.folderUnder(node, "/vaults/main"))
    }

    @Test
    fun aNodeWrittenInTheAppHasNoFolder() {
        assertNull(Workspaces.folderUnder(node("a"), "/vaults/main"))
    }

    @Test
    fun foldersAreSuggestedLargestFirstAndTinyOnesDropped() {
        val nodes = (1..5).map { node("e$it", origin = "/v/estudos/$it.md") } +
            (1..3).map { node("b$it", origin = "/v/blog/$it.md") } +
            listOf(node("x", origin = "/v/stray/1.md"))

        assertEquals(listOf("estudos" to 5, "blog" to 3), Workspaces.suggestFolders(nodes, "/v"))
    }

    @Test
    fun theImportRootIsDerivedRatherThanRetyped() {
        // A person should not have to remember the path they imported from; the vault knows.
        val nodes = listOf(
            node("a", origin = "/home/iago/workspace/vaults/main/estudos/one.md", project = "obsidian-main"),
            node("b", origin = "/home/iago/workspace/vaults/main/blog/two.md", project = "obsidian-main"),
        )
        assertEquals(
            mapOf("obsidian-main" to "/home/iago/workspace/vaults/main"),
            Workspaces.importRoots(nodes),
        )
    }

    @Test
    fun nodesWrittenInTheAppContributeNoImportRoot() {
        assertTrue(Workspaces.importRoots(listOf(node("a"), node("b"))).isEmpty())
    }
}
