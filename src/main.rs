use std::{collections::VecDeque, io, io::IsTerminal, time::Duration};

use crossterm::{
    event::{Event as CrosstermEvent, EventStream},
    execute,
    terminal::{EnterAlternateScreen, LeaveAlternateScreen, disable_raw_mode, enable_raw_mode},
};
use futures_util::StreamExt;
use mindgraph::{
    app::{AppAction, AppEvent, AppState},
    services::{
        NoteService, NoteServiceImpl, TaskService, TaskServiceImpl, VaultService, VaultServiceImpl,
        WorkspaceService, WorkspaceServiceImpl,
    },
    storage::database::Database,
    ui,
};
use ratatui::Terminal;
use ratatui::backend::CrosstermBackend;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let result = run().await;
    restore_terminal()?;
    result
}

async fn run() -> Result<(), Box<dyn std::error::Error>> {
    if !io::stdout().is_terminal() {
        return Err("MindGraph requires an interactive terminal".into());
    }

    enable_raw_mode()?;
    execute!(io::stdout(), EnterAlternateScreen)?;

    let backend = CrosstermBackend::new(io::stdout());
    let mut terminal = Terminal::new(backend)?;

    let database = Database::open_default().await?;
    let vault_service = VaultServiceImpl::new(database.vault_repository());
    let note_service = NoteServiceImpl::new(database.note_repository());
    let task_service = TaskServiceImpl::new(database.task_repository());
    let workspace_service = WorkspaceServiceImpl::new(database.workspace_repository());

    let mut app = AppState::new();
    let startup_actions = app.apply(AppEvent::Started);
    process_actions(
        &mut app,
        &vault_service,
        &note_service,
        &task_service,
        &workspace_service,
        startup_actions,
    )
    .await?;

    let mut events = EventStream::new();
    let mut tick = tokio::time::interval(Duration::from_secs(1));

    loop {
        terminal.draw(|frame| ui::draw(frame, &app))?;

        tokio::select! {
            _ = tick.tick() => {
                let actions = app.apply(AppEvent::Tick);
                process_actions(
                    &mut app,
                    &vault_service,
                    &note_service,
                    &task_service,
                    &workspace_service,
                    actions,
                )
                .await?;
            }
            maybe_event = events.next() => {
                if let Some(Ok(event)) = maybe_event {
                    if let CrosstermEvent::Key(key) = event {
                        let actions = app.apply(AppEvent::Key(key));
                        process_actions(
                            &mut app,
                            &vault_service,
                            &note_service,
                            &task_service,
                            &workspace_service,
                            actions,
                        )
                        .await?;
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
    vault_service: &VaultServiceImpl,
    note_service: &NoteServiceImpl,
    task_service: &TaskServiceImpl,
    workspace_service: &WorkspaceServiceImpl,
    actions: Vec<AppAction>,
) -> Result<(), Box<dyn std::error::Error>> {
    let mut pending: VecDeque<AppAction> = actions.into();

    while let Some(action) = pending.pop_front() {
        let next_actions = match action {
            AppAction::LoadVaults => {
                let vaults = vault_service.list_vaults().await?;
                app.apply(AppEvent::VaultsLoaded(vaults))
            }
            AppAction::LoadAllNotes => {
                let notes = note_service.list_all_notes().await?;
                app.apply(AppEvent::NotesLoaded(notes))
            }
            AppAction::LoadNoteDocument { path } => {
                let document = note_service.read_note_document(path.clone()).await?;
                let note_id = app
                    .notes
                    .iter()
                    .find(|note| note.path == path)
                    .map(|note| note.id)
                    .unwrap_or_default();
                app.apply(AppEvent::NoteDocumentLoaded { note_id, document })
            }
            AppAction::LoadTasks => {
                let tasks = task_service.list_tasks().await?;
                app.apply(AppEvent::TasksLoaded(tasks))
            }
            AppAction::LoadWorkspaces => {
                let workspaces = workspace_service.list_workspaces().await?;
                app.apply(AppEvent::WorkspacesLoaded(workspaces))
            }
            AppAction::CreateNote {
                vault_id,
                title,
                slug,
                document,
            } => {
                let note = note_service
                    .create_note(vault_id, title, slug, document)
                    .await?;
                app.apply(AppEvent::NoteCreated(note))
            }
            AppAction::UpdateNote {
                note_id,
                title,
                slug,
                document,
            } => {
                let note = note_service
                    .update_note(note_id, title, slug, document)
                    .await?;
                app.apply(AppEvent::NoteUpdated(note))
            }
            AppAction::DeleteNote { note_id } => {
                note_service.delete_note(note_id).await?;
                app.apply(AppEvent::NoteDeleted(note_id))
            }
            AppAction::CreateTask { title, description } => {
                let task = task_service.create_task(title, description).await?;
                app.apply(AppEvent::TaskCreated(task))
            }
            AppAction::UpdateTask {
                task_id,
                title,
                description,
            } => {
                let task = task_service
                    .update_task(task_id, title, description)
                    .await?;
                app.apply(AppEvent::TaskUpdated(task))
            }
            AppAction::ToggleTask { task_id } => {
                let task = task_service.toggle_task(task_id).await?;
                app.apply(AppEvent::TaskUpdated(task))
            }
            AppAction::DeleteTask { task_id } => {
                task_service.delete_task(task_id).await?;
                app.apply(AppEvent::TaskDeleted(task_id))
            }
            AppAction::CreateWorkspace { name, path } => {
                let workspace = workspace_service.create_workspace(name, path).await?;
                app.apply(AppEvent::WorkspaceCreated(workspace))
            }
            AppAction::UpdateWorkspace {
                workspace_id,
                name,
                path,
            } => {
                let workspace = workspace_service
                    .update_workspace(workspace_id, name, path)
                    .await?;
                app.apply(AppEvent::WorkspaceUpdated(workspace))
            }
            AppAction::DeleteWorkspace { workspace_id } => {
                workspace_service.delete_workspace(workspace_id).await?;
                app.apply(AppEvent::WorkspaceDeleted(workspace_id))
            }
            AppAction::ShowMessage(message) => app.apply(AppEvent::Message(message)),
            AppAction::None => vec![AppAction::None],
        };

        for next_action in next_actions
            .into_iter()
            .filter(|action| !matches!(action, AppAction::None))
        {
            pending.push_back(next_action);
        }
    }
    Ok(())
}

fn restore_terminal() -> Result<(), Box<dyn std::error::Error>> {
    disable_raw_mode()?;
    execute!(io::stdout(), LeaveAlternateScreen)?;
    Ok(())
}
