use sqlx::{FromRow, SqlitePool};

use crate::app::{Task, current_unix_timestamp};

#[derive(Clone)]
pub struct TaskRepository {
    pool: SqlitePool,
}

#[derive(Debug, FromRow)]
struct TaskRow {
    id: i64,
    title: String,
    description: String,
    completed: i64,
    created_at_unix: i64,
}

impl From<TaskRow> for Task {
    fn from(row: TaskRow) -> Self {
        Self {
            id: row.id,
            title: row.title,
            description: row.description,
            completed: row.completed != 0,
            created_at_unix: row.created_at_unix,
        }
    }
}

impl TaskRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn list_tasks(&self) -> Result<Vec<Task>, sqlx::Error> {
        let rows = sqlx::query_as::<_, TaskRow>(
            r#"
            SELECT id, title, description, completed, created_at_unix
            FROM tasks
            ORDER BY completed ASC, created_at_unix DESC, id DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Task::from).collect())
    }

    pub async fn create_task(
        &self,
        title: String,
        description: String,
    ) -> Result<Task, sqlx::Error> {
        let created_at_unix = current_unix_timestamp();
        let row = sqlx::query_as::<_, TaskRow>(
            r#"
            INSERT INTO tasks (title, description, completed, created_at_unix)
            VALUES (?1, ?2, 0, ?3)
            RETURNING id, title, description, completed, created_at_unix
            "#,
        )
        .bind(title)
        .bind(description)
        .bind(created_at_unix)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn update_task(
        &self,
        task_id: i64,
        title: String,
        description: String,
    ) -> Result<Task, sqlx::Error> {
        let row = sqlx::query_as::<_, TaskRow>(
            r#"
            UPDATE tasks
            SET title = ?2, description = ?3
            WHERE id = ?1
            RETURNING id, title, description, completed, created_at_unix
            "#,
        )
        .bind(task_id)
        .bind(title)
        .bind(description)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn toggle_task(&self, task_id: i64) -> Result<Task, sqlx::Error> {
        let row = sqlx::query_as::<_, TaskRow>(
            r#"
            UPDATE tasks
            SET completed = CASE completed WHEN 0 THEN 1 ELSE 0 END
            WHERE id = ?1
            RETURNING id, title, description, completed, created_at_unix
            "#,
        )
        .bind(task_id)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn delete_task(&self, task_id: i64) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM tasks WHERE id = ?1")
            .bind(task_id)
            .execute(&self.pool)
            .await?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::database::initialize;
    use sqlx::sqlite::SqlitePoolOptions;

    #[tokio::test]
    async fn repository_round_trip_persists_tasks() {
        let url = "sqlite::memory:";
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect(&url)
            .await
            .expect("create pool");
        initialize(&pool).await.expect("init db");

        let repository = TaskRepository::new(pool);
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

    #[test]
    fn row_mapping_preserves_timestamp() {
        let row = TaskRow {
            id: 1,
            title: "task".to_string(),
            description: "body".to_string(),
            completed: 0,
            created_at_unix: current_unix_timestamp(),
        };

        let task: Task = row.into();
        assert_eq!(task.id, 1);
        assert_eq!(task.title, "task");
        assert_eq!(task.description, "body");
        assert!(!task.completed);
    }
}
