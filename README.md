# MindGraph

MindGraph is a pane-driven terminal workspace for tasks, workspaces, and lightweight knowledge management.

It is built with Ratatui, persists data in SQLite, and keeps the main application flow in `src/app/`, `src/ui/`, `src/services/`, and `src/storage/`.

## What You Get

- Dashboard, tasks, notifications, and workspaces screens
- Task and workspace CRUD backed by SQLite
- Launcher overlay for quick navigation and actions
- Theme toggle and Pomodoro timer
- Plugin trait surface for future extensions

## Getting Started

```bash
cargo run
```

The database file is created automatically in the system temp directory.

To pin the database location, set `MINDGRAPH_DB_PATH`. `FORGE_DB_PATH` is still accepted for backward compatibility.

## Useful Commands

```bash
make run
make test
make fmt
make clippy
make coverage
```

## Documentation

- [AGENTS.md](AGENTS.md) contributor guide and repository conventions
- [HOW_TO_CONTRIBUTE.md](HOW_TO_CONTRIBUTE.md) contributor workflow
- [ROADMAP.md](ROADMAP.md) product direction and upcoming work
- [BENCHMARK.md](BENCHMARK.md) benchmark notes and methodology

## Project Layout

- `src/app/` owns `AppState`, events, and reducer-style state changes
- `src/ui/` renders screens and widgets
- `src/services/` contains async service traits and implementations
- `src/storage/` owns SQLite access and repositories
- `src/plugins/` defines the plugin trait surface

## Current Focus

The current product is a TUI shell for tasks, workspaces, notifications, and Pomodoro tracking.
The storage layer already has vault, note, and link repositories, but those workflows are not yet exposed in the UI.
