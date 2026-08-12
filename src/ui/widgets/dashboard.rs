use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout},
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, ListItem, Paragraph},
};

use crate::{
    app::{AppState, PomodoroPhase, PomodoroSession, RunState, Theme, WorkItem},
    ui::widgets::master_detail,
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let sections = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Length(7), Constraint::Min(0)])
        .split(area);

    draw_summary(frame, sections[0], app);

    let body = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(42), Constraint::Percentage(58)])
        .split(sections[1]);

    let left = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Percentage(58), Constraint::Percentage(42)])
        .split(body[0]);

    let right = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Percentage(46), Constraint::Percentage(54)])
        .split(body[1]);

    draw_work_item_list(frame, left[0], app);
    draw_task_list(frame, left[1], app);
    draw_pomodoro_list(frame, right[0], app);
    draw_work_item_panel(frame, right[1], app);
}

fn draw_summary(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let summary = Paragraph::new(vec![
        Line::from(vec![
            Span::styled("work items ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format!("{}", app.work_items.len())),
            Span::raw("  tasks "),
            Span::raw(format!("{}", app.tasks.len())),
            Span::raw("  sessions "),
            Span::raw(format!("{}", app.pomodoro_sessions.len())),
        ]),
        Line::from(""),
        Line::from("dashboard"),
        Line::from("  j/k move work items  enter open run  : scoped menu"),
        Line::from("  work items, tasks, and pomodoro sessions share the same workspace"),
    ])
    .block(Block::default().borders(Borders::ALL).title("control room"))
    .style(style_for_theme(app.theme));

    frame.render_widget(summary, area);
}

fn style_for_theme(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default(),
        Theme::Slate => Style::default(),
    }
}

fn draw_work_item_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .work_items
        .iter()
        .map(|work_item| {
            ListItem::new(Span::styled(
                render_work_item_label(app, work_item),
                style_for_work_item(app.theme, work_item),
            ))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(
        frame,
        area,
        "work items",
        items,
        app.selected_work_item,
        app.theme,
    );
}

fn draw_task_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .tasks
        .iter()
        .map(|task| {
            let label = format!(
                "#{} {}{}",
                task.id,
                task.title,
                if task.doing { " [doing]" } else { "" }
            );
            ListItem::new(Span::styled(
                label,
                style_for_task(app.theme, task.completed, task.doing),
            ))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(frame, area, "tasks", items, app.selected_task, app.theme);
}

fn draw_pomodoro_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .pomodoro_sessions
        .iter()
        .map(|session| {
            let label = render_session_label(session);
            ListItem::new(Span::styled(label, style_for_session(session)))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(
        frame,
        area,
        "pomodoro sessions",
        items,
        app.selected_pomodoro_session,
        app.theme,
    );
}

fn draw_work_item_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    master_detail::render_panel(
        frame,
        area,
        "selected work item",
        match selected_work_item(app) {
            Some(work_item) => render_overview_lines(app, work_item),
            None => vec![
                Line::from("no work item selected"),
                Line::from(""),
                Line::from("j/k move  enter open run"),
            ],
        },
        app.theme,
    );
}

fn render_overview_lines(app: &AppState, work_item: &WorkItem) -> Vec<Line<'static>> {
    let task = resolve_task_title(app, work_item.task_id);
    let note = resolve_note_title(app, work_item.note_id);

    vec![
        Line::from(vec![
            Span::styled("state: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(run_state_label(work_item.run_state)),
        ]),
        Line::from(vec![
            Span::styled("task: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(task),
        ]),
        Line::from(vec![
            Span::styled("note: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note),
        ]),
        Line::from(vec![
            Span::styled("sessions: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format!("{}", work_item.pomodoro_session_ids.len())),
        ]),
        Line::from(vec![
            Span::styled("elapsed: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(work_item.elapsed_seconds)),
        ]),
        Line::from(""),
        Line::from("enter open run  j/k move  : launcher"),
    ]
}

fn selected_work_item(app: &AppState) -> Option<&WorkItem> {
    app.selected_work_item
        .and_then(|index| app.work_items.get(index))
}

fn render_work_item_label(app: &AppState, work_item: &WorkItem) -> String {
    let task = resolve_task_label(app, work_item.task_id);
    let note = resolve_note_label(app, work_item.note_id);

    format!(
        "{}  {} :: {}  ({})",
        run_state_label(work_item.run_state),
        task,
        note,
        format_duration(work_item.elapsed_seconds)
    )
}

fn style_for_work_item(theme: Theme, work_item: &WorkItem) -> Style {
    if work_item.run_state == RunState::Running {
        master_detail::panel_style(theme)
    } else {
        master_detail::inactive_style(theme)
    }
}

fn resolve_task_title(app: &AppState, task_id: Option<i64>) -> String {
    task_id
        .and_then(|task_id| app.tasks.iter().find(|task| task.id == task_id))
        .map(|task| task.title.clone())
        .unwrap_or_else(|| "unassigned task".to_string())
}

fn resolve_note_title(app: &AppState, note_id: Option<i64>) -> String {
    note_id
        .and_then(|note_id| app.notes.iter().find(|note| note.id == note_id))
        .map(|note| note.title.clone())
        .unwrap_or_else(|| "unassigned note".to_string())
}

fn resolve_task_label(app: &AppState, task_id: Option<i64>) -> String {
    task_id
        .and_then(|task_id| app.tasks.iter().find(|task| task.id == task_id))
        .map(|task| task.title.clone())
        .unwrap_or_else(|| "unassigned task".to_string())
}

fn resolve_note_label(app: &AppState, note_id: Option<i64>) -> String {
    note_id
        .and_then(|note_id| app.notes.iter().find(|note| note.id == note_id))
        .map(|note| note.title.clone())
        .unwrap_or_else(|| "unassigned note".to_string())
}

fn style_for_task(theme: Theme, completed: bool, doing: bool) -> Style {
    if doing {
        master_detail::panel_style(theme)
    } else if completed {
        master_detail::inactive_style(theme)
    } else {
        master_detail::inactive_style(theme)
    }
}

fn style_for_session(session: &PomodoroSession) -> Style {
    match session.phase {
        PomodoroPhase::Work => Style::default().add_modifier(Modifier::BOLD),
        PomodoroPhase::Break => Style::default(),
    }
}

fn render_session_label(session: &PomodoroSession) -> String {
    let phase = match session.phase {
        PomodoroPhase::Work => "work",
        PomodoroPhase::Break => "break",
    };

    format!(
        "#{id} {phase} {duration}",
        id = session.id,
        duration = format_duration(u64::from(session.elapsed_seconds))
    )
}

fn run_state_label(state: RunState) -> &'static str {
    match state {
        RunState::Idle => "idle",
        RunState::Running => "running",
        RunState::Paused => "paused",
        RunState::Stopped => "stopped",
    }
}

fn format_duration(total_seconds: u64) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}
