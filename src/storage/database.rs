use std::{env, fs, path::PathBuf};

use sqlx::{
    sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions},
    SqlitePool,
};

use crate::storage::task_repository::TaskRepository;

#[derive(Clone)]
pub struct Database {
    pool: SqlitePool,
}

impl Database {
    pub async fn open_default() -> Result<Self, sqlx::Error> {
        let path = default_database_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(sqlx::Error::Io)?;
        }

        let options = SqliteConnectOptions::new()
            .filename(&path)
            .create_if_missing(true)
            .journal_mode(SqliteJournalMode::Wal);

        let pool = SqlitePoolOptions::new()
            .max_connections(5)
            .connect_with(options)
            .await?;
        initialize(&pool).await?;
        Ok(Self { pool })
    }

    pub fn task_repository(&self) -> TaskRepository {
        TaskRepository::new(self.pool.clone())
    }
}

pub async fn initialize(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            completed INTEGER NOT NULL DEFAULT 0,
            created_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    Ok(())
}

fn default_database_path() -> PathBuf {
    if let Ok(custom_path) = env::var("FORGE_DB_PATH") {
        return PathBuf::from(custom_path);
    }

    let mut path = env::temp_dir();
    path.push("forge.db");
    path
}
