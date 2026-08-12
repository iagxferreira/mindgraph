use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
};

use crate::app::Theme;

pub fn split(area: Rect) -> [Rect; 2] {
    let chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(58), Constraint::Percentage(42)])
        .split(area);

    [chunks[0], chunks[1]]
}

pub fn render_list(
    frame: &mut Frame<'_>,
    area: Rect,
    title: &str,
    items: Vec<ListItem<'static>>,
    selected: Option<usize>,
    theme: Theme,
) {
    let mut state = ListState::default();
    state.select(selected);

    let list = List::new(items)
        .block(Block::default().title(title).borders(Borders::ALL))
        .highlight_style(highlight_style(theme))
        .highlight_symbol(">> ")
        .style(inactive_style(theme));

    frame.render_stateful_widget(list, area, &mut state);
}

pub fn render_panel(
    frame: &mut Frame<'_>,
    area: Rect,
    title: &str,
    body: Vec<Line<'static>>,
    theme: Theme,
) {
    let panel = Paragraph::new(body)
        .block(Block::default().title(title).borders(Borders::ALL))
        .style(panel_style(theme));

    frame.render_widget(panel, area);
}

pub fn render_editor(
    frame: &mut Frame<'_>,
    area: Rect,
    title: &str,
    body: Vec<Line<'static>>,
    theme: Theme,
) {
    let editor = Paragraph::new(body)
        .block(Block::default().title(title).borders(Borders::ALL))
        .style(panel_style(theme));

    frame.render_widget(editor, area);
}

pub fn focus_line(label: &str, value: &str, focused: bool) -> Line<'static> {
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

pub fn panel_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Rgb(248, 196, 113)),
        Theme::Slate => Style::default().fg(Color::Rgb(140, 180, 255)),
    }
}

pub fn highlight_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default()
            .bg(Color::Rgb(248, 196, 113))
            .fg(Color::Black)
            .add_modifier(Modifier::BOLD),
        Theme::Slate => Style::default()
            .bg(Color::Rgb(140, 180, 255))
            .fg(Color::Black)
            .add_modifier(Modifier::BOLD),
    }
}

pub fn inactive_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Gray),
        Theme::Slate => Style::default().fg(Color::DarkGray),
    }
}
