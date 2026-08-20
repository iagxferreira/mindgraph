package dev.mindgraph.storage

import dev.mindgraph.model.Note
import dev.mindgraph.model.currentUnixTimestamp
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notes live in `data.json` for their metadata but their body is a markdown file on disk,
 * matching `src/storage/note_repository.rs`. New notes are written to
 * `<vault root>/notes/<slug>.md`, the same layout `src/ui/widgets/mind.rs` produces.
 */
class NoteRepository(private val database: Database) {
    suspend fun listNotes(): List<Note> =
        database.readData().notes.sortedWith(noteOrder)

    suspend fun listNotesByVault(vaultId: Long): List<Note> =
        database.readData().notes.filter { it.vaultId == vaultId }.sortedWith(noteOrder)

    suspend fun createNote(vaultId: Long, vaultRootPath: String, title: String, document: String): Note {
        val slug = slugify(title)
        val path = Paths.get(vaultRootPath, "notes", "$slug.md")
        return createNoteAt(vaultId, title, slug, path, document)
    }

    private suspend fun createNoteAt(vaultId: Long, title: String, slug: String, path: Path, document: String): Note =
        database.withData { data ->
            val now = currentUnixTimestamp()
            writeDocument(path, document)
            val note = Note(
                id = data.allocateNoteId(),
                vaultId = vaultId,
                title = title,
                slug = slug,
                path = path.toString(),
                createdAtUnix = now,
                updatedAtUnix = now,
            )
            data.notes.add(note)
            note
        }

    suspend fun updateNote(noteId: Long, title: String, document: String): Note =
        database.withData { data ->
            val existing = data.notes.find { it.id == noteId } ?: throw StorageNotFoundException("note", noteId)
            val slug = slugify(title)
            val nextPath = Paths.get(existing.path).resolveSibling("$slug.md")
            movePath(Paths.get(existing.path), nextPath)
            writeDocument(nextPath, document)

            val updated = existing.copy(
                title = title,
                slug = slug,
                path = nextPath.toString(),
                updatedAtUnix = currentUnixTimestamp(),
            )
            data.notes[data.notes.indexOfFirst { it.id == noteId }] = updated
            updated
        }

    suspend fun deleteNote(noteId: Long) {
        database.withData { data ->
            val removed = data.notes.find { it.id == noteId } ?: throw StorageNotFoundException("note", noteId)
            data.notes.removeAll { it.id == noteId }
            data.links.removeAll { it.sourceNoteId == noteId || it.targetNoteId == noteId }
            val path = Paths.get(removed.path)
            if (Files.exists(path)) Files.delete(path)
        }
    }

    suspend fun readNoteDocument(path: String): String = withContext(Dispatchers.IO) {
        val file = Paths.get(path)
        if (!Files.exists(file)) "" else Files.readString(file)
    }

    private fun writeDocument(path: Path, document: String) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, document)
    }

    private fun movePath(previous: Path, next: Path) {
        if (previous == next) return
        next.parent?.let { Files.createDirectories(it) }
        if (Files.exists(previous)) Files.move(previous, next)
    }

    private fun slugify(title: String): String = title.trim().lowercase().replace(' ', '-')

    companion object {
        private val noteOrder = compareByDescending<Note> { it.updatedAtUnix }
            .thenByDescending { it.createdAtUnix }
            .thenByDescending { it.id }
    }
}
