use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
};

use crate::{
    app::{AppState, Screen, Theme},
    ui::widgets::{dashboard, launcher, mind, pomodoro, run, tasks, workspaces},
};

pub fn draw(frame: &mut Frame<'_>, app: &AppState) {
    let root = frame.area();
    let shell = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(4),
            Constraint::Min(0),
            Constraint::Length(2),
        ])
        .split(root);

    draw_header(frame, shell[0], app);

    match app.active_screen {
        Screen::Dashboard => dashboard::draw(frame, shell[1], app),
        Screen::Pomodoro => pomodoro::draw(frame, shell[1], app),
        Screen::Run => run::draw(frame, shell[1], app),
        Screen::Tasks => tasks::draw(frame, shell[1], app),
        Screen::Mind => mind::draw(frame, shell[1], app),
        Screen::Notifications => dashboard::draw(frame, shell[1], app),
        Screen::Workspaces => workspaces::draw(frame, shell[1], app),
    }

    draw_command_bar(frame, shell[2], app);

    if app.launcher.is_some() {
        launcher::draw(frame, app);
    }
}

fn draw_header(frame: &mut Frame<'_>, area: Rect, app: &AppState) {
    let header = Paragraph::new(Line::from(vec![
        Span::styled("mindgraph", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw("  "),
        Span::raw("pane-first knowledge shell"),
        Span::raw("  "),
        Span::styled("screen: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(screen_label(app.active_screen)),
        if matches!(app.active_screen, Screen::Pomodoro) {
            Span::styled("controls: ", Style::default().add_modifier(Modifier::BOLD))
        } else {
            Span::raw("")
        },
        if matches!(app.active_screen, Screen::Pomodoro) {
            Span::raw("p pause/resume  s stop/save  t attach task  c clear task")
        } else {
            Span::raw("")
        },
        Span::raw("  "),
        Span::styled("tasks: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(format!("{}", app.tasks.len())),
        Span::raw("  "),
        Span::styled("notes: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(format!("{}", app.notes.len())),
        Span::raw("  "),
        Span::styled(
            "workspaces: ",
            Style::default().add_modifier(Modifier::BOLD),
        ),
        Span::raw(format!("{}", app.workspaces.len())),
        Span::raw("  "),
        Span::styled("palette: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(if app.launcher.is_some() {
            "open"
        } else {
            "closed"
        }),
    ]))
    .block(Block::default().borders(Borders::BOTTOM))
    .style(inactive_style(app.theme));

    frame.render_widget(header, area);
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
        Span::styled(":", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": scoped menu"),
        Span::raw("  •  "),
        Span::styled("ctrl+l", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": next screen"),
        Span::raw("  •  "),
        Span::styled("ctrl+h", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": previous screen"),
        Span::raw("  •  "),
        Span::styled("q", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(": quit"),
    ];

    match app.active_screen {
        Screen::Mind if app.mind_draft.is_some() => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("tab", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": insert spaces"),
                Span::raw("  •  "),
                Span::styled("ctrl+s", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": save"),
                Span::raw("  •  "),
                Span::styled("esc", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": cancel"),
            ]);
        }
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
        Screen::Pomodoro => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("p", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(if app.pomodoro.running {
                    ": pause"
                } else {
                    ": resume"
                }),
                Span::raw("  •  "),
                Span::styled("s", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": stop/save"),
                Span::raw("  •  "),
                Span::styled("t", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": attach selected task"),
                Span::raw("  •  "),
                Span::styled("c", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": clear task"),
                Span::raw("  •  "),
                Span::styled("j/k", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": sessions"),
            ]);
        }
        Screen::Workspaces if app.workspace_input_mode.is_some() => {
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
                Span::styled("m", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": mark doing"),
                Span::raw("  •  "),
                Span::styled("t", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": theme"),
            ]);
        }
        Screen::Run => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("j/k", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": move work items"),
                Span::raw("  •  "),
                Span::styled("a", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": bind task/note"),
                Span::raw("  •  "),
                Span::styled("r", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": start"),
                Span::raw("  •  "),
                Span::styled("p", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": pause"),
                Span::raw("  •  "),
                Span::styled("s", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": stop"),
            ]);
        }
        Screen::Mind => {
            hints.extend([
                Span::raw("  •  "),
                Span::styled("j/k", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": move"),
                Span::raw("  •  "),
                Span::styled("h/l", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": collapse/expand"),
                Span::raw("  •  "),
                Span::styled("e/enter", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": open"),
                Span::raw("  •  "),
                Span::styled("a", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": add"),
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
                Span::raw(if app.pomodoro.running {
                    ": pause pomodoro"
                } else {
                    ": start pomodoro"
                }),
                Span::raw("  •  "),
                Span::styled("s", Style::default().add_modifier(Modifier::BOLD)),
                Span::raw(": stop pomodoro"),
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

    if app.launcher.is_some() {
        hints.extend([
            Span::raw("  •  "),
            Span::styled("enter", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(": run"),
            Span::raw("  •  "),
            Span::styled("esc", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(": close menu"),
        ]);
    }

    if matches!(app.active_screen, Screen::Workspaces) {
        hints.extend([
            Span::raw("  •  "),
            Span::styled("a", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(": add"),
            Span::raw("  •  "),
            Span::styled("e/enter", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(": edit"),
            Span::raw("  •  "),
            Span::styled("d", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(": delete"),
        ]);
    }

    hints
}

fn screen_label(screen: Screen) -> &'static str {
    match screen {
        Screen::Dashboard => "dashboard",
        Screen::Pomodoro => "pomodoro",
        Screen::Run => "run",
        Screen::Tasks => "tasks",
        Screen::Mind => "mind",
        Screen::Notifications => "notifications",
        Screen::Workspaces => "workspaces",
    }
}

fn inactive_style(theme: Theme) -> Style {
    match theme {
        Theme::Ember => Style::default().fg(Color::Gray),
        Theme::Slate => Style::default().fg(Color::DarkGray),
    }
}
