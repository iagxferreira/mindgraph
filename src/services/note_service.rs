use async_trait::async_trait;
use thiserror::Error;

use crate::{
    app::Note,
    storage::{StorageError, note_repository::NoteRepository},
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] StorageError),
}

#[async_trait]
pub trait NoteService: Send + Sync {
    async fn list_all_notes(&self) -> Result<Vec<Note>, ServiceError>;
    async fn list_notes(&self, vault_id: i64) -> Result<Vec<Note>, ServiceError>;
    async fn create_note(
        &self,
        vault_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, ServiceError>;
    async fn update_note(
        &self,
        note_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, ServiceError>;
    async fn delete_note(&self, note_id: i64) -> Result<(), ServiceError>;
}

#[derive(Clone)]
pub struct NoteServiceImpl {
    repository: NoteRepository,
}

impl NoteServiceImpl {
    pub fn new(repository: NoteRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl NoteService for NoteServiceImpl {
    async fn list_all_notes(&self) -> Result<Vec<Note>, ServiceError> {
        Ok(self.repository.list_notes().await?)
    }

    async fn list_notes(&self, vault_id: i64) -> Result<Vec<Note>, ServiceError> {
        Ok(self.repository.list_notes_by_vault(vault_id).await?)
    }

    async fn create_note(
        &self,
        vault_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, ServiceError> {
        Ok(self
            .repository
            .create_note(vault_id, title, slug, content)
            .await?)
    }

    async fn update_note(
        &self,
        note_id: i64,
        title: String,
        slug: String,
        content: String,
    ) -> Result<Note, ServiceError> {
        Ok(self
            .repository
            .update_note(note_id, title, slug, content)
            .await?)
    }

    async fn delete_note(&self, note_id: i64) -> Result<(), ServiceError> {
        self.repository.delete_note(note_id).await?;
        Ok(())
    }
}
