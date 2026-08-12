use ratatui::{
    Frame,
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::ListItem,
};

use crate::{
    app::{AppState, RunState, Theme, WorkItem},
    ui::widgets::master_detail,
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let [list_area, detail_area] = master_detail::split(area);

    draw_work_item_list(frame, list_area, app);
    draw_dashboard_panel(frame, detail_area, app);
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

fn draw_dashboard_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    master_detail::render_panel(
        frame,
        area,
        "work item",
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
    let task = app
        .tasks
        .iter()
        .find(|task| task.id == work_item.task_id)
        .map(|task| task.title.clone())
        .unwrap_or_else(|| format!("task #{}", work_item.task_id));
    let note = app
        .notes
        .iter()
        .find(|note| note.id == work_item.note_id)
        .map(|note| note.title.clone())
        .unwrap_or_else(|| format!("note #{}", work_item.note_id));

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
    let task = app
        .tasks
        .iter()
        .find(|task| task.id == work_item.task_id)
        .map(|task| task.title.as_str())
        .unwrap_or("unknown task");
    let note = app
        .notes
        .iter()
        .find(|note| note.id == work_item.note_id)
        .map(|note| note.title.as_str())
        .unwrap_or("unknown note");

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
