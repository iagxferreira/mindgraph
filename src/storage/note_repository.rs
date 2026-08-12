use sqlx::{FromRow, SqlitePool};

use crate::app::{Note, current_unix_timestamp};

#[derive(Clone)]
pub struct NoteRepository {
    pool: SqlitePool,
}

#[derive(Debug, FromRow)]
struct NoteRow {
    id: i64,
    vault_id: i64,
    title: String,
    slug: String,
    content: String,
    created_at_unix: i64,
    updated_at_unix: i64,
}

impl From<NoteRow> for Note {
    fn from(row: NoteRow) -> Self {
        Self {
            id: row.id,
            vault_id: row.vault_id,
            title: row.title,
            slug: row.slug,
            content: row.content,
            created_at_unix: row.created_at_unix,
            updated_at_unix: row.updated_at_unix,
        }
    }
}

impl NoteRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn list_notes(&self) -> Result<Vec<Note>, sqlx::Error> {
        let rows = sqlx::query_as::<_, NoteRow>(
            r#"
            SELECT id, vault_id, title, slug, content, created_at_unix, updated_at_unix
            FROM notes
            ORDER BY updated_at_unix DESC, created_at_unix DESC, id DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Note::from).collect())
    }

    pub async fn list_notes_by_vault(&self, vault_id: i64) -> Result<Vec<Note>, sqlx::Error> {
        let rows = sqlx::query_as::<_, NoteRow>(
            r#"
            SELECT id, vault_id, title, slug, content, created_at_unix, updated_at_unix
            FROM notes
            WHERE vault_id = ?1
            ORDER BY updated_at_unix DESC, created_at_unix DESC, id DESC
            "#,
        )
        .bind(vault_id)
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Note::from).collect())
    }

    pub async fn create_note(
        &self,
        vault_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, sqlx::Error> {
        let now = current_unix_timestamp();
        let row = sqlx::query_as::<_, NoteRow>(
            r#"
            INSERT INTO notes (
                vault_id,
                title,
                slug,
                content,
                created_at_unix,
                updated_at_unix
            )
            VALUES (?1, ?2, ?3, ?4, ?5, ?5)
            RETURNING id, vault_id, title, slug, content, created_at_unix, updated_at_unix
            "#,
        )
        .bind(vault_id)
        .bind(title)
        .bind(slug)
        .bind(content)
        .bind(now)
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn update_note(
        &self,
        note_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, sqlx::Error> {
        let row = sqlx::query_as::<_, NoteRow>(
            r#"
            UPDATE notes
            SET title = ?2,
                slug = ?3,
                content = ?4,
                updated_at_unix = ?5
            WHERE id = ?1
            RETURNING id, vault_id, title, slug, content, created_at_unix, updated_at_unix
            "#,
        )
        .bind(note_id)
        .bind(title)
        .bind(slug)
        .bind(content)
        .bind(current_unix_timestamp())
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn delete_note(&self, note_id: i64) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM notes WHERE id = ?1")
            .bind(note_id)
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
    async fn repository_round_trip_persists_notes() {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .expect("create pool");
        initialize(&pool).await.expect("init db");

        let repository = NoteRepository::new(pool);
        let created = repository
            .create_note(
                1,
                "Rust".to_string(),
                "rust".to_string(),
                "Rust is a systems language.".to_string(),
            )
            .await
            .expect("create note");

        assert_eq!(created.vault_id, 1);
        assert_eq!(created.title, "Rust");
        assert_eq!(created.slug, "rust");

        let updated = repository
            .update_note(
                created.id,
                "Rust language".to_string(),
                "rust-language".to_string(),
                "Rust powers tools.".to_string(),
            )
            .await
            .expect("update note");
        assert_eq!(updated.title, "Rust language");
        assert_eq!(updated.slug, "rust-language");

        let notes = repository.list_notes_by_vault(1).await.expect("list");
        assert_eq!(notes.len(), 1);
        assert_eq!(notes[0].content, "Rust powers tools.");
    }
}
