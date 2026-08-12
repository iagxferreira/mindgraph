use std::path::PathBuf;

use crate::app::{RunState, WorkItem};

use super::{
    database::{load_data, save_data},
    error::StorageError,
};

#[derive(Clone)]
pub struct WorkItemRepository {
    root_dir: PathBuf,
}

impl WorkItemRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_work_items(&self) -> Result<Vec<WorkItem>, StorageError> {
        let mut work_items = load_data(&self.root_dir)?.work_items;
        work_items.sort_by(|left, right| {
            right
                .updated_at_unix
                .cmp(&left.updated_at_unix)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(work_items)
    }

    pub async fn create_work_item(
        &self,
        task_id: i64,
        note_id: i64,
    ) -> Result<WorkItem, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let now = crate::app::current_unix_timestamp();
        let work_item = WorkItem {
            id: data.allocate_work_item_id(),
            task_id,
            note_id,
            run_state: RunState::Idle,
            pomodoro_session_id: None,
            started_at_unix: None,
            stopped_at_unix: None,
            elapsed_seconds: 0,
            created_at_unix: now,
            updated_at_unix: now,
        };
        data.work_items.push(work_item.clone());
        save_data(&self.root_dir, &data)?;
        Ok(work_item)
    }

    pub async fn update_work_item(
        &self,
        work_item_id: i64,
        task_id: i64,
        note_id: i64,
        run_state: RunState,
        pomodoro_session_id: Option<i64>,
        started_at_unix: Option<i64>,
        stopped_at_unix: Option<i64>,
        elapsed_seconds: u64,
    ) -> Result<WorkItem, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let item = data
            .work_items
            .iter_mut()
            .find(|current| current.id == work_item_id)
            .ok_or(StorageError::NotFound {
                entity: "work item",
                id: work_item_id,
            })?;

        item.task_id = task_id;
        item.note_id = note_id;
        item.run_state = run_state;
        item.pomodoro_session_id = pomodoro_session_id;
        item.started_at_unix = started_at_unix;
        item.stopped_at_unix = stopped_at_unix;
        item.elapsed_seconds = elapsed_seconds;
        item.updated_at_unix = crate::app::current_unix_timestamp();
        let updated = item.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn delete_work_item(&self, work_item_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.work_items.len();
        data.work_items.retain(|item| item.id != work_item_id);

        if data.work_items.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "work item",
                id: work_item_id,
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
    async fn repository_round_trip_persists_work_items() {
        let temp_dir = tempfile::tempdir().expect("temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("open database");

        let repository = WorkItemRepository::new(database.root_dir().to_path_buf());
        let created = repository
            .create_work_item(1, 2)
            .await
            .expect("create work item");

        assert_eq!(created.task_id, 1);
        assert_eq!(created.note_id, 2);
        assert_eq!(created.run_state, RunState::Idle);

        let updated = repository
            .update_work_item(
                created.id,
                1,
                2,
                RunState::Running,
                Some(9),
                Some(10),
                None,
                42,
            )
            .await
            .expect("update work item");
        assert_eq!(updated.run_state, RunState::Running);
        assert_eq!(updated.pomodoro_session_id, Some(9));
        assert_eq!(updated.elapsed_seconds, 42);

        let items = repository.list_work_items().await.expect("list work items");
        assert_eq!(items.len(), 1);
    }
}
