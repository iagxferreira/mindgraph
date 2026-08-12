use sqlx::{FromRow, SqlitePool};

use crate::app::{Link, current_unix_timestamp};

#[derive(Clone)]
pub struct LinkRepository {
    pool: SqlitePool,
}

#[derive(Debug, FromRow)]
struct LinkRow {
    id: i64,
    source_note_id: i64,
    target_note_id: i64,
    relationship: String,
    created_at_unix: i64,
}

impl From<LinkRow> for Link {
    fn from(row: LinkRow) -> Self {
        Self {
            id: row.id,
            source_note_id: row.source_note_id,
            target_note_id: row.target_note_id,
            relationship: row.relationship,
            created_at_unix: row.created_at_unix,
        }
    }
}

impl LinkRepository {
    pub fn new(pool: SqlitePool) -> Self {
        Self { pool }
    }

    pub async fn list_links(&self) -> Result<Vec<Link>, sqlx::Error> {
        let rows = sqlx::query_as::<_, LinkRow>(
            r#"
            SELECT id, source_note_id, target_note_id, relationship, created_at_unix
            FROM links
            ORDER BY created_at_unix DESC, id DESC
            "#,
        )
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Link::from).collect())
    }

    pub async fn list_outgoing_links(&self, note_id: i64) -> Result<Vec<Link>, sqlx::Error> {
        let rows = sqlx::query_as::<_, LinkRow>(
            r#"
            SELECT id, source_note_id, target_note_id, relationship, created_at_unix
            FROM links
            WHERE source_note_id = ?1
            ORDER BY created_at_unix DESC, id DESC
            "#,
        )
        .bind(note_id)
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Link::from).collect())
    }

    pub async fn list_backlinks(&self, note_id: i64) -> Result<Vec<Link>, sqlx::Error> {
        let rows = sqlx::query_as::<_, LinkRow>(
            r#"
            SELECT id, source_note_id, target_note_id, relationship, created_at_unix
            FROM links
            WHERE target_note_id = ?1
            ORDER BY created_at_unix DESC, id DESC
            "#,
        )
        .bind(note_id)
        .fetch_all(&self.pool)
        .await?;

        Ok(rows.into_iter().map(Link::from).collect())
    }

    pub async fn create_link(
        &self,
        source_note_id: i64,
        target_note_id: i64,
        relationship: String,
    ) -> Result<Link, sqlx::Error> {
        let row = sqlx::query_as::<_, LinkRow>(
            r#"
            INSERT INTO links (
                source_note_id,
                target_note_id,
                relationship,
                created_at_unix
            )
            VALUES (?1, ?2, ?3, ?4)
            RETURNING id, source_note_id, target_note_id, relationship, created_at_unix
            "#,
        )
        .bind(source_note_id)
        .bind(target_note_id)
        .bind(relationship)
        .bind(current_unix_timestamp())
        .fetch_one(&self.pool)
        .await?;

        Ok(row.into())
    }

    pub async fn delete_link(&self, link_id: i64) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM links WHERE id = ?1")
            .bind(link_id)
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
    async fn repository_round_trip_persists_links() {
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .expect("create pool");
        initialize(&pool).await.expect("init db");

        let repository = LinkRepository::new(pool);
        let created = repository
            .create_link(1, 2, "references".to_string())
            .await
            .expect("create link");

        assert_eq!(created.source_note_id, 1);
        assert_eq!(created.target_note_id, 2);
        assert_eq!(created.relationship, "references");

        let backlinks = repository.list_backlinks(2).await.expect("backlinks");
        assert_eq!(backlinks.len(), 1);
        assert_eq!(backlinks[0].source_note_id, 1);
    }
}
