use std::{
    io,
    io::IsTerminal,
    time::Duration,
};

use crossterm::{
    event::{Event as CrosstermEvent, EventStream},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use futures_util::StreamExt;
use forge::{
    app::{AppAction, AppEvent, AppState},
    services::{TaskService, TaskServiceImpl},
    storage::database::Database,
    ui,
};
use ratatui::backend::CrosstermBackend;
use ratatui::Terminal;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let result = run().await;
    restore_terminal()?;
    result
}

async fn run() -> Result<(), Box<dyn std::error::Error>> {
    if !io::stdout().is_terminal() {
        return Err("Forge requires an interactive terminal".into());
    }

    enable_raw_mode()?;
    execute!(io::stdout(), EnterAlternateScreen)?;

    let backend = CrosstermBackend::new(io::stdout());
    let mut terminal = Terminal::new(backend)?;

    let database = Database::open_default().await?;
    let task_service = TaskServiceImpl::new(database.task_repository());

    let mut app = AppState::new();
    let startup_actions = app.apply(AppEvent::Started);
    process_actions(&mut app, &task_service, startup_actions).await?;

    let mut events = EventStream::new();
    let mut tick = tokio::time::interval(Duration::from_millis(250));

    loop {
        terminal.draw(|frame| ui::draw(frame, &app))?;

        tokio::select! {
            _ = tick.tick() => {
                let actions = app.apply(AppEvent::Tick);
                process_actions(&mut app, &task_service, actions).await?;
            }
            maybe_event = events.next() => {
                if let Some(Ok(event)) = maybe_event {
                    if let CrosstermEvent::Key(key) = event {
                        let actions = app.apply(AppEvent::Key(key));
                        process_actions(&mut app, &task_service, actions).await?;
                    } else if let CrosstermEvent::Resize(_, _) = event {
                        app.apply(AppEvent::Resize);
                    }
                }
            }
        }

        if app.should_quit {
            break;
        }
    }

    Ok(())
}

async fn process_actions(
    app: &mut AppState,
    task_service: &TaskServiceImpl,
    actions: Vec<AppAction>,
) -> Result<(), Box<dyn std::error::Error>> {
    for action in actions {
        match action {
            AppAction::LoadTasks => {
                let tasks = task_service.list_tasks().await?;
                app.apply(AppEvent::TasksLoaded(tasks));
            }
            AppAction::CreateTask { title, description } => {
                let task = task_service.create_task(title, description).await?;
                app.apply(AppEvent::TaskCreated(task));
            }
            AppAction::UpdateTask {
                task_id,
                title,
                description,
            } => {
                let task = task_service
                    .update_task(task_id, title, description)
                    .await?;
                app.apply(AppEvent::TaskUpdated(task));
            }
            AppAction::ToggleTask { task_id } => {
                let task = task_service.toggle_task(task_id).await?;
                app.apply(AppEvent::TaskUpdated(task));
            }
            AppAction::DeleteTask { task_id } => {
                task_service.delete_task(task_id).await?;
                app.apply(AppEvent::TaskDeleted(task_id));
            }
            AppAction::ShowMessage(message) => {
                app.apply(AppEvent::Message(message));
            }
            AppAction::None => {}
        }
    }
    Ok(())
}

fn restore_terminal() -> Result<(), Box<dyn std::error::Error>> {
    disable_raw_mode()?;
    execute!(io::stdout(), LeaveAlternateScreen)?;
    Ok(())
}
