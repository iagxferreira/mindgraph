use ratatui::{
    Frame,
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::ListItem,
};

use crate::{
    app::{AppState, Workspace, WorkspaceInputField, WorkspaceInputMode},
    ui::widgets::master_detail,
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let [list_area, detail_area] = master_detail::split(area);

    draw_workspace_list(frame, list_area, app);
    draw_workspace_panel(frame, detail_area, app);
}

fn draw_workspace_list(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let items = app
        .workspaces
        .iter()
        .map(|workspace| {
            let content = format!("{}  {}", workspace.name, workspace.path);
            ListItem::new(Span::styled(content, master_detail::panel_style(app.theme)))
        })
        .collect::<Vec<_>>();

    master_detail::render_list(
        frame,
        area,
        "workspaces",
        items,
        app.selected_workspace,
        app.theme,
    );
}

fn draw_workspace_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(mode) = &app.workspace_input_mode {
        master_detail::render_editor(
            frame,
            area,
            editor_title(mode),
            render_editor_lines(app, mode),
            app.theme,
        );
        return;
    }

    master_detail::render_panel(
        frame,
        area,
        "workspace details",
        match selected_workspace(app) {
            Some(workspace) => render_details_lines(workspace),
            None => vec![
                Line::from("no workspace selected"),
                Line::from(""),
                Line::from("use j/k or arrows to move"),
                Line::from("press a to add a workspace"),
            ],
        },
        app.theme,
    );
}

fn selected_workspace(app: &AppState) -> Option<&Workspace> {
    app.selected_workspace
        .and_then(|index| app.workspaces.get(index))
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
        master_detail::focus_line("name", &app.workspace_input_name, name_focus),
        Line::from(""),
        master_detail::focus_line("path", &app.workspace_input_path, path_focus),
        Line::from(""),
        Line::from("enter next field/save  tab switch field  esc cancel"),
    ]
}

fn editor_title(mode: &WorkspaceInputMode) -> &'static str {
    match mode {
        WorkspaceInputMode::Creating => "new workspace",
        WorkspaceInputMode::Editing { .. } => "edit workspace",
    }
}
