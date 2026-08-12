use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
    Frame,
};

use crate::app::{AppState, Theme};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let sections = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Length(7), Constraint::Min(0)])
        .split(area);

    let summary = Paragraph::new(vec![
        Line::from(vec![
            Span::styled("tasks ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format!("{}", app.tasks.len())),
            Span::raw("  completed "),
            Span::raw(format!(
                "{}",
                app.tasks.iter().filter(|task| task.completed).count()
            )),
        ]),
        Line::from(""),
        Line::from("keyboard"),
        Line::from("  ctrl+l switch screen"),
        Line::from("  j/k or arrows move selection"),
        Line::from("  space toggle task  a add  d delete  t theme  q quit"),
    ])
    .block(
        Block::default()
            .borders(Borders::ALL)
            .title("dashboard"),
    )
    .style(style_for_theme(app.theme));
    frame.render_widget(summary, sections[0]);

    let content = Paragraph::new("dashboard is the command center for the first forge milestone.")
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title("overview"),
        )
        .style(style_for_theme(app.theme));
    frame.render_widget(content, sections[1]);
}

fn style_for_theme(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Rgb(248, 196, 113)),
        Theme::Slate => Style::default().fg(Color::Rgb(140, 180, 255)),
    }
}
