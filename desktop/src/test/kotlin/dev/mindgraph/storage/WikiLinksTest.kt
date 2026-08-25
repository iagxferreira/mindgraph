package dev.mindgraph.storage

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiLinksTest {
    @Test
    fun resolvesLinksByTitleCaseInsensitively() = runTest {
        val store = NodeStore(Vault(Files.createTempDirectory("mindgraph-wiki")))
        val target = store.create("Layout Notes")
        val nodes = store.load()

        assertEquals(listOf(target.id), WikiLinks.resolve("see [[layout notes]] for context", nodes))
    }

    @Test
    fun readsThePipeAliasForm() {
        assertEquals(listOf("Real Title"), WikiLinks.titlesIn("[[Real Title|shown text]]"))
    }

    @Test
    fun unresolvedLinksAreReportedNotDropped() = runTest {
        val store = NodeStore(Vault(Files.createTempDirectory("mindgraph-wiki-miss")))
        store.create("Exists")
        val nodes = store.load()

        assertTrue(WikiLinks.resolve("[[Nothing here]]", nodes).isEmpty())
        assertEquals(listOf("Nothing here"), WikiLinks.unresolved("[[Nothing here]]", nodes))
    }

    @Test
    fun ignoresTextThatIsNotALink() {
        assertTrue(WikiLinks.titlesIn("an [ordinary](link) and [single] brackets").isEmpty())
    }
}
