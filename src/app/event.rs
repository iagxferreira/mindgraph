use crossterm::event::KeyEvent;

use crate::app::{Note, PomodoroSession, RunState, Task, Vault, WorkItem, Workspace};

#[derive(Debug, Clone)]
pub enum AppEvent {
    Started,
    Tick,
    Resize,
    Key(KeyEvent),
    VaultsLoaded(Vec<Vault>),
    NotesLoaded(Vec<Note>),
    NoteDocumentLoaded { note_id: i64, document: String },
    TasksLoaded(Vec<Task>),
    PomodoroSessionsLoaded(Vec<PomodoroSession>),
    WorkItemsLoaded(Vec<WorkItem>),
    WorkspacesLoaded(Vec<Workspace>),
    NoteCreated(Note),
    NoteUpdated(Note),
    NoteDeleted(i64),
    TaskCreated(Task),
    TaskUpdated(Task),
    TaskDeleted(i64),
    PomodoroSessionCreated(PomodoroSession),
    WorkItemCreated(WorkItem),
    WorkItemUpdated(WorkItem),
    WorkItemDeleted(i64),
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
    LoadPomodoroSessions,
    LoadWorkItems,
    LoadWorkspaces,
    CreateNote {
        vault_id: i64,
        title: String,
        slug: String,
        path: String,
        document: String,
    },
    UpdateNote {
        note_id: i64,
        title: String,
        slug: String,
        path: String,
        document: String,
    },
    DeleteNote {
        note_id: i64,
    },
    LoadNoteDocument {
        path: String,
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
    SetTaskDoing {
        task_id: i64,
        doing: bool,
    },
    AddTaskTrackedTime {
        task_id: i64,
        tracked_seconds: u64,
    },
    CreatePomodoroSession {
        session: PomodoroSession,
    },
    CreateWorkItem {
        task_id: i64,
        note_id: i64,
    },
    SelectOrCreateWorkItem {
        task_id: i64,
        note_id: i64,
    },
    StartWorkItem {
        work_item_id: i64,
    },
    PauseWorkItem {
        work_item_id: i64,
    },
    StopWorkItem {
        work_item_id: i64,
    },
    UpdateWorkItem {
        work_item_id: i64,
        task_id: i64,
        note_id: i64,
        run_state: RunState,
        pomodoro_session_id: Option<i64>,
        started_at_unix: Option<i64>,
        stopped_at_unix: Option<i64>,
        elapsed_seconds: u64,
    },
    DeleteWorkItem {
        work_item_id: i64,
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
