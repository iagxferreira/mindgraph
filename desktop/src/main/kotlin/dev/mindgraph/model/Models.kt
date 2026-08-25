package dev.mindgraph.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val doing: Boolean = false,
    val completed: Boolean = false,
    @SerialName("tracked_seconds") val trackedSeconds: Long = 0,
    @SerialName("created_at_unix") val createdAtUnix: Long,
)

@Serializable
data class Workspace(
    val id: Long,
    val name: String,
    val path: String,
)

@Serializable
data class Vault(
    val id: Long,
    val name: String,
    @SerialName("root_path") val rootPath: String,
    @SerialName("created_at_unix") val createdAtUnix: Long,
)

@Serializable
data class Note(
    val id: Long,
    @SerialName("vault_id") val vaultId: Long,
    val title: String,
    val slug: String,
    val path: String,
    @SerialName("created_at_unix") val createdAtUnix: Long,
    @SerialName("updated_at_unix") val updatedAtUnix: Long,
)

@Serializable
data class Link(
    val id: Long,
    @SerialName("source_note_id") val sourceNoteId: Long,
    @SerialName("target_note_id") val targetNoteId: Long,
    val relationship: String,
    @SerialName("created_at_unix") val createdAtUnix: Long,
)

@Serializable
enum class RunState { Idle, Running, Paused, Stopped }

@Serializable
enum class PomodoroPhase { Work, Break }

@Serializable
data class PomodoroSession(
    val id: Long,
    @SerialName("task_id") val taskId: Long? = null,
    val phase: PomodoroPhase,
    @SerialName("started_at_unix") val startedAtUnix: Long,
    @SerialName("stopped_at_unix") val stoppedAtUnix: Long,
    @SerialName("elapsed_seconds") val elapsedSeconds: Int,
)

@Serializable
data class WorkItem(
    val id: Long,
    @SerialName("task_id") val taskId: Long? = null,
    @SerialName("note_id") val noteId: Long? = null,
    @SerialName("run_state") val runState: RunState,
    @SerialName("pomodoro_session_ids") val pomodoroSessionIds: List<Long> = emptyList(),
    @SerialName("started_at_unix") val startedAtUnix: Long? = null,
    @SerialName("stopped_at_unix") val stoppedAtUnix: Long? = null,
    @SerialName("elapsed_seconds") val elapsedSeconds: Long = 0,
    @SerialName("created_at_unix") val createdAtUnix: Long,
    @SerialName("updated_at_unix") val updatedAtUnix: Long,
)
