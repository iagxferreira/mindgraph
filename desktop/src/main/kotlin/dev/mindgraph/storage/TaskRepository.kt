package dev.mindgraph.storage

import dev.mindgraph.model.Task
import dev.mindgraph.model.currentUnixTimestamp

class TaskRepository(private val database: Database) {
    suspend fun listTasks(): List<Task> = database.readData().tasks.sortedBy { it.id }

    suspend fun createTask(title: String, description: String): Task =
        database.withData { data ->
            val task = Task(
                id = data.allocateTaskId(),
                title = title,
                description = description,
                createdAtUnix = currentUnixTimestamp(),
            )
            data.tasks.add(task)
            task
        }

    suspend fun setTaskDoing(taskId: Long, doing: Boolean): Task =
        database.withData { data ->
            val index = data.tasks.indexOfFirst { it.id == taskId }
            if (index < 0) throw StorageNotFoundException("task", taskId)
            val updated = data.tasks[index].copy(doing = doing)
            data.tasks[index] = updated
            updated
        }
}
