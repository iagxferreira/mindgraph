use ratatui::{
    Frame,
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::ListItem,
};

use crate::{
    app::{AppState, RunState, WorkItem},
    ui::widgets::master_detail,
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let [list_area, detail_area] = master_detail::split(area);

    draw_work_item_list(frame, list_area, app);
    draw_work_item_panel(frame, detail_area, app);
}

fn draw_work_item_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .work_items
        .iter()
        .map(|work_item| {
            let status = run_state_label(work_item.run_state);
            let task_title = resolve_task_title(app, work_item.task_id);
            let note_title = resolve_note_title(app, work_item.note_id);
            let content = format!("{status} {task_title} :: {note_title}");
            let style = if work_item.run_state == RunState::Running {
                master_detail::panel_style(app.theme)
            } else {
                master_detail::inactive_style(app.theme)
            };
            ListItem::new(Span::styled(content, style))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(
        frame,
        area,
        "run items",
        items,
        app.selected_work_item,
        app.theme,
    );
}

fn draw_work_item_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    master_detail::render_panel(
        frame,
        area,
        "run details",
        match selected_work_item(app) {
            Some(work_item) => render_details_lines(app, work_item),
            None => vec![
                Line::from("no work item selected"),
                Line::from(""),
                Line::from("use j/k or arrows to move"),
            ],
        },
        app.theme,
    );
}

fn selected_work_item(app: &AppState) -> Option<&WorkItem> {
    app.selected_work_item
        .and_then(|index| app.work_items.get(index))
}

fn render_details_lines(app: &AppState, work_item: &WorkItem) -> Vec<Line<'static>> {
    let task_title = resolve_task_title(app, work_item.task_id);
    let note_title = resolve_note_title(app, work_item.note_id);
    let pomodoro_label = work_item
        .pomodoro_session_id
        .map(|session_id| format!("session #{session_id}"))
        .unwrap_or_else(|| "none".to_string());

    vec![
        Line::from(vec![
            Span::styled("state: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(run_state_label(work_item.run_state)),
        ]),
        Line::from(vec![
            Span::styled("task: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(task_title),
        ]),
        Line::from(vec![
            Span::styled("note: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note_title),
        ]),
        Line::from(vec![
            Span::styled("pomodoro: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(pomodoro_label),
        ]),
        Line::from(vec![
            Span::styled("elapsed: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(work_item.elapsed_seconds)),
        ]),
        Line::from(vec![
            Span::styled("started: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_timestamp(work_item.started_at_unix)),
        ]),
        Line::from(vec![
            Span::styled("stopped: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_timestamp(work_item.stopped_at_unix)),
        ]),
        Line::from(""),
        Line::from("j/k move  enter select  use launcher for edits"),
    ]
}

fn resolve_task_title(app: &AppState, task_id: i64) -> String {
    app.tasks
        .iter()
        .find(|task| task.id == task_id)
        .map(|task| task.title.clone())
        .unwrap_or_else(|| format!("task #{task_id}"))
}

fn resolve_note_title(app: &AppState, note_id: i64) -> String {
    app.notes
        .iter()
        .find(|note| note.id == note_id)
        .map(|note| note.title.clone())
        .unwrap_or_else(|| format!("note #{note_id}"))
}

fn run_state_label(state: RunState) -> &'static str {
    match state {
        RunState::Idle => "idle",
        RunState::Running => "running",
        RunState::Paused => "paused",
        RunState::Stopped => "stopped",
    }
}

fn format_timestamp(value: Option<i64>) -> String {
    value
        .map(|timestamp| timestamp.to_string())
        .unwrap_or_else(|| "none".to_string())
}

fn format_duration(total_seconds: u64) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}
