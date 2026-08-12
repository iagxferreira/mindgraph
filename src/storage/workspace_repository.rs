use sqlx::{FromRow, SqlitePool};

use crate::app::{current_unix_timestamp, Workspace};

#[derive(Clone)]
pub struct WorkspaceRepository {
    pool: SqlitePool,
}

#[derive(Debug, FromRow)]
struct WorkspaceRow {
    id: i64,
    name: String,
    path: String,
}

impl From<WorkspaceRow> for Workspace {
    fn from(row: WorkspaceRow) -> Self {
        Self {
            id: row.id,
            name: row.name,
            path: row.path,
        }
    }
}

impl WorkspaceRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn list_workspaces(&self) -> Result<Vec<Workspace>, sqlx::Error> {
        let rows = sqlx::query_as::<_, WorkspaceRow>(
            r#"
            SELECT id, name, path
            FROM workspaces
            ORDER BY id ASC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Workspace::from).collect())
    }

    pub async fn create_workspace(
        &self,
        name: String,
        path: String,
    ) -> Result<Workspace, sqlx::Error> {
        let row = sqlx::query_as::<_, WorkspaceRow>(
            r#"
            INSERT INTO workspaces (name, path, created_at_unix)
            VALUES (?1, ?2, ?3)
            RETURNING id, name, path
            "#,
        )
        .bind(name)
        .bind(path)
        .bind(current_unix_timestamp())
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn update_workspace(
        &self,
        workspace_id: i64,
        name: String,
        path: String,
    ) -> Result<Workspace, sqlx::Error> {
        let row = sqlx::query_as::<_, WorkspaceRow>(
            r#"
            UPDATE workspaces
            SET name = ?2, path = ?3
            WHERE id = ?1
            RETURNING id, name, path
            "#,
        )
        .bind(workspace_id)
        .bind(name)
        .bind(path)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn delete_workspace(&self, workspace_id: i64) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM workspaces WHERE id = ?1")
            .bind(workspace_id)
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
    async fn repository_round_trip_persists_workspaces() {
        let url = "sqlite::memory:";
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect(&url)
            .await
            .expect("create pool");
        initialize(&pool).await.expect("init db");

        let repository = WorkspaceRepository::new(pool);
        let created = repository
            .create_workspace("forge".to_string(), "/tmp/forge".to_string())
            .await
            .expect("create workspace");

        assert_eq!(created.name, "forge");
        assert_eq!(created.path, "/tmp/forge");

        let updated = repository
            .update_workspace(created.id, "forge-rs".to_string(), "/tmp/forge-rs".to_string())
            .await
            .expect("update workspace");
        assert_eq!(updated.name, "forge-rs");
        assert_eq!(updated.path, "/tmp/forge-rs");

        let workspaces = repository.list_workspaces().await.expect("list");
        assert!(workspaces.iter().any(|workspace| workspace.name == "forge-rs"));
    }
}
