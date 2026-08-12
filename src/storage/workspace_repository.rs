use std::path::PathBuf;

use crate::{
    app::Workspace,
    storage::{
        database::{load_config, load_data, save_data},
        error::StorageError,
    },
};

#[derive(Clone)]
pub struct WorkspaceRepository {
    root_dir: PathBuf,
}

impl WorkspaceRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_workspaces(&self) -> Result<Vec<Workspace>, StorageError> {
        let mut workspaces = load_data(&self.root_dir)?.workspaces;
        workspaces.sort_by(|left, right| left.id.cmp(&right.id));
        Ok(workspaces)
    }

    pub async fn create_workspace(
        &self,
        name: String,
        path: String,
    ) -> Result<Workspace, StorageError> {
        let config = load_config(&self.root_dir)?;
        let mut data = load_data(&self.root_dir)?;
        std::fs::create_dir_all(&path)?;
        if path.starts_with(&config.workspace_root) {
            std::fs::create_dir_all(&config.workspace_root)?;
        }

        let workspace = Workspace {
            id: data.allocate_workspace_id(),
            name,
            path,
        };
        data.workspaces.push(workspace.clone());
        save_data(&self.root_dir, &data)?;
        Ok(workspace)
    }

    pub async fn update_workspace(
        &self,
        workspace_id: i64,
        name: String,
        path: String,
    ) -> Result<Workspace, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let workspace = data
            .workspaces
            .iter_mut()
            .find(|current| current.id == workspace_id)
            .ok_or(StorageError::NotFound {
                entity: "workspace",
                id: workspace_id,
            })?;
        std::fs::create_dir_all(&path)?;
        workspace.name = name;
        workspace.path = path;
        let updated = workspace.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn delete_workspace(&self, workspace_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.workspaces.len();
        data.workspaces
            .retain(|workspace| workspace.id != workspace_id);

        if data.workspaces.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "workspace",
                id: workspace_id,
            });
        }

        save_data(&self.root_dir, &data)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::database::Database;

    #[tokio::test]
    async fn repository_round_trip_persists_workspaces() {
        let temp_dir = tempfile::tempdir().expect("create temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("open database");

        let repository = WorkspaceRepository::new(database.root_dir().to_path_buf());
        let created = repository
            .create_workspace("mindgraph".to_string(), "/tmp/mindgraph".to_string())
            .await
            .expect("create workspace");

        assert_eq!(created.name, "mindgraph");
        assert_eq!(created.path, "/tmp/mindgraph");

        let updated = repository
            .update_workspace(
                created.id,
                "mindgraph-rs".to_string(),
                "/tmp/mindgraph-rs".to_string(),
            )
            .await
            .expect("update workspace");
        assert_eq!(updated.name, "mindgraph-rs");
        assert_eq!(updated.path, "/tmp/mindgraph-rs");

        let workspaces = repository.list_workspaces().await.expect("list");
        assert!(
            workspaces
                .iter()
                .any(|workspace| workspace.name == "mindgraph-rs")
        );
    }
}
