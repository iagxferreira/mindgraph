use async_trait::async_trait;
use thiserror::Error;

use crate::{
    app::{RunState, WorkItem},
    storage::{StorageError, work_item_repository::WorkItemRepository},
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] StorageError),
}

#[async_trait]
pub trait WorkItemService: Send + Sync {
    async fn list_work_items(&self) -> Result<Vec<WorkItem>, ServiceError>;
    async fn create_work_item(&self, task_id: i64, note_id: i64) -> Result<WorkItem, ServiceError>;
    async fn update_work_item(
        &self,
        work_item_id: i64,
        task_id: i64,
        note_id: i64,
        run_state: RunState,
        pomodoro_session_id: Option<i64>,
        started_at_unix: Option<i64>,
        stopped_at_unix: Option<i64>,
        elapsed_seconds: u64,
    ) -> Result<WorkItem, ServiceError>;
    async fn delete_work_item(&self, work_item_id: i64) -> Result<(), ServiceError>;
}

#[derive(Clone)]
pub struct WorkItemServiceImpl {
    repository: WorkItemRepository,
}

impl WorkItemServiceImpl {
    pub fn new(repository: WorkItemRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl WorkItemService for WorkItemServiceImpl {
    async fn list_work_items(&self) -> Result<Vec<WorkItem>, ServiceError> {
        Ok(self.repository.list_work_items().await?)
    }

    async fn create_work_item(&self, task_id: i64, note_id: i64) -> Result<WorkItem, ServiceError> {
        Ok(self.repository.create_work_item(task_id, note_id).await?)
    }

    async fn update_work_item(
        &self,
        work_item_id: i64,
        task_id: i64,
        note_id: i64,
        run_state: RunState,
        pomodoro_session_id: Option<i64>,
        started_at_unix: Option<i64>,
        stopped_at_unix: Option<i64>,
        elapsed_seconds: u64,
    ) -> Result<WorkItem, ServiceError> {
        Ok(self
            .repository
            .update_work_item(
                work_item_id,
                task_id,
                note_id,
                run_state,
                pomodoro_session_id,
                started_at_unix,
                stopped_at_unix,
                elapsed_seconds,
            )
            .await?)
    }

    async fn delete_work_item(&self, work_item_id: i64) -> Result<(), ServiceError> {
        self.repository.delete_work_item(work_item_id).await?;
        Ok(())
    }
}
