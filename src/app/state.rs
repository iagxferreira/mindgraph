use std::{
    collections::BTreeSet,
    time::{SystemTime, UNIX_EPOCH},
};

use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use serde::{Deserialize, Serialize};

use crate::app::{AppAction, AppEvent};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Screen {
    Dashboard,
    Tasks,
    Mind,
    Notifications,
    Workspaces,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Task {
    pub id: i64,
    pub title: String,
    pub description: String,
    pub completed: bool,
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
    pub content: String,
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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MindDraft {
    pub mode: MindDraftMode,
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PomodoroPhase {
    Work,
    Break,
}

#[derive(Debug, Clone)]
pub struct PomodoroState {
    pub phase: PomodoroPhase,
    pub running: bool,
    pub remaining_seconds: u32,
    pub work_seconds: u32,
    pub break_seconds: u32,
    pub completed_sessions: u32,
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
    Screen(Screen),
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
    pub mind_selection: Option<MindSelection>,
    pub mind_expanded_vaults: BTreeSet<i64>,
    pub tasks: Vec<Task>,
    pub notifications: Vec<String>,
    pub workspaces: Vec<Workspace>,
    pub selected_task: Option<usize>,
    pub selected_workspace: Option<usize>,
    pub theme: Theme,
    pub status_line: String,
    pub pomodoro: PomodoroState,
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
            mind_selection: None,
            mind_expanded_vaults: BTreeSet::new(),
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
                work_seconds: 25 * 60,
                break_seconds: 5 * 60,
                completed_sessions: 0,
            },
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
                AppAction::LoadWorkspaces,
            ],
            AppEvent::Tick => {
                self.tick_pomodoro();
                vec![AppAction::None]
            }
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
                self.status_line = format!("loaded {} notes", self.notes.len());
                vec![AppAction::None]
            }
            AppEvent::TasksLoaded(tasks) => {
                self.tasks = tasks;
                self.sync_selection();
                self.status_line = format!("loaded {} tasks", self.tasks.len());
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
                self.clear_mind_draft();
                self.mind_selection = Some(MindSelection::Note { note_id });
                self.sync_mind_selection();
                self.status_line = "note created".to_string();
                vec![AppAction::None]
            }
            AppEvent::NoteUpdated(note) => {
                let note_id = note.id;
                if let Some(existing) = self.notes.iter_mut().find(|current| current.id == note.id)
                {
                    *existing = note;
                }
                self.sort_notes();
                self.clear_mind_draft();
                self.mind_selection = Some(MindSelection::Note { note_id });
                self.sync_mind_selection();
                self.status_line = "note updated".to_string();
                vec![AppAction::None]
            }
            AppEvent::NoteDeleted(note_id) => {
                self.notes.retain(|note| note.id != note_id);
                self.sync_mind_selection();
                self.clear_mind_draft();
                self.status_line = "note deleted".to_string();
                vec![AppAction::None]
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
                self.clear_task_input();
                self.status_line = "task deleted".to_string();
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
                    _ => self.move_task_selection(-1),
                }
                vec![AppAction::None]
            }
            KeyCode::Down | KeyCode::Char('j') => {
                match self.active_screen {
                    Screen::Tasks => self.move_task_selection(1),
                    Screen::Mind => self.move_mind_selection(1),
                    Screen::Workspaces | Screen::Notifications | Screen::Dashboard => {
                        self.move_task_selection(1)
                    }
                }
                vec![AppAction::None]
            }
            KeyCode::Char(' ') if self.active_screen == Screen::Tasks => self
                .selected_task_id()
                .map(|task_id| vec![AppAction::ToggleTask { task_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
            KeyCode::Char('a') if self.active_screen == Screen::Mind => {
                if let Some(vault_id) = self.selected_mind_vault_id() {
                    self.begin_mind_draft(MindDraftMode::Creating { vault_id }, "# Untitled\n\n".to_string());
                } else {
                    self.status_line = "select a vault first".to_string();
                }
                vec![AppAction::None]
            }
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Mind => {
                match self.selected_mind_entry() {
                    Some(MindTreeEntry::Vault { vault_id }) => self.toggle_mind_vault(vault_id),
                    Some(MindTreeEntry::Note { note_id, .. }) => {
                        if let Some(note) = self.notes.iter().find(|note| note.id == note_id) {
                            self.begin_mind_draft(
                                MindDraftMode::Editing { note_id },
                                markdown_note_document_from(note),
                            );
                        }
                    }
                    None => {}
                }
                vec![AppAction::None]
            }
            KeyCode::Char('d') if self.active_screen == Screen::Mind => self
                .selected_mind_note_id()
                .map(|note_id| vec![AppAction::DeleteNote { note_id }])
                .unwrap_or_else(|| {
                    self.status_line = "select a note to delete".to_string();
                    vec![AppAction::None]
                }),
            KeyCode::Left | KeyCode::Char('h') if self.active_screen == Screen::Mind => {
                self.collapse_mind_selection();
                vec![AppAction::None]
            }
            KeyCode::Right | KeyCode::Char('l') if self.active_screen == Screen::Mind => {
                self.expand_mind_selection();
                vec![AppAction::None]
            }
            KeyCode::Char('a') if self.active_screen == Screen::Tasks => {
                self.begin_task_input(TaskInputMode::Creating, String::new(), String::new());
                vec![AppAction::None]
            }
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Tasks => {
                if let Some(task_id) = self.selected_task_id() {
                    let (title, description) = self
                        .selected_task
                        .and_then(|index| self.tasks.get(index))
                        .map(|task| (task.title.clone(), task.description.clone()))
                        .unwrap_or_default();
                    self.begin_task_input(TaskInputMode::Editing { task_id }, title, description);
                }
                vec![AppAction::None]
            }
            KeyCode::Char('d') if self.active_screen == Screen::Tasks => self
                .selected_task_id()
                .map(|task_id| vec![AppAction::DeleteTask { task_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
            KeyCode::Char('t') => {
                self.theme = match self.theme {
                    Theme::Ember => Theme::Slate,
                    Theme::Slate => Theme::Ember,
                };
                self.status_line = "theme switched".to_string();
                vec![AppAction::None]
            }
            KeyCode::Char('a') if self.active_screen == Screen::Workspaces => {
                self.begin_workspace_input(
                    WorkspaceInputMode::Creating,
                    String::new(),
                    String::new(),
                );
                vec![AppAction::None]
            }
            KeyCode::Char('e') | KeyCode::Enter if self.active_screen == Screen::Workspaces => {
                if let Some(workspace_id) = self.selected_workspace_id() {
                    let (name, path) = self
                        .selected_workspace
                        .and_then(|index| self.workspaces.get(index))
                        .map(|workspace| (workspace.name.clone(), workspace.path.clone()))
                        .unwrap_or_default();
                    self.begin_workspace_input(
                        WorkspaceInputMode::Editing { workspace_id },
                        name,
                        path,
                    );
                }
                vec![AppAction::None]
            }
            KeyCode::Char('d') if self.active_screen == Screen::Workspaces => self
                .selected_workspace_id()
                .map(|workspace_id| vec![AppAction::DeleteWorkspace { workspace_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
            KeyCode::Char(' ') if self.active_screen == Screen::Workspaces => vec![AppAction::None],
            KeyCode::Char('p') => {
                self.toggle_pomodoro();
                vec![AppAction::None]
            }
            KeyCode::Char('r') => {
                self.reset_pomodoro();
                vec![AppAction::None]
            }
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
                    Some(LauncherTarget::Screen(screen)) => {
                        self.active_screen = screen;
                        self.status_line = format!("switched to {}", screen_label(screen));
                    }
                    Some(LauncherTarget::ToggleTheme) => {
                        self.theme = match self.theme {
                            Theme::Ember => Theme::Slate,
                            Theme::Slate => Theme::Ember,
                        };
                        self.status_line = "theme switched".to_string();
                    }
                    Some(LauncherTarget::TogglePomodoro) => {
                        self.toggle_pomodoro();
                    }
                    Some(LauncherTarget::ResetPomodoro) => {
                        self.reset_pomodoro();
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

    fn selected_workspace_id(&self) -> Option<i64> {
        self.selected_workspace
            .and_then(|index| self.workspaces.get(index))
            .map(|workspace| workspace.id)
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

    fn expand_all_vaults(&mut self) {
        self.mind_expanded_vaults
            .extend(self.vaults.iter().map(|vault| vault.id));
    }

    fn move_mind_selection(&mut self, offset: isize) {
        let entries = self.mind_entries();
        if entries.is_empty() {
            self.mind_selection = None;
            return;
        }

        let current_index = self
            .mind_selection
            .and_then(|selection| entries.iter().position(|entry| entry.matches(selection)))
            .unwrap_or(0) as isize;
        let next = (current_index + offset).clamp(0, entries.len().saturating_sub(1) as isize);
        self.mind_selection = Some(entries[next as usize].selection());
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
            document,
        });
        self.status_line = match mode {
            MindDraftMode::Creating { .. } => "creating note: ctrl+s saves markdown".to_string(),
            MindDraftMode::Editing { .. } => "editing note: ctrl+s saves markdown".to_string(),
        };
    }

    fn clear_mind_draft(&mut self) {
        self.mind_draft = None;
    }

    fn handle_mind_draft_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        if matches!(key.code, KeyCode::Esc) {
            self.clear_mind_draft();
            self.status_line = "note edit cancelled".to_string();
            return vec![AppAction::None];
        }

        let Some(draft) = self.mind_draft.as_mut() else {
            return vec![AppAction::None];
        };

        match key.code {
            KeyCode::Char('s') if key.modifiers.contains(KeyModifiers::CONTROL) => {
                let title = markdown_note_title(&draft.document);
                let slug = slugify(&title);
                let content = draft.document.clone();

                match draft.mode.clone() {
                    MindDraftMode::Creating { vault_id } => vec![AppAction::CreateNote {
                        vault_id,
                        title,
                        slug,
                        content,
                    }],
                    MindDraftMode::Editing { note_id } => vec![AppAction::UpdateNote {
                        note_id,
                        title,
                        slug,
                        content,
                    }],
                }
            }
            KeyCode::Enter => {
                draft.document.push('\n');
                vec![AppAction::None]
            }
            KeyCode::Tab => {
                draft.document.push_str("    ");
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

    fn open_launcher(&mut self) {
        self.launcher = Some(LauncherState {
            query: String::new(),
            selected: 0,
        });
        self.status_line = "launcher open".to_string();
    }

    fn close_launcher(&mut self) {
        self.launcher = None;
    }

    pub fn filtered_launcher_entries(&self) -> Vec<LauncherEntry> {
        let query = self
            .launcher
            .as_ref()
            .map(|launcher| launcher.query.trim().to_lowercase())
            .unwrap_or_default();

        let entries = self.launcher_entries();
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

    fn launcher_entries(&self) -> Vec<LauncherEntry> {
        vec![
            LauncherEntry {
                label: "dashboard".to_string(),
                hint: "overview".to_string(),
                target: LauncherTarget::Screen(Screen::Dashboard),
            },
            LauncherEntry {
                label: "tasks".to_string(),
                hint: format!("{} items", self.tasks.len()),
                target: LauncherTarget::Screen(Screen::Tasks),
            },
            LauncherEntry {
                label: "mind".to_string(),
                hint: format!("{} notes", self.notes.len()),
                target: LauncherTarget::Screen(Screen::Mind),
            },
            LauncherEntry {
                label: "notifications".to_string(),
                hint: format!("{} alerts", self.notifications.len()),
                target: LauncherTarget::Screen(Screen::Notifications),
            },
            LauncherEntry {
                label: "workspaces".to_string(),
                hint: format!("{} contexts", self.workspaces.len()),
                target: LauncherTarget::Screen(Screen::Workspaces),
            },
            LauncherEntry {
                label: "toggle theme".to_string(),
                hint: match self.theme {
                    Theme::Ember => "ember -> slate".to_string(),
                    Theme::Slate => "slate -> ember".to_string(),
                },
                target: LauncherTarget::ToggleTheme,
            },
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
            LauncherEntry {
                label: "quit".to_string(),
                hint: "close the app".to_string(),
                target: LauncherTarget::Quit,
            },
        ]
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

    fn toggle_pomodoro(&mut self) {
        self.pomodoro.running = !self.pomodoro.running;
        self.status_line = if self.pomodoro.running {
            "pomodoro started".to_string()
        } else {
            "pomodoro paused".to_string()
        };
    }

    fn reset_pomodoro(&mut self) {
        self.pomodoro.running = false;
        self.pomodoro.phase = PomodoroPhase::Work;
        self.pomodoro.remaining_seconds = self.pomodoro.work_seconds;
        self.status_line = "pomodoro reset".to_string();
    }

    pub fn tick_pomodoro(&mut self) {
        if !self.pomodoro.running || self.pomodoro.remaining_seconds == 0 {
            return;
        }

        self.pomodoro.remaining_seconds -= 1;
        if self.pomodoro.remaining_seconds == 0 {
            self.advance_pomodoro_phase();
        }
    }

    fn advance_pomodoro_phase(&mut self) {
        match self.pomodoro.phase {
            PomodoroPhase::Work => {
                self.pomodoro.phase = PomodoroPhase::Break;
                self.pomodoro.remaining_seconds = self.pomodoro.break_seconds;
                self.pomodoro.completed_sessions += 1;
                self.status_line = "work session complete".to_string();
            }
            PomodoroPhase::Break => {
                self.pomodoro.phase = PomodoroPhase::Work;
                self.pomodoro.remaining_seconds = self.pomodoro.work_seconds;
                self.status_line = "break complete".to_string();
            }
        }
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
        Screen::Workspaces => Screen::Dashboard,
    }
}

fn previous_screen(screen: Screen) -> Screen {
    match screen {
        Screen::Dashboard => Screen::Workspaces,
        Screen::Tasks => Screen::Dashboard,
        Screen::Mind => Screen::Tasks,
        Screen::Notifications => Screen::Mind,
        Screen::Workspaces => Screen::Notifications,
    }
}

fn screen_label(screen: Screen) -> &'static str {
    match screen {
        Screen::Dashboard => "dashboard",
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
    if note.content.trim_start().starts_with("# ") {
        note.content.clone()
    } else if note.content.trim().is_empty() {
        format!("# {}\n\n", note.title)
    } else {
        format!("# {}\n\n{}", note.title, note.content)
    }
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
        assert_eq!(state.active_screen, Screen::Workspaces);
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
                AppAction::LoadWorkspaces
            ]
        ));
    }

    #[test]
    fn pomodoro_can_toggle_and_reset() {
        let mut state = AppState::new();

        state.toggle_pomodoro();
        assert!(state.pomodoro.running);

        state.reset_pomodoro();
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
                completed: false,
                created_at_unix: 0,
            },
            Task {
                id: 2,
                title: "two".to_string(),
                description: String::new(),
                completed: false,
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

        assert!(state.launcher.is_some());
    }

    #[test]
    fn launcher_filters_entries() {
        let mut state = AppState::new();

        state.apply(AppEvent::Key(KeyEvent::new(
            KeyCode::Char(':'),
            KeyModifiers::NONE,
        )));
        for ch in "dash".chars() {
            state.apply(AppEvent::Key(KeyEvent::new(
                KeyCode::Char(ch),
                KeyModifiers::NONE,
            )));
        }

        let entries = state.filtered_launcher_entries();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].label, "dashboard");
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
