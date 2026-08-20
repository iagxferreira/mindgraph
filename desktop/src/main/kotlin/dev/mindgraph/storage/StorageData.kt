package dev.mindgraph.storage

import dev.mindgraph.model.Link
import dev.mindgraph.model.Note
import dev.mindgraph.model.PomodoroSession
import dev.mindgraph.model.Task
import dev.mindgraph.model.Vault
import dev.mindgraph.model.WorkItem
import dev.mindgraph.model.Workspace
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StorageConfig(
    @SerialName("workspace_root") val workspaceRoot: String = "",
)

/**
 * Mirrors the shape of `src/storage/database.rs: StorageData` in the Rust app so both
 * apps can read/write the same `data.json`. Mutable lists + counters are used deliberately
 * so repositories can load-mutate-save imperatively, matching the Rust repository pattern.
 */
@Serializable
data class StorageData(
    var tasks: MutableList<Task> = mutableListOf(),
    @SerialName("pomodoro_sessions") var pomodoroSessions: MutableList<PomodoroSession> = mutableListOf(),
    var workspaces: MutableList<Workspace> = mutableListOf(),
    var vaults: MutableList<Vault> = mutableListOf(),
    var notes: MutableList<Note> = mutableListOf(),
    var links: MutableList<Link> = mutableListOf(),
    @SerialName("work_items") var workItems: MutableList<WorkItem> = mutableListOf(),
    @SerialName("next_task_id") var nextTaskId: Long = 0,
    @SerialName("next_pomodoro_session_id") var nextPomodoroSessionId: Long = 0,
    @SerialName("next_work_item_id") var nextWorkItemId: Long = 0,
    @SerialName("next_workspace_id") var nextWorkspaceId: Long = 0,
    @SerialName("next_vault_id") var nextVaultId: Long = 0,
    @SerialName("next_note_id") var nextNoteId: Long = 0,
    @SerialName("next_link_id") var nextLinkId: Long = 0,
) {
    fun normalizeCounters() {
        nextTaskId = maxOf(nextTaskId, (tasks.maxOfOrNull { it.id } ?: 0) + 1)
        nextPomodoroSessionId = maxOf(nextPomodoroSessionId, (pomodoroSessions.maxOfOrNull { it.id } ?: 0) + 1)
        nextWorkspaceId = maxOf(nextWorkspaceId, (workspaces.maxOfOrNull { it.id } ?: 0) + 1)
        nextVaultId = maxOf(nextVaultId, (vaults.maxOfOrNull { it.id } ?: 0) + 1)
        nextNoteId = maxOf(nextNoteId, (notes.maxOfOrNull { it.id } ?: 0) + 1)
        nextLinkId = maxOf(nextLinkId, (links.maxOfOrNull { it.id } ?: 0) + 1)
        nextWorkItemId = maxOf(nextWorkItemId, (workItems.maxOfOrNull { it.id } ?: 0) + 1)
    }

    fun allocateTaskId(): Long = allocate(::normalizeCounters, { nextTaskId }, { nextTaskId = it })
    fun allocatePomodoroSessionId(): Long = allocate(::normalizeCounters, { nextPomodoroSessionId }, { nextPomodoroSessionId = it })
    fun allocateWorkspaceId(): Long = allocate(::normalizeCounters, { nextWorkspaceId }, { nextWorkspaceId = it })
    fun allocateVaultId(): Long = allocate(::normalizeCounters, { nextVaultId }, { nextVaultId = it })
    fun allocateNoteId(): Long = allocate(::normalizeCounters, { nextNoteId }, { nextNoteId = it })
    fun allocateLinkId(): Long = allocate(::normalizeCounters, { nextLinkId }, { nextLinkId = it })
    fun allocateWorkItemId(): Long = allocate(::normalizeCounters, { nextWorkItemId }, { nextWorkItemId = it })

    private inline fun allocate(normalize: () -> Unit, get: () -> Long, set: (Long) -> Unit): Long {
        normalize()
        val id = maxOf(get(), 1)
        set(id + 1)
        return id
    }
}
