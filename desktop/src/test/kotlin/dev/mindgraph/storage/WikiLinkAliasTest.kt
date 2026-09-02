package dev.mindgraph.storage

import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Linking to a node by a name it was given elsewhere. This is what turns imported notes from
 * disconnected dots into a graph: their links point at a slug their titles no longer carry.
 */
class WikiLinkAliasTest {

    private fun node(title: String, slug: String, aliases: List<String> = emptyList()) = Node(
        id = NodeId(title),
        title = title,
        body = "",
        aliases = aliases,
        createdAt = "2026-09-02T00:00:00Z",
        updatedAt = "2026-09-02T00:00:00Z",
        slug = slug,
    )

    @Test
    fun aLinkToAnAliasFindsTheNode() {
        val target = node(
            title = "PR granularity convention for the agni Rust-to-Kotlin migration",
            slug = "pr-granularity-convention-for-the-agni-rust-to-kotlin-migration",
            aliases = listOf("pr-workflow-kotlin-migration"),
        )

        // Exactly the shape imported memory notes have: a sentence for a title, linked by slug.
        assertEquals(
            listOf(target.id),
            WikiLinks.resolve("see [[pr-workflow-kotlin-migration]]", listOf(target)),
        )
    }

    @Test
    fun anAliasMatchIsCaseInsensitive() {
        val target = node("A title", "a-title", aliases = listOf("Some-Alias"))
        assertEquals(listOf(target.id), WikiLinks.resolve("[[some-alias]]", listOf(target)))
    }

    @Test
    fun aNodesOwnTitleBeatsAnotherNodesAlias() {
        val byTitle = node("shared-name", "shared-name-1")
        val byAlias = node("Something else", "something-else", aliases = listOf("shared-name"))

        // An alias is a nickname; it must not shadow something's real name.
        assertEquals(
            listOf(byTitle.id),
            WikiLinks.resolve("[[shared-name]]", listOf(byAlias, byTitle)),
        )
    }

    @Test
    fun aLinkMatchedByAliasIsNotAlsoReportedUnresolved() {
        val target = node("A title", "a-title", aliases = listOf("nickname"))
        val body = "[[nickname]] and [[nothing-here]]"

        // resolve and unresolved share one index, so they cannot disagree.
        assertEquals(listOf(target.id), WikiLinks.resolve(body, listOf(target)))
        assertEquals(listOf("nothing-here"), WikiLinks.unresolved(body, listOf(target)))
    }

    @Test
    fun aliasesRoundTripThroughTheVault() = runTest {
        val store = NodeStore(Vault(Files.createTempDirectory("mindgraph-alias")))
        val created = store.create("A long descriptive title", extras = mapOf("aliases" to "x"))
        store.save(created.copy(aliases = listOf("short-name", "other-name")))

        assertEquals(listOf("short-name", "other-name"), store.load().single().aliases)
    }

    @Test
    fun aMemoryNameCountsAsAnAliasWithoutReimporting() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-legacy")).also { it.prepare() }
        val store = NodeStore(vault)
        // A node as the importer wrote them before aliases existed. Re-importing would never
        // reach it, because the import skips anything whose origin it has already seen.
        Files.writeString(
            vault.nodesDir.resolve("legacy.md"),
            """
            ---
            id: 01M1FMFEDMZXFNXMT6TX0D6FX9
            title: "A sentence-long description"
            kind: note
            created: 2026-09-02T00:00:00Z
            updated: 2026-09-02T00:00:00Z
            memoryName: pr-workflow-kotlin-migration
            ---

            body
            """.trimIndent(),
        )

        val loaded = store.load().single()
        assertEquals(listOf("pr-workflow-kotlin-migration"), loaded.aliases)
        assertTrue(WikiLinks.resolve("[[pr-workflow-kotlin-migration]]", listOf(loaded)).isNotEmpty())
    }

    @Test
    fun savingDoesNotDuplicateAnAliasThatCameFromTheMemoryName() = runTest {
        val vault = Vault(Files.createTempDirectory("mindgraph-nodup")).also { it.prepare() }
        val store = NodeStore(vault)
        Files.writeString(
            vault.nodesDir.resolve("n.md"),
            "---\nid: 01M1FMFEDMZXFNXMT6TX0D6FXA\ntitle: T\nkind: note\ncreated: c\nupdated: u\nmemoryName: the-name\n---\n\nbody\n",
        )

        val once = store.load().single()
        store.save(once)

        assertEquals(listOf("the-name"), store.load().single().aliases)
    }
}
