# Repository Guidelines

MindGraph is a Rust workspace for a pane-driven terminal workspace with tasks, markdown notes, work items, workspaces, Pomodoro tracking, and file-backed persistence. Keep changes small, testable, and scoped to the owning layer.

## Project Structure

- `src/main.rs` wires the event loop, terminal setup, and services.
- `src/app/` owns `AppState`, events, and reducer-style state changes.
- `src/ui/` renders screens and widgets only.
- `src/services/` contains async service traits and implementations.
- `src/storage/` owns file-backed persistence and repositories.
- `src/plugins/` defines the plugin trait surface.
- `README.md` stays high level. `BENCHMARK.md` records benchmark notes and results.
- `ROADMAP.md` tracks product direction and unfinished work.

Prefer moving shared logic into the owning layer and keeping UI, service, and storage boundaries clear.

## Build And Test

- `make run` starts the TUI locally.
- `make test` runs the test suite.
- `make fmt` formats the codebase.
- `make clippy` runs lint checks for all targets.
- `make coverage` generates coverage reporting.

Use `cargo test` when you need direct Cargo output. Prefer the `Makefile` targets for day-to-day work.

## Style And Boundaries

Use standard Rust formatting: 4-space indentation, `snake_case` for functions and modules, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Prefer explicit, testable functions over shared mutable state.

Keep boundaries clear:

- the app layer handles events and state transitions
- the UI layer renders state and user hints
- the service layer mediates async work
- the storage layer owns file-backed persistence and repositories

## Testing

Use `#[test]` for synchronous logic and `#[tokio::test]` for async code. Name tests by behavior, such as `set_overwrites_existing_value`.

Focus coverage on state transitions, repository behavior, service logic, and non-trivial widget rendering.

## Documentation

- Update `README.md` when public usage changes.
- Update `BENCHMARK.md` when benchmark methodology or results change.
- Update `ROADMAP.md` when the product direction changes.
- Update `AGENTS.md` when workflow or project conventions change.

## Commits

Use concise imperative commit messages, for example `feat: add task filter`. Group code, docs, and benchmark changes separately when possible.
