use async_trait::async_trait;
use thiserror::Error;

use crate::{
    app::Vault,
    storage::{StorageError, vault_repository::VaultRepository},
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Storage(#[from] StorageError),
}

#[async_trait]
pub trait VaultService: Send + Sync {
    async fn list_vaults(&self) -> Result<Vec<Vault>, ServiceError>;
}

#[derive(Clone)]
pub struct VaultServiceImpl {
    repository: VaultRepository,
}

impl VaultServiceImpl {
    pub fn new(repository: VaultRepository) -> Self {
        Self { repository }
    }
}

#[async_trait]
impl VaultService for VaultServiceImpl {
    async fn list_vaults(&self) -> Result<Vec<Vault>, ServiceError> {
        Ok(self.repository.list_vaults().await?)
    }
}
