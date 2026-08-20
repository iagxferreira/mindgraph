package dev.mindgraph.storage

import dev.mindgraph.model.Workspace

class WorkspaceRepository(private val database: Database) {
    suspend fun listWorkspaces(): List<Workspace> = database.readData().workspaces.sortedBy { it.id }

    suspend fun createWorkspace(name: String, path: String): Workspace =
        database.withData { data ->
            val workspace = Workspace(id = data.allocateWorkspaceId(), name = name, path = path)
            data.workspaces.add(workspace)
            workspace
        }
}
