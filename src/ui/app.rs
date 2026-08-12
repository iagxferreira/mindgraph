use ratatui::{
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    symbols,
    text::{Line, Span},
    widgets::{block::Title, Block, Borders, Paragraph, Tabs},
    Frame,
};

use crate::{
    app::{AppState, Screen, Theme},
    ui::widgets::{dashboard, tasks},
};

pub fn draw(frame: &mut Frame<'_>, app: &AppState) {
    let root = frame.area();
    let chunks = Layout::default()
        .direction(Direction::Vertical)
        .constraints([Constraint::Length(3), Constraint::Min(0), Constraint::Length(2)])
        .split(root);

    draw_header(frame, chunks[0], app);

    match app.active_screen {
        Screen::Dashboard => dashboard::draw(frame, chunks[1], app),
        Screen::Tasks => tasks::draw(frame, chunks[1], app),
        Screen::Notifications => dashboard::draw(frame, chunks[1], app),
        Screen::Workspaces => dashboard::draw(frame, chunks[1], app),
    }

    draw_command_bar(frame, chunks[2], app);
}

fn draw_header(frame: &mut Frame<'_>, area: Rect, app: &AppState) {
    let titles = ["dashboard", "tasks", "notifications", "workspaces"]
        .into_iter()
        .map(Line::from)
        .collect::<Vec<_>>();

    let tabs = Tabs::new(titles)
        .select(match app.active_screen {
            Screen::Dashboard => 0,
            Screen::Tasks => 1,
            Screen::Notifications => 2,
            Screen::Workspaces => 3,
        })
        .block(
            Block::default()
                .borders(Borders::BOTTOM)
                .title(Title::from("forge")),
        )
        .highlight_style(active_style(app.theme))
        .divider(symbols::DOT)
        .style(inactive_style(app.theme));

    frame.render_widget(tabs, area);
}

fn draw_command_bar(frame: &mut Frame<'_>, area: Rect, app: &AppState) {
    let commands = command_hints(app);
    let bar = Paragraph::new(Line::from(commands))
        .block(
            Block::default()
                .borders(Borders::TOP)
                .title(Line::from(vec![
                    Span::styled("status: ", Style::default().add_modifier(Modifier::BOLD)),
                    Span::raw(app.status_line.clone()),
                ])),
        )
        .style(inactive_style(app.theme));

    frame.render_widget(bar, area);
}

fn command_hints(app: &AppState) -> Vec<Span<'static>> {
    let mut hints = vec![
        Span::styled("ctrl+l", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": switch screen"),
        Span::raw("  •  "),
        Span::styled("ctrl+h", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": previous screen"),
        Span::raw("  •  "),
        Span::styled("q", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": quit"),
    ];

    match app.active_screen {
        Screen::Tasks if app.task_input_mode.is_some() => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("enter", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": next field/save"),
                Span::raw("  •  "),
                Span::styled("tab", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": switch field"),
                Span::raw("  •  "),
                Span::styled("esc", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": cancel"),
            ]);
        }
        Screen::Tasks => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("j/k", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": move"),
                Span::raw("  •  "),
                Span::styled("a", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": add"),
                Span::raw("  •  "),
                Span::styled("e/enter", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": edit"),
                Span::raw("  •  "),
                Span::styled("space", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": toggle"),
                Span::raw("  •  "),
                Span::styled("d", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": delete"),
                Span::raw("  •  "),
                Span::styled("t", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": theme"),
            ]);
        }
        Screen::Dashboard => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("p", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(if app.pomodoro.running { ": pause pomodoro" } else { ": start pomodoro" }),
                Span::raw("  •  "),
                Span::styled("r", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": reset pomodoro"),
                Span::raw("  •  "),
                Span::styled("t", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": theme"),
            ]);
        }
        Screen::Notifications | Screen::Workspaces => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("j/k", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": move"),
            ]);
        }
    }

    hints
}

fn active_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default()
            .fg(Color::Black)
            .bg(Color::Yellow)
            .add_modifier(Modifier::BOLD),
        Theme::Slate => Style::default()
            .fg(Color::Black)
            .bg(Color::Cyan)
            .add_modifier(Modifier::BOLD),
    }
}

fn inactive_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Gray),
        Theme::Slate => Style::default().fg(Color::DarkGray),
    }
}
