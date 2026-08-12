use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
    Frame,
};

use crate::app::{AppState, PomodoroPhase, Theme};

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

    let body_chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(58), Constraint::Percentage(42)])
        .split(sections[1]);

    let content = Paragraph::new("dashboard is the command center for the first forge milestone.")
        .block(Block::default().borders(Borders::ALL).title("overview"))
        .style(style_for_theme(app.theme));
    frame.render_widget(content, body_chunks[0]);

    let pomodoro = Paragraph::new(render_pomodoro_lines(app))
        .block(Block::default().borders(Borders::ALL).title("pomodoro"))
        .style(style_for_theme(app.theme));
    frame.render_widget(pomodoro, body_chunks[1]);
}

fn style_for_theme(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Rgb(248, 196, 113)),
        Theme::Slate => Style::default().fg(Color::Rgb(140, 180, 255)),
    }
}

fn render_pomodoro_lines(app: &AppState) -> Vec<Line<'static>> {
    let phase = match app.pomodoro.phase {
        PomodoroPhase::Work => "work",
        PomodoroPhase::Break => "break",
    };

    vec![
        Line::from(vec![
            Span::styled("phase: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(phase),
        ]),
        Line::from(vec![
            Span::styled("state: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(if app.pomodoro.running { "running" } else { "paused" }),
        ]),
        Line::from(vec![
            Span::styled("remaining: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(app.pomodoro.remaining_seconds)),
        ]),
        Line::from(vec![
            Span::styled("sessions: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format!("{}", app.pomodoro.completed_sessions)),
        ]),
        Line::from(""),
        Line::from("p start/pause  r reset"),
    ]
}

fn format_duration(total_seconds: u32) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}
