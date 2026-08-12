use crossterm::event::KeyEvent;

use crate::app::Task;

#[derive(Debug, Clone)]
pub enum AppEvent {
    Started,
    Tick,
    Resize,
    Key(KeyEvent),
    TasksLoaded(Vec<Task>),
    TaskCreated(Task),
    TaskUpdated(Task),
    TaskDeleted(i64),
    Message(String),
}

#[derive(Debug, Clone)]
pub enum AppAction {
    None,
    LoadTasks,
    CreateTask { title: String },
    ToggleTask { task_id: i64 },
    DeleteTask { task_id: i64 },
    ShowMessage(String),
}
