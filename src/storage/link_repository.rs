use std::path::PathBuf;

use crate::{
    app::Link,
    storage::{
        database::{load_data, save_data},
        error::StorageError,
    },
};

#[derive(Clone)]
pub struct LinkRepository {
    root_dir: PathBuf,
}

impl LinkRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_links(&self) -> Result<Vec<Link>, StorageError> {
        let mut links = load_data(&self.root_dir)?.links;
        links.sort_by(|left, right| {
            right
                .created_at_unix
                .cmp(&left.created_at_unix)
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(links)
    }

    pub async fn list_outgoing_links(&self, note_id: i64) -> Result<Vec<Link>, StorageError> {
        let mut links = load_data(&self.root_dir)?
            .links
            .into_iter()
            .filter(|link| link.source_note_id == note_id)
            .collect::<Vec<_>>();
        links.sort_by(|left, right| {
            right
                .created_at_unix
                .cmp(&left.created_at_unix)
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(links)
    }

    pub async fn list_backlinks(&self, note_id: i64) -> Result<Vec<Link>, StorageError> {
        let mut links = load_data(&self.root_dir)?
            .links
            .into_iter()
            .filter(|link| link.target_note_id == note_id)
            .collect::<Vec<_>>();
        links.sort_by(|left, right| {
            right
                .created_at_unix
                .cmp(&left.created_at_unix)
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(links)
    }

    pub async fn create_link(
        &self,
        source_note_id: i64,
        target_note_id: i64,
        relationship: String,
    ) -> Result<Link, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let link = Link {
            id: data.allocate_link_id(),
            source_note_id,
            target_note_id,
            relationship,
            created_at_unix: crate::app::current_unix_timestamp(),
        };
        data.links.push(link.clone());
        save_data(&self.root_dir, &data)?;
        Ok(link)
    }

    pub async fn delete_link(&self, link_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.links.len();
        data.links.retain(|link| link.id != link_id);

        if data.links.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "link",
                id: link_id,
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
    async fn repository_round_trip_persists_links() {
        let temp_dir = tempfile::tempdir().expect("temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("init db");

        let repository = LinkRepository::new(database.root_dir().to_path_buf());
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
