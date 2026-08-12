use ratatui::{
    layout::{Constraint, Direction, Layout},
    style::{Color, Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, List, ListItem, ListState, Paragraph},
    Frame,
};

use crate::app::{AppState, Theme, Workspace, WorkspaceInputField, WorkspaceInputMode};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let chunks = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(58), Constraint::Percentage(42)])
        .split(area);

    draw_workspace_list(frame, chunks[0], app);
    draw_workspace_panel(frame, chunks[1], app);
}

fn draw_workspace_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .workspaces
        .iter()
        .map(|workspace| {
            let content = format!("{}  {}", workspace.name, workspace.path);
            ListItem::new(Span::styled(content, Style::default().fg(workspace_color(app.theme))))
        })
        .collect::<Vec<_>>();

    let mut state = ListState::default();
    state.select(app.selected_workspace);

    let list = List::new(items)
        .block(Block::default().title("workspaces").borders(Borders::ALL))
        .highlight_style(
            Style::default()
                .bg(Color::White)
                .fg(Color::Black)
                .add_modifier(Modifier::BOLD),
        )
        .highlight_symbol(">> ");

    frame.render_stateful_widget(list, area, &mut state);
}

fn draw_workspace_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(mode) = &app.workspace_input_mode {
        let editor = Paragraph::new(render_editor_lines(app, mode))
            .block(Block::default().title(editor_title(mode)).borders(Borders::ALL))
            .style(style_for_theme(app.theme));
        frame.render_widget(editor, area);
        return;
    }

    let body = match selected_workspace(app) {
        Some(workspace) => render_details_lines(workspace),
        None => vec![
            Line::from("no workspace selected"),
            Line::from(""),
            Line::from("use j/k or arrows to move"),
            Line::from("press a to add a workspace"),
        ],
    };

    let details = Paragraph::new(body)
        .block(Block::default().title("workspace details").borders(Borders::ALL))
        .style(style_for_theme(app.theme));
    frame.render_widget(details, area);
}

fn selected_workspace(app: &AppState) -> Option<&Workspace> {
    app.selected_workspace.and_then(|index| app.workspaces.get(index))
}

fn render_details_lines(workspace: &Workspace) -> Vec<Line<'static>> {
    vec![
        Line::from(vec![
            Span::styled("name: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(workspace.name.clone()),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("path: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(workspace.path.clone()),
        ]),
        Line::from(""),
        Line::from("e edit  d delete"),
    ]
}

fn render_editor_lines(app: &AppState, mode: &WorkspaceInputMode) -> Vec<Line<'static>> {
    let name_focus = matches!(app.workspace_input_focus, WorkspaceInputField::Name);
    let path_focus = matches!(app.workspace_input_focus, WorkspaceInputField::Path);

    let description_hint = match mode {
        WorkspaceInputMode::Creating => "create a workspace with a name and path.",
        WorkspaceInputMode::Editing { .. } => "edit the selected workspace name and path.",
    };

    vec![
        Line::from(description_hint),
        Line::from(""),
        focus_line("name", &app.workspace_input_name, name_focus),
        Line::from(""),
        focus_line("path", &app.workspace_input_path, path_focus),
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

fn editor_title(mode: &WorkspaceInputMode) -> &'static str {
    match mode {
        WorkspaceInputMode::Creating => "new workspace",
        WorkspaceInputMode::Editing { .. } => "edit workspace",
    }
}

fn workspace_color(theme: Theme) -> Color {
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
