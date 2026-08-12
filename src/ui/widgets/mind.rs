use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout},
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
};

use crate::{
    app::{AppState, MindDraft, MindDraftMode, MindSelection, Note},
    ui::widgets::master_detail::{highlight_style, inactive_style, panel_style},
};

pub fn draw(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let sections = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([Constraint::Percentage(36), Constraint::Percentage(64)])
        .split(area);

    draw_tree(frame, sections[0], app);
    draw_panel(frame, sections[1], app);
}

fn draw_tree(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    let mut lines = Vec::new();

    if app.vaults.is_empty() {
        lines.push(Line::from(vec![Span::styled(
            "no vaults available",
            panel_style(app.theme),
        )]));
    } else {
        for vault in &app.vaults {
            let vault_selected = matches!(
                app.mind_selection,
                Some(MindSelection::Vault { vault_id }) if vault_id == vault.id
            );
            let vault_style = if vault_selected {
                highlight_style(app.theme)
            } else {
                panel_style(app.theme)
            };
            let expanded = app.mind_expanded_vaults.contains(&vault.id);
            let branch = if expanded { "[-]" } else { "[+]" };

            lines.push(Line::from(vec![
                Span::styled(branch, vault_style),
                Span::raw(" "),
                Span::styled(vault.name.clone(), vault_style),
            ]));

            if !expanded {
                continue;
            }

            let vault_notes = notes_for_vault(app, vault.id);
            if vault_notes.is_empty() {
                lines.push(Line::from(vec![Span::styled(
                    "  (empty)",
                    inactive_style(app.theme),
                )]));
                continue;
            }

            for note in vault_notes {
                let note_selected = matches!(
                    app.mind_selection,
                    Some(MindSelection::Note { note_id }) if note_id == note.id
                );
                let note_style = if note_selected {
                    highlight_style(app.theme)
                } else {
                    panel_style(app.theme)
                };

                let preview = first_non_empty_line(&note.content).unwrap_or_default();
                let summary = if preview.is_empty() {
                    note.title.clone()
                } else {
                    format!("{} - {}", note.title, preview)
                };

                lines.push(Line::from(vec![
                    Span::styled("  - ", note_style),
                    Span::styled(summary, note_style),
                ]));
            }
        }
    }

    let tree = Paragraph::new(lines)
        .block(Block::default().title("mind").borders(Borders::ALL))
        .style(panel_style(app.theme));

    frame.render_widget(tree, area);
}

fn draw_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(draft) = &app.mind_draft {
        draw_editor(frame, area, app, draft);
        return;
    }

    let body = match selected_note(app) {
        Some(note) => render_note_lines(note),
        None => {
            if app.vaults.is_empty() {
                vec![
                    Line::from("create a vault first"),
                    Line::from(""),
                    Line::from("mind uses vault roots as tree anchors"),
                ]
            } else {
                vec![
                    Line::from("select a vault or note"),
                    Line::from(""),
                    Line::from("j/k move  h/l collapse/expand  e open  a add"),
                ]
            }
        }
    };

    let panel = Paragraph::new(body)
        .block(Block::default().title("markdown").borders(Borders::ALL))
        .style(panel_style(app.theme));

    frame.render_widget(panel, area);
}

fn draw_editor(
    frame: &mut Frame<'_>,
    area: ratatui::prelude::Rect,
    app: &AppState,
    draft: &MindDraft,
) {
    let mut lines = vec![Line::from(vec![Span::styled(
        match draft.mode {
            MindDraftMode::Creating { .. } => "new markdown note",
            MindDraftMode::Editing { .. } => "edit markdown note",
        },
        Style::default().add_modifier(Modifier::BOLD),
    )])];

    lines.push(Line::from(""));

    let location = match draft.mode {
        MindDraftMode::Creating { vault_id } => vault_name(app, vault_id)
            .map(|name| format!("vault: {name}"))
            .unwrap_or_else(|| "vault: unknown".to_string()),
        MindDraftMode::Editing { note_id } => note_for_id(app, note_id)
            .and_then(|note| vault_name(app, note.vault_id).map(|name| format!("vault: {name}")))
            .unwrap_or_else(|| "vault: unknown".to_string()),
    };
    lines.push(Line::from(location));
    lines.push(Line::from(""));

    let mut document_lines: Vec<Line<'static>> = if draft.document.trim().is_empty() {
        vec![Line::from("write markdown here")]
    } else {
        draft
            .document
            .lines()
            .map(|line| Line::from(line.to_string()))
            .collect()
    };

    lines.append(&mut document_lines);
    lines.push(Line::from(""));
    lines.push(Line::from(
        "type markdown directly  tab inserts spaces  ctrl+s save  esc cancel",
    ));

    let editor = Paragraph::new(lines)
        .block(Block::default().title("markdown").borders(Borders::ALL))
        .style(panel_style(app.theme));

    frame.render_widget(editor, area);
}

fn render_note_lines(note: &Note) -> Vec<Line<'static>> {
    let content = if note.content.trim().is_empty() {
        "no markdown content yet".to_string()
    } else {
        note.content.clone()
    };

    let mut lines = vec![
        Line::from(vec![
            Span::styled("title: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note.title.clone()),
        ]),
        Line::from(vec![
            Span::styled("slug: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note.slug.clone()),
        ]),
        Line::from(vec![
            Span::styled("vault: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note.vault_id.to_string()),
        ]),
        Line::from(""),
        Line::from(vec![
            Span::styled("content: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw("markdown"),
        ]),
        Line::from(""),
    ];

    lines.extend(content.lines().map(|line| Line::from(line.to_string())));
    lines.push(Line::from(""));
    lines.push(Line::from("e edit  d delete  h/l tree  ctrl+l next tab"));
    lines
}

fn selected_note(app: &AppState) -> Option<&Note> {
    match app.mind_selection {
        Some(MindSelection::Note { note_id }) => note_for_id(app, note_id),
        Some(MindSelection::Vault { vault_id }) => {
            app.notes.iter().find(|note| note.vault_id == vault_id)
        }
        None => None,
    }
}

fn note_for_id(app: &AppState, note_id: i64) -> Option<&Note> {
    app.notes.iter().find(|note| note.id == note_id)
}

fn notes_for_vault(app: &AppState, vault_id: i64) -> Vec<&Note> {
    app.notes
        .iter()
        .filter(|note| note.vault_id == vault_id)
        .collect()
}

fn vault_name(app: &AppState, vault_id: i64) -> Option<String> {
    app.vaults
        .iter()
        .find(|vault| vault.id == vault_id)
        .map(|vault| vault.name.clone())
}

fn first_non_empty_line(content: &str) -> Option<String> {
    content
        .lines()
        .find(|line| !line.trim().is_empty())
        .map(|line| line.trim().to_string())
}
