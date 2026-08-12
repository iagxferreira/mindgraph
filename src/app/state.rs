use std::time::{SystemTime, UNIX_EPOCH};

use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use serde::{Deserialize, Serialize};

use crate::app::{AppAction, AppEvent};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Screen {
    Dashboard,
    Tasks,
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

#[derive(Debug, Clone)]
pub struct AppState {
    pub active_screen: Screen,
    pub tasks: Vec<Task>,
    pub notifications: Vec<String>,
    pub workspaces: Vec<Workspace>,
    pub selected_task: Option<usize>,
    pub selected_workspace: Option<usize>,
    pub theme: Theme,
    pub status_line: String,
    pub pomodoro: PomodoroState,
    pub workspace_input_mode: Option<WorkspaceInputMode>,
    pub workspace_input_focus: WorkspaceInputField,
    pub workspace_input_name: String,
    pub workspace_input_path: String,
    pub task_input_mode: Option<TaskInputMode>,
    pub task_input_focus: TaskInputField,
    pub task_input_title: String,
    pub task_input_description: String,
    pub should_quit: bool,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            active_screen: Screen::Dashboard,
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
            workspace_input_mode: None,
            workspace_input_focus: WorkspaceInputField::Name,
            workspace_input_name: String::new(),
            workspace_input_path: String::new(),
            task_input_mode: None,
            task_input_focus: TaskInputField::Title,
            task_input_title: String::new(),
            task_input_description: String::new(),
            should_quit: false,
        }
    }

    pub fn apply(&mut self, event: AppEvent) -> Vec<AppAction> {
        match event {
            AppEvent::Started => vec![AppAction::LoadTasks, AppAction::LoadWorkspaces],
            AppEvent::Tick => {
                self.tick_pomodoro();
                vec![AppAction::None]
            }
            AppEvent::Resize => vec![AppAction::None],
            AppEvent::Key(key) => self.handle_key(key),
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
            AppEvent::TaskCreated(task) => {
                self.tasks.push(task);
                self.sync_selection();
                self.clear_task_input();
                self.status_line = "task created".to_string();
                vec![AppAction::None]
            }
            AppEvent::TaskUpdated(task) => {
                if let Some(existing) = self.tasks.iter_mut().find(|current| current.id == task.id) {
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
                self.workspaces.retain(|workspace| workspace.id != workspace_id);
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
            KeyCode::BackTab => {
                self.active_screen = previous_screen(self.active_screen);
                self.status_line = format!("switched to {}", screen_label(self.active_screen));
                vec![AppAction::None]
            }
            KeyCode::Up | KeyCode::Char('k') => {
                self.move_task_selection(-1);
                vec![AppAction::None]
            }
            KeyCode::Down | KeyCode::Char('j') => {
                self.move_task_selection(1);
                vec![AppAction::None]
            }
            KeyCode::Char(' ') if self.active_screen == Screen::Tasks => self
                .selected_task_id()
                .map(|task_id| vec![AppAction::ToggleTask { task_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
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

    fn handle_task_input_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        match key.code {
            KeyCode::Esc => {
                self.clear_task_input();
                self.status_line = "task edit cancelled".to_string();
                vec![AppAction::None]
            }
            KeyCode::Enter => {
                match self.task_input_focus {
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
                }
            }
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
            self.selected_workspace =
                Some(self.selected_workspace.unwrap_or(0).min(self.workspaces.len() - 1));
        }
    }

    fn begin_task_input(&mut self, mode: TaskInputMode, title: String, description: String) {
        self.task_input_mode = Some(mode);
        self.task_input_focus = TaskInputField::Title;
        self.task_input_title = title;
        self.task_input_description = description;
        self.status_line = match self.task_input_mode {
            Some(TaskInputMode::Creating) => "creating task: type a title and press enter".to_string(),
            Some(TaskInputMode::Editing { .. }) => "editing task: type a title and press enter".to_string(),
            None => "ready".to_string(),
        };
    }

    fn clear_task_input(&mut self) {
        self.task_input_mode = None;
        self.task_input_focus = TaskInputField::Title;
        self.task_input_title.clear();
        self.task_input_description.clear();
    }

    fn begin_workspace_input(
        &mut self,
        mode: WorkspaceInputMode,
        name: String,
        path: String,
    ) {
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
        Screen::Tasks => Screen::Notifications,
        Screen::Notifications => Screen::Workspaces,
        Screen::Workspaces => Screen::Dashboard,
    }
}

fn previous_screen(screen: Screen) -> Screen {
    match screen {
        Screen::Dashboard => Screen::Workspaces,
        Screen::Tasks => Screen::Dashboard,
        Screen::Notifications => Screen::Tasks,
        Screen::Workspaces => Screen::Notifications,
    }
}

fn screen_label(screen: Screen) -> &'static str {
    match screen {
        Screen::Dashboard => "dashboard",
        Screen::Tasks => "tasks",
        Screen::Notifications => "notifications",
        Screen::Workspaces => "workspaces",
    }
}

pub fn current_unix_timestamp() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs() as i64)
        .unwrap_or_default()
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
    fn shift_tab_cycles_backwards_between_screens() {
        let mut state = AppState::new();
        state.active_screen = Screen::Tasks;

        let actions = state.apply(AppEvent::Key(KeyEvent::new(KeyCode::BackTab, KeyModifiers::SHIFT)));

        assert_eq!(state.active_screen, Screen::Dashboard);
        assert!(matches!(actions.as_slice(), [AppAction::None]));
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
    fn pomodoro_can_toggle_and_reset() {
        let mut state = AppState::new();

        state.toggle_pomodoro();
        assert!(state.pomodoro.running);

        state.reset_pomodoro();
        assert!(!state.pomodoro.running);
        assert_eq!(state.pomodoro.phase, PomodoroPhase::Work);
        assert_eq!(state.pomodoro.remaining_seconds, state.pomodoro.work_seconds);
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

        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Char('a'), KeyModifiers::NONE)));

        assert_eq!(state.task_input_mode, Some(TaskInputMode::Creating));
        assert_eq!(state.task_input_title, "");
        assert_eq!(state.task_input_description, "");
    }

    #[test]
    fn typing_title_and_description_creates_actions() {
        let mut state = AppState::new();
        state.active_screen = Screen::Tasks;
        state.begin_task_input(TaskInputMode::Creating, String::new(), String::new());

        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Char('h'), KeyModifiers::NONE)));
        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Char('i'), KeyModifiers::NONE)));
        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE)));
        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Char('o'), KeyModifiers::NONE)));
        state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Char('k'), KeyModifiers::NONE)));
        let actions = state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Enter, KeyModifiers::NONE)));

        assert!(
            matches!(actions.as_slice(), [AppAction::CreateTask { title, description }] if title == "hi" && description == "ok")
        );
    }
}
