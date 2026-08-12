use crossterm::event::KeyEvent;

use crate::app::{Task, Workspace};

#[derive(Debug, Clone)]
pub enum AppEvent {
    Started,
    Tick,
    Resize,
    Key(KeyEvent),
    TasksLoaded(Vec<Task>),
    WorkspacesLoaded(Vec<Workspace>),
    TaskCreated(Task),
    TaskUpdated(Task),
    TaskDeleted(i64),
    WorkspaceCreated(Workspace),
    WorkspaceUpdated(Workspace),
    WorkspaceDeleted(i64),
    Message(String),
}

#[derive(Debug, Clone)]
pub enum AppAction {
    None,
    LoadTasks,
    LoadWorkspaces,
    CreateTask { title: String, description: String },
    UpdateTask { task_id: i64, title: String, description: String },
    ToggleTask { task_id: i64 },
    DeleteTask { task_id: i64 },
    CreateWorkspace { name: String, path: String },
    UpdateWorkspace { workspace_id: i64, name: String, path: String },
    DeleteWorkspace { workspace_id: i64 },
    ShowMessage(String),
}
