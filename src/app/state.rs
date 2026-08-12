use std::{
    collections::BTreeSet,
    fs,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use serde::{Deserialize, Serialize};

use crate::app::{AppAction, AppEvent};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Screen {
    Dashboard,
    Pomodoro,
    Run,
    Tasks,
    Mind,
    Notifications,
    Workspaces,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum RunState {
    Idle,
    Running,
    Paused,
    Stopped,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Task {
    pub id: i64,
    pub title: String,
    pub description: String,
    #[serde(default)]
    pub doing: bool,
    pub completed: bool,
    #[serde(default)]
    pub tracked_seconds: u64,
    pub created_at_unix: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Workspace {
    pub id: i64,
    pub name: String,
    pub path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Vault {
    pub id: i64,
    pub name: String,
    pub root_path: String,
    pub created_at_unix: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Note {
    pub id: i64,
    pub vault_id: i64,
    pub title: String,
    pub slug: String,
    pub path: String,
    pub created_at_unix: i64,
    pub updated_at_unix: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct WorkItem {
    pub id: i64,
    pub task_id: i64,
    pub note_id: i64,
    pub run_state: RunState,
    #[serde(default)]
    pub pomodoro_session_id: Option<i64>,
    #[serde(default)]
    pub started_at_unix: Option<i64>,
    #[serde(default)]
    pub stopped_at_unix: Option<i64>,
    #[serde(default)]
    pub elapsed_seconds: u64,
    pub created_at_unix: i64,
    pub updated_at_unix: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Link {
    pub id: i64,
    pub source_note_id: i64,
    pub target_note_id: i64,
    pub relationship: String,
    pub created_at_unix: i64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MindSelection {
    Vault { vault_id: i64 },
    Note { note_id: i64 },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum MindDraftMode {
    Creating { vault_id: i64 },
    Editing { note_id: i64 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MindDraftFocus {
    Path,
    Document,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MindDraft {
    pub mode: MindDraftMode,
    pub focus: MindDraftFocus,
    pub document: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum MindTreeEntry {
    Vault { vault_id: i64 },
    Note { vault_id: i64, note_id: i64 },
}

impl MindTreeEntry {
    fn selection(self) -> MindSelection {
        match self {
            MindTreeEntry::Vault { vault_id } => MindSelection::Vault { vault_id },
            MindTreeEntry::Note { note_id, .. } => MindSelection::Note { note_id },
        }
    }

    fn matches(self, selection: MindSelection) -> bool {
        match (self, selection) {
            (MindTreeEntry::Vault { vault_id: left }, MindSelection::Vault { vault_id: right }) => {
                left == right
            }
            (MindTreeEntry::Note { note_id: left, .. }, MindSelection::Note { note_id: right }) => {
                left == right
            }
            _ => false,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Theme {
    Ember,
    Slate,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum PomodoroPhase {
    Work,
    Break,
}

#[derive(Debug, Clone)]
pub struct PomodoroState {
    pub phase: PomodoroPhase,
    pub running: bool,
    pub remaining_seconds: u32,
    pub elapsed_seconds: u32,
    pub work_seconds: u32,
    pub break_seconds: u32,
    pub completed_sessions: u32,
    pub task_id: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct PomodoroSession {
    pub id: i64,
    pub task_id: Option<i64>,
    pub phase: PomodoroPhase,
    pub started_at_unix: i64,
    pub stopped_at_unix: i64,
    pub elapsed_seconds: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TaskInputMode {
    Creating,
    Editing { task_id: i64 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TaskInputField {
    Title,
    Description,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WorkspaceInputMode {
    Creating,
    Editing { workspace_id: i64 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorkspaceInputField {
    Name,
    Path,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LauncherTarget {
    OpenTaskInput,
    EditTask,
    ToggleTask,
    ToggleTaskDoing,
    DeleteTask,
    AttachPomodoroTask,
    ClearPomodoroTask,
    OpenMindDraft,
    EditMindDraft,
    DeleteMindDraft,
    OpenWorkspaceInput,
    EditWorkspace,
    DeleteWorkspace,
    SelectOrCreateRunWorkItem,
    StartRunWorkItem,
    PauseRunWorkItem,
    StopRunWorkItem,
    DeleteRunWorkItem,
    ToggleTheme,
    TogglePomodoro,
    ResetPomodoro,
    Quit,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LauncherEntry {
    pub label: String,
    pub hint: String,
    pub target: LauncherTarget,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LauncherState {
    pub screen: Screen,
    pub query: String,
    pub selected: usize,
}

#[derive(Debug, Clone)]
pub struct AppState {
    pub active_screen: Screen,
    pub vaults: Vec<Vault>,
    pub selected_vault: Option<usize>,
    pub notes: Vec<Note>,
    pub selected_note: Option<usize>,
    pub work_items: Vec<WorkItem>,
    pub selected_work_item: Option<usize>,
    pub mind_selection: Option<MindSelection>,
    pub mind_expanded_vaults: BTreeSet<i64>,
    pub mind_path_selection: Option<String>,
    pub mind_path_expanded: BTreeSet<String>,
    pub mind_document: Option<String>,
    pub tasks: Vec<Task>,
    pub notifications: Vec<String>,
    pub workspaces: Vec<Workspace>,
    pub selected_task: Option<usize>,
    pub selected_workspace: Option<usize>,
    pub theme: Theme,
    pub status_line: String,
    pub pomodoro: PomodoroState,
    pub pomodoro_sessions: Vec<PomodoroSession>,
    pub selected_pomodoro_session: Option<usize>,
    pub mind_draft: Option<MindDraft>,
    pub workspace_input_mode: Option<WorkspaceInputMode>,
    pub workspace_input_focus: WorkspaceInputField,
    pub workspace_input_name: String,
    pub workspace_input_path: String,
    pub task_input_mode: Option<TaskInputMode>,
    pub task_input_focus: TaskInputField,
    pub task_input_title: String,
    pub task_input_description: String,
    pub launcher: Option<LauncherState>,
    pub should_quit: bool,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            active_screen: Screen::Dashboard,
            vaults: Vec::new(),
            selected_vault: None,
            notes: Vec::new(),
            selected_note: None,
            work_items: Vec::new(),
            selected_work_item: None,
            mind_selection: None,
            mind_expanded_vaults: BTreeSet::new(),
            mind_path_selection: None,
            mind_path_expanded: BTreeSet::new(),
            mind_document: None,
            tasks: Vec::new(),
            notifications: Vec::new(),
            workspaces: vec![Workspace {
                id: 1,
                name: "Default".to_string(),
                path: ".".to_string(),
            }],
            selected_task: None,
            selected_workspace: Some(0),
            theme: Theme::Ember,
            status_line: "Ready".to_string(),
            pomodoro: PomodoroState {
                phase: PomodoroPhase::Work,
                running: false,
                remaining_seconds: 25 * 60,
                elapsed_seconds: 0,
                work_seconds: 25 * 60,
                break_seconds: 5 * 60,
                completed_sessions: 0,
                task_id: None,
            },
            pomodoro_sessions: Vec::new(),
            selected_pomodoro_session: None,
            mind_draft: None,
            workspace_input_mode: None,
            workspace_input_focus: WorkspaceInputField::Name,
            workspace_input_name: String::new(),
            workspace_input_path: String::new(),
            task_input_mode: None,
            task_input_focus: TaskInputField::Title,
            task_input_title: String::new(),
            task_input_description: String::new(),
            launcher: None,
            should_quit: false,
        }
    }

    pub fn apply(&mut self, event: AppEvent) -> Vec<AppAction> {
        match event {
            AppEvent::Started => vec![
                AppAction::LoadVaults,
                AppAction::LoadAllNotes,
                AppAction::LoadTasks,
                AppAction::LoadPomodoroSessions,
                AppAction::LoadWorkItems,
                AppAction::LoadWorkspaces,
            ],
            AppEvent::Tick => self.tick_pomodoro(),
            AppEvent::Resize => vec![AppAction::None],
            AppEvent::Key(key) => self.handle_key(key),
            AppEvent::VaultsLoaded(vaults) => {
                self.vaults = vaults;
                self.sync_vault_selection();
                self.expand_all_vaults();
                self.sync_mind_selection();
                self.status_line = format!("loaded {} vaults", self.vaults.len());
                vec![AppAction::None]
            }
            AppEvent::NotesLoaded(notes) => {
                self.notes = notes;
                self.sort_notes();
                self.sync_mind_selection();
                self.mind_document = None;
                self.status_line = format!("loaded {} notes", self.notes.len());
                self.selected_mind_note_path()
                    .map(|path| vec![AppAction::LoadNoteDocument { path }])
                    .unwrap_or_else(|| vec![AppAction::None])
            }
            AppEvent::NoteDocumentLoaded { note_id, document } => {
                if self.selected_mind_note_id() == Some(note_id) {
                    self.mind_document = Some(document);
                }
                vec![AppAction::None]
            }
            AppEvent::TasksLoaded(tasks) => {
                self.tasks = tasks;
                self.sync_selection();
                self.sync_pomodoro_task_reference();
                self.status_line = format!("loaded {} tasks", self.tasks.len());
                vec![AppAction::None]
            }
            AppEvent::PomodoroSessionsLoaded(sessions) => {
                self.pomodoro_sessions = sessions;
                self.sync_pomodoro_session_selection();
                self.status_line =
                    format!("loaded {} pomodoro sessions", self.pomodoro_sessions.len());
                vec![AppAction::None]
            }
            AppEvent::WorkItemsLoaded(work_items) => {
                self.work_items = work_items;
                self.sort_work_items();
                self.sync_work_item_selection();
                self.status_line = format!("loaded {} work items", self.work_items.len());
                vec![AppAction::None]
            }
            AppEvent::WorkItemCreated(work_item) => {
                let work_item_id = work_item.id;
                self.work_items.push(work_item);
                self.sort_work_items();
                self.selected_work_item =
                    self.work_items.iter().position(|item| item.id == work_item_id);
                self.status_line = "work item created".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkItemUpdated(work_item) => {
                if let Some(existing) = self
                    .work_items
                    .iter_mut()
                    .find(|current| current.id == work_item.id)
                {
                    *existing = work_item;
                }
                self.sort_work_items();
                self.sync_work_item_selection();
                self.status_line = "work item updated".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkItemDeleted(work_item_id) => {
                self.work_items.retain(|item| item.id != work_item_id);
                self.sync_work_item_selection();
                self.status_line = "work item deleted".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkspacesLoaded(workspaces) => {
                self.workspaces = workspaces;
                self.sync_workspace_selection();
                self.status_line = format!("loaded {} workspaces", self.workspaces.len());
                vec![AppAction::None]
            }
            AppEvent::NoteCreated(note) => {
                let note_id = note.id;
                self.notes.push(note);
                self.sort_notes();
                self.mind_selection = Some(MindSelection::Note { note_id });
                self.sync_mind_selection();
                self.clear_mind_draft();
                self.status_line = "note created".to_string();
                self.sync_mind_document_action()
            }
            AppEvent::NoteUpdated(note) => {
                let note_id = note.id;
                if let Some(existing) = self.notes.iter_mut().find(|current| current.id == note.id)
                {
                    *existing = note;
                }
                self.sort_notes();
                self.mind_selection = Some(MindSelection::Note { note_id });
                self.sync_mind_selection();
                self.clear_mind_draft();
                self.status_line = "note updated".to_string();
                self.sync_mind_document_action()
            }
            AppEvent::NoteDeleted(note_id) => {
                self.notes.retain(|note| note.id != note_id);
                self.sync_mind_selection();
                self.clear_mind_draft();
                if self.selected_mind_note_id().is_none() {
                    self.mind_document = None;
                }
                self.status_line = "note deleted".to_string();
                self.sync_mind_document_action()
            }
            AppEvent::TaskCreated(task) => {
                self.tasks.push(task);
                self.sync_selection();
                self.clear_task_input();
                self.status_line = "task created".to_string();
                vec![AppAction::None]
            }
            AppEvent::TaskUpdated(task) => {
                if let Some(existing) = self.tasks.iter_mut().find(|current| current.id == task.id)
                {
                    *existing = task;
                }
                self.clear_task_input();
                self.status_line = "task updated".to_string();
                vec![AppAction::None]
            }
            AppEvent::TaskDeleted(task_id) => {
                self.tasks.retain(|task| task.id != task_id);
                self.sync_selection();
                if self.pomodoro.task_id == Some(task_id) {
                    self.pomodoro.task_id = None;
                }
                self.clear_task_input();
                self.status_line = "task deleted".to_string();
                vec![AppAction::None]
            }
            AppEvent::PomodoroSessionCreated(session) => {
                self.pomodoro_sessions.push(session);
                self.sort_pomodoro_sessions();
                self.sync_pomodoro_session_selection();
                self.status_line = "pomodoro saved".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkspaceCreated(workspace) => {
                self.workspaces.push(workspace);
                self.sync_workspace_selection();
                self.clear_workspace_input();
                self.status_line = "workspace created".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkspaceUpdated(workspace) => {
                if let Some(existing) = self
                    .workspaces
                    .iter_mut()
                    .find(|current| current.id == workspace.id)
                {
                    *existing = workspace;
                }
                self.clear_workspace_input();
                self.status_line = "workspace updated".to_string();
                vec![AppAction::None]
            }
            AppEvent::WorkspaceDeleted(workspace_id) => {
                self.workspaces
                    .retain(|workspace| workspace.id != workspace_id);
                self.sync_workspace_selection();
                self.clear_workspace_input();
                self.status_line = "workspace deleted".to_string();
                vec![AppAction::None]
            }
            AppEvent::Message(message) => {
                self.status_line = message;
                vec![AppAction::None]
            }
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        if self.launcher.is_some() {
            return self.handle_launcher_key(key);
        }

        if self.mind_draft.is_some() {
            return self.handle_mind_draft_key(key);
        }

        if self.workspace_input_mode.is_some() {
            return self.handle_workspace_input_key(key);
        }

        if self.task_input_mode.is_some() {
            return self.handle_task_input_key(key);
        }

        if matches!(
            key.code,
            KeyCode::Char('h') if key.modifiers.contains(KeyModifiers::CONTROL)
        ) {
            self.active_screen = previous_screen(self.active_screen);
            self.status_line = format!("switched to {}", screen_label(self.active_screen));
            return vec![AppAction::None];
        }

        if matches!(
            key.code,
            KeyCode::Char('l') if key.modifiers.contains(KeyModifiers::CONTROL)
        ) {
            self.active_screen = next_screen(self.active_screen);
            self.status_line = format!("switched to {}", screen_label(self.active_screen));
            return vec![AppAction::None];
        }

        match key.code {
            KeyCode::Char('q') => {
                self.should_quit = true;
                vec![AppAction::ShowMessage("quitting".to_string())]
            }
            KeyCode::Char(':') => {
                self.open_launcher();
                vec![AppAction::None]
            }
            KeyCode::BackTab => {
                self.active_screen = previous_screen(self.active_screen);
                self.status_line = format!("switched to {}", screen_label(self.active_screen));
                vec![AppAction::None]
            }
            KeyCode::Up | KeyCode::Char('k') => {
                match self.active_screen {
                    Screen::Mind => self.move_mind_selection(-1),
                    Screen::Pomodoro => self.move_pomodoro_session_selection(-1),
                    Screen::Run => self.move_work_item_selection(-1),
                    _ => self.move_task_selection(-1),
                }
                if self.active_screen == Screen::Mind {
                    self.sync_mind_document_action()
                } else {
                    vec![AppAction::None]
                }
            }
            KeyCode::Down | KeyCode::Char('j') => {
                match self.active_screen {
                    Screen::Tasks => self.move_task_selection(1),
                    Screen::Mind => self.move_mind_selection(1),
                    Screen::Pomodoro => self.move_pomodoro_session_selection(1),
                    Screen::Run => self.move_work_item_selection(1),
                    Screen::Workspaces | Screen::Notifications | Screen::Dashboard => {
                        self.move_task_selection(1)
                    }
                }
                if self.active_screen == Screen::Mind {
                    self.sync_mind_document_action()
                } else {
                    vec![AppAction::None]
                }
            }
            KeyCode::Char(' ') if self.active_screen == Screen::Tasks => self
                .selected_task_id()
                .map(|task_id| vec![AppAction::ToggleTask { task_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
            KeyCode::Char('a') if self.active_screen == Screen::Mind => self.begin_mind_note(),
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Mind => {
                self.open_selected_mind_item()
            }
            KeyCode::Char('d') if self.active_screen == Screen::Mind => {
                self.delete_selected_mind_note()
            }
            KeyCode::Left | KeyCode::Char('h') if self.active_screen == Screen::Mind => {
                self.collapse_mind_selection();
                vec![AppAction::None]
            }
            KeyCode::Right | KeyCode::Char('l') if self.active_screen == Screen::Mind => {
                self.expand_mind_selection();
                vec![AppAction::None]
            }
            KeyCode::Char('a') if self.active_screen == Screen::Tasks => self.begin_task_create(),
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Tasks => {
                self.begin_task_edit()
            }
            KeyCode::Char('d') if self.active_screen == Screen::Tasks => {
                self.delete_selected_task()
            }
            KeyCode::Char('m') if self.active_screen == Screen::Tasks => {
                self.toggle_selected_task_doing()
            }
            KeyCode::Char('t') if self.active_screen != Screen::Pomodoro => {
                self.theme = match self.theme {
                    Theme::Ember => Theme::Slate,
                    Theme::Slate => Theme::Ember,
                };
                self.status_line = "theme switched".to_string();
                vec![AppAction::None]
            }
            KeyCode::Char('p') if self.active_screen == Screen::Pomodoro => self.toggle_pomodoro(),
            KeyCode::Char('s') if self.active_screen == Screen::Pomodoro => self.stop_pomodoro(),
            KeyCode::Char('t') if self.active_screen == Screen::Pomodoro => {
                self.attach_selected_task_to_pomodoro()
            }
            KeyCode::Char('c') if self.active_screen == Screen::Pomodoro => {
                self.clear_pomodoro_task()
            }
            KeyCode::Char('a') if self.active_screen == Screen::Workspaces => {
                self.begin_workspace_create()
            }
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Workspaces => {
                self.begin_workspace_edit()
            }
            KeyCode::Char('d') if self.active_screen == Screen::Workspaces => {
                self.delete_selected_workspace()
            }
            KeyCode::Char(' ') if self.active_screen == Screen::Workspaces => vec![AppAction::None],
            KeyCode::Char('a') if self.active_screen == Screen::Run => {
                self.select_or_create_run_work_item()
            }
            KeyCode::Char('r') if self.active_screen == Screen::Run => {
                self.start_selected_run_work_item()
            }
            KeyCode::Char('p') if self.active_screen == Screen::Run => {
                self.pause_selected_run_work_item()
            }
            KeyCode::Char('s') if self.active_screen == Screen::Run => {
                self.stop_selected_run_work_item()
            }
            KeyCode::Char('d') if self.active_screen == Screen::Run => {
                self.delete_selected_run_work_item()
            }
            KeyCode::Char('p') => self.toggle_pomodoro(),
            KeyCode::Char('r') => self.reset_pomodoro(),
            _ => vec![AppAction::None],
        }
    }

    fn handle_launcher_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        let Some(current_launcher) = self.launcher.clone() else {
            return vec![AppAction::None];
        };

        match key.code {
            KeyCode::Esc => {
                self.close_launcher();
                self.status_line = "launcher closed".to_string();
                vec![AppAction::None]
            }
            KeyCode::Enter => {
                let entries = self.filtered_launcher_entries();
                let action = entries
                    .get(current_launcher.selected)
                    .map(|entry| entry.target);
                self.close_launcher();
                match action {
                    Some(LauncherTarget::OpenTaskInput) => {
                        self.begin_task_create();
                    }
                    Some(LauncherTarget::EditTask) => {
                        self.begin_task_edit();
                    }
                    Some(LauncherTarget::ToggleTask) => {
                        if let Some(task_id) = self.selected_task_id() {
                            return vec![AppAction::ToggleTask { task_id }];
                        }
                        self.status_line = "select a task first".to_string();
                    }
                    Some(LauncherTarget::ToggleTaskDoing) => {
                        return self.toggle_selected_task_doing();
                    }
                    Some(LauncherTarget::DeleteTask) => {
                        return self.delete_selected_task();
                    }
                    Some(LauncherTarget::AttachPomodoroTask) => {
                        return self.attach_selected_task_to_pomodoro();
                    }
                    Some(LauncherTarget::ClearPomodoroTask) => {
                        return self.clear_pomodoro_task();
                    }
                    Some(LauncherTarget::OpenMindDraft) => {
                        self.begin_mind_note();
                    }
                    Some(LauncherTarget::EditMindDraft) => {
                        return self.open_selected_mind_item();
                    }
                    Some(LauncherTarget::DeleteMindDraft) => {
                        return self.delete_selected_mind_note();
                    }
                    Some(LauncherTarget::OpenWorkspaceInput) => {
                        self.begin_workspace_create();
                    }
                    Some(LauncherTarget::EditWorkspace) => {
                        self.begin_workspace_edit();
                    }
                    Some(LauncherTarget::DeleteWorkspace) => {
                        return self.delete_selected_workspace();
                    }
                    Some(LauncherTarget::SelectOrCreateRunWorkItem) => {
                        return self.select_or_create_run_work_item();
                    }
                    Some(LauncherTarget::StartRunWorkItem) => {
                        return self.start_selected_run_work_item();
                    }
                    Some(LauncherTarget::PauseRunWorkItem) => {
                        return self.pause_selected_run_work_item();
                    }
                    Some(LauncherTarget::StopRunWorkItem) => {
                        return self.stop_selected_run_work_item();
                    }
                    Some(LauncherTarget::DeleteRunWorkItem) => {
                        return self.delete_selected_run_work_item();
                    }
                    Some(LauncherTarget::ToggleTheme) => {
                        self.theme = match self.theme {
                            Theme::Ember => Theme::Slate,
                            Theme::Slate => Theme::Ember,
                        };
                        self.status_line = "theme switched".to_string();
                    }
                    Some(LauncherTarget::TogglePomodoro) => {
                        return self.toggle_pomodoro();
                    }
                    Some(LauncherTarget::ResetPomodoro) => {
                        return self.reset_pomodoro();
                    }
                    Some(LauncherTarget::Quit) => {
                        self.should_quit = true;
                        self.status_line = "quitting".to_string();
                        return vec![AppAction::ShowMessage("quitting".to_string())];
                    }
                    None => {
                        self.status_line = "launcher empty".to_string();
                    }
                }
                vec![AppAction::None]
            }
            KeyCode::Up | KeyCode::Char('k') => {
                if let Some(launcher) = self.launcher.as_mut() {
                    launcher.selected = launcher.selected.saturating_sub(1);
                }
                vec![AppAction::None]
            }
            KeyCode::Down | KeyCode::Char('j') => {
                let len = self.filtered_launcher_entries().len();
                if let Some(launcher) = self.launcher.as_mut() {
                    if len > 0 {
                        launcher.selected = (launcher.selected + 1).min(len - 1);
                    }
                }
                vec![AppAction::None]
            }
            KeyCode::Backspace => {
                if let Some(launcher) = self.launcher.as_mut() {
                    launcher.query.pop();
                    launcher.selected = 0;
                }
                vec![AppAction::None]
            }
            KeyCode::Char(c) => {
                if let Some(launcher) = self.launcher.as_mut() {
                    launcher.query.push(c);
                    launcher.selected = 0;
                }
                vec![AppAction::None]
            }
            _ => vec![AppAction::None],
        }
    }

    fn handle_task_input_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        match key.code {
            KeyCode::Esc => {
                self.clear_task_input();
                self.status_line = "task edit cancelled".to_string();
                vec![AppAction::None]
            }
            KeyCode::Enter => match self.task_input_focus {
                TaskInputField::Title => {
                    self.task_input_focus = TaskInputField::Description;
                    vec![AppAction::None]
                }
                TaskInputField::Description => {
                    let title = self.task_input_title.trim().to_string();
                    if title.is_empty() {
                        self.status_line = "task title cannot be empty".to_string();
                        return vec![AppAction::None];
                    }

                    let description = self.task_input_description.trim().to_string();
                    match self.task_input_mode.clone() {
                        Some(TaskInputMode::Creating) => {
                            vec![AppAction::CreateTask { title, description }]
                        }
                        Some(TaskInputMode::Editing { task_id }) => {
                            vec![AppAction::UpdateTask {
                                task_id,
                                title,
                                description,
                            }]
                        }
                        None => vec![AppAction::None],
                    }
                }
            },
            KeyCode::Tab | KeyCode::Down => {
                self.task_input_focus = match self.task_input_focus {
                    TaskInputField::Title => TaskInputField::Description,
                    TaskInputField::Description => TaskInputField::Title,
                };
                vec![AppAction::None]
            }
            KeyCode::BackTab | KeyCode::Up => {
                self.task_input_focus = match self.task_input_focus {
                    TaskInputField::Title => TaskInputField::Description,
                    TaskInputField::Description => TaskInputField::Title,
                };
                vec![AppAction::None]
            }
            KeyCode::Backspace => {
                self.delete_task_input_char();
                vec![AppAction::None]
            }
            KeyCode::Char('h') if key.modifiers.contains(KeyModifiers::CONTROL) => {
                self.delete_task_input_char();
                vec![AppAction::None]
            }
            KeyCode::Char(c) => {
                self.push_task_input_char(c);
                vec![AppAction::None]
            }
            _ => vec![AppAction::None],
        }
    }

    fn handle_workspace_input_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        match key.code {
            KeyCode::Esc => {
                self.clear_workspace_input();
                self.status_line = "workspace edit cancelled".to_string();
                vec![AppAction::None]
            }
            KeyCode::Enter => match self.workspace_input_focus {
                WorkspaceInputField::Name => {
                    self.workspace_input_focus = WorkspaceInputField::Path;
                    vec![AppAction::None]
                }
                WorkspaceInputField::Path => {
                    let name = self.workspace_input_name.trim().to_string();
                    if name.is_empty() {
                        self.status_line = "workspace name cannot be empty".to_string();
                        return vec![AppAction::None];
                    }

                    let path = self.workspace_input_path.trim().to_string();
                    match self.workspace_input_mode.clone() {
                        Some(WorkspaceInputMode::Creating) => {
                            vec![AppAction::CreateWorkspace { name, path }]
                        }
                        Some(WorkspaceInputMode::Editing { workspace_id }) => {
                            vec![AppAction::UpdateWorkspace {
                                workspace_id,
                                name,
                                path,
                            }]
                        }
                        None => vec![AppAction::None],
                    }
                }
            },
            KeyCode::Tab | KeyCode::Down => {
                self.workspace_input_focus = match self.workspace_input_focus {
                    WorkspaceInputField::Name => WorkspaceInputField::Path,
                    WorkspaceInputField::Path => WorkspaceInputField::Name,
                };
                vec![AppAction::None]
            }
            KeyCode::BackTab | KeyCode::Up => {
                self.workspace_input_focus = match self.workspace_input_focus {
                    WorkspaceInputField::Name => WorkspaceInputField::Path,
                    WorkspaceInputField::Path => WorkspaceInputField::Name,
                };
                vec![AppAction::None]
            }
            KeyCode::Backspace => {
                self.delete_workspace_input_char();
                vec![AppAction::None]
            }
            KeyCode::Char('h') if key.modifiers.contains(KeyModifiers::CONTROL) => {
                self.delete_workspace_input_char();
                vec![AppAction::None]
            }
            KeyCode::Char(c) => {
                self.push_workspace_input_char(c);
                vec![AppAction::None]
            }
            _ => vec![AppAction::None],
        }
    }

    fn move_task_selection(&mut self, offset: isize) {
        if self.tasks.is_empty() {
            self.selected_task = None;
            return;
        }

        let current = self.selected_task.unwrap_or(0) as isize;
        let next = (current + offset).clamp(0, self.tasks.len().saturating_sub(1) as isize);
        self.selected_task = Some(next as usize);
    }

    fn selected_task_id(&self) -> Option<i64> {
        self.selected_task
            .and_then(|index| self.tasks.get(index))
            .map(|task| task.id)
    }

    fn selected_doing_task_id(&self) -> Option<i64> {
        self.selected_task
            .and_then(|index| self.tasks.get(index))
            .and_then(|task| task.doing.then_some(task.id))
            .or_else(|| {
                self.tasks
                    .iter()
                    .find(|task| task.doing)
                    .map(|task| task.id)
            })
    }

    fn sync_pomodoro_task_reference(&mut self) {
        if let Some(task_id) = self.pomodoro.task_id
            && !self.tasks.iter().any(|task| task.id == task_id)
        {
            self.pomodoro.task_id = None;
        }
    }

    fn selected_workspace_id(&self) -> Option<i64> {
        self.selected_workspace
            .and_then(|index| self.workspaces.get(index))
            .map(|workspace| workspace.id)
    }

    fn move_pomodoro_session_selection(&mut self, offset: isize) {
        if self.pomodoro_sessions.is_empty() {
            self.selected_pomodoro_session = None;
            return;
        }

        let current = self.selected_pomodoro_session.unwrap_or(0) as isize;
        let next =
            (current + offset).clamp(0, self.pomodoro_sessions.len().saturating_sub(1) as isize);
        self.selected_pomodoro_session = Some(next as usize);
    }

    fn selected_vault_id(&self) -> Option<i64> {
        self.selected_vault
            .and_then(|index| self.vaults.get(index))
            .map(|vault| vault.id)
    }

    fn selected_mind_entry(&self) -> Option<MindTreeEntry> {
        self.mind_selection
            .or_else(|| {
                self.mind_entries()
                    .into_iter()
                    .next()
                    .map(|entry| match entry {
                        MindTreeEntry::Vault { vault_id } => MindSelection::Vault { vault_id },
                        MindTreeEntry::Note { note_id, .. } => MindSelection::Note { note_id },
                    })
            })
            .and_then(|selection| self.resolve_mind_selection(selection))
    }

    fn selected_mind_vault_id(&self) -> Option<i64> {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Vault { vault_id }) => Some(vault_id),
            Some(MindTreeEntry::Note { vault_id, .. }) => Some(vault_id),
            None => self.selected_vault_id(),
        }
    }

    fn selected_mind_note_id(&self) -> Option<i64> {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Note { note_id, .. }) => Some(note_id),
            _ => None,
        }
    }

    fn selected_mind_note_path(&self) -> Option<String> {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Note { note_id, .. }) => self
                .notes
                .iter()
                .find(|note| note.id == note_id)
                .map(|note| note.path.clone()),
            _ => None,
        }
    }

    fn mind_entries(&self) -> Vec<MindTreeEntry> {
        let mut entries = Vec::with_capacity(self.vaults.len().saturating_mul(2));

        for vault in &self.vaults {
            entries.push(MindTreeEntry::Vault { vault_id: vault.id });

            if !self.mind_expanded_vaults.contains(&vault.id) {
                continue;
            }

            entries.extend(
                self.notes
                    .iter()
                    .filter(|note| note.vault_id == vault.id)
                    .map(|note| MindTreeEntry::Note {
                        vault_id: vault.id,
                        note_id: note.id,
                    }),
            );
        }

        entries
    }

    fn resolve_mind_selection(&self, selection: MindSelection) -> Option<MindTreeEntry> {
        match selection {
            MindSelection::Vault { vault_id } => self
                .vaults
                .iter()
                .any(|vault| vault.id == vault_id)
                .then_some(MindTreeEntry::Vault { vault_id }),
            MindSelection::Note { note_id } => self.notes.iter().find_map(|note| {
                (note.id == note_id).then_some(MindTreeEntry::Note {
                    vault_id: note.vault_id,
                    note_id,
                })
            }),
        }
    }

    fn sync_vault_selection(&mut self) {
        if self.vaults.is_empty() {
            self.selected_vault = None;
        } else {
            self.selected_vault = Some(self.selected_vault.unwrap_or(0).min(self.vaults.len() - 1));
        }
    }

    fn sync_selection(&mut self) {
        if self.tasks.is_empty() {
            self.selected_task = None;
        } else {
            self.selected_task = Some(self.selected_task.unwrap_or(0).min(self.tasks.len() - 1));
        }
    }

    fn sync_workspace_selection(&mut self) {
        if self.workspaces.is_empty() {
            self.selected_workspace = None;
        } else {
            self.selected_workspace = Some(
                self.selected_workspace
                    .unwrap_or(0)
                    .min(self.workspaces.len() - 1),
            );
        }
    }

    fn sync_pomodoro_session_selection(&mut self) {
        if self.pomodoro_sessions.is_empty() {
            self.selected_pomodoro_session = None;
        } else {
            self.selected_pomodoro_session = Some(
                self.selected_pomodoro_session
                    .unwrap_or(0)
                    .min(self.pomodoro_sessions.len() - 1),
            );
        }
    }

    fn sync_work_item_selection(&mut self) {
        if self.work_items.is_empty() {
            self.selected_work_item = None;
        } else {
            self.selected_work_item = self
                .selected_work_item_id()
                .and_then(|selected_id| {
                    self.work_items
                        .iter()
                        .position(|item| item.id == selected_id)
                })
                .or_else(|| {
                    Some(
                        self.selected_work_item
                            .unwrap_or(0)
                            .min(self.work_items.len() - 1),
                    )
                });
        }
    }

    fn move_work_item_selection(&mut self, offset: isize) {
        if self.work_items.is_empty() {
            self.selected_work_item = None;
            return;
        }

        let current = self.selected_work_item.unwrap_or(0) as isize;
        let next = (current + offset).clamp(0, self.work_items.len().saturating_sub(1) as isize);
        self.selected_work_item = Some(next as usize);
    }

    fn sort_work_items(&mut self) {
        self.work_items.sort_by(|left, right| {
            right
                .updated_at_unix
                .cmp(&left.updated_at_unix)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
    }

    fn selected_work_item_id(&self) -> Option<i64> {
        self.selected_work_item
            .and_then(|index| self.work_items.get(index))
            .map(|item| item.id)
    }

    fn selected_run_context(&self) -> Option<(i64, i64)> {
        Some((self.selected_task_id()?, self.selected_mind_note_id()?))
    }

    fn select_or_create_run_work_item(&mut self) -> Vec<AppAction> {
        let Some((task_id, note_id)) = self.selected_run_context() else {
            self.status_line = "select a task and note first".to_string();
            return vec![AppAction::None];
        };

        if let Some(index) = self
            .work_items
            .iter()
            .position(|item| item.task_id == task_id && item.note_id == note_id)
        {
            self.selected_work_item = Some(index);
            self.status_line = "run work item selected".to_string();
            return vec![AppAction::None];
        }

        self.status_line = "creating work item for selected task and note".to_string();
        vec![AppAction::CreateWorkItem { task_id, note_id }]
    }

    fn start_selected_run_work_item(&mut self) -> Vec<AppAction> {
        let Some(index) = self.selected_work_item else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        let Some(work_item) = self.work_items.get(index).cloned() else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        if work_item.run_state == RunState::Running {
            self.status_line = "work item already running".to_string();
            return vec![AppAction::None];
        }

        self.pomodoro.task_id = Some(work_item.task_id);
        self.pomodoro.running = true;
        self.status_line = format!(
            "running {} on {}",
            work_item.task_id,
            self.notes
                .iter()
                .find(|note| note.id == work_item.note_id)
                .map(|note| note.title.clone())
                .unwrap_or_else(|| "note".to_string())
        );

        vec![
            AppAction::SetTaskDoing {
                task_id: work_item.task_id,
                doing: true,
            },
            AppAction::UpdateWorkItem {
                work_item_id: work_item.id,
                task_id: work_item.task_id,
                note_id: work_item.note_id,
                run_state: RunState::Running,
                pomodoro_session_id: work_item.pomodoro_session_id,
                started_at_unix: work_item.started_at_unix.or(Some(current_unix_timestamp())),
                stopped_at_unix: None,
                elapsed_seconds: work_item.elapsed_seconds,
            },
        ]
    }

    fn pause_selected_run_work_item(&mut self) -> Vec<AppAction> {
        let Some(index) = self.selected_work_item else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        let Some(work_item) = self.work_items.get(index).cloned() else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        if work_item.run_state != RunState::Running {
            self.status_line = "work item already paused".to_string();
            return vec![AppAction::None];
        }

        self.pomodoro.running = false;
        self.status_line = "run paused".to_string();

        vec![AppAction::UpdateWorkItem {
            work_item_id: work_item.id,
            task_id: work_item.task_id,
            note_id: work_item.note_id,
            run_state: RunState::Paused,
            pomodoro_session_id: work_item.pomodoro_session_id,
            started_at_unix: work_item.started_at_unix,
            stopped_at_unix: None,
            elapsed_seconds: u64::from(self.pomodoro.elapsed_seconds),
        }]
    }

    fn stop_selected_run_work_item(&mut self) -> Vec<AppAction> {
        let Some(index) = self.selected_work_item else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        let Some(work_item) = self.work_items.get(index).cloned() else {
            self.status_line = "select a work item first".to_string();
            return vec![AppAction::None];
        };

        let elapsed_seconds = u64::from(self.pomodoro.elapsed_seconds).max(work_item.elapsed_seconds);
        let mut actions = self.stop_pomodoro();
        let started_at_unix = work_item.started_at_unix.or_else(|| {
            let elapsed_seconds = elapsed_seconds.min(i64::MAX as u64) as i64;
            Some(current_unix_timestamp().saturating_sub(elapsed_seconds))
        });
        actions.push(AppAction::UpdateWorkItem {
            work_item_id: work_item.id,
            task_id: work_item.task_id,
            note_id: work_item.note_id,
            run_state: RunState::Stopped,
            pomodoro_session_id: work_item.pomodoro_session_id,
            started_at_unix,
            stopped_at_unix: Some(current_unix_timestamp()),
            elapsed_seconds,
        });
        actions
    }

    fn delete_selected_run_work_item(&mut self) -> Vec<AppAction> {
        self.selected_work_item_id()
            .map(|work_item_id| vec![AppAction::DeleteWorkItem { work_item_id }])
            .unwrap_or_else(|| {
                self.status_line = "select a work item first".to_string();
                vec![AppAction::None]
            })
    }

    fn sync_mind_selection(&mut self) {
        let entries = self.mind_entries();
        if entries.is_empty() {
            self.mind_selection = None;
            return;
        }

        if let Some(selection) = self.mind_selection {
            if self.resolve_mind_selection(selection).is_some() {
                return;
            }
        }

        self.mind_selection = entries.first().map(|entry| match entry {
            MindTreeEntry::Vault { vault_id } => MindSelection::Vault {
                vault_id: *vault_id,
            },
            MindTreeEntry::Note { note_id, .. } => MindSelection::Note { note_id: *note_id },
        });
    }

    fn sort_notes(&mut self) {
        self.notes.sort_by(|left, right| {
            right
                .updated_at_unix
                .cmp(&left.updated_at_unix)
                .then_with(|| right.created_at_unix.cmp(&left.created_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
    }

    fn sort_pomodoro_sessions(&mut self) {
        self.pomodoro_sessions.sort_by(|left, right| {
            right
                .stopped_at_unix
                .cmp(&left.stopped_at_unix)
                .then_with(|| right.started_at_unix.cmp(&left.started_at_unix))
                .then_with(|| right.id.cmp(&left.id))
        });
    }

    fn expand_all_vaults(&mut self) {
        self.mind_expanded_vaults
            .extend(self.vaults.iter().map(|vault| vault.id));
    }

    fn move_mind_selection(&mut self, offset: isize) {
        let entries = self.mind_entries();
        if entries.is_empty() {
            self.mind_selection = None;
            self.mind_document = None;
            return;
        }

        let current_index = self
            .mind_selection
            .and_then(|selection| entries.iter().position(|entry| entry.matches(selection)))
            .unwrap_or(0) as isize;
        let next = (current_index + offset).clamp(0, entries.len().saturating_sub(1) as isize);
        self.mind_selection = Some(entries[next as usize].selection());
    }

    fn sync_mind_document_action(&mut self) -> Vec<AppAction> {
        match self.selected_mind_note_path() {
            Some(path) => vec![AppAction::LoadNoteDocument { path }],
            None => {
                self.mind_document = None;
                vec![AppAction::None]
            }
        }
    }

    fn begin_task_create(&mut self) -> Vec<AppAction> {
        self.begin_task_input(TaskInputMode::Creating, String::new(), String::new());
        vec![AppAction::None]
    }

    fn begin_task_edit(&mut self) -> Vec<AppAction> {
        if let Some(task_id) = self.selected_task_id() {
            let (title, description) = self
                .selected_task
                .and_then(|index| self.tasks.get(index))
                .map(|task| (task.title.clone(), task.description.clone()))
                .unwrap_or_default();
            self.begin_task_input(TaskInputMode::Editing { task_id }, title, description);
        } else {
            self.status_line = "select a task first".to_string();
        }

        vec![AppAction::None]
    }

    fn delete_selected_task(&mut self) -> Vec<AppAction> {
        self.selected_task_id()
            .map(|task_id| vec![AppAction::DeleteTask { task_id }])
            .unwrap_or_else(|| {
                self.status_line = "select a task first".to_string();
                vec![AppAction::None]
            })
    }

    fn toggle_selected_task_doing(&mut self) -> Vec<AppAction> {
        let Some(task_id) = self.selected_task_id() else {
            self.status_line = "select a task first".to_string();
            return vec![AppAction::None];
        };

        let doing = self
            .selected_task
            .and_then(|index| self.tasks.get(index))
            .map(|task| !task.doing)
            .unwrap_or(true);

        vec![AppAction::SetTaskDoing { task_id, doing }]
    }

    fn attach_selected_task_to_pomodoro(&mut self) -> Vec<AppAction> {
        let Some(task_id) = self.selected_task_id() else {
            self.status_line = "select a task first".to_string();
            return vec![AppAction::None];
        };

        let mut actions = Vec::new();
        if let Some(previous_task_id) = self.pomodoro.task_id.filter(|current| *current != task_id)
        {
            actions.push(AppAction::SetTaskDoing {
                task_id: previous_task_id,
                doing: false,
            });
        }

        self.pomodoro.task_id = Some(task_id);
        actions.push(AppAction::SetTaskDoing {
            task_id,
            doing: true,
        });
        self.status_line = self
            .tasks
            .iter()
            .find(|task| task.id == task_id)
            .map(|task| format!("pomodoro attached to {}", task.title))
            .unwrap_or_else(|| "pomodoro task attached".to_string());

        actions
    }

    fn clear_pomodoro_task(&mut self) -> Vec<AppAction> {
        let Some(task_id) = self.pomodoro.task_id.take() else {
            self.status_line = "no pomodoro task attached".to_string();
            return vec![AppAction::None];
        };

        self.status_line = "pomodoro task cleared".to_string();
        vec![AppAction::SetTaskDoing {
            task_id,
            doing: false,
        }]
    }

    fn start_or_resume_pomodoro(&mut self) -> Vec<AppAction> {
        if self.pomodoro.running {
            self.status_line = "pomodoro already running".to_string();
            return vec![AppAction::None];
        }

        if self.pomodoro.task_id.is_none() {
            self.pomodoro.task_id = self.selected_doing_task_id();
        }

        self.pomodoro.running = true;
        self.status_line = if let Some(task_id) = self.pomodoro.task_id {
            let task_label = self
                .tasks
                .iter()
                .find(|task| task.id == task_id)
                .map(|task| task.title.clone())
                .unwrap_or_else(|| "task".to_string());
            format!("pomodoro running on {task_label}")
        } else {
            "pomodoro running".to_string()
        };
        vec![AppAction::None]
    }

    fn pause_pomodoro(&mut self) -> Vec<AppAction> {
        if !self.pomodoro.running {
            self.status_line = "pomodoro already paused".to_string();
            return vec![AppAction::None];
        }

        self.pomodoro.running = false;
        self.status_line = "pomodoro paused".to_string();
        vec![AppAction::None]
    }

    fn stop_pomodoro(&mut self) -> Vec<AppAction> {
        let elapsed_seconds = self.pomodoro.elapsed_seconds;
        let task_id = self.pomodoro.task_id;
        let phase = self.pomodoro.phase;

        self.pomodoro.running = false;
        self.pomodoro.phase = PomodoroPhase::Work;
        self.pomodoro.remaining_seconds = self.pomodoro.work_seconds;
        self.pomodoro.elapsed_seconds = 0;
        self.pomodoro.task_id = None;

        if elapsed_seconds == 0 {
            self.status_line = "pomodoro stopped".to_string();
            return vec![AppAction::None];
        }

        let stopped_at_unix = current_unix_timestamp();
        let started_at_unix = stopped_at_unix.saturating_sub(i64::from(elapsed_seconds));
        let mut actions = vec![AppAction::CreatePomodoroSession {
            session: PomodoroSession {
                id: 0,
                task_id,
                phase,
                started_at_unix,
                stopped_at_unix,
                elapsed_seconds,
            },
        }];

        if let Some(task_id) = task_id {
            actions.push(AppAction::AddTaskTrackedTime {
                task_id,
                tracked_seconds: u64::from(elapsed_seconds),
            });
            actions.push(AppAction::SetTaskDoing {
                task_id,
                doing: false,
            });
        }

        self.status_line = format!(
            "saved {} of pomodoro time",
            format_duration(elapsed_seconds)
        );
        actions
    }

    fn begin_mind_note(&mut self) -> Vec<AppAction> {
        if let Some(vault_id) = self.selected_mind_vault_id() {
            let vault_root = self
                .selected_mind_vault_root_path()
                .unwrap_or_else(|| ".".to_string());
            self.mind_path_selection = Some(vault_root.clone());
            self.mind_path_expanded.insert(vault_root.clone());
            self.begin_mind_draft(
                MindDraftMode::Creating { vault_id },
                "# Untitled\n\n".to_string(),
            );
        } else {
            self.status_line = "select a vault first".to_string();
        }

        vec![AppAction::None]
    }

    fn open_selected_mind_item(&mut self) -> Vec<AppAction> {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Vault { vault_id }) => {
                self.toggle_mind_vault(vault_id);
                vec![AppAction::None]
            }
            Some(MindTreeEntry::Note { note_id, .. }) => {
                if let Some(note) = self.notes.iter().find(|note| note.id == note_id) {
                    self.begin_mind_draft(
                        MindDraftMode::Editing { note_id },
                        self.mind_document
                            .clone()
                            .unwrap_or_else(|| markdown_note_document_from(note)),
                    );
                }
                self.sync_mind_document_action()
            }
            None => vec![AppAction::None],
        }
    }

    fn delete_selected_mind_note(&mut self) -> Vec<AppAction> {
        self.selected_mind_note_id()
            .map(|note_id| vec![AppAction::DeleteNote { note_id }])
            .unwrap_or_else(|| {
                self.status_line = "select a note to delete".to_string();
                vec![AppAction::None]
            })
    }

    fn begin_workspace_create(&mut self) -> Vec<AppAction> {
        self.begin_workspace_input(WorkspaceInputMode::Creating, String::new(), String::new());
        vec![AppAction::None]
    }

    fn begin_workspace_edit(&mut self) -> Vec<AppAction> {
        if let Some(workspace_id) = self.selected_workspace_id() {
            let (name, path) = self
                .selected_workspace
                .and_then(|index| self.workspaces.get(index))
                .map(|workspace| (workspace.name.clone(), workspace.path.clone()))
                .unwrap_or_default();
            self.begin_workspace_input(WorkspaceInputMode::Editing { workspace_id }, name, path);
        } else {
            self.status_line = "select a workspace first".to_string();
        }

        vec![AppAction::None]
    }

    fn delete_selected_workspace(&mut self) -> Vec<AppAction> {
        self.selected_workspace_id()
            .map(|workspace_id| vec![AppAction::DeleteWorkspace { workspace_id }])
            .unwrap_or_else(|| {
                self.status_line = "select a workspace first".to_string();
                vec![AppAction::None]
            })
    }

    fn toggle_mind_vault(&mut self, vault_id: i64) {
        if !self.mind_expanded_vaults.remove(&vault_id) {
            self.mind_expanded_vaults.insert(vault_id);
        }
        self.mind_selection = Some(MindSelection::Vault { vault_id });
        self.sync_mind_selection();
    }

    fn expand_mind_selection(&mut self) {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Vault { vault_id }) => {
                if self.mind_expanded_vaults.insert(vault_id) {
                    self.mind_selection = Some(MindSelection::Vault { vault_id });
                } else if let Some(note_id) = self
                    .notes
                    .iter()
                    .find(|note| note.vault_id == vault_id)
                    .map(|note| note.id)
                {
                    self.mind_selection = Some(MindSelection::Note { note_id });
                }
            }
            Some(MindTreeEntry::Note { .. }) | None => {}
        }
        self.sync_mind_selection();
    }

    fn collapse_mind_selection(&mut self) {
        match self.selected_mind_entry() {
            Some(MindTreeEntry::Vault { vault_id }) => {
                self.mind_expanded_vaults.remove(&vault_id);
                self.mind_selection = Some(MindSelection::Vault { vault_id });
            }
            Some(MindTreeEntry::Note { vault_id, .. }) => {
                self.mind_selection = Some(MindSelection::Vault { vault_id });
            }
            None => {}
        }
        self.sync_mind_selection();
    }

    fn begin_task_input(&mut self, mode: TaskInputMode, title: String, description: String) {
        self.task_input_mode = Some(mode);
        self.task_input_focus = TaskInputField::Title;
        self.task_input_title = title;
        self.task_input_description = description;
        self.status_line = match self.task_input_mode {
            Some(TaskInputMode::Creating) => {
                "creating task: type a title and press enter".to_string()
            }
            Some(TaskInputMode::Editing { .. }) => {
                "editing task: type a title and press enter".to_string()
            }
            None => "ready".to_string(),
        };
    }

    fn clear_task_input(&mut self) {
        self.task_input_mode = None;
        self.task_input_focus = TaskInputField::Title;
        self.task_input_title.clear();
        self.task_input_description.clear();
    }

    fn begin_workspace_input(&mut self, mode: WorkspaceInputMode, name: String, path: String) {
        self.workspace_input_mode = Some(mode);
        self.workspace_input_focus = WorkspaceInputField::Name;
        self.workspace_input_name = name;
        self.workspace_input_path = path;
        self.status_line = match self.workspace_input_mode {
            Some(WorkspaceInputMode::Creating) => {
                "creating workspace: type a name and path".to_string()
            }
            Some(WorkspaceInputMode::Editing { .. }) => {
                "editing workspace: type a name and path".to_string()
            }
            None => "ready".to_string(),
        };
    }

    fn clear_workspace_input(&mut self) {
        self.workspace_input_mode = None;
        self.workspace_input_focus = WorkspaceInputField::Name;
        self.workspace_input_name.clear();
        self.workspace_input_path.clear();
    }

    fn begin_mind_draft(&mut self, mode: MindDraftMode, document: String) {
        self.mind_draft = Some(MindDraft {
            mode: mode.clone(),
            focus: match mode {
                MindDraftMode::Creating { .. } => MindDraftFocus::Path,
                MindDraftMode::Editing { .. } => MindDraftFocus::Document,
            },
            document,
        });
        self.status_line = match mode {
            MindDraftMode::Creating { .. } => "creating note: ctrl+s saves markdown".to_string(),
            MindDraftMode::Editing { .. } => "editing note: ctrl+s saves markdown".to_string(),
        };
    }

    fn clear_mind_draft(&mut self) {
        self.mind_draft = None;
        self.mind_path_selection = None;
    }

    fn handle_mind_draft_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        if matches!(key.code, KeyCode::Esc) {
            self.clear_mind_draft();
            self.status_line = "note edit cancelled".to_string();
            return vec![AppAction::None];
        }

        let Some((mode, focus)) = self
            .mind_draft
            .as_ref()
            .map(|draft| (draft.mode.clone(), draft.focus))
        else {
            return vec![AppAction::None];
        };

        if matches!(mode, MindDraftMode::Creating { .. }) {
            match key.code {
                KeyCode::Tab => {
                    if let Some(draft) = self.mind_draft.as_mut() {
                        draft.focus = match draft.focus {
                            MindDraftFocus::Path => MindDraftFocus::Document,
                            MindDraftFocus::Document => MindDraftFocus::Path,
                        };
                    }
                    return vec![AppAction::None];
                }
                KeyCode::Up | KeyCode::Char('k') if matches!(focus, MindDraftFocus::Path) => {
                    self.move_mind_path_selection(-1);
                    return vec![AppAction::None];
                }
                KeyCode::Down | KeyCode::Char('j') if matches!(focus, MindDraftFocus::Path) => {
                    self.move_mind_path_selection(1);
                    return vec![AppAction::None];
                }
                KeyCode::Left | KeyCode::Char('h') if matches!(focus, MindDraftFocus::Path) => {
                    self.collapse_mind_path_selection();
                    return vec![AppAction::None];
                }
                KeyCode::Right | KeyCode::Char('l') if matches!(focus, MindDraftFocus::Path) => {
                    self.expand_mind_path_selection();
                    return vec![AppAction::None];
                }
                KeyCode::Enter if matches!(focus, MindDraftFocus::Path) => {
                    if let Some(draft) = self.mind_draft.as_mut() {
                        draft.focus = MindDraftFocus::Document;
                    }
                    return vec![AppAction::None];
                }
                _ => {}
            }
        }

        if matches!(key.code, KeyCode::Char('s')) && key.modifiers.contains(KeyModifiers::CONTROL) {
            let Some(snapshot) = self.mind_draft.as_ref() else {
                return vec![AppAction::None];
            };

            let title = markdown_note_title(&snapshot.document);
            let slug = slugify(&title);
            let path = match snapshot.mode {
                MindDraftMode::Creating { .. } => self.selected_mind_create_path_for_save(&title),
                MindDraftMode::Editing { .. } => self.selected_mind_edit_path_for_save(&title),
            };
            let document = snapshot.document.clone();

            return match snapshot.mode {
                MindDraftMode::Creating { vault_id } => vec![AppAction::CreateNote {
                    vault_id,
                    title,
                    slug,
                    path,
                    document,
                }],
                MindDraftMode::Editing { note_id } => vec![AppAction::UpdateNote {
                    note_id,
                    title,
                    slug,
                    path,
                    document,
                }],
            };
        }

        let Some(draft) = self.mind_draft.as_mut() else {
            return vec![AppAction::None];
        };

        match key.code {
            KeyCode::Enter => {
                draft.document.push('\n');
                vec![AppAction::None]
            }
            KeyCode::Tab => {
                if matches!(draft.mode, MindDraftMode::Creating { .. }) {
                    draft.document.push_str("    ");
                } else {
                    draft.document.push_str("    ");
                }
                vec![AppAction::None]
            }
            KeyCode::Backspace => {
                draft.document.pop();
                vec![AppAction::None]
            }
            KeyCode::Char('h') if key.modifiers.contains(KeyModifiers::CONTROL) => {
                draft.document.pop();
                vec![AppAction::None]
            }
            KeyCode::Char(c) => {
                draft.document.push(c);
                vec![AppAction::None]
            }
            _ => vec![AppAction::None],
        }
    }

    fn selected_mind_create_path_for_save(&self, title: &str) -> String {
        let directory = self
            .mind_path_selection
            .clone()
            .or_else(|| self.selected_mind_vault_root_path())
            .unwrap_or_else(|| ".".to_string());

        Path::new(&directory)
            .join(format!("{}.md", slugify(title)))
            .to_string_lossy()
            .into_owned()
    }

    fn selected_mind_edit_path_for_save(&self, title: &str) -> String {
        let existing_path = self.selected_mind_note_path().unwrap_or_default();
        let parent = Path::new(&existing_path)
            .parent()
            .map(Path::to_path_buf)
            .unwrap_or_else(|| PathBuf::from("."));
        parent
            .join(format!("{}.md", slugify(title)))
            .to_string_lossy()
            .into_owned()
    }

    pub fn selected_mind_vault_root_path(&self) -> Option<String> {
        self.selected_mind_vault_id().and_then(|vault_id| {
            self.vaults
                .iter()
                .find(|vault| vault.id == vault_id)
                .map(|vault| vault.root_path.clone())
        })
    }

    fn move_mind_path_selection(&mut self, offset: isize) {
        let entries = self.visible_mind_paths();
        if entries.is_empty() {
            return;
        }

        let current = self
            .mind_path_selection
            .as_ref()
            .and_then(|selected| entries.iter().position(|entry| entry == selected))
            .unwrap_or(0) as isize;
        let next = (current + offset).clamp(0, entries.len().saturating_sub(1) as isize);
        self.mind_path_selection = Some(entries[next as usize].clone());
    }

    fn collapse_mind_path_selection(&mut self) {
        let Some(selected) = self.mind_path_selection.clone() else {
            return;
        };

        if self.mind_path_expanded.remove(&selected) {
            return;
        }

        if let Some(parent) = Path::new(&selected).parent().and_then(|path| path.to_str()) {
            self.mind_path_selection = Some(parent.to_string());
        }
    }

    fn expand_mind_path_selection(&mut self) {
        let Some(selected) = self.mind_path_selection.clone() else {
            return;
        };

        if !Path::new(&selected).is_dir() {
            return;
        }

        self.mind_path_expanded.insert(selected);
    }

    fn visible_mind_paths(&self) -> Vec<String> {
        let Some(root) = self.selected_mind_vault_root_path() else {
            return Vec::new();
        };

        let mut entries = vec![root.clone()];
        self.collect_mind_paths(Path::new(&root), 0, &mut entries);
        entries
    }

    fn collect_mind_paths(&self, path: &Path, depth: usize, entries: &mut Vec<String>) {
        if !self
            .mind_path_expanded
            .contains(&path.to_string_lossy().into_owned())
            && depth > 0
        {
            return;
        }

        let mut dirs = match fs::read_dir(path) {
            Ok(entries) => entries
                .filter_map(Result::ok)
                .map(|entry| entry.path())
                .filter(|child| child.is_dir())
                .collect::<Vec<_>>(),
            Err(_) => return,
        };

        dirs.sort();

        for dir in dirs {
            let dir_str = dir.to_string_lossy().into_owned();
            entries.push(dir_str.clone());
            self.collect_mind_paths(&dir, depth + 1, entries);
        }
    }

    fn open_launcher(&mut self) {
        self.launcher = Some(LauncherState {
            screen: self.active_screen,
            query: String::new(),
            selected: 0,
        });
        self.status_line = format!("{} menu open", screen_label(self.active_screen));
    }

    fn close_launcher(&mut self) {
        self.launcher = None;
    }

    pub fn filtered_launcher_entries(&self) -> Vec<LauncherEntry> {
        let Some(launcher) = self.launcher.as_ref() else {
            return Vec::new();
        };
        let query = launcher.query.trim().to_lowercase();
        let entries = self.launcher_entries(launcher.screen);

        if query.is_empty() {
            return entries;
        }

        entries
            .into_iter()
            .filter(|entry| {
                let label = entry.label.to_lowercase();
                let hint = entry.hint.to_lowercase();
                label.contains(&query) || hint.contains(&query)
            })
            .collect()
    }

    fn launcher_entries(&self, screen: Screen) -> Vec<LauncherEntry> {
        let mut entries = match screen {
            Screen::Dashboard => vec![
                LauncherEntry {
                    label: if self.pomodoro.running {
                        "pause pomodoro".to_string()
                    } else {
                        "start pomodoro".to_string()
                    },
                    hint: format!(
                        "{} remaining",
                        format_duration(self.pomodoro.remaining_seconds)
                    ),
                    target: LauncherTarget::TogglePomodoro,
                },
                LauncherEntry {
                    label: "reset pomodoro".to_string(),
                    hint: "return to work phase".to_string(),
                    target: LauncherTarget::ResetPomodoro,
                },
            ],
            Screen::Pomodoro => vec![
                LauncherEntry {
                    label: if self.pomodoro.running {
                        "pause pomodoro".to_string()
                    } else {
                        "resume pomodoro".to_string()
                    },
                    hint: format!(
                        "{} remaining",
                        format_duration(self.pomodoro.remaining_seconds)
                    ),
                    target: LauncherTarget::TogglePomodoro,
                },
                LauncherEntry {
                    label: "stop pomodoro".to_string(),
                    hint: "save the current session".to_string(),
                    target: LauncherTarget::ResetPomodoro,
                },
                LauncherEntry {
                    label: "attach task".to_string(),
                    hint: "use the selected task".to_string(),
                    target: LauncherTarget::AttachPomodoroTask,
                },
                LauncherEntry {
                    label: "clear task".to_string(),
                    hint: "detach the current task".to_string(),
                    target: LauncherTarget::ClearPomodoroTask,
                },
            ],
            Screen::Tasks => vec![
                LauncherEntry {
                    label: "add task".to_string(),
                    hint: "open the task editor".to_string(),
                    target: LauncherTarget::OpenTaskInput,
                },
                LauncherEntry {
                    label: "edit task".to_string(),
                    hint: "edit the selected task".to_string(),
                    target: LauncherTarget::EditTask,
                },
                LauncherEntry {
                    label: "toggle task".to_string(),
                    hint: "flip completed state".to_string(),
                    target: LauncherTarget::ToggleTask,
                },
                LauncherEntry {
                    label: "delete task".to_string(),
                    hint: "remove the selected task".to_string(),
                    target: LauncherTarget::DeleteTask,
                },
                LauncherEntry {
                    label: "mark doing".to_string(),
                    hint: "track the selected task time".to_string(),
                    target: LauncherTarget::ToggleTaskDoing,
                },
                LauncherEntry {
                    label: "toggle theme".to_string(),
                    hint: match self.theme {
                        Theme::Ember => "ember -> slate".to_string(),
                        Theme::Slate => "slate -> ember".to_string(),
                    },
                    target: LauncherTarget::ToggleTheme,
                },
            ],
            Screen::Mind => vec![
                LauncherEntry {
                    label: "add note".to_string(),
                    hint: "open the note path picker".to_string(),
                    target: LauncherTarget::OpenMindDraft,
                },
                LauncherEntry {
                    label: "edit note".to_string(),
                    hint: "edit the selected note".to_string(),
                    target: LauncherTarget::EditMindDraft,
                },
                LauncherEntry {
                    label: "delete note".to_string(),
                    hint: "remove the selected note".to_string(),
                    target: LauncherTarget::DeleteMindDraft,
                },
                LauncherEntry {
                    label: "toggle theme".to_string(),
                    hint: match self.theme {
                        Theme::Ember => "ember -> slate".to_string(),
                        Theme::Slate => "slate -> ember".to_string(),
                    },
                    target: LauncherTarget::ToggleTheme,
                },
            ],
            Screen::Notifications => vec![],
            Screen::Workspaces => vec![
                LauncherEntry {
                    label: "add workspace".to_string(),
                    hint: "open the workspace editor".to_string(),
                    target: LauncherTarget::OpenWorkspaceInput,
                },
                LauncherEntry {
                    label: "edit workspace".to_string(),
                    hint: "edit the selected workspace".to_string(),
                    target: LauncherTarget::EditWorkspace,
                },
                LauncherEntry {
                    label: "delete workspace".to_string(),
                    hint: "remove the selected workspace".to_string(),
                    target: LauncherTarget::DeleteWorkspace,
                },
                LauncherEntry {
                    label: "toggle theme".to_string(),
                    hint: match self.theme {
                        Theme::Ember => "ember -> slate".to_string(),
                        Theme::Slate => "slate -> ember".to_string(),
                    },
                    target: LauncherTarget::ToggleTheme,
                },
            ],
            Screen::Run => vec![
                LauncherEntry {
                    label: "create/select work item".to_string(),
                    hint: "bind the selected task and note".to_string(),
                    target: LauncherTarget::SelectOrCreateRunWorkItem,
                },
                LauncherEntry {
                    label: "start run".to_string(),
                    hint: "start the selected work item".to_string(),
                    target: LauncherTarget::StartRunWorkItem,
                },
                LauncherEntry {
                    label: "pause run".to_string(),
                    hint: "pause the selected work item".to_string(),
                    target: LauncherTarget::PauseRunWorkItem,
                },
                LauncherEntry {
                    label: "stop run".to_string(),
                    hint: "save elapsed time".to_string(),
                    target: LauncherTarget::StopRunWorkItem,
                },
                LauncherEntry {
                    label: "delete work item".to_string(),
                    hint: "remove the selected work item".to_string(),
                    target: LauncherTarget::DeleteRunWorkItem,
                },
            ],
        };

        entries.push(LauncherEntry {
            label: "quit".to_string(),
            hint: "close the app".to_string(),
            target: LauncherTarget::Quit,
        });

        entries
    }

    fn push_workspace_input_char(&mut self, c: char) {
        match self.workspace_input_focus {
            WorkspaceInputField::Name => self.workspace_input_name.push(c),
            WorkspaceInputField::Path => self.workspace_input_path.push(c),
        }
    }

    fn delete_workspace_input_char(&mut self) {
        match self.workspace_input_focus {
            WorkspaceInputField::Name => {
                self.workspace_input_name.pop();
            }
            WorkspaceInputField::Path => {
                self.workspace_input_path.pop();
            }
        }
    }

    fn push_task_input_char(&mut self, c: char) {
        match self.task_input_focus {
            TaskInputField::Title => self.task_input_title.push(c),
            TaskInputField::Description => self.task_input_description.push(c),
        }
    }

    fn delete_task_input_char(&mut self) {
        match self.task_input_focus {
            TaskInputField::Title => {
                self.task_input_title.pop();
            }
            TaskInputField::Description => {
                self.task_input_description.pop();
            }
        }
    }

    fn toggle_pomodoro(&mut self) -> Vec<AppAction> {
        if self.pomodoro.running {
            self.pause_pomodoro()
        } else {
            self.start_or_resume_pomodoro()
        }
    }

    fn reset_pomodoro(&mut self) -> Vec<AppAction> {
        self.stop_pomodoro()
    }

    pub fn tick_pomodoro(&mut self) -> Vec<AppAction> {
        if !self.pomodoro.running || self.pomodoro.remaining_seconds == 0 {
            return vec![AppAction::None];
        }

        self.pomodoro.remaining_seconds -= 1;
        self.pomodoro.elapsed_seconds = self.pomodoro.elapsed_seconds.saturating_add(1);
        if self.pomodoro.remaining_seconds == 0 {
            return self.advance_pomodoro_phase();
        }

        vec![AppAction::None]
    }

    fn advance_pomodoro_phase(&mut self) -> Vec<AppAction> {
        match self.pomodoro.phase {
            PomodoroPhase::Work => self.finish_work_session(),
            PomodoroPhase::Break => {
                self.pomodoro.phase = PomodoroPhase::Work;
                self.pomodoro.remaining_seconds = self.pomodoro.work_seconds;
                self.pomodoro.elapsed_seconds = 0;
                self.pomodoro.task_id = None;
                self.status_line = "break complete".to_string();
                vec![AppAction::None]
            }
        }
    }

    fn finish_work_session(&mut self) -> Vec<AppAction> {
        let elapsed_seconds = self.pomodoro.elapsed_seconds;
        let task_id = self.pomodoro.task_id;

        self.pomodoro.phase = PomodoroPhase::Break;
        self.pomodoro.remaining_seconds = self.pomodoro.break_seconds;
        self.pomodoro.elapsed_seconds = 0;
        self.pomodoro.running = false;
        self.pomodoro.completed_sessions += 1;

        let stopped_at_unix = current_unix_timestamp();
        let started_at_unix = stopped_at_unix.saturating_sub(i64::from(elapsed_seconds));
        let mut actions = vec![AppAction::CreatePomodoroSession {
            session: PomodoroSession {
                id: 0,
                task_id,
                phase: PomodoroPhase::Work,
                started_at_unix,
                stopped_at_unix,
                elapsed_seconds,
            },
        }];

        if let Some(task_id) = task_id {
            actions.push(AppAction::AddTaskTrackedTime {
                task_id,
                tracked_seconds: u64::from(elapsed_seconds),
            });
        }

        self.status_line = "work session complete".to_string();
        actions
    }
}

impl Default for AppState {
    fn default() -> Self {
        Self::new()
    }
}

fn next_screen(screen: Screen) -> Screen {
    match screen {
        Screen::Dashboard => Screen::Tasks,
        Screen::Tasks => Screen::Mind,
        Screen::Mind => Screen::Notifications,
        Screen::Notifications => Screen::Workspaces,
        Screen::Workspaces => Screen::Pomodoro,
        Screen::Pomodoro => Screen::Run,
        Screen::Run => Screen::Dashboard,
    }
}

fn previous_screen(screen: Screen) -> Screen {
    match screen {
        Screen::Dashboard => Screen::Run,
        Screen::Tasks => Screen::Dashboard,
        Screen::Mind => Screen::Tasks,
        Screen::Notifications => Screen::Mind,
        Screen::Workspaces => Screen::Notifications,
        Screen::Pomodoro => Screen::Workspaces,
        Screen::Run => Screen::Pomodoro,
    }
}

fn screen_label(screen: Screen) -> &'static str {
    match screen {
        Screen::Dashboard => "dashboard",
        Screen::Pomodoro => "pomodoro",
        Screen::Run => "run",
        Screen::Tasks => "tasks",
        Screen::Mind => "mind",
        Screen::Notifications => "notifications",
        Screen::Workspaces => "workspaces",
    }
}

fn normalized_note_title(title: &str) -> String {
    let trimmed = title.trim();
    if trimmed.is_empty() {
        "Untitled".to_string()
    } else {
        trimmed.to_string()
    }
}

fn markdown_note_title(document: &str) -> String {
    for line in document.lines() {
        let trimmed = line.trim();
        if let Some(title) = trimmed.strip_prefix("# ") {
            return normalized_note_title(title);
        }
        if !trimmed.is_empty() {
            return normalized_note_title(trimmed);
        }
    }

    "Untitled".to_string()
}

fn markdown_note_document_from(note: &Note) -> String {
    format!("# {}\n\n", note.title)
}

fn slugify(input: &str) -> String {
    let mut slug = String::new();
    let mut last_was_dash = false;

    for ch in input.chars().flat_map(|ch| ch.to_lowercase()) {
        if ch.is_ascii_alphanumeric() {
            slug.push(ch);
            last_was_dash = false;
        } else if !last_was_dash && !slug.is_empty() {
            slug.push('-');
            last_was_dash = true;
        }
    }

    while slug.ends_with('-') {
        slug.pop();
    }

    if slug.is_empty() {
        "untitled".to_string()
    } else {
        slug
    }
}

pub fn current_unix_timestamp() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs() as i64)
        .unwrap_or_default()
}

fn format_duration(total_seconds: u32) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};

    #[test]
    fn ctrl_l_cycles_forward_between_screens() {
        let mut state = AppState::new();
        let actions = state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('l'),
            KeyModifiers::CONTROL,
        )));

        assert_eq!(state.active_screen, Screen::Tasks);
        assert!(matches!(actions.as_slice(), [AppAction::None]));
    }

    #[test]
    fn ctrl_l_cycles_through_mind_screen() {
        let mut state = AppState::new();

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('l'),
            KeyModifiers::CONTROL,
        )));
        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('l'),
            KeyModifiers::CONTROL,
        )));

        assert_eq!(state.active_screen, Screen::Mind);
    }

    #[test]
    fn ctrl_h_cycles_backwards_between_screens() {
        let mut state = AppState::new();

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('h'),
            KeyModifiers::CONTROL,
        )));
        assert_eq!(state.active_screen, Screen::Run);
    }

    #[test]
    fn started_requests_full_note_load() {
        let mut state = AppState::new();
        let actions = state.apply(AppEvent::Started);

        assert!(matches!(
            actions.as_slice(),
            [
                AppAction::LoadVaults,
                AppAction::LoadAllNotes,
                AppAction::LoadTasks,
                AppAction::LoadPomodoroSessions,
                AppAction::LoadWorkItems,
                AppAction::LoadWorkspaces
            ]
        ));
    }

    #[test]
    fn pomodoro_can_toggle_and_reset() {
        let mut state = AppState::new();

        let _ = state.toggle_pomodoro();
        assert!(state.pomodoro.running);

        let _ = state.reset_pomodoro();
        assert!(!state.pomodoro.running);
        assert_eq!(state.pomodoro.phase, PomodoroPhase::Work);
        assert_eq!(
            state.pomodoro.remaining_seconds,
            state.pomodoro.work_seconds
        );
    }

    #[test]
    fn task_selection_is_clamped_when_tasks_change() {
        let mut state = AppState::new();
        state.selected_task = Some(4);

        state.apply(AppEvent::TasksLoaded(vec![
            Task {
                id: 1,
                title: "one".to_string(),
                description: String::new(),
                doing: false,
                completed: false,
                tracked_seconds: 0,
                created_at_unix: 0,
            },
            Task {
                id: 2,
                title: "two".to_string(),
                description: String::new(),
                doing: false,
                completed: false,
                tracked_seconds: 0,
                created_at_unix: 0,
            },
        ]));

        assert_eq!(state.selected_task, Some(1));
    }

    #[test]
    fn create_task_opens_input_mode() {
        let mut state = AppState::new();
        state.active_screen = Screen::Tasks;

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('a'),
            KeyModifiers::NONE,
        )));

        assert_eq!(state.task_input_mode, Some(TaskInputMode::Creating));
        assert_eq!(state.task_input_title, "");
        assert_eq!(state.task_input_description, "");
    }

    #[test]
    fn colon_opens_launcher() {
        let mut state = AppState::new();

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char(':'),
            KeyModifiers::NONE,
        )));

        assert!(matches!(
            state.launcher,
            Some(LauncherState {
                screen: Screen::Dashboard,
                ..
            })
        ));
    }

    #[test]
    fn launcher_filters_entries_by_screen_scope() {
        let mut state = AppState::new();
        state.active_screen = Screen::Tasks;

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char(':'),
            KeyModifiers::NONE,
        )));
        for ch in "add".chars() {
            state.apply(AppEvent::Key(KeyEvent::new(
                KeyCode::Char(ch),
                KeyModifiers::NONE,
            )));
        }

        let entries = state.filtered_launcher_entries();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].label, "add task");
    }

    #[test]
    fn typing_title_and_description_creates_actions() {
        let mut state = AppState::new();
        state.active_screen = Screen::Tasks;
        state.begin_task_input(TaskInputMode::Creating, String::new(), String::new());

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('h'),
            KeyModifiers::NONE,
        )));
        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('i'),
            KeyModifiers::NONE,
        )));
        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Enter,
            KeyModifiers::NONE,
        )));
        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('o'),
            KeyModifiers::NONE,
        )));
        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char('k'),
            KeyModifiers::NONE,
        )));
        let actions = state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Enter,
            KeyModifiers::NONE,
        )));

        assert!(
            matches!(actions.as_slice(), [AppAction::CreateTask { title, description }] if title == "hi" && description == "ok")
        );
    }
}
