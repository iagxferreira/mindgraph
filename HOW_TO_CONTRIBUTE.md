# How To Contribute

## Before You Start

- Read [README.md](README.md) for the current product shape.
- Read [ROADMAP.md](ROADMAP.md) to see what is intentionally in scope.
- Read `desktop/src/main/kotlin/dev/mindgraph/state/AppViewModel.kt` if you are
  changing state, time tracking, or note/link creation flow.
- Prefer small changes that stay within one layer: model, storage, state, or UI.

## Local Workflow

- `make run` starts MindGraph.
- `make test` runs the test suite.
- `make build` assembles the application.

If you need direct Gradle output, run from `desktop/`: `./gradlew run`,
`./gradlew test`, `./gradlew build`.

## Data Location

MindGraph stores data in `~/.config/mindgraph/` by default.

- `config.json` stores app-level configuration.
- `data.json` stores tasks, workspaces, vaults, notes, links, pomodoro sessions, and
  work items.
- Set `MINDGRAPH_HOME` to use a different storage directory.
- If `HOME` is unavailable, the fallback root is `./.mindgraph/`.
- The storage directory is created on first launch if it does not already exist.

## Style

- Use standard Kotlin formatting: 4-space indentation.
- Use `camelCase` for functions and properties.
- Use `PascalCase` for types.
- Favor small, explicit functions over shared mutable state.

## Testing

- Use `kotlin.test`'s `@Test` for test cases.
- Use `kotlinx-coroutines-test`'s `runTest` for suspend-function tests.
- Name tests by behavior, such as `readsRustShapedDataJsonIncludingLegacyPomodoroAlias`.
- When storage changes, add or update round-trip coverage confirming the persisted
  `data.json` keeps the field names and shape other tooling expects.

Focus coverage on repository behavior, file-backed persistence, view model state
transitions, and non-trivial layout/canvas logic.

## Commits

- Use concise imperative commit messages, for example `feat: add workspace selector`.
- Keep code, docs, and benchmark changes separate when practical.
- Commit docs updates when product naming, workflow, or screen behavior changes.
