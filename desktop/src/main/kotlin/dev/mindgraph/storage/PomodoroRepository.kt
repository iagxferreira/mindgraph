package dev.mindgraph.storage

import dev.mindgraph.model.PomodoroPhase
import dev.mindgraph.model.PomodoroSession

class PomodoroRepository(private val database: Database) {
    suspend fun listSessions(): List<PomodoroSession> = database.readData().pomodoroSessions.sortedWith(sessionOrder)

    suspend fun createSession(
        taskId: Long?,
        phase: PomodoroPhase,
        startedAtUnix: Long,
        stoppedAtUnix: Long,
        elapsedSeconds: Int,
    ): PomodoroSession =
        database.withData { data ->
            val session = PomodoroSession(
                id = data.allocatePomodoroSessionId(),
                taskId = taskId,
                phase = phase,
                startedAtUnix = startedAtUnix,
                stoppedAtUnix = stoppedAtUnix,
                elapsedSeconds = elapsedSeconds,
            )
            data.pomodoroSessions.add(session)
            session
        }

    companion object {
        private val sessionOrder = compareByDescending<PomodoroSession> { it.stoppedAtUnix }
            .thenByDescending { it.startedAtUnix }
            .thenByDescending { it.id }
    }
}
