package dev.mindgraph.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mindgraph.model.Link
import dev.mindgraph.model.Note
import dev.mindgraph.model.PomodoroPhase
import dev.mindgraph.model.PomodoroSession
import dev.mindgraph.model.RunState
import dev.mindgraph.model.Vault
import dev.mindgraph.model.WorkItem
import dev.mindgraph.model.currentUnixTimestamp
import dev.mindgraph.storage.Database
import dev.mindgraph.storage.LinkRepository
import dev.mindgraph.storage.NoteRepository
import dev.mindgraph.storage.PomodoroRepository
import dev.mindgraph.storage.VaultRepository
import dev.mindgraph.storage.WorkItemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single source of truth for the UI, the Compose equivalent of the Rust app's
 * `AppState` reducer: repositories do file I/O on a background dispatcher, this class
 * holds the resulting snapshot in Compose state, and the UI only ever reads from here.
 */
class AppViewModel(database: Database) {
    private val noteRepository = NoteRepository(database)
    private val linkRepository = LinkRepository(database)
    private val workItemRepository = WorkItemRepository(database)
    private val pomodoroRepository = PomodoroRepository(database)
    private val vaultRepository = VaultRepository(database)

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layout = GraphLayoutEngine()

    var vaults by mutableStateOf<List<Vault>>(emptyList())
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var links by mutableStateOf<List<Link>>(emptyList())
        private set
    var workItems by mutableStateOf<List<WorkItem>>(emptyList())
        private set
    var pomodoroSessions by mutableStateOf<List<PomodoroSession>>(emptyList())
        private set

    var selectedNoteId by mutableStateOf<Long?>(null)
        private set
    var statusMessage by mutableStateOf("Ready")
        private set

    var runningWorkItemId by mutableStateOf<Long?>(null)
        private set
    var runningSinceUnix by mutableStateOf<Long?>(null)
        private set
    var liveNowUnix by mutableStateOf(currentUnixTimestamp())
        private set

    private var tickerJob: Job? = null

    init {
        scope.launch { refreshAll() }
    }

    private suspend fun refreshAll() {
        vaults = vaultRepository.listVaults()
        notes = noteRepository.listNotes()
        links = linkRepository.listLinks()
        workItems = workItemRepository.listWorkItems()
        pomodoroSessions = pomodoroRepository.listSessions()
        layout.sync(notes.map { it.id }, links)
    }

    fun selectNote(noteId: Long?) {
        selectedNoteId = noteId
    }

