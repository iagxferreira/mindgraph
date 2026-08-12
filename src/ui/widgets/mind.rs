use ratatui::{
    Frame,
    layout::{Constraint, Direction, Layout},
    style::{Modifier, Style},
    text::{Line, Span},
    widgets::{Block, Borders, Paragraph},
};
use std::{collections::BTreeSet, fs, path::Path};

use crate::{
    app::{AppState, MindDraft, MindDraftFocus, MindDraftMode, MindSelection, Note},
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
    if let Some(draft) = &app.mind_draft {
        if matches!(draft.mode, MindDraftMode::Creating { .. }) {
            draw_path_picker(frame, area, app, draft);
            return;
        }
    }

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

                let preview = file_name(&note.path).unwrap_or_else(|| note.slug.clone());
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

fn draw_path_picker(
    frame: &mut Frame<'_>,
    area: ratatui::prelude::Rect,
    app: &AppState,
    draft: &MindDraft,
) {
    let Some(root) = app.selected_mind_vault_root_path() else {
        frame.render_widget(
            Paragraph::new(vec![Line::from("missing vault root")])
                .block(Block::default().title("path").borders(Borders::ALL))
                .style(panel_style(app.theme)),
            area,
        );
        return;
    };

    let entries = visible_dirs(Path::new(&root), &app.mind_path_expanded);
    let mut lines = vec![Line::from(vec![Span::styled(
        "pick a directory for this note",
        Style::default().add_modifier(Modifier::BOLD),
    )])];
    lines.push(Line::from(""));

    for (path, depth) in entries {
        let selected = app
            .mind_path_selection
            .as_ref()
            .map(|current| current == &path)
            .unwrap_or(false);
        let label = Path::new(&path)
            .file_name()
            .and_then(|name| name.to_str())
            .map(|name| name.to_string())
            .unwrap_or_else(|| path.clone());
        let style = if selected {
            highlight_style(app.theme)
        } else {
            panel_style(app.theme)
        };

        lines.push(Line::from(vec![
            Span::styled(if selected { ">" } else { " " }, style),
            Span::raw(" "),
            Span::raw("  ".repeat(depth)),
            Span::styled(label, style),
        ]));
    }

    lines.push(Line::from(""));
    lines.push(Line::from(match draft.focus {
        MindDraftFocus::Title => "title focus  type name  tab directory",
        MindDraftFocus::Path => "directory focus  j/k move  h/l expand  tab body",
        MindDraftFocus::Document => "body focus  tab title  ctrl+s save",
    }));

    let picker = Paragraph::new(lines)
        .block(Block::default().title("path").borders(Borders::ALL))
        .style(panel_style(app.theme));

    frame.render_widget(picker, area);
}

fn draw_panel(frame: &mut Frame<'_>, area: ratatui::prelude::Rect, app: &AppState) {
    if let Some(draft) = &app.mind_draft {
        draw_editor(frame, area, app, draft);
        return;
    }

    let body = match selected_note(app) {
        Some(note) => render_note_lines(note, app.mind_document.as_deref()),
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

    let title_style = if matches!(draft.focus, MindDraftFocus::Title) {
        highlight_style(app.theme)
    } else {
        panel_style(app.theme)
    };
    lines.push(Line::from(vec![
        Span::styled("title: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::styled(draft.title.clone(), title_style),
    ]));

    let location = match draft.mode {
        MindDraftMode::Creating { vault_id } => vault_name(app, vault_id)
            .map(|name| format!("vault: {name}"))
            .unwrap_or_else(|| "vault: unknown".to_string()),
        MindDraftMode::Editing { note_id } => note_for_id(app, note_id)
            .and_then(|note| vault_name(app, note.vault_id).map(|name| format!("vault: {name}")))
            .unwrap_or_else(|| "vault: unknown".to_string()),
    };
    lines.push(Line::from(location));
    if let MindDraftMode::Creating { .. } = draft.mode {
        if let Some(path) = &app.mind_path_selection {
            lines.push(Line::from(format!("directory: {path}")));
        }
    }
    lines.push(Line::from(vec![
        Span::styled("save path: ", Style::default().add_modifier(Modifier::BOLD)),
        Span::raw(match draft.mode {
            MindDraftMode::Creating { .. } => app
                .mind_path_selection
                .as_ref()
                .map(|directory| {
                    Path::new(directory)
                        .join(format!("{}.md", draft.title.trim().to_lowercase().replace(' ', "-")))
                        .to_string_lossy()
                        .into_owned()
                })
                .unwrap_or_else(|| "select a directory".to_string()),
            MindDraftMode::Editing { .. } => note_for_id(app, match draft.mode {
                MindDraftMode::Editing { note_id } => note_id,
                MindDraftMode::Creating { .. } => unreachable!(),
            })
            .map(|note| {
                let parent = Path::new(&note.path)
                    .parent()
                    .map(Path::to_path_buf)
                    .unwrap_or_else(|| Path::new(".").to_path_buf());
                parent
                    .join(format!("{}.md", draft.title.trim().to_lowercase().replace(' ', "-")))
                    .to_string_lossy()
                    .into_owned()
            })
            .unwrap_or_else(|| "unknown".to_string()),
        }),
    ]));
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
        "type the title or markdown body  tab switches fields  ctrl+s save  esc cancel",
    ));

    let editor = Paragraph::new(lines)
        .block(Block::default().title("markdown").borders(Borders::ALL))
        .style(panel_style(app.theme));

    frame.render_widget(editor, area);
}

fn render_note_lines(note: &Note, document: Option<&str>) -> Vec<Line<'static>> {
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
            Span::styled("path: ", Style::default().add_modifier(Modifier::BOLD)),
            Span::raw(note.path.clone()),
        ]),
        Line::from(""),
    ];

    let content = document.unwrap_or("document not loaded yet");
    if content.trim().is_empty() {
        lines.push(Line::from("no markdown content yet"));
    } else {
        lines.extend(content.lines().map(|line| Line::from(line.to_string())));
    }
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

fn file_name(path: &str) -> Option<String> {
    Path::new(path)
        .file_name()
        .and_then(|name| name.to_str())
        .map(|name| name.to_string())
}

fn visible_dirs(root: &Path, expanded: &BTreeSet<String>) -> Vec<(String, usize)> {
    let mut entries = vec![(root.to_string_lossy().into_owned(), 0)];
    collect_dirs(root, 0, expanded, &mut entries);
    entries
}

fn collect_dirs(
    path: &Path,
    depth: usize,
    expanded: &BTreeSet<String>,
    entries: &mut Vec<(String, usize)>,
) {
    let path_str = path.to_string_lossy().into_owned();
    if depth > 0 && !expanded.contains(&path_str) {
        return;
    }

    let mut dirs = match fs::read_dir(path) {
        Ok(entries) => entries
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|child| child.is_dir())
            .collect::<Vec<_>>(),
        Err(_) => return,
    };

    dirs.sort();
    for dir in dirs {
        entries.push((dir.to_string_lossy().into_owned(), depth + 1));
        collect_dirs(&dir, depth + 1, expanded, entries);
    }
}
