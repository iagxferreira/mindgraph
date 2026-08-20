package dev.mindgraph.storage

import dev.mindgraph.model.Link
import dev.mindgraph.model.currentUnixTimestamp

class LinkRepository(private val database: Database) {
    suspend fun listLinks(): List<Link> = database.readData().links.sortedWith(linkOrder)

    suspend fun createLink(sourceNoteId: Long, targetNoteId: Long, relationship: String): Link =
        database.withData { data ->
            val link = Link(
                id = data.allocateLinkId(),
                sourceNoteId = sourceNoteId,
                targetNoteId = targetNoteId,
                relationship = relationship,
                createdAtUnix = currentUnixTimestamp(),
            )
            data.links.add(link)
            link
        }

    suspend fun deleteLink(linkId: Long) {
        database.withData { data ->
            if (!data.links.removeAll { it.id == linkId }) {
                throw StorageNotFoundException("link", linkId)
            }
        }
    }

    companion object {
        private val linkOrder = compareByDescending<Link> { it.createdAtUnix }.thenByDescending { it.id }
    }
}
