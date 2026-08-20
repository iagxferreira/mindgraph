# Changelog

All notable changes to MindGraph will be tracked in this file.

## [Unreleased]

### Changed

- Replaced the Rust/Ratatui TUI with a Kotlin/Compose Multiplatform Desktop app,
  living in `desktop/`. The app reads and writes the same `~/.config/mindgraph/`
  storage format the TUI used.

### Added

- A pannable/zoomable graph view of notes and links, with node size encoding
  cumulative tracked time per note.
- Per-note markdown editing, link creation with a relationship label, and
  start/pause/stop time tracking logged as pomodoro sessions.

### Removed

- The Rust/Ratatui TUI shell (dashboard, tasks, mind, run, pomodoro, notifications,
  and workspaces screens) and its launcher overlay. Tasks and Workspaces screens are
  planned to return in the new app; see [ROADMAP.md](ROADMAP.md).

## [0.1.0] (Rust TUI, historical)

### Added

- Pane-driven terminal shell with dashboard, tasks, mind, run, pomodoro, notifications, and workspaces screens.
- File-backed task, workspace, note, link, pomodoro session, and work-item persistence with reducer-driven state updates.
- Launcher overlay with screen-scoped actions and a dedicated Run workflow.
- Markdown note editing with filesystem-backed note paths.
- Contributor docs for workflow, roadmap, and benchmark notes.
