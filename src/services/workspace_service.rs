use async_trait::async_trait;
use thiserror::Error;

use crate::{app::Workspace, storage::workspace_repository::WorkspaceRepository};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] sqlx::Error),
}

#[async_trait]
pub trait WorkspaceService: Send + Sync {
    async fn list_workspaces(&self) -> Result<Vec<Workspace>, ServiceError>;
    async fn create_workspace(&self, name: String, path: String)
    -> Result<Workspace, ServiceError>;
    async fn update_workspace(
        &self,
        workspace_id: i64,
        name: String,
        path: String,
    ) -> Result<Workspace, ServiceError>;
    async fn delete_workspace(&self, workspace_id: i64) -> Result<(), ServiceError>;
}

#[derive(Clone)]
pub struct WorkspaceServiceImpl {
    repository: WorkspaceRepository,
}

impl WorkspaceServiceImpl {
    pub fn new(repository: WorkspaceRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl WorkspaceService for WorkspaceServiceImpl {
    async fn list_workspaces(&self) -> Result<Vec<Workspace>, ServiceError> {
        Ok(self.repository.list_workspaces().await?)
    }

    async fn create_workspace(
        &self,
        name: String,
        path: String,
    ) -> Result<Workspace, ServiceError> {
        Ok(self.repository.create_workspace(name, path).await?)
    }

    async fn update_workspace(
        &self,
        workspace_id: i64,
        name: String,
        path: String,
    ) -> Result<Workspace, ServiceError> {
        Ok(self
            .repository
            .update_workspace(workspace_id, name, path)
            .await?)
    }

    async fn delete_workspace(&self, workspace_id: i64) -> Result<(), ServiceError> {
        self.repository.delete_workspace(workspace_id).await?;
        Ok(())
    }
}
