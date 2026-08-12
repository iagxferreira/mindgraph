# Repository Guidelines

## Project Structure

- `src/main.rs` wires the Ratatui event loop and async services.
- `src/app/` owns `AppState`, events, and reducer-style state changes.
- `src/ui/` renders screens and widgets only.
- `src/services/` contains async service traits and implementations.
- `src/storage/` owns SQLite access and persistence.
- `src/plugins/` is reserved for future plugin traits and loading hooks.

Keep changes small and local to the owning layer. UI code should not talk to SQLx directly, and storage code should not depend on Ratatui.

## Build and Test

- `make run` starts Forge.
- `make test` runs the test suite.
- `make fmt` formats the codebase.
- `make clippy` runs lint checks for all targets.
- `make coverage` generates coverage reporting.

Use `cargo test` only when you need direct Cargo output. Prefer the `Makefile` targets for day-to-day work.

## Style and Naming

Use standard Rust formatting: 4-space indentation, `snake_case` for functions and modules, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Keep UI text lowercase to match the current terminal style.

Favor explicit state transitions through `AppState::apply` and small helper functions. Avoid adding global mutable state.

## Testing

Use `#[test]` for synchronous logic and `#[tokio::test]` for async repository or service tests. Name tests by behavior, such as `repository_round_trip_persists_workspaces`.

Cover reducer behavior, SQLite persistence, and non-trivial widget logic. Keep UI rendering thin; most behavior should remain testable outside Ratatui.

## Workflow

- Screen navigation uses `Ctrl+L` and `Ctrl+H`.
- Task actions use `a`, `e` or `Enter`, `d`, `space`, and `t`.
- Workspace actions follow the same pattern on the Workspaces screen.
- SQLite uses `FORGE_DB_PATH` when set; otherwise Forge writes to a temp directory.

## Commit Notes

Use concise imperative commits, for example `feat: wire workspace state and ui`. Group code, docs, and tooling changes separately when possible.
