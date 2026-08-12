use std::{env, fs, path::PathBuf};

use sqlx::{
    Row, SqlitePool,
    sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions},
};

use crate::storage::{
    link_repository::LinkRepository, note_repository::NoteRepository,
    task_repository::TaskRepository, vault_repository::VaultRepository,
    workspace_repository::WorkspaceRepository,
};

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

    pub fn vault_repository(&self) -> VaultRepository {
        VaultRepository::new(self.pool.clone())
    }

    pub fn note_repository(&self) -> NoteRepository {
        NoteRepository::new(self.pool.clone())
    }

    pub fn link_repository(&self) -> LinkRepository {
        LinkRepository::new(self.pool.clone())
    }

    pub fn workspace_repository(&self) -> WorkspaceRepository {
        WorkspaceRepository::new(self.pool.clone())
    }
}

pub async fn initialize(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            completed INTEGER NOT NULL DEFAULT 0,
            created_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    ensure_task_description_column(pool).await?;
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS vaults (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            root_path TEXT NOT NULL,
            created_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    ensure_default_vault(pool).await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vault_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            slug TEXT NOT NULL,
            content TEXT NOT NULL DEFAULT '',
            created_at_unix INTEGER NOT NULL,
            updated_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS links (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_note_id INTEGER NOT NULL,
            target_note_id INTEGER NOT NULL,
            relationship TEXT NOT NULL DEFAULT 'references',
            created_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS workspaces (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            path TEXT NOT NULL,
            created_at_unix INTEGER NOT NULL
        )
        "#,
    )
    .execute(pool)
    .await?;

    ensure_default_workspace(pool).await?;

    Ok(())
}

async fn ensure_task_description_column(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let columns = sqlx::query("PRAGMA table_info(tasks)")
        .fetch_all(pool)
        .await?;

    let has_description = columns
        .iter()
        .any(|row| row.get::<String, _>("name") == "description");

    if !has_description {
        sqlx::query("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            .execute(pool)
            .await?;
    }

    Ok(())
}

async fn ensure_default_workspace(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM workspaces")
        .fetch_one(pool)
        .await?;

    if count.0 == 0 {
        sqlx::query(
            r#"
            INSERT INTO workspaces (name, path, created_at_unix)
            VALUES ('default', '.', strftime('%s', 'now'))
            "#,
        )
        .execute(pool)
        .await?;
    }

    Ok(())
}

async fn ensure_default_vault(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM vaults")
        .fetch_one(pool)
        .await?;

    if count.0 == 0 {
        sqlx::query(
            r#"
            INSERT INTO vaults (name, root_path, created_at_unix)
            VALUES ('default', '.', strftime('%s', 'now'))
            "#,
        )
        .execute(pool)
        .await?;
    }

    Ok(())
}

fn default_database_path() -> PathBuf {
    if let Ok(custom_path) = env::var("MINDGRAPH_DB_PATH") {
        return PathBuf::from(custom_path);
    }

    if let Ok(custom_path) = env::var("FORGE_DB_PATH") {
        return PathBuf::from(custom_path);
    }

    let mut path = env::temp_dir();
    path.push("mindgraph.db");
    path
}
