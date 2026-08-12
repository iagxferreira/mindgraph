use std::{
    fs,
    io::ErrorKind,
    path::{Path, PathBuf},
};

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
        path: String,
        document: String,
    ) -> Result<Note, StorageError> {
        let now = current_unix_timestamp();
        let mut data = load_data(&self.root_dir)?;
        let note_id = data.allocate_note_id();
        let path = PathBuf::from(path);
        write_document(&path, &document)?;

        let note = Note {
            id: note_id,
            vault_id,
            title,
            slug,
            path: path.to_string_lossy().into_owned(),
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
        path: String,
        document: String,
    ) -> Result<Note, StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let (current_path, _vault_id) = data
            .notes
            .iter()
            .find(|current| current.id == note_id)
            .map(|note| (note.path.clone(), note.vault_id))
            .ok_or(StorageError::NotFound {
                entity: "note",
                id: note_id,
            })?;

        let next_path = PathBuf::from(path);
        move_note_file(Path::new(&current_path), &next_path)?;
        write_document(&next_path, &document)?;

        let note = data
            .notes
            .iter_mut()
            .find(|current| current.id == note_id)
            .expect("note must exist after lookup");
        note.title = title;
        note.slug = slug;
        note.path = next_path.to_string_lossy().into_owned();
        note.updated_at_unix = current_unix_timestamp();
        let note = note.clone();

        save_data(&self.root_dir, &data)?;
        Ok(note)
    }

    pub async fn delete_note(&self, note_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.notes.len();
        let removed_note = data.notes.iter().find(|note| note.id == note_id).cloned();
        data.notes.retain(|note| note.id != note_id);

        if data.notes.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "note",
                id: note_id,
            });
        }

        if let Some(note) = removed_note {
            let path = PathBuf::from(note.path);
            if path.exists() {
                fs::remove_file(path)?;
            }
        }

        data.links
            .retain(|link| link.source_note_id != note_id && link.target_note_id != note_id);
        save_data(&self.root_dir, &data)?;
        Ok(())
    }

    pub async fn read_note_document(&self, path: String) -> Result<String, StorageError> {
        match fs::read_to_string(path) {
            Ok(document) => Ok(document),
            Err(error) if error.kind() == ErrorKind::NotFound => Ok(String::new()),
            Err(error) => Err(error.into()),
        }
    }
}

fn write_document(path: &Path, document: &str) -> Result<(), StorageError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, document)?;
    Ok(())
}

fn move_note_file(previous: &Path, next: &Path) -> Result<(), StorageError> {
    if previous == next {
        return Ok(());
    }

    if let Some(parent) = next.parent() {
        fs::create_dir_all(parent)?;
    }

    if previous.exists() {
        fs::rename(previous, next)?;
    }

    Ok(())
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
                temp_dir
                    .path()
                    .join("vaults")
                    .join("default")
                    .join("notes")
                    .join("rust.md")
                    .to_string_lossy()
                    .into_owned(),
                "Rust is a systems language.".to_string(),
            )
            .await
            .expect("create note");

        assert_eq!(created.vault_id, 1);
        assert_eq!(created.title, "Rust");
        assert_eq!(created.slug, "rust");
        assert!(Path::new(&created.path).exists());

        let updated = repository
            .update_note(
                created.id,
                "Rust language".to_string(),
                "rust-language".to_string(),
                created.path.clone(),
                "Rust powers tools.".to_string(),
            )
            .await
            .expect("update note");
        assert_eq!(updated.title, "Rust language");
        assert_eq!(updated.slug, "rust-language");
        assert!(Path::new(&updated.path).exists());

        let notes = repository.list_notes_by_vault(1).await.expect("list");
        assert_eq!(notes.len(), 1);
        assert_eq!(
            repository
                .read_note_document(updated.path.clone())
                .await
                .expect("read note"),
            "Rust powers tools."
        );
    }
}
