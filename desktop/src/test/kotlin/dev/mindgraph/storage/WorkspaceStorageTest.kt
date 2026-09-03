package dev.mindgraph.storage

import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.Workspace
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A workspace is a node, so it round-trips through markdown like everything else. */
class WorkspaceStorageTest {

    private fun newVault(): Pair<NodeStore, Vault> {
        val vault = Vault(Files.createTempDirectory("mindgraph-workspace"))
        return NodeStore(vault) to vault
    }

    private suspend fun roundTrip(store: NodeStore, workspace: Workspace): Workspace? {
        val node = store.create("A workspace", "")
        store.save(node.copy(workspace = workspace))
        return store.load().single { it.id == node.id }.workspace
    }

    @Test
    fun aFolderRuleSurvivesAReload() = runTest {
        val (store, _) = newVault()
        val rule = Workspace.Rule.OriginUnder("/home/iago/workspace/vaults/main/estudos")
        assertEquals(rule, roundTrip(store, Workspace(rule))?.rule)
    }

    @Test
    fun everyRuleKindSurvives() = runTest {
        val rules = listOf(
            Workspace.Rule.OriginUnder("/v/estudos"),
            Workspace.Rule.InProject("tally"),
            Workspace.Rule.OfKind(NodeKind.Rfc),
            Workspace.Rule.ContextFor(NodeId("01M0V4BQMAJ000RTB5PNFK2P5N")),
            Workspace.Rule.Nothing,
        )
        for (rule in rules) {
            val (store, _) = newVault()
            assertEquals(rule, roundTrip(store, Workspace(rule))?.rule, "$rule did not survive")
        }
    }

    @Test
    fun correctionsSurvive() = runTest {
        val (store, _) = newVault()
        val workspace = Workspace(
            rule = Workspace.Rule.InProject("tally"),
            include = listOf(NodeId("01M0V4BQMAJ000RTB5PNFK2P5N")),
            exclude = listOf(NodeId("01M0V4BNNTVG12ZJ9QHSZG0BTB")),
        )
        val reloaded = roundTrip(store, workspace)
        assertEquals(workspace.include, reloaded?.include)
        assertEquals(workspace.exclude, reloaded?.exclude)
    }

    @Test
    fun anOrdinaryNodeHasNoWorkspace() = runTest {
        val (store, _) = newVault()
        store.create("Just a note", "body")
        assertNull(store.load().single().workspace)
    }

    @Test
    fun theBlockIsWrittenInPlainFrontmatter() = runTest {
        // Hand-editable, like everything else in the vault.
        val (store, vault) = newVault()
        val node = store.create("A workspace", "")
        store.save(node.copy(workspace = Workspace(Workspace.Rule.InProject("tally"))))

        val raw = Files.list(vault.nodesDir).use { it.toList() }
            .map { Files.readString(it) }
            .single { it.contains("title: A workspace") }
        // Frontmatter.quote only quotes what needs it, so these land bare.
        assertTrue(raw.contains("workspace: in_project"), raw)
        assertTrue(raw.contains("workspaceOf: tally"), raw)
    }

    @Test
    fun anUnreadableRuleSelectsNothingRatherThanEverything() = runTest {
        // A workspace that silently widened to the whole vault would look like it had worked.
        val (store, vault) = newVault()
        vault.prepare()
        Files.writeString(
            vault.nodesDir.resolve("broken.md"),
            """
            ---
            id: 01M0V4BQMAJ000RTB5PNFK2P5N
            title: Broken workspace
            workspace: "not_a_rule"
            workspaceOf: "whatever"
            created: 2026-09-03T00:00:00Z
            updated: 2026-09-03T00:00:00Z
            ---

            Body.
            """.trimIndent(),
        )
        assertEquals(Workspace.Rule.Nothing, store.load().single().workspace?.rule)
    }

    @Test
    fun theRuleIsNotWrittenTwice() = runTest {
        // It is a modelled field now, so it must be known to the store or writeNode would emit
        // it once from the field and once as a preserved extra.
        val (store, vault) = newVault()
        val node = store.create("A workspace", "")
        store.save(node.copy(workspace = Workspace(Workspace.Rule.InProject("tally"))))
        store.save(store.load().single { it.id == node.id })

        val raw = Files.list(vault.nodesDir).use { it.toList() }
            .map { Files.readString(it) }
            .single { it.contains("title: A workspace") }
        assertEquals(1, Regex("^workspace:", RegexOption.MULTILINE).findAll(raw).count(), raw)
    }
}
