use std::path::PathBuf;

use crate::{
    app::Task,
    storage::{
        database::{load_data, save_data},
        error::StorageError,
    },
};

#[derive(Clone)]
pub struct TaskRepository {
    root_dir: PathBuf,
}

impl TaskRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_tasks(&self) -> Result<Vec<Task>, StorageError> {
        let mut tasks = load_data(&self.root_dir)?.tasks;
        tasks.sort_by(|left, right| {
            left.completed
                .cmp(&right.completed)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(tasks)
    }

    pub async fn create_task(
        &self,
        title: String,
        description: String,
    ) -> Result<Task, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let task = Task {
            id: data.allocate_task_id(),
            title,
            description,
            completed: false,
            created_at_unix: crate::app::current_unix_timestamp(),
        };
        data.tasks.push(task.clone());
        save_data(&self.root_dir, &data)?;
        Ok(task)
    }

    pub async fn update_task(
        &self,
        task_id: i64,
        title: String,
        description: String,
    ) -> Result<Task, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let task = data
            .tasks
            .iter_mut()
            .find(|current| current.id == task_id)
            .ok_or(StorageError::NotFound {
                entity: "task",
                id: task_id,
            })?;
        task.title = title;
        task.description = description;
        let updated = task.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn toggle_task(&self, task_id: i64) -> Result<Task, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let task = data
            .tasks
            .iter_mut()
            .find(|current| current.id == task_id)
            .ok_or(StorageError::NotFound {
                entity: "task",
                id: task_id,
            })?;
        task.completed = !task.completed;
        let updated = task.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn delete_task(&self, task_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.tasks.len();
        data.tasks.retain(|task| task.id != task_id);

        if data.tasks.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "task",
                id: task_id,
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
    async fn repository_round_trip_persists_tasks() {
        let temp_dir = tempfile::tempdir().expect("temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("open database");

        let repository = TaskRepository::new(database.root_dir().to_path_buf());
        let created = repository
            .create_task(
                "ship the milestone".to_string(),
                "write release notes".to_string(),
            )
            .await
            .expect("create task");

        assert_eq!(created.title, "ship the milestone");
        assert_eq!(created.description, "write release notes");
        assert!(!created.completed);

        let toggled = repository.toggle_task(created.id).await.expect("toggle");
        assert!(toggled.completed);

        let updated = repository
            .update_task(
                created.id,
                "renamed task".to_string(),
                "updated body".to_string(),
            )
            .await
            .expect("update");
        assert_eq!(updated.title, "renamed task");
        assert_eq!(updated.description, "updated body");

        let tasks = repository.list_tasks().await.expect("list");
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].title, "renamed task");
        assert_eq!(tasks[0].description, "updated body");
        assert!(tasks[0].completed);
    }
}
