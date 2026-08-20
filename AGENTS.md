# Repository Guidelines

MindGraph is a Kotlin/Compose Multiplatform Desktop app: a graph of linked notes
correlated with time tracking. Keep changes small, testable, and scoped to the
owning layer.

## Project Structure

The app lives entirely under `desktop/`.

- `desktop/src/main/kotlin/dev/mindgraph/Main.kt` wires the window and app entry point.
- `desktop/src/main/kotlin/dev/mindgraph/model/` defines the domain data classes,
  matching the on-disk `data.json` schema field-for-field.
- `desktop/src/main/kotlin/dev/mindgraph/storage/` owns file-backed persistence and
  repositories.
- `desktop/src/main/kotlin/dev/mindgraph/state/` owns `AppViewModel` (the single
  source of truth for UI state) and the graph layout engine.
- `desktop/src/main/kotlin/dev/mindgraph/ui/` renders screens and widgets only.
- `README.md` stays high level. `BENCHMARK.md` records benchmark notes and results.
- `ROADMAP.md` tracks product direction and unfinished work.

Prefer moving shared logic into the owning layer and keeping storage, state, and UI
boundaries clear.

## Build And Test

- `make run` starts the desktop app locally.
- `make test` runs the test suite.
- `make build` assembles the application.

Or run Gradle directly from `desktop/`: `./gradlew run`, `./gradlew test`,
`./gradlew build`.

## Style And Boundaries

Use standard Kotlin formatting: 4-space indentation, `camelCase` for functions and
properties, `PascalCase` for types, and `UPPER_SNAKE_CASE` for constants. Prefer
explicit, testable functions over shared mutable state.

Keep boundaries clear:

- the storage layer owns file-backed persistence and repositories
- the state layer (`AppViewModel`) mediates repository calls and holds the UI-facing
  snapshot
- the UI layer renders state and user input only

## Testing

Use `kotlin.test` with `@Test`, and `kotlinx-coroutines-test`'s `runTest` for
suspend-function tests. Name tests by behavior, such as
`createdNoteLinkAndWorkItemSurviveAReload`.

Focus coverage on repository behavior (especially `data.json` compatibility), view
model state transitions, and non-trivial layout/canvas logic.

## Documentation

- Update `README.md` when public usage changes.
- Update `BENCHMARK.md` when benchmark methodology or results change.
- Update `ROADMAP.md` when the product direction changes.
- Update `AGENTS.md` when workflow or project conventions change.

## Commits

Use concise imperative commit messages, for example `feat: add task filter`. Group
code, docs, and benchmark changes separately when possible.
