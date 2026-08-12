use crossterm::event::KeyEvent;

use crate::app::{Note, Task, Vault, Workspace};

#[derive(Debug, Clone)]
pub enum AppEvent {
    Started,
    Tick,
    Resize,
    Key(KeyEvent),
    VaultsLoaded(Vec<Vault>),
    NotesLoaded(Vec<Note>),
    TasksLoaded(Vec<Task>),
    WorkspacesLoaded(Vec<Workspace>),
    NoteCreated(Note),
    NoteUpdated(Note),
    NoteDeleted(i64),
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
    LoadVaults,
    LoadAllNotes,
    LoadTasks,
    LoadWorkspaces,
    CreateNote {
        vault_id: i64,
        title: String,
        slug: String,
        content: String,
    },
    UpdateNote {
        note_id: i64,
        title: String,
        slug: String,
        content: String,
    },
    DeleteNote {
        note_id: i64,
    },
    CreateTask {
        title: String,
        description: String,
    },
    UpdateTask {
        task_id: i64,
        title: String,
        description: String,
    },
    ToggleTask {
        task_id: i64,
    },
    DeleteTask {
        task_id: i64,
    },
    CreateWorkspace {
        name: String,
        path: String,
    },
    UpdateWorkspace {
        workspace_id: i64,
        name: String,
        path: String,
    },
    DeleteWorkspace {
        workspace_id: i64,
    },
    ShowMessage(String),
}
