# Roadmap

This file tracks the next MindGraph milestones. Keep items small, shippable, and
focused on one layer at a time.

## Current State

- MindGraph is a Kotlin/Compose Multiplatform Desktop app, replacing the earlier
  Rust/Ratatui TUI.
- The current user-facing surface is the notes graph: a pannable/zoomable canvas of
  notes and links, a detail panel for editing a selected note and managing its
  links, and per-note start/pause/stop time tracking.
- Node size on the graph encodes cumulative tracked time for that note — this is the
  core differentiator over the old TUI, which could only render links as a flat tree.
- File-backed persistence exists for tasks, workspaces, vaults, notes, links,
  pomodoro sessions, and work items, and is shared with the format the Rust app used.
- Tasks, Workspaces, Notifications, and a command launcher have storage support but
  no dedicated UI yet.

## Next Milestone

- Add a Tasks screen: list, create, edit, complete, and attach a task to a note's
  work item.
- Add a Workspaces screen for managing vault/workspace roots beyond the default.
- Improve note search so notes are findable by title/content, not just by browsing
  the graph.
- Package the app for distribution (`./gradlew packageDeb`/`packageMsi`/`packageDmg`)
  and document the install path.

## After That

- Daily notes and templates, once the note-editing UX is more than raw markdown.
- Richer relationship types on links (beyond a free-text label) and a way to filter
  the graph by relationship or time range.
- A weekly/monthly view correlating tracked time across notes, for reflecting on the
  routine of study rather than just one note at a time.

## Later

- Sync or multi-vault workflows only after the local model is stable.
- AI-assisted retrieval or generation only if it meaningfully improves the local
  workflow.

## Notes

- Do not relist storage tables as new work; the file-backed repositories already
  exist and mirror the `data.json` schema documented in `AGENTS.md`.
- Treat MindGraph as the product name everywhere in the repository.
- Keep `AppViewModel` as the single source of truth for UI state.
- Prefer adding behavior behind the storage/repository layer.
- Update this file when a feature is started, split, or finished.
