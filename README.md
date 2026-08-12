# MindGraph

MindGraph is a pane-driven terminal workspace for tasks, markdown notes, work items, Pomodoro tracking, and workspaces.

It is built with Ratatui and keeps the main application flow in `src/app/`, `src/ui/`, `src/services/`, and `src/storage/`.

## What You Get

- Dashboard, tasks, mind, run, pomodoro, notifications, and workspaces screens
- File-backed persistence for tasks, workspaces, vaults, notes, links, pomodoro sessions, and work items
- Launcher overlay for screen-scoped actions
- Markdown note editing and filesystem-backed note paths
- Work items that link a task to a note and track run state
- Theme toggle and Pomodoro timer
- Plugin trait surface for future extensions

## Getting Started

```bash
cargo run
```

By default MindGraph stores data in `~/.config/mindgraph/`.

- `config.json` stores app-level configuration.
- `data.json` stores tasks, workspaces, vaults, notes, links, pomodoro sessions, and work items.
- `workspaces/` is the default workspace root under the storage directory.

To change the storage location, set `MINDGRAPH_HOME` to a different directory.
If `HOME` is unavailable, MindGraph falls back to `./.mindgraph/`.

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

The current product is a TUI shell for tasks, markdown notes, linked work items, workspaces, and Pomodoro tracking.
The note workflow is file-backed, the run workflow links a task to a note, and the next polish work is around making those links easier to create and inspect.
