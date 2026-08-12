use std::path::PathBuf;

use crate::app::PomodoroSession;

use super::{
    database::{load_data, save_data},
    error::StorageError,
};

#[derive(Clone)]
pub struct PomodoroRepository {
    root_dir: PathBuf,
}

impl PomodoroRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_sessions(&self) -> Result<Vec<PomodoroSession>, StorageError> {
        let mut sessions = load_data(&self.root_dir)?.pomodoro_sessions;
        sessions.sort_by(|left, right| {
            right
                .stopped_at_unix
                .cmp(&left.stopped_at_unix)
                .then_with(|| right.started_at_unix.cmp(&left.started_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(sessions)
    }

    pub async fn create_session(
        &self,
        task_id: Option<i64>,
        phase: crate::app::PomodoroPhase,
        started_at_unix: i64,
        stopped_at_unix: i64,
        elapsed_seconds: u32,
    ) -> Result<PomodoroSession, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let session = PomodoroSession {
            id: data.allocate_pomodoro_session_id(),
            task_id,
            phase,
            started_at_unix,
            stopped_at_unix,
            elapsed_seconds,
        };
        data.pomodoro_sessions.push(session.clone());
        save_data(&self.root_dir, &data)?;
        Ok(session)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{app::PomodoroPhase, storage::database::Database};

    #[tokio::test]
    async fn repository_round_trip_persists_sessions() {
        let temp_dir = tempfile::tempdir().expect("temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("open database");

        let repository = PomodoroRepository::new(database.root_dir().to_path_buf());
        let created = repository
            .create_session(Some(7), PomodoroPhase::Work, 10, 40, 30)
            .await
            .expect("create session");

        assert_eq!(created.task_id, Some(7));
        assert_eq!(created.elapsed_seconds, 30);

        let sessions = repository.list_sessions().await.expect("list sessions");
        assert_eq!(sessions.len(), 1);
        assert_eq!(sessions[0].id, created.id);
    }
}
