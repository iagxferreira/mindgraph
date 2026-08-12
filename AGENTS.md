# Repository Guidelines

Agni is a Rust workspace for an in-memory cache server, client, and benchmark tools. Keep changes small, testable, and scoped to the owning crate.

## Project Structure

- `agni/` is the core library with store, protocol, and command logic.
- `agni-server/` is the TCP server binary.
- `agni-client/` is the CLI client binary.
- `agni-bench/` is the benchmarking binary.
- `README.md` stays high level. `BENCHMARK.md` records performance results.

Prefer moving shared logic into `agni/` and keeping binaries thin.

## Build And Test

- `cargo test` runs the workspace tests.
- `cargo fmt` formats the codebase.
- `cargo clippy --all-targets --all-features` runs lint checks.
- `cargo run -p agni-server -- --config config.example.yml` starts the server locally.
- `cargo run -p agni-client -- PING` sends a command to a running server.

Use release builds for benchmark work:

- `cargo build --release -p agni-server -p agni-bench`

## Style And Boundaries

Use standard Rust formatting: 4-space indentation, `snake_case` for functions and modules, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Prefer explicit, testable functions over shared mutable state.

Keep boundaries clear:

- the server handles networking and I/O
- the client handles CLI input and output
- the core crate owns cache behavior and protocol types

## Testing

Use `#[test]` for synchronous logic and `#[tokio::test]` for async code. Name tests by behavior, such as `set_overwrites_existing_value`.

Focus coverage on protocol parsing, store behavior, command execution, and client/server integration points.

## Documentation

- Update `README.md` when public usage changes.
- Update `BENCHMARK.md` when benchmark methodology or results change.
- Update `AGENTS.md` when workflow or project conventions change.

## Commits

Use concise imperative commit messages, for example `feat: add ttl command`. Group code, docs, and benchmark changes separately when possible.
