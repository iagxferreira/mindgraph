package dev.mindgraph.storage

import dev.mindgraph.model.RunState
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryRoundTripTest {
    @Test
    fun createdNoteLinkAndWorkItemSurviveAReload() = runTest {
        val tempDir = Files.createTempDirectory("mindgraph-roundtrip")
        val database = Database.openAt(tempDir)
        val vault = VaultRepository(database).listVaults().first()

        val noteRepository = NoteRepository(database)
        val linkRepository = LinkRepository(database)
        val workItemRepository = WorkItemRepository(database)

        val source = noteRepository.createNote(vault.id, vault.rootPath, "Rust", "Rust is a systems language.")
        val target = noteRepository.createNote(vault.id, vault.rootPath, "Ownership", "Ownership is Rust's core idea.")
        linkRepository.createLink(source.id, target.id, "explains")
        val workItem = workItemRepository.createWorkItem(taskId = null, noteId = source.id)
        workItemRepository.updateWorkItem(
            workItemId = workItem.id,
            taskId = null,
            noteId = source.id,
            runState = RunState.Stopped,
            pomodoroSessionIds = emptyList(),
            startedAtUnix = null,
            stoppedAtUnix = 100,
            elapsedSeconds = 90,
        )

        // Reopen against the same directory, as if the Rust binary had loaded it instead.
        val reopened = Database.openAt(tempDir)
        val notes = NoteRepository(reopened).listNotes()
        val links = LinkRepository(reopened).listLinks()
        val workItems = WorkItemRepository(reopened).listWorkItems()

        assertEquals(2, notes.size)
        assertEquals("Ownership is Rust's core idea.", noteRepository.readNoteDocument(target.path))
        assertEquals(1, links.size)
        assertEquals("explains", links.first().relationship)
        assertEquals(90L, workItems.first { it.noteId == source.id }.elapsedSeconds)

        val rawJson = Files.readString(tempDir.resolve("data.json"))
        assertTrue(rawJson.contains("\"source_note_id\""))
        assertTrue(rawJson.contains("\"pomodoro_session_ids\""))
    }
}
