use std::{
    env, fs,
    path::{Path, PathBuf},
};

use serde::{Deserialize, Serialize};

use crate::app::{Link, Note, Task, Vault, Workspace, current_unix_timestamp};

use super::{
    error::StorageError, link_repository::LinkRepository, note_repository::NoteRepository,
    task_repository::TaskRepository, vault_repository::VaultRepository,
    workspace_repository::WorkspaceRepository,
};

#[derive(Clone)]
pub struct Database {
    root_dir: PathBuf,
}

impl Database {
    pub async fn open_default() -> Result<Self, StorageError> {
        let root_dir = default_root_dir();
        Self::open_at(root_dir).await
    }

    pub async fn open_at(root_dir: impl Into<PathBuf>) -> Result<Self, StorageError> {
        let root_dir = root_dir.into();
        initialize(&root_dir).await?;
        Ok(Self { root_dir })
    }

    pub fn root_dir(&self) -> &Path {
        &self.root_dir
    }

    pub fn task_repository(&self) -> TaskRepository {
        TaskRepository::new(self.root_dir.clone())
    }

    pub fn vault_repository(&self) -> VaultRepository {
        VaultRepository::new(self.root_dir.clone())
    }

    pub fn note_repository(&self) -> NoteRepository {
        NoteRepository::new(self.root_dir.clone())
    }

    pub fn link_repository(&self) -> LinkRepository {
        LinkRepository::new(self.root_dir.clone())
    }

    pub fn workspace_repository(&self) -> WorkspaceRepository {
        WorkspaceRepository::new(self.root_dir.clone())
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageConfig {
    pub workspace_root: String,
}

impl StorageConfig {
    fn default_for(root_dir: &Path) -> Self {
        Self {
            workspace_root: root_dir.join("workspaces").to_string_lossy().into_owned(),
        }
    }
}

impl Default for StorageConfig {
    fn default() -> Self {
        Self {
            workspace_root: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct StorageData {
    pub tasks: Vec<Task>,
    pub workspaces: Vec<Workspace>,
    pub vaults: Vec<Vault>,
    pub notes: Vec<Note>,
    pub links: Vec<Link>,
    pub next_task_id: i64,
    pub next_workspace_id: i64,
    pub next_vault_id: i64,
    pub next_note_id: i64,
    pub next_link_id: i64,
}

impl StorageData {
    pub(crate) fn normalize_counters(&mut self) {
        self.next_task_id = self
            .next_task_id
            .max(self.tasks.iter().map(|item| item.id).max().unwrap_or(0) + 1);
        self.next_workspace_id = self.next_workspace_id.max(
            self.workspaces
                .iter()
                .map(|item| item.id)
                .max()
                .unwrap_or(0)
                + 1,
        );
        self.next_vault_id = self
            .next_vault_id
            .max(self.vaults.iter().map(|item| item.id).max().unwrap_or(0) + 1);
        self.next_note_id = self
            .next_note_id
            .max(self.notes.iter().map(|item| item.id).max().unwrap_or(0) + 1);
        self.next_link_id = self
            .next_link_id
            .max(self.links.iter().map(|item| item.id).max().unwrap_or(0) + 1);
    }

    pub(crate) fn allocate_task_id(&mut self) -> i64 {
        self.normalize_counters();
        let id = self.next_task_id.max(1);
        self.next_task_id = id + 1;
        id
    }

    pub(crate) fn allocate_workspace_id(&mut self) -> i64 {
        self.normalize_counters();
        let id = self.next_workspace_id.max(1);
        self.next_workspace_id = id + 1;
        id
    }

    pub(crate) fn allocate_vault_id(&mut self) -> i64 {
        self.normalize_counters();
        let id = self.next_vault_id.max(1);
        self.next_vault_id = id + 1;
        id
    }

    pub(crate) fn allocate_note_id(&mut self) -> i64 {
        self.normalize_counters();
        let id = self.next_note_id.max(1);
        self.next_note_id = id + 1;
        id
    }

    pub(crate) fn allocate_link_id(&mut self) -> i64 {
        self.normalize_counters();
        let id = self.next_link_id.max(1);
        self.next_link_id = id + 1;
        id
    }
}

pub async fn initialize(root_dir: &Path) -> Result<(), StorageError> {
    fs::create_dir_all(root_dir)?;

    let config = load_config(root_dir)?;
    let workspace_root = PathBuf::from(&config.workspace_root);
    fs::create_dir_all(&workspace_root)?;

    let mut data = load_data(root_dir)?;

    if data.workspaces.is_empty() {
        let default_workspace_path = workspace_root.join("default");
        fs::create_dir_all(&default_workspace_path)?;
        let workspace_id = data.allocate_workspace_id();
        data.workspaces.push(Workspace {
            id: workspace_id,
            name: "default".to_string(),
            path: default_workspace_path.to_string_lossy().into_owned(),
        });
    }

    if data.vaults.is_empty() {
        let default_vault_path = root_dir.join("vaults").join("default");
        fs::create_dir_all(&default_vault_path)?;
        let vault_id = data.allocate_vault_id();
        data.vaults.push(Vault {
            id: vault_id,
            name: "default".to_string(),
            root_path: default_vault_path.to_string_lossy().into_owned(),
            created_at_unix: current_unix_timestamp(),
        });
    }

    data.normalize_counters();
    save_data(root_dir, &data)?;
    save_config(root_dir, &config)?;
    Ok(())
}

pub(crate) fn load_config(root_dir: &Path) -> Result<StorageConfig, StorageError> {
    let path = config_path(root_dir);
    if !path.exists() {
        return Ok(StorageConfig::default_for(root_dir));
    }

    let config: StorageConfig = read_json(&path)?;
    if config.workspace_root.trim().is_empty() {
        Ok(StorageConfig::default_for(root_dir))
    } else {
        Ok(config)
    }
}

pub(crate) fn save_config(root_dir: &Path, config: &StorageConfig) -> Result<(), StorageError> {
    write_json(&config_path(root_dir), config)
}

pub(crate) fn load_data(root_dir: &Path) -> Result<StorageData, StorageError> {
    let path = data_path(root_dir);
    if !path.exists() {
        return Ok(StorageData::default());
    }

    let mut data: StorageData = read_json(&path)?;
    data.normalize_counters();
    Ok(data)
}

pub(crate) fn save_data(root_dir: &Path, data: &StorageData) -> Result<(), StorageError> {
    write_json(&data_path(root_dir), data)
}

pub(crate) fn config_path(root_dir: &Path) -> PathBuf {
    root_dir.join("config.json")
}

pub(crate) fn data_path(root_dir: &Path) -> PathBuf {
    root_dir.join("data.json")
}

fn default_root_dir() -> PathBuf {
    if let Ok(custom_path) = env::var("MINDGRAPH_HOME") {
        return PathBuf::from(custom_path);
    }

    if let Some(home) = env::var_os("HOME").map(PathBuf::from) {
        return home.join(".config").join("mindgraph");
    }

    let mut path = env::current_dir().unwrap_or_else(|_| env::temp_dir());
    path.push(".mindgraph");
    path
}

fn read_json<T>(path: &Path) -> Result<T, StorageError>
where
    T: for<'de> Deserialize<'de>,
{
    let contents = fs::read_to_string(path)?;
    Ok(serde_json::from_str(&contents)?)
}

fn write_json<T>(path: &Path, value: &T) -> Result<(), StorageError>
where
    T: Serialize,
{
    let contents = serde_json::to_string_pretty(value)?;
    fs::write(path, contents)?;
    Ok(())
}
