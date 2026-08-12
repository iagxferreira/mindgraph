use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::Span,
    widgets::{Block, Borders, List, ListItem, ListState},
    Frame,
};

use crate::app::{AppState, Theme};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Length(3), Constraint::Min(0)])
        .split(area);

    let header = Block::default()
        .title("Tasks")
        .borders(Borders::ALL);
    frame.render_widget(header, chunks[0]);

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

    frame.render_stateful_widget(list, chunks[1], &mut state);
}

fn task_color(theme: Theme) -> Color {
    match theme {
        Theme::Ember => Color::Rgb(255, 214, 170),
        Theme::Slate => Color::Rgb(178, 214, 255),
    }
}
