use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
    Frame,
};

use crate::app::{AppState, Task, TaskInputField, TaskInputMode, Theme};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(58), Constraint::Percentage(42)])
        .split(area);

    draw_task_list(frame, chunks[0], app);
    draw_task_panel(frame, chunks[1], app);
}

fn draw_task_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .tasks
        .iter()
        .map(|task| {
            let checkbox = if task.completed { "[x]" } else { "[ ]" };
            let content = format!("{checkbox} {}", task.title);
            let style = if task.completed {
                Style::default().fg(Color::DarkGray)
            } else {
                Style::default().fg(task_color(app.theme))
            };
            ListItem::new(Span::styled(content, style))
        })
        .collect::<Vec<_>>();

    let mut state = ListState::default();
    state.select(app.selected_task);

    let list = List::new(items)
        .block(Block::default().title("tasks").borders(Borders::ALL))
        .highlight_style(
            Style::default()
                .bg(Color::White)
                .fg(Color::Black)
                .add_modifier(Modifier::BOLD),
        )
        .highlight_symbol(">> ");

    frame.render_stateful_widget(list, area, &mut state);
}

fn draw_task_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(mode) = &app.task_input_mode {
        let editor = Paragraph::new(render_editor_lines(app, mode))
            .block(Block::default().title(editor_title(mode)).borders(Borders::ALL))
            .style(style_for_theme(app.theme));
        frame.render_widget(editor, area);
        return;
    }

    let body = match selected_task(app) {
        Some(task) => render_details_lines(task),
        None => vec![
            Line::from("no task selected"),
            Line::from(""),
            Line::from("use j/k or arrows to move"),
            Line::from("press a to add a task"),
        ],
    };

    let details = Paragraph::new(body)
        .block(Block::default().title("task details").borders(Borders::ALL))
        .style(style_for_theme(app.theme));
    frame.render_widget(details, area);
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
            Span::styled("description: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(description),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("status: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(if task.completed { "completed" } else { "open" }),
        ]),
        Line::from(""),
        Line::from("e edit  space toggle  d delete"),
    ]
}

fn render_editor_lines(app: &AppState, mode: &TaskInputMode) -> Vec<Line<'static>> {
    let title_focus = matches!(app.task_input_focus, TaskInputField::Title);
    let description_focus = matches!(app.task_input_focus, TaskInputField::Description);

    let description_hint = match mode {
        TaskInputMode::Creating => "create a task with a title and description.",
        TaskInputMode::Editing { .. } => "edit the selected task title and description.",
    };

    vec![
        Line::from(description_hint),
        Line::from(""),
        focus_line("title", &app.task_input_title, title_focus),
        Line::from(""),
        focus_line("description", &app.task_input_description, description_focus),
        Line::from(""),
        Line::from("enter next field/save  tab switch field  esc cancel"),
    ]
}

fn focus_line(label: &str, value: &str, focused: bool) -> Line<'static> {
    let prefix = if focused { "> " } else { "  " };
    Line::from(vec![
        Span::styled(prefix, Style::default().add_modifier(Modifier::BOLD)),
        Span::styled(
            format!("{label}: "),
            Style::default().add_modifier(Modifier::BOLD),
        ),
        Span::raw(value.to_string()),
    ])
}

fn editor_title(mode: &TaskInputMode) -> &'static str {
    match mode {
        TaskInputMode::Creating => "new task",
        TaskInputMode::Editing { .. } => "edit task",
    }
}

fn task_color(theme: Theme) -> Color {
    match theme {
        Theme::Ember => Color::Rgb(255, 214, 170),
        Theme::Slate => Color::Rgb(178, 214, 255),
    }
}

fn style_for_theme(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Rgb(248, 196, 113)),
        Theme::Slate => Style::default().fg(Color::Rgb(140, 180, 255)),
    }
}
