# Repository Guidelines

## Project Structure & Module Organization

Forge is a Rust TUI application built around a small, clean module split:
- `src/main.rs` starts the Ratatui/Crossterm event loop.
- `src/app/` holds `AppState`, events, and reducer-style state updates.
- `src/ui/` renders screens and widgets.
- `src/services/` contains async service abstractions.
- `src/storage/` owns SQLite access and persistence logic.
- `src/plugins/` defines the plugin trait for future extensions.

Keep new code small and colocated with the owning feature. Prefer `mod.rs` plus focused submodules over large files.

## Build, Test, and Development Commands

- `cargo run` starts Forge in the terminal.
- `cargo test` runs unit tests, including state and SQLite repository tests.
- `cargo fmt` formats the code with `rustfmt`.
- `cargo clippy --all-targets --all-features` runs lint checks.
- `cargo check` is useful for a fast compile pass during iteration.

## Coding Style & Naming Conventions

Use standard Rust style: 4-space indentation, `snake_case` for functions and modules, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Favor explicit, testable functions over shared mutable logic.

Keep the architecture boundary clear:
- UI code should not talk to SQLx directly.
- Storage code should not depend on Ratatui.
- `AppState` should remain the single source of truth for UI state.

## Testing Guidelines

Use Rust’s built-in test framework with `#[test]` and `#[tokio::test]` where async work is required. Name tests by behavior, such as `repository_round_trip_persists_tasks`. Add tests for reducer behavior, persistence, and any non-trivial screen logic.

## Commit & Pull Request Guidelines

The current history does not establish a strong convention, so use concise imperative commit messages, for example: `Add task repository tests`. For pull requests, include a short summary, the commands you ran, and screenshots or terminal captures for UI changes when relevant.

## Configuration Tips

SQLite uses `FORGE_DB_PATH` when set; otherwise Forge stores data in a temp directory. Avoid hardcoding paths in application code.
