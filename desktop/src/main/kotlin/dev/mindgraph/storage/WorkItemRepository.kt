package dev.mindgraph.storage

import dev.mindgraph.model.RunState
import dev.mindgraph.model.WorkItem
import dev.mindgraph.model.currentUnixTimestamp

class WorkItemRepository(private val database: Database) {
    suspend fun listWorkItems(): List<WorkItem> = database.readData().workItems.sortedWith(workItemOrder)

    suspend fun createWorkItem(taskId: Long?, noteId: Long?): WorkItem =
        database.withData { data ->
            val now = currentUnixTimestamp()
            val item = WorkItem(
                id = data.allocateWorkItemId(),
                taskId = taskId,
                noteId = noteId,
                runState = RunState.Idle,
                createdAtUnix = now,
                updatedAtUnix = now,
            )
            data.workItems.add(item)
            item
        }

    suspend fun updateWorkItem(
        workItemId: Long,
        taskId: Long?,
        noteId: Long?,
        runState: RunState,
        pomodoroSessionIds: List<Long>,
        startedAtUnix: Long?,
        stoppedAtUnix: Long?,
        elapsedSeconds: Long,
    ): WorkItem =
        database.withData { data ->
            val index = data.workItems.indexOfFirst { it.id == workItemId }
            if (index < 0) throw StorageNotFoundException("work item", workItemId)
            val updated = data.workItems[index].copy(
                taskId = taskId,
                noteId = noteId,
                runState = runState,
                pomodoroSessionIds = pomodoroSessionIds,
                startedAtUnix = startedAtUnix,
                stoppedAtUnix = stoppedAtUnix,
                elapsedSeconds = elapsedSeconds,
                updatedAtUnix = currentUnixTimestamp(),
            )
            data.workItems[index] = updated
            updated
        }

    suspend fun deleteWorkItem(workItemId: Long) {
        database.withData { data ->
            if (!data.workItems.removeAll { it.id == workItemId }) {
                throw StorageNotFoundException("work item", workItemId)
            }
        }
    }

    companion object {
        private val workItemOrder = compareByDescending<WorkItem> { it.updatedAtUnix }
            .thenByDescending { it.createdAtUnix }
            .thenByDescending { it.id }
    }
}
