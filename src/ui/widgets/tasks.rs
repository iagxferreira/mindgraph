use ratatui::{
    Frame,
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::ListItem,
};

use crate::{
    app::{AppState, Task, TaskInputField, TaskInputMode},
    ui::widgets::master_detail,
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let [list_area, detail_area] = master_detail::split(area);

    draw_task_list(frame, list_area, app);
    draw_task_panel(frame, detail_area, app);
}

fn draw_task_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .tasks
        .iter()
        .map(|task| {
            let checkbox = if task.completed {
                "[x]"
            } else if task.doing {
                "[>]"
            } else {
                "[ ]"
            };
            let content = format!(
                "{checkbox} {} ({})",
                task.title,
                format_duration(task.tracked_seconds)
            );
            let style = if task.completed {
                master_detail::inactive_style(app.theme)
            } else {
                master_detail::panel_style(app.theme)
            };
            ListItem::new(Span::styled(content, style))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(frame, area, "tasks", items, app.selected_task, app.theme);
}

fn draw_task_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(mode) = &app.task_input_mode {
        master_detail::render_editor(
            frame,
            area,
            editor_title(mode),
            render_editor_lines(app, mode),
            app.theme,
        );
        return;
    }

    master_detail::render_panel(
        frame,
        area,
        "task details",
        match selected_task(app) {
            Some(task) => render_details_lines(task),
            None => vec![
                Line::from("no task selected"),
                Line::from(""),
                Line::from("use j/k or arrows to move"),
                Line::from("press a to add a task"),
            ],
        },
        app.theme,
    );
}

fn selected_task(app: &AppState) -> Option<&Task> {
    app.selected_task.and_then(|index| app.tasks.get(index))
}

fn render_details_lines(task: &Task) -> Vec<Line<'static>> {
    let description = if task.description.trim().is_empty() {
        "no description".to_string()
    } else {
        task.description.clone()
    };

    vec![
        Line::from(vec![
            Span::styled("title: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(task.title.clone()),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled(
                "description: ",
                Style::default().add_modifier(Modifier::BOLD),
            ),
            Span::raw(description),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("status: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(if task.completed {
                "completed"
            } else if task.doing {
                "doing"
            } else {
                "open"
            }),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("tracked: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(task.tracked_seconds)),
        ]),
        Line::from(""),
        Line::from("e edit  space toggle done  m mark doing  d delete"),
    ]
}

fn render_editor_lines(app: &AppState, mode: &TaskInputMode) -> Vec<Line<'static>> {
    let title_focus = matches!(app.task_input_focus, TaskInputField::Title);
    let description_focus = matches!(app.task_input_focus, TaskInputField::Description);
    let description_hint = if let Some(work_item_id) = app.task_input_work_item_id {
        format!("create a task for work item #{work_item_id}.")
    } else {
        match mode {
            TaskInputMode::Creating => "create a task with a title and description.".to_string(),
            TaskInputMode::Editing { .. } => {
                "edit the selected task title and description.".to_string()
            }
        }
    };

    vec![
        Line::from(description_hint),
        Line::from(""),
        master_detail::focus_line("title", &app.task_input_title, title_focus),
        Line::from(""),
        master_detail::focus_line(
            "description",
            &app.task_input_description,
            description_focus,
        ),
        Line::from(""),
        Line::from("enter next field/save  tab switch field  esc cancel"),
    ]
}

fn format_duration(total_seconds: u64) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}

fn editor_title(mode: &TaskInputMode) -> &'static str {
    match mode {
        TaskInputMode::Creating => "new task",
        TaskInputMode::Editing { .. } => "edit task",
    }
}
