use async_trait::async_trait;
use thiserror::Error;

use crate::{
    app::Task,
    storage::{StorageError, task_repository::TaskRepository},
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] StorageError),
}

#[async_trait]
pub trait TaskService: Send + Sync {
    async fn list_tasks(&self) -> Result<Vec<Task>, ServiceError>;
    async fn create_task(&self, title: String, description: String) -> Result<Task, ServiceError>;
    async fn update_task(
        &self,
        task_id: i64,
        title: String,
        description: String,
    ) -> Result<Task, ServiceError>;
    async fn toggle_task(&self, task_id: i64) -> Result<Task, ServiceError>;
    async fn delete_task(&self, task_id: i64) -> Result<(), ServiceError>;
    async fn set_task_doing(&self, task_id: i64, doing: bool) -> Result<Task, ServiceError>;
    async fn add_task_tracked_time(
        &self,
        task_id: i64,
        tracked_seconds: u64,
    ) -> Result<Task, ServiceError>;
}

#[derive(Clone)]
pub struct TaskServiceImpl {
    repository: TaskRepository,
}

impl TaskServiceImpl {
    pub fn new(repository: TaskRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl TaskService for TaskServiceImpl {
    async fn list_tasks(&self) -> Result<Vec<Task>, ServiceError> {
        Ok(self.repository.list_tasks().await?)
    }

    async fn create_task(&self, title: String, description: String) -> Result<Task, ServiceError> {
        Ok(self.repository.create_task(title, description).await?)
    }

    async fn update_task(
        &self,
        task_id: i64,
        title: String,
        description: String,
    ) -> Result<Task, ServiceError> {
        Ok(self
            .repository
            .update_task(task_id, title, description)
            .await?)
    }

    async fn toggle_task(&self, task_id: i64) -> Result<Task, ServiceError> {
        Ok(self.repository.toggle_task(task_id).await?)
    }

    async fn delete_task(&self, task_id: i64) -> Result<(), ServiceError> {
        self.repository.delete_task(task_id).await?;
        Ok(())
    }

    async fn set_task_doing(&self, task_id: i64, doing: bool) -> Result<Task, ServiceError> {
        Ok(self.repository.set_task_doing(task_id, doing).await?)
    }

    async fn add_task_tracked_time(
        &self,
        task_id: i64,
        tracked_seconds: u64,
    ) -> Result<Task, ServiceError> {
        Ok(self
            .repository
            .add_task_tracked_time(task_id, tracked_seconds)
            .await?)
    }
}
