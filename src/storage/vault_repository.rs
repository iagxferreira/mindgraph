use std::path::PathBuf;

use crate::{
    app::Vault,
    storage::{
        database::{load_data, save_data},
        error::StorageError,
    },
};

#[derive(Clone)]
pub struct VaultRepository {
    root_dir: PathBuf,
}

impl VaultRepository {
    pub fn new(root_dir: PathBuf) -> Self {
        Self { root_dir }
    }

    pub async fn list_vaults(&self) -> Result<Vec<Vault>, StorageError> {
        let mut vaults = load_data(&self.root_dir)?.vaults;
        vaults.sort_by(|left, right| left.id.cmp(&right.id));
        Ok(vaults)
    }

    pub async fn create_vault(
        &self,
        name: String,
        root_path: String,
    ) -> Result<Vault, StorageError> {
        std::fs::create_dir_all(&root_path)?;
        let mut data = load_data(&self.root_dir)?;
        let vault = Vault {
            id: data.allocate_vault_id(),
            name,
            root_path,
            created_at_unix: crate::app::current_unix_timestamp(),
        };
        data.vaults.push(vault.clone());
        save_data(&self.root_dir, &data)?;
        Ok(vault)
    }

    pub async fn update_vault(
        &self,
        vault_id: i64,
        name: String,
        root_path: String,
    ) -> Result<Vault, StorageError> {
        std::fs::create_dir_all(&root_path)?;
        let mut data = load_data(&self.root_dir)?;
        let vault = data
            .vaults
            .iter_mut()
            .find(|current| current.id == vault_id)
            .ok_or(StorageError::NotFound {
                entity: "vault",
                id: vault_id,
            })?;
        vault.name = name;
        vault.root_path = root_path;
        let updated = vault.clone();
        save_data(&self.root_dir, &data)?;
        Ok(updated)
    }

    pub async fn delete_vault(&self, vault_id: i64) -> Result<(), StorageError> {
        let mut data = load_data(&self.root_dir)?;
        let initial_len = data.vaults.len();
        data.vaults.retain(|vault| vault.id != vault_id);

        if data.vaults.len() == initial_len {
            return Err(StorageError::NotFound {
                entity: "vault",
                id: vault_id,
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
    async fn repository_round_trip_persists_vaults() {
        let temp_dir = tempfile::tempdir().expect("create temp dir");
        let database = Database::open_at(temp_dir.path().join(".mindgraph"))
            .await
            .expect("open database");

        let repository = VaultRepository::new(database.root_dir().to_path_buf());
        let vault_path = temp_dir.path().join("vaults").join("mindgraph");
        let created = repository
            .create_vault(
                "mindgraph".to_string(),
                vault_path.to_string_lossy().into_owned(),
            )
            .await
            .expect("create vault");

        assert_eq!(created.name, "mindgraph");
        assert_eq!(created.root_path, vault_path.to_string_lossy());

        let updated = repository
            .update_vault(
                created.id,
                "mindgraph-personal".to_string(),
                temp_dir
                    .path()
                    .join("vaults")
                    .join("personal")
                    .to_string_lossy()
                    .into_owned(),
            )
            .await
            .expect("update vault");
        assert_eq!(updated.name, "mindgraph-personal");
        assert_eq!(
            updated.root_path,
            temp_dir
                .path()
                .join("vaults")
                .join("personal")
                .to_string_lossy()
        );

        let vaults = repository.list_vaults().await.expect("list");
        assert!(
            vaults
                .iter()
                .any(|vault| vault.name == "mindgraph-personal")
        );
    }
}
