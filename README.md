# Forge

Forge is a terminal-native productivity environment for software engineers, built in Rust with Ratatui, Crossterm, Tokio, and SQLite via SQLx.

## Features

- Dashboard, tasks, notifications, and workspaces screens
- Keyboard navigation for fast terminal use
- Persistent tasks stored in SQLite
- Theme switching
- Clean architecture with separate app, UI, services, storage, and plugins layers

## Project Layout

- `src/main.rs` - application entry point and TUI event loop
- `src/app/` - `AppState`, events, and reducer-style state transitions
- `src/ui/` - screen layout and widgets
- `src/services/` - async service abstractions
- `src/storage/` - SQLite persistence and database setup
- `src/plugins/` - plugin trait for future extensions

## Requirements

- Rust 1.78+ recommended
- A terminal that supports alternate screen mode and keyboard input

## Running Forge

```bash
cargo run
```

By default, Forge stores its SQLite database in a temp directory. Set `FORGE_DB_PATH` to use a custom file:

```bash
FORGE_DB_PATH=./forge.db cargo run
```

## Usage

Forge opens in the terminal’s alternate screen. Use the keyboard shortcuts below to move between screens and manage tasks without leaving the TUI.

The first screen is the dashboard. Press `Tab` to move through Tasks, Notifications, and Workspaces. The task list is persisted automatically through SQLite, so changes remain available across launches when you use a stable `FORGE_DB_PATH`.

## Development Commands

```bash
cargo check
cargo test
cargo fmt
cargo clippy --all-targets --all-features
```

## Controls

- `Tab` switch screens
- `j` / `k` or arrow keys move task selection
- `space` toggle the selected task
- `a` add a task
- `d` delete the selected task
- `t` switch theme
- `q` quit

## Testing

The repository uses Rust’s built-in test framework. Tests cover reducer behavior and SQLite task persistence. Add new tests alongside the module they validate.

## Configuration

- `FORGE_DB_PATH` - optional path to the SQLite database file

Keep UI code, async services, and storage concerns separated when adding new features. This keeps the app testable and makes future plugin support easier to add.

## Roadmap

- Add task input and editing flows
- Implement notifications and workspace screens
- Add a working pomodoro timer
- Introduce workspace-specific configuration
- Expand the plugin system for external integrations
