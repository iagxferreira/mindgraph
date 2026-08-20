package dev.mindgraph.storage

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the Kotlin app can read a `data.json` shaped exactly like the Rust app writes,
 * including the legacy `pomodoro_session_id` single-value alias, so both apps can share a
 * data directory.
 */
class DatabaseCompatTest {
    @Test
    fun readsRustShapedDataJsonIncludingLegacyPomodoroAlias() = runTest {
        val tempDir = Files.createTempDirectory("mindgraph-compat")
        val handWritten = """
            {
              "tasks": [],
              "pomodoro_sessions": [],
              "workspaces": [{"id": 1, "name": "default", "path": "${tempDir}/workspaces/default"}],
              "vaults": [{"id": 1, "name": "default", "root_path": "${tempDir}/vaults/default", "created_at_unix": 1000}],
              "notes": [],
              "links": [],
              "work_items": [
                {
                  "id": 1, "task_id": null, "note_id": null, "run_state": "Stopped",
                  "pomodoro_session_id": 42,
                  "started_at_unix": null, "stopped_at_unix": 100, "elapsed_seconds": 60,
                  "created_at_unix": 1, "updated_at_unix": 2
                },
                {
                  "id": 2, "task_id": null, "note_id": null, "run_state": "Idle",
                  "pomodoro_session_ids": [7, 8],
                  "elapsed_seconds": 0, "created_at_unix": 1, "updated_at_unix": 2
                },
                {
                  "id": 3, "task_id": null, "note_id": null, "run_state": "Idle",
                  "elapsed_seconds": 0, "created_at_unix": 1, "updated_at_unix": 2
                }
              ],
              "next_task_id": 1, "next_pomodoro_session_id": 1, "next_work_item_id": 4,
              "next_workspace_id": 2, "next_vault_id": 2, "next_note_id": 1, "next_link_id": 1
            }
        """.trimIndent()
        Files.writeString(tempDir.resolve("data.json"), handWritten)

        val database = Database.openAt(tempDir)
        val workItemRepository = WorkItemRepository(database)
        val items = workItemRepository.listWorkItems().associateBy { it.id }

        assertEquals(listOf(42L), items.getValue(1).pomodoroSessionIds)
        assertEquals(listOf(7L, 8L), items.getValue(2).pomodoroSessionIds)
        assertTrue(items.getValue(3).pomodoroSessionIds.isEmpty())
    }
}
