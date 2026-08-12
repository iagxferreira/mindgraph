# MindGraph

MindGraph is a pane-driven terminal workspace for tasks, markdown notes, work items, Pomodoro tracking, and workspaces.

It is built with Ratatui and keeps the main application flow in `src/app/`, `src/ui/`, `src/services/`, and `src/storage/`.

## What You Get

- Dashboard, tasks, mind, run, pomodoro, notifications, and workspaces screens
- File-backed persistence for tasks, workspaces, vaults, notes, links, pomodoro sessions, and work items
- Launcher overlay for screen-scoped actions and direct `goto` jumps by id
- Markdown note editing with explicit title and filesystem path selection
- Work items as the main work context, with optional task and note links plus linked Pomodoro sessions
- Dashboard controls for creating tasks, selecting work items, and managing work sessions
- Theme toggle and Pomodoro timer
- Plugin trait surface for future extensions

## Getting Started

```bash
cargo run
```

By default MindGraph stores data in `~/.config/mindgraph/`.

On first launch, MindGraph creates the storage directory structure if it does not already exist.

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
The dashboard now acts as the main work surface, the launcher supports screen-scoped commands plus direct id jumps, and work items can exist before their task or note is attached.
The next polish work is around making those partial work items easier to inspect, attach, and resume.
