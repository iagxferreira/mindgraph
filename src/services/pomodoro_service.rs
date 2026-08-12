use async_trait::async_trait;
use thiserror::Error;

use crate::{
    app::{PomodoroPhase, PomodoroSession},
    storage::{StorageError, pomodoro_repository::PomodoroRepository},
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] StorageError),
}

#[async_trait]
pub trait PomodoroService: Send + Sync {
    async fn list_sessions(&self) -> Result<Vec<PomodoroSession>, ServiceError>;
    async fn create_session(
        &self,
        task_id: Option<i64>,
        phase: PomodoroPhase,
        started_at_unix: i64,
        stopped_at_unix: i64,
        elapsed_seconds: u32,
    ) -> Result<PomodoroSession, ServiceError>;
}

#[derive(Clone)]
pub struct PomodoroServiceImpl {
    repository: PomodoroRepository,
}

impl PomodoroServiceImpl {
    pub fn new(repository: PomodoroRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl PomodoroService for PomodoroServiceImpl {
    async fn list_sessions(&self) -> Result<Vec<PomodoroSession>, ServiceError> {
        Ok(self.repository.list_sessions().await?)
    }

    async fn create_session(
        &self,
        task_id: Option<i64>,
        phase: PomodoroPhase,
        started_at_unix: i64,
        stopped_at_unix: i64,
        elapsed_seconds: u32,
    ) -> Result<PomodoroSession, ServiceError> {
        Ok(self
            .repository
            .create_session(
                task_id,
                phase,
                started_at_unix,
                stopped_at_unix,
                elapsed_seconds,
            )
            .await?)
    }
}
