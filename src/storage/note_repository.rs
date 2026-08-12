use std::path::PathBuf;

use crate::{
    app::{Note, current_unix_timestamp},
    storage::{
        database::{load_data, save_data},
        error::StorageError,
    },
};

#[derive(Clone)]
pub struct NoteRepository {
    root_dir: PathBuf,
}

impl NoteRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_notes(&self) -> Result<Vec<Note>, StorageError> {
        let mut notes = load_data(&self.root_dir)?.notes;
        notes.sort_by(|left, right| {
            right
                .updated_at_unix
                .cmp(&left.updated_at_unix)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(notes)
    }

    pub async fn list_notes_by_vault(&self, vault_id: i64) -> Result<Vec<Note>, StorageError> {
        let mut notes = load_data(&self.root_dir)?
            .notes
            .into_iter()
            .filter(|note| note.vault_id == vault_id)
            .collect::<Vec<_>>();
        notes.sort_by(|left, right| {
            right
                .updated_at_unix
                .cmp(&left.updated_at_unix)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
        Ok(notes)
    }

    pub async fn create_note(
        &self,
        vault_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, StorageError> {
        let now = current_unix_timestamp();
        let mut data = load_data(&self.root_dir)?;
        let note = Note {
            id: data.allocate_note_id(),
            vault_id,
            title,
            slug,
            content,
            created_at_unix: now,
            updated_at_unix: now,
        };
        data.notes.push(note.clone());
        save_data(&self.root_dir, &data)?;
        Ok(note)
    }

    pub async fn update_note(
        &self,
        note_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let note = data
            .notes
            .iter_mut()
            .find(|current| current.id == note_id)
            .ok_or(StorageError::NotFound {
                entity: "note",
                id: note_id,
            })?;
        note.title = title;
        note.slug = slug;
        note.content = content;
        note.updated_at_unix = current_unix_timestamp();
        let updated = note.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn delete_note(&self, note_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.notes.len();
        data.notes.retain(|note| note.id != note_id);

        if data.notes.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "note",
                id: note_id,
            });
        }

        data.links
            .retain(|link| link.source_note_id != note_id && link.target_note_id != note_id);
        save_data(&self.root_dir, &data)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::database::Database;

    #[tokio::test]
    async fn repository_round_trip_persists_notes() {
        let temp_dir = tempfile::tempdir().expect("temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("init db");

        let repository = NoteRepository::new(database.root_dir().to_path_buf());
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
