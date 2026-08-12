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
            task_input_mode: None,
            task_input_focus: TaskInputField::Title,
            task_input_title: String::new(),
            task_input_description: String::new(),
            should_quit: false,
        }
    }

    pub fn apply(&mut self, event: AppEvent) -> Vec<AppAction> {
        match event {
            AppEvent::Started => vec![AppAction::LoadTasks],
            AppEvent::Tick => vec![AppAction::None],
            AppEvent::Resize => vec![AppAction::None],
            AppEvent::Key(key) => self.handle_key(key),
            AppEvent::TasksLoaded(tasks) => {
                self.tasks = tasks;
                self.sync_selection();
                self.status_line = format!("loaded {} tasks", self.tasks.len());
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
            AppEvent::Message(message) => {
                self.status_line = message;
                vec![AppAction::None]
            }
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
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
            KeyCode::Char(' ') => self
                .selected_task_id()
                .map(|task_id| vec![AppAction::ToggleTask { task_id }])
                .unwrap_or_else(|| vec![AppAction::None]),
            KeyCode::Char('a') => {
                self.begin_task_input(TaskInputMode::Creating, String::new(), String::new());
                vec![AppAction::None]
            }
            KeyCode::Char('e') | KeyCode::Enter => {
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
            KeyCode::Char('d') => self
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

    fn sync_selection(&mut self) {
        if self.tasks.is_empty() {
            self.selected_task = None;
        } else {
            self.selected_task = Some(self.selected_task.unwrap_or(0).min(self.tasks.len() - 1));
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
        assert_eq!(state.active_screen, Screen::Dashboard);
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