    fun createNote(title: String, document: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            statusMessage = "note title cannot be empty"
            return
        }
        val vault = vaults.firstOrNull() ?: return
        scope.launch {
            val note = noteRepository.createNote(vault.id, vault.rootPath, trimmed, document)
            refreshAll()
            selectedNoteId = note.id
            statusMessage = "note created"
        }
    }

    fun updateNote(noteId: Long, title: String, document: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            statusMessage = "note title cannot be empty"
            return
        }
        scope.launch {
            noteRepository.updateNote(noteId, trimmed, document)
            refreshAll()
            statusMessage = "note saved"
        }
    }

    fun deleteNote(noteId: Long) {
        scope.launch {
            noteRepository.deleteNote(noteId)
            if (selectedNoteId == noteId) selectedNoteId = null
            refreshAll()
            statusMessage = "note deleted"
        }
    }

    suspend fun readNoteDocument(path: String): String = noteRepository.readNoteDocument(path)

    fun createLink(sourceNoteId: Long, targetNoteId: Long, relationship: String) {
        if (sourceNoteId == targetNoteId) return
        scope.launch {
            linkRepository.createLink(sourceNoteId, targetNoteId, relationship.ifBlank { "relates to" })
            refreshAll()
            statusMessage = "link created"
        }
    }

    fun deleteLink(linkId: Long) {
        scope.launch {
            linkRepository.deleteLink(linkId)
            refreshAll()
            statusMessage = "link removed"
        }
    }

    // --- work items + time tracking ---

    /** Total tracked seconds for a note, the value the graph nodes size themselves by. */
    fun trackedSecondsForNote(noteId: Long): Long {
        val persisted = workItems.filter { it.noteId == noteId }.sumOf { it.elapsedSeconds }
        val runningId = runningWorkItemId
        val since = runningSinceUnix
        if (runningId != null && since != null) {
            val runningItem = workItems.find { it.id == runningId }
            if (runningItem?.noteId == noteId) {
                return persisted + (liveNowUnix - since).coerceAtLeast(0)
            }
        }
        return persisted
    }

    fun workItemForNote(noteId: Long): WorkItem? =
        workItems.filter { it.noteId == noteId }.maxByOrNull { it.updatedAtUnix }

    /**
     * Starts (or resumes) tracking on a note's work item. [elapsedSeconds] is the running
     * total across all past segments and is never reset; `startedAtUnix` marks the
     * beginning of the *current* segment only, so it's always overwritten here rather than
     * reused from a stale previous run.
     */
    fun startWork(noteId: Long) {
        scope.launch {
            val existing = workItemForNote(noteId)
            val item = existing ?: workItemRepository.createWorkItem(taskId = null, noteId = noteId)
            val now = currentUnixTimestamp()
            val updated = workItemRepository.updateWorkItem(
                workItemId = item.id,
                taskId = item.taskId,
                noteId = item.noteId,
                runState = RunState.Running,
                pomodoroSessionIds = item.pomodoroSessionIds,
                startedAtUnix = now,
                stoppedAtUnix = null,
                elapsedSeconds = item.elapsedSeconds,
            )
            runningWorkItemId = updated.id
            runningSinceUnix = now
            refreshAll()
            startTicker()
            statusMessage = "tracking time on \"${noteTitle(noteId)}\""
        }
    }

    fun pauseWork(workItemId: Long) {
        val isActive = runningWorkItemId == workItemId
        val since = if (isActive) runningSinceUnix else null
        if (isActive) {
            stopTicker()
            runningWorkItemId = null
            runningSinceUnix = null
        }
        scope.launch {
            val item = workItems.find { it.id == workItemId } ?: return@launch
            val delta = if (since != null) (currentUnixTimestamp() - since).coerceAtLeast(0) else 0
            workItemRepository.updateWorkItem(
                workItemId = item.id,
                taskId = item.taskId,
                noteId = item.noteId,
                runState = RunState.Paused,
                pomodoroSessionIds = item.pomodoroSessionIds,
                startedAtUnix = item.startedAtUnix,
                stoppedAtUnix = null,
                elapsedSeconds = item.elapsedSeconds + delta,
            )
            refreshAll()
            statusMessage = "paused"
        }
    }

    /**
     * Stops tracking. Only the just-finished segment's duration is logged as a
     * [PomodoroSession] (not the cumulative total), so repeated start/stop cycles on the
     * same note don't inflate the session log.
     */
    fun stopWork(workItemId: Long) {
        val isActive = runningWorkItemId == workItemId
        val since = if (isActive) runningSinceUnix else null
        if (isActive) {
            stopTicker()
            runningWorkItemId = null
            runningSinceUnix = null
        }
        scope.launch {
            val item = workItems.find { it.id == workItemId } ?: return@launch
            val now = currentUnixTimestamp()
            val delta = if (since != null) (now - since).coerceAtLeast(0) else 0
            val totalElapsed = item.elapsedSeconds + delta

            var sessionIds = item.pomodoroSessionIds
            if (since != null && delta > 0) {
                val session = pomodoroRepository.createSession(
                    taskId = item.taskId,
                    phase = PomodoroPhase.Work,
                    startedAtUnix = since,
                    stoppedAtUnix = now,
                    elapsedSeconds = delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                )
                sessionIds = item.pomodoroSessionIds + session.id
            }
            workItemRepository.updateWorkItem(
                workItemId = item.id,
                taskId = item.taskId,
                noteId = item.noteId,
                runState = RunState.Stopped,
                pomodoroSessionIds = sessionIds,
                startedAtUnix = null,
                stoppedAtUnix = now,
                elapsedSeconds = totalElapsed,
            )
            refreshAll()
            statusMessage = "stopped, ${totalElapsed}s tracked"
        }
    }

    private fun noteTitle(noteId: Long): String = notes.find { it.id == noteId }?.title ?: "note"

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                delay(1000)
                liveNowUnix = currentUnixTimestamp()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
