use std::time::{SystemTime, UNIX_EPOCH};

use crossterm::event::{KeyCode, KeyEvent};
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
                self.status_line = format!("Loaded {} tasks", self.tasks.len());
                vec![AppAction::None]
            }
            AppEvent::TaskCreated(task) => {
                self.tasks.push(task);
                self.sync_selection();
                self.status_line = "Task created".to_string();
                vec![AppAction::None]
            }
            AppEvent::TaskUpdated(task) => {
                if let Some(existing) = self.tasks.iter_mut().find(|current| current.id == task.id) {
                    *existing = task;
                }
                self.status_line = "Task updated".to_string();
                vec![AppAction::None]
            }
            AppEvent::TaskDeleted(task_id) => {
                self.tasks.retain(|task| task.id != task_id);
                self.sync_selection();
                self.status_line = "Task deleted".to_string();
                vec![AppAction::None]
            }
            AppEvent::Message(message) => {
                self.status_line = message;
                vec![AppAction::None]
            }
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> Vec<AppAction> {
        match key.code {
            KeyCode::Char('q') => {
                self.should_quit = true;
                vec![AppAction::ShowMessage("Quitting".to_string())]
            }
            KeyCode::Tab => {
                self.active_screen = next_screen(self.active_screen);
                self.status_line = format!("Switched to {:?}", self.active_screen);
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
                let title = format!("Task {}", self.tasks.len() + 1);
                vec![AppAction::CreateTask { title }]
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
                self.status_line = "Theme switched".to_string();
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
    fn tab_cycles_between_screens() {
        let mut state = AppState::new();
        let actions = state.apply(AppEvent::Key(KeyEvent::new(KeyCode::Tab, KeyModifiers::NONE)));

        assert_eq!(state.active_screen, Screen::Tasks);
        assert!(matches!(actions.as_slice(), [AppAction::None]));
    }

    #[test]
    fn task_selection_is_clamped_when_tasks_change() {
        let mut state = AppState::new();
        state.selected_task = Some(4);

        state.apply(AppEvent::TasksLoaded(vec![
            Task {
                id: 1,
                title: "one".to_string(),
                completed: false,
                created_at_unix: 0,
            },
            Task {
                id: 2,
                title: "two".to_string(),
                completed: false,
                created_at_unix: 0,
            },
        ]));

        assert_eq!(state.selected_task, Some(1));
    }
}
