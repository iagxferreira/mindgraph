# Repository Guidelines

MindGraph is a Kotlin/Compose Multiplatform Desktop app over a folder of markdown: a
graph of linked notes and tasks, correlated with tracked time, that a running app
exposes to coding agents over MCP. Keep changes small, testable, and scoped to the
owning layer.

If you are an agent working on this repository, read
`.claude/skills/mindgraph-workflow/SKILL.md` first. Claude can load it as a skill;
other agents should treat the Markdown file as their project workflow instructions.
The short version: record the work in the vault before doing it.

## Project Structure

The app lives entirely under `desktop/`, at
`desktop/src/main/kotlin/dev/mindgraph/`.

- `Main.kt` wires the window and app entry point.
- `model/` defines the domain: `Node` with its `NodeKind` and optional `TaskFacet`,
  projected `Edge`s, and `WorkSession`, which records who spent the time.
- `storage/` owns the markdown vault — frontmatter parsing, the node store, and the
  append-only session log. **Markdown is the source of truth**; nothing caches across
  calls.
- `state/` owns `AppViewModel` (the single source of UI state), the graph layout
  engine, `TaskGraph`, which derives blocked/ready state and ranks what to do next,
  and the shared rules: `Linking`, `GraphFilter`, `WorkSummary`.
- `mcp/` is the MCP protocol, its loopback HTTP transport, and the tools agents call.
- `ui/` renders screens and widgets only.

**Rules that both the app and the agents must obey live in `state/`, never in either
caller.** An agent must not be able to build a graph the app would have refused —
that is why cycle refusal, filtering and ranking are not implemented twice.

`README.md` stays high level. `ROADMAP.md` tracks direction, though the vault is the
live version of it. `BENCHMARK.md` records benchmark notes and results.

## Build And Test

- `make run` starts the desktop app locally.
- `make test` runs the test suite.
- `make build` assembles the application.

Or run Gradle directly from `desktop/`: `./gradlew run`, `./gradlew test`,
`./gradlew build`.

**Restart the app after rebuilding.** Gradle overwrites `build/classes` while the
running JVM is still lazily loading from it, and the next MCP call fails with
`NoClassDefFoundError`. It is not a code bug and it will waste your time twice.

## The MCP server

The server runs inside the app, on `127.0.0.1:4319` (`MINDGRAPH_MCP_PORT` to move it),
for as long as the window is open. `ConnectionRefused` means the app is closed.

```bash
claude mcp add --transport http mindgraph http://127.0.0.1:4319/mcp
codex mcp add mindgraph --url http://127.0.0.1:4319/mcp
```

Other agents should configure a Streamable HTTP MCP server named `mindgraph` at
`http://127.0.0.1:4319/mcp`. If an agent runs outside the local machine, use a
deliberate secure tunnel instead of exposing the loopback server directly.

Adding a tool means adding to the surface every request in every session carries, so a
tool earns its place by enabling the loop — orient, capture, structure, close — not by
exposing a method. Destructive and fiddly operations stay in the app.

## Style And Boundaries

Use standard Kotlin formatting: 4-space indentation, `camelCase` for functions and
properties, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Prefer
explicit, testable functions over shared mutable state.

Keep boundaries clear:

- the storage layer owns the vault and the session log
- the state layer mediates it and holds the UI-facing snapshot, plus the rules the
  MCP tools share
- the UI layer renders state and user input only

Comments explain *why*, not what. If a decision has a reason that is not obvious from
the code — why deadlines outrank leverage, why archiving is not a status — the comment
is where it belongs.

## Testing

Use `kotlin.test` with `@Test`, and `kotlinx-coroutines-test`'s `runTest` for
suspend-function tests. Name tests by behaviour, such as
`archivingAnUnfinishedBlockerReleasesWhatWaitedOnIt`.

Focus coverage on vault round-trips, derived graph state, the MCP protocol and its
tools, and non-trivial layout logic. Pull decisions out of composables so they can be
tested — `WorkSummary` and `GraphFilter` exist for that reason. Compose UI itself is
not covered by the suite and cannot be verified in a headless session; say so rather
than implying a layout was checked.

**A new field is not finished until it round-trips through storage, can be set, and is
visible somewhere.** `due` and `kind` each shipped missing one of the three.

## Documentation

- Update `README.md` when public usage changes.
- Update `ROADMAP.md` when the product direction changes — and the vault first.
- Update `BENCHMARK.md` when benchmark methodology or results change.
- Update `AGENTS.md` when workflow or project conventions change.
- Update `CHANGELOG.md` under `[Unreleased]` as features land.

## Commits

Conventional Commits with a scope naming the layer: `feat(state):`, `feat(mcp):`,
`fix(ui):`. One layer per commit, and the body explains why.

Atomicity is verified, not assumed: check each commit out into a `git worktree` and run
`./gradlew test` at that commit. A commit whose adapter and interface are split across
two commits does not compile, and only this check catches it.
