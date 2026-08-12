use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
    Frame,
};

use crate::app::{AppState, TaskInputMode, Theme};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let mut constraints = vec![Constraint::Length(3)];
    if app.task_input_mode.is_some() {
        constraints.push(Constraint::Length(5));
    }
    constraints.push(Constraint::Min(0));

    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints(constraints)
        .split(area);

    let header = Block::default()
        .title("Tasks")
        .borders(Borders::ALL);
    frame.render_widget(header, chunks[0]);

    let mut content_index = 1;
    if let Some(mode) = &app.task_input_mode {
        let editor = Paragraph::new(render_editor_lines(app, mode))
            .block(Block::default().title(editor_title(mode)).borders(Borders::ALL))
            .style(style_for_theme(app.theme));
        frame.render_widget(editor, chunks[1]);
        content_index = 2;
    }

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
        .block(Block::default().title("Task List").borders(Borders::ALL))
        .highlight_style(
            Style::default()
                .bg(Color::White)
                .fg(Color::Black)
                .add_modifier(Modifier::BOLD),
        )
        .highlight_symbol(">> ");

    frame.render_stateful_widget(list, chunks[content_index], &mut state);
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

fn render_editor_lines(app: &AppState, mode: &TaskInputMode) -> Vec<Line<'static>> {
    let description = match mode {
        TaskInputMode::Creating => "Type a new task title and press Enter to save.",
        TaskInputMode::Editing { .. } => "Edit the task title and press Enter to save.",
    };

    vec![
        Line::from(description),
        Line::from(""),
        Line::from(vec![
            Span::styled("> ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(app.task_input.clone()),
        ]),
        Line::from(""),
        Line::from("Enter save  Esc cancel"),
    ]
}

fn editor_title(mode: &TaskInputMode) -> &'static str {
    match mode {
        TaskInputMode::Creating => "New Task",
        TaskInputMode::Editing { .. } => "Edit Task",
    }
}
