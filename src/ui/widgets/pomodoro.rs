use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout},
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
};

use crate::app::{AppState, PomodoroPhase, Theme};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let sections = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Length(8), Constraint::Min(0)])
        .split(area);

    let summary = Paragraph::new(render_summary_lines(app))
        .block(Block::default().borders(Borders::ALL).title("pomodoro"))
        .style(style_for_theme(app.theme));
    frame.render_widget(summary, sections[0]);

    let sessions = render_sessions(app);
    let mut state = ListState::default();
    state.select(app.selected_pomodoro_session);

    let list = List::new(sessions)
        .block(
            Block::default()
                .borders(Borders::ALL)
                .title("saved sessions"),
        )
        .highlight_style(highlight_style(app.theme))
        .highlight_symbol(">> ")
        .style(style_for_theme(app.theme));
    frame.render_stateful_widget(list, sections[1], &mut state);
}

fn render_summary_lines(app: &AppState) -> Vec<Line<'static>> {
    let phase = match app.pomodoro.phase {
        PomodoroPhase::Work => "work",
        PomodoroPhase::Break => "break",
    };

    vec![
        Line::from(vec![
            Span::styled("state: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(if app.pomodoro.running {
                "running"
            } else {
                "paused"
            }),
        ]),
        Line::from(vec![
            Span::styled("phase: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(phase),
        ]),
        Line::from(vec![
            Span::styled("remaining: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(app.pomodoro.remaining_seconds.into())),
        ]),
        Line::from(vec![
            Span::styled("elapsed: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(format_duration(app.pomodoro.elapsed_seconds.into())),
        ]),
        Line::from(vec![
            Span::styled("task: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(current_task_label(app)),
        ]),
        Line::from(""),
        Line::from("controls"),
        Line::from("  p pause/resume  s stop/save"),
        Line::from("  j/k browse saved sessions"),
        Line::from("  m mark a task doing from Tasks"),
    ]
}

fn render_sessions(app: &AppState) -> Vec<ListItem<'static>> {
    if app.pomodoro_sessions.is_empty() {
        return vec![ListItem::new(Line::from("no saved pomodoros yet"))];
    }

    app.pomodoro_sessions
        .iter()
        .map(|session| {
            let label = match session.phase {
                PomodoroPhase::Work => "work",
                PomodoroPhase::Break => "break",
            };
            let task_label = session
                .task_id
                .and_then(|task_id| {
                    app.tasks
                        .iter()
                        .find(|task| task.id == task_id)
                        .map(|task| task.title.clone())
                })
                .unwrap_or_else(|| "no task".to_string());

            ListItem::new(Line::from(vec![
                Span::styled(
                    format!(
                        "{label} {}",
                        format_duration(session.elapsed_seconds.into())
                    ),
                    Style::default().add_modifier(Modifier::BOLD),
                ),
                Span::raw("  "),
                Span::styled(task_label, Style::default()),
            ]))
        })
        .collect()
}

fn current_task_label(app: &AppState) -> String {
    app.pomodoro
        .task_id
        .and_then(|task_id| {
            app.tasks
                .iter()
                .find(|task| task.id == task_id)
                .map(|task| {
                    format!(
                        "{} ({} tracked)",
                        task.title,
                        format_duration(task.tracked_seconds.min(u64::from(u32::MAX)))
                    )
                })
        })
        .unwrap_or_else(|| "none".to_string())
}

fn style_for_theme(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(ratatui::style::Color::Rgb(248, 196, 113)),
        Theme::Slate => Style::default().fg(ratatui::style::Color::Rgb(140, 180, 255)),
    }
}

fn highlight_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default()
            .bg(ratatui::style::Color::Rgb(248, 196, 113))
            .fg(ratatui::style::Color::Black)
            .add_modifier(Modifier::BOLD),
        Theme::Slate => Style::default()
            .bg(ratatui::style::Color::Rgb(140, 180, 255))
            .fg(ratatui::style::Color::Black)
            .add_modifier(Modifier::BOLD),
    }
}

fn format_duration(total_seconds: u64) -> String {
    let minutes = total_seconds / 60;
    let seconds = total_seconds % 60;
    format!("{minutes:02}:{seconds:02}")
}
