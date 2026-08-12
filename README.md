# MindGraph

MindGraph is a pane-driven terminal workspace for tasks, workspaces, and lightweight knowledge management.

It is built with Ratatui and keeps the main application flow in `src/app/`, `src/ui/`, `src/services/`, and `src/storage/`.

## What You Get

- Dashboard, tasks, notifications, and workspaces screens
- File-backed persistence for tasks, workspaces, vaults, notes, and links
- Launcher overlay for quick navigation and actions
- Theme toggle and Pomodoro timer
- Plugin trait surface for future extensions

## Getting Started

```bash
cargo run
```

The first launch creates `~/.config/mindgraph/` with `config.json` and `data.json`.

- `config.json` stores app-level configuration.
- `data.json` stores tasks, workspaces, vaults, notes, and links.

To change the storage location, set `MINDGRAPH_HOME` to a different directory.

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
- `src/storage/` owns JSON-backed persistence and repositories
- `src/plugins/` defines the plugin trait surface

## Current Focus

The current product is a TUI shell for tasks, workspaces, notifications, and Pomodoro tracking.
The storage layer already has vault, note, and link repositories, but those workflows are not yet exposed in the UI.
