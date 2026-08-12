# How To Contribute

## Before You Start

- Read [README.md](README.md) for the current product shape.
- Read [ROADMAP.md](ROADMAP.md) to see what is intentionally in scope.
- Prefer small changes that stay within one layer: app, UI, service, or storage.

## Local Workflow

- `make run` starts MindGraph.
- `make test` runs the test suite.
- `make fmt` formats the codebase.
- `make clippy` runs lint checks for all targets.
- `make coverage` generates coverage reporting.

If you need direct Cargo output, use `cargo test` or `cargo run`.

## Data Location

MindGraph creates `~/.config/mindgraph/` on first launch.

- `config.json` stores app-level configuration.
- `data.json` stores tasks, workspaces, vaults, notes, and links.
- Set `MINDGRAPH_HOME` to use a different storage directory.

## Style

- Keep Rust formatting standard and let `cargo fmt` handle whitespace.
- Use `snake_case` for functions and modules.
- Use `PascalCase` for types.
- Favor small, explicit functions over shared mutable state.

## Testing

- Use `#[test]` for synchronous logic.
- Use `#[tokio::test]` for async repository or service tests.
- Name tests by behavior, such as `repository_round_trip_persists_workspaces`.

Focus coverage on reducer behavior, file-backed persistence, service logic, and non-trivial widget logic.

## Commits

- Use concise imperative commit messages, for example `feat: add workspace selector`.
- Keep code, docs, and benchmark changes separate when practical.
