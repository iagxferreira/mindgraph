use sqlx::{FromRow, SqlitePool};

use crate::app::{Vault, current_unix_timestamp};

#[derive(Clone)]
pub struct VaultRepository {
    pool: SqlitePool,
}

#[derive(Debug, FromRow)]
struct VaultRow {
    id: i64,
    name: String,
    root_path: String,
    created_at_unix: i64,
}

impl From<VaultRow> for Vault {
    fn from(row: VaultRow) -> Self {
        Self {
            id: row.id,
            name: row.name,
            root_path: row.root_path,
            created_at_unix: row.created_at_unix,
        }
    }
}

impl VaultRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn list_vaults(&self) -> Result<Vec<Vault>, sqlx::Error> {
        let rows = sqlx::query_as::<_, VaultRow>(
            r#"
            SELECT id, name, root_path, created_at_unix
            FROM vaults
            ORDER BY id ASC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Vault::from).collect())
    }

    pub async fn create_vault(
        &self,
        name: String,
        root_path: String,
    ) -> Result<Vault, sqlx::Error> {
        let row = sqlx::query_as::<_, VaultRow>(
            r#"
            INSERT INTO vaults (name, root_path, created_at_unix)
            VALUES (?1, ?2, ?3)
            RETURNING id, name, root_path, created_at_unix
            "#,
        )
        .bind(name)
        .bind(root_path)
        .bind(current_unix_timestamp())
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn update_vault(
        &self,
        vault_id: i64,
        name: String,
        root_path: String,
    ) -> Result<Vault, sqlx::Error> {
        let row = sqlx::query_as::<_, VaultRow>(
            r#"
            UPDATE vaults
            SET name = ?2, root_path = ?3
            WHERE id = ?1
            RETURNING id, name, root_path, created_at_unix
            "#,
        )
        .bind(vault_id)
        .bind(name)
        .bind(root_path)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn delete_vault(&self, vault_id: i64) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM vaults WHERE id = ?1")
            .bind(vault_id)
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
    async fn repository_round_trip_persists_vaults() {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .expect("create pool");
        initialize(&pool).await.expect("init db");

        let repository = VaultRepository::new(pool);
        let created = repository
            .create_vault("mindgraph".to_string(), "/vaults/mindgraph".to_string())
            .await
            .expect("create vault");

        assert_eq!(created.name, "mindgraph");
        assert_eq!(created.root_path, "/vaults/mindgraph");

        let updated = repository
            .update_vault(
                created.id,
                "mindgraph-personal".to_string(),
                "/vaults/personal".to_string(),
            )
            .await
            .expect("update vault");
        assert_eq!(updated.name, "mindgraph-personal");
        assert_eq!(updated.root_path, "/vaults/personal");

        let vaults = repository.list_vaults().await.expect("list");
        assert!(
            vaults
                .iter()
                .any(|vault| vault.name == "mindgraph-personal")
        );
    }
}
