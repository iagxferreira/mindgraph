use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout, Rect},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Clear, List, ListItem, ListState, Paragraph},
};

use crate::app::{AppState, Screen};

pub fn draw(frame: &mut Frame<'_>, app: &AppState) {
    let overlay = centered_rect(72, 58, frame.area());
    frame.render_widget(Clear, overlay);

    let menu_title = app
        .launcher
        .as_ref()
        .map(|launcher| format!("{} menu", screen_label(launcher.screen)))
        .unwrap_or_else(|| "menu".to_string());

    let block = Block::default()
        .title(Line::from(vec![
            Span::styled(":", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(" "),
            Span::raw(menu_title),
        ]))
        .borders(Borders::ALL)
        .style(style());
    let inner = block.inner(overlay);
    frame.render_widget(block, overlay);
    let sections = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Length(3),
            Constraint::Min(0),
            Constraint::Length(2),
        ])
        .split(inner);

    let query = app
        .launcher
        .as_ref()
        .map(|launcher| launcher.query.as_str())
        .unwrap_or_default();

    let query_line = Paragraph::new(Line::from(vec![
        Span::styled("filter ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(if query.is_empty() { "all" } else { query }),
    ]))
    .style(style());
    frame.render_widget(query_line, sections[0]);

    let entries = app.filtered_launcher_entries();
    let list_items = if entries.is_empty() {
        vec![ListItem::new(Line::from("no matches"))]
    } else {
        entries
            .into_iter()
            .map(|entry| {
                ListItem::new(Line::from(vec![
                    Span::styled(entry.label, item_style()),
                    Span::raw("  "),
                    Span::styled(entry.hint, hint_style()),
                ]))
            })
            .collect::<Vec<_>>()
    };

    let mut state = ListState::default();
    let selected = app
        .launcher
        .as_ref()
        .map(|launcher| launcher.selected)
        .unwrap_or(0)
        .min(list_items.len().saturating_sub(1));
    state.select(Some(selected));

    let list = List::new(list_items)
        .highlight_style(highlight_style())
        .highlight_symbol(">> ")
        .style(style());
    frame.render_stateful_widget(list, sections[1], &mut state);

    let footer = Paragraph::new(Line::from(footer_hints(app))).style(style());
    frame.render_widget(footer, sections[2]);
}

fn centered_rect(width_pct: u16, height_pct: u16, area: Rect) -> Rect {
    let popup_layout = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Percentage((100 - height_pct) / 2),
            Constraint::Percentage(height_pct),
            Constraint::Percentage((100 - height_pct) / 2),
        ])
        .split(area);

    let horizontal = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([
            Constraint::Percentage((100 - width_pct) / 2),
            Constraint::Percentage(width_pct),
            Constraint::Percentage((100 - width_pct) / 2),
        ])
        .split(popup_layout[1]);

    horizontal[1]
}

fn style() -> Style {
    Style::default().fg(Color::Rgb(240, 240, 240))
}

fn accent_style() -> Style {
    Style::default()
        .fg(Color::Rgb(248, 196, 113))
        .add_modifier(Modifier::BOLD)
}

fn item_style() -> Style {
    Style::default()
        .fg(Color::Rgb(240, 240, 240))
        .add_modifier(Modifier::BOLD)
}

fn hint_style() -> Style {
    Style::default().fg(Color::Gray)
}

fn highlight_style() -> Style {
    Style::default()
        .bg(Color::Rgb(248, 196, 113))
        .fg(Color::Black)
        .add_modifier(Modifier::BOLD)
}

fn footer_hints(app: &AppState) -> Vec<Span<'static>> {
    let mut hints = vec![
        Span::styled("enter", accent_style()),
        Span::raw(" run  "),
        Span::styled("esc", accent_style()),
        Span::raw(" close  "),
        Span::styled("j/k", accent_style()),
        Span::raw(" move  "),
        Span::styled("backspace", accent_style()),
        Span::raw(" edit"),
    ];

    if let Some(launcher) = app.launcher.as_ref() {
        match launcher.screen {
            Screen::Pomodoro => {
                hints.push(Span::raw("  •  "));
                hints.push(Span::styled("p", accent_style()));
                hints.push(Span::raw(" pause/resume  "));
                hints.push(Span::styled("s", accent_style()));
                hints.push(Span::raw(" stop/save  "));
                hints.push(Span::styled("t", accent_style()));
                hints.push(Span::raw(" attach task  "));
                hints.push(Span::styled("c", accent_style()));
                hints.push(Span::raw(" clear task"));
            }
            Screen::Run => {
                hints.push(Span::raw("  •  "));
                hints.push(Span::styled("a", accent_style()));
                hints.push(Span::raw(" bind task/note  "));
                hints.push(Span::styled("r", accent_style()));
                hints.push(Span::raw(" start  "));
                hints.push(Span::styled("p", accent_style()));
                hints.push(Span::raw(" pause  "));
                hints.push(Span::styled("s", accent_style()));
                hints.push(Span::raw(" stop  "));
                hints.push(Span::styled("d", accent_style()));
                hints.push(Span::raw(" delete"));
            }
            Screen::Tasks => {
                hints.push(Span::raw("  •  "));
                hints.push(Span::styled("m", accent_style()));
                hints.push(Span::raw(" mark doing"));
            }
            _ => {}
        }
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
