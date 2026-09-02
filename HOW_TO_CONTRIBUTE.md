# How To Contribute

## Before You Start

- Read [README.md](README.md) for the current product shape.
- Read [ROADMAP.md](ROADMAP.md) to see what is intentionally in scope.
- If you are an agent, read `.claude/skills/mindgraph-workflow/SKILL.md` and follow
  it before making changes. Agents without Claude-style skills should use that file as
  ordinary project instructions.
- Read `desktop/src/main/kotlin/dev/mindgraph/state/AppViewModel.kt` if you are
  changing state, time tracking, or note/link creation flow.
- Prefer small changes that stay within one layer: model, storage, state, or UI.

## Local Workflow

- `make run` starts MindGraph.
- `make test` runs the test suite.
- `make build` assembles the application.

If you need direct Gradle output, run from `desktop/`: `./gradlew run`,
`./gradlew test`, `./gradlew build`.

## Agent MCP Setup

MindGraph hosts its MCP server inside the running desktop app. Start the app first,
then configure your agent against the Streamable HTTP endpoint:

```bash
claude mcp add --transport http mindgraph http://127.0.0.1:4319/mcp
codex mcp add mindgraph --url http://127.0.0.1:4319/mcp
```

For other MCP clients, create a server named `mindgraph` with URL
`http://127.0.0.1:4319/mcp`. Set `MINDGRAPH_MCP_PORT` before launching the app if you
need a different port.

Before doing repository work, agents should call `list_ready_tasks`, create or find
the task node, mark it `doing` with `update_status` and their agent name, then close
it with `done` or `dropped` when finished.

Agent changes are append-only. Create a new note or task when new context is needed and
link it to the existing node. Do not rewrite an existing node's title, body, kind,
deadline, or assignee; those edits are human-only in the app. Agents may still update
the status of the task they are working.

## Data Location

MindGraph stores its markdown vault in `~/.config/mindgraph/vault/` by default.

- `nodes/` contains one markdown file per node.
- `.mindgraph/sessions.jsonl` stores append-only tracked-time sessions.
- Set `MINDGRAPH_HOME` to use a different storage directory.
- If `HOME` is unavailable, the fallback root is `./.mindgraph-vault/`.
- The vault directory is created on first launch if it does not already exist.

## Style

- Use standard Kotlin formatting: 4-space indentation.
- Use `camelCase` for functions and properties.
- Use `PascalCase` for types.
- Favor small, explicit functions over shared mutable state.

## Testing

- Use `kotlin.test`'s `@Test` for test cases.
- Use `kotlinx-coroutines-test`'s `runTest` for suspend-function tests.
- Name tests by behavior, such as
  `archivingAnUnfinishedBlockerReleasesWhatWaitedOnIt`.
- When storage changes, add or update round-trip coverage confirming markdown
  frontmatter and session logs keep the shape other tooling expects.

Focus coverage on vault round-trips, derived graph state, the MCP protocol and tools,
view model state transitions, and non-trivial layout/canvas logic.

## Commits

- Use Conventional Commits with a scope naming the layer, for example
  `feat(mcp): add search notes tool`.
- Keep code, docs, and benchmark changes separate when practical.
- Commit docs updates when product naming, workflow, or screen behavior changes.
