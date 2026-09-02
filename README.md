# MindGraph

MindGraph is a second brain built on a graph. Notes, tasks, and tracked time are the
same kind of thing — a **node** — so a thought can become work without becoming a
second record, and the dependencies between them form a graph you can actually read.

The running app hosts an MCP server, so coding agents read and write that graph
directly: they ask what is ready, do the work, and close it out. Time they spend is
logged as theirs, next to yours.

Your vault is a folder of markdown files. Nothing is locked in a database.

It is built with Kotlin and Compose Multiplatform for Desktop, in `desktop/`.

![The notes graph in Mind mode](assets/screenshots/graph-mind.png)

## The idea

Most tools make you choose. A note-taking app gives you linked thoughts but no sense
of what to do next; a task manager gives you a checklist but nowhere to think. Keeping
both means keeping them in sync forever.

MindGraph has one entity. Every node is a markdown document, and a node is *also* a
task when it has a status. Writing up a problem and tracking the work on it happen in
the same file.

That single model is what makes the graph worth handing to an agent. There is one
thing to read, one thing to write, and the ordering between them is derived rather
than declared — so an agent cannot put the plan out of step with itself.

## What you get

**One graph, two ways to read it.** Mind mode is force-directed, for associative
thinking. Flow mode lays dependencies out in ranked columns, because springs scramble
exactly the ordering a dependency graph exists to show.

![The same graph in Flow mode](assets/screenshots/graph-flow.png)

**Kinds you can see at a glance.** A node is a note, an RFC, or a reference — drawn as
a circle, a diamond, or a square. Shape rather than colour, because colour already
means task state and size already means tracked time. Filter the canvas to one kind
without moving anything: hiding the rest answers the same question as clustering, and
leaves the surviving edges meaning what they meant.

**Status you don't have to maintain.** A task whose dependencies are unfinished is
blocked — computed by walking the graph, never typed in. Finish the thing upstream and
what it was holding up becomes ready on its own. Cycles are refused when you create
them, not discovered later.

**A task list that has an opinion.** Ready work is ordered overdue first, then by how
much finishing it unblocks. There is no priority field: a declared rank would
contradict the derived one, and it inflates until everything is urgent. A deadline
decays on its own, because time moves.

![Tasks grouped by derived state](assets/screenshots/tasks.png)

**Notes and tasks edited the same way.** Title, markdown body, live preview, and the
task controls in the header — "Make this a task" is a button, not a different screen.
Type `[[a note title]]` in the body and it becomes a link.

![The markdown editor with task status](assets/screenshots/note-editor.png)

**Archive instead of deleting.** A node can be put away and keep its id, its links and
its tracked time. Archiving is not a fifth status, because that would overwrite whether
the work was *done* or *dropped* — and archived work stops blocking whatever depended
on it, so tidying up never strands the tasks behind it.

**Time tracked per node — and whose time it was.** Start a timer on anything. Node size
on the graph encodes cumulative tracked time, so the picture of your vault doubles as a
picture of where your time went. The log records who spent it, so the Work screen can
say how much of a body of work a machine did, and which agent did it.

![Where the time went, split by worker](assets/screenshots/work.png)

## Agents work the graph

The MCP server runs inside the app, on loopback, for as long as the window is open.
That is deliberate: an agent's change lands on the graph you are looking at, not just
in a file you will notice later.

| Tool | What it is for |
| --- | --- |
| `list_ready_tasks` | What can actually be started now, ranked. The question to ask first |
| `get_node` | Read one node in full when a ready-task summary is not enough |
| `create_task` | Capture, with an optional deadline |
| `link_nodes` | `depends_on` or `relates_to`. Cycles are refused |
| `update_status` | `todo` / `doing` / `done` / `dropped`, and what the change unblocked |

Five tools, kept deliberately few: every schema is sent on every request of every
session, so the surface is the loop — orient, capture, structure, close — and nothing
else. Destructive and fiddly operations stay in the app, where you can see what you
are doing.

Point a client at it while the app is running:

```bash
claude mcp add --transport http mindgraph http://127.0.0.1:4319/mcp
codex mcp add mindgraph --url http://127.0.0.1:4319/mcp
```

Other agents should configure a Streamable HTTP MCP server named `mindgraph` with the
same URL. The server is loopback-only by default, so agents on another machine need an
SSH tunnel or another deliberately secured transport rather than opening the port.

Set `MINDGRAPH_MCP_PORT` to move it. If the port is busy the app says so and starts
anyway — MindGraph without MCP is still MindGraph.

Agents working on this repository should also load the workflow skill at
`.claude/skills/mindgraph-workflow/SKILL.md`. The file is plain Markdown, so agents
without Claude-style skills can treat it as their system or project instructions. Its
important rule is simple: ask `list_ready_tasks`, create or find the work node, mark it
`doing` with `update_status`, then close it when the work is finished.

**Machine labour.** Moving a task to `doing` starts the clock and closing it stops the
clock, so elapsed time is a consequence of the work being tracked rather than a second
thing to remember. Where the change came from decides who is credited: the same status
set in the window is your work, set over MCP it is the machine's. Nothing is declared,
so the split cannot drift from the truth. An agent can name itself, and the Work screen
totals it by name.

## Your data

A vault is a directory of markdown. One file per node, and the frontmatter fully
describes it:

```markdown
---
id: 01M0V4BQMAJ000RTB5PNFK2P5N
title: Write the store layer
kind: note
status: doing
due: 2026-09-04
depends_on: [01M0V4BNNTVG12ZJ9QHSZG0BTB]
created: 2026-08-24T21:00:00Z
updated: 2026-08-24T21:00:00Z
---

Markdown is the truth. See [[Pick the node schema]] for why.
```

This has consequences worth knowing:

- **Edit in any editor.** Files you hand-write load exactly like files the app wrote.
- **Fields you add are kept.** Unknown frontmatter keys survive round trips.
- **Rename freely.** The `id` is a ULID and never changes, so renaming a file or a
  title never breaks a link.
- **Version it.** A vault is plain text, so `git init` in it works.
- **Delete a link, delete the edge.** `[[wikilinks]]` are read from the body every
  load and never copied into frontmatter, so nothing is left stranded.
- **A typo costs a label, not a document.** An unreadable `kind` reads as a note, an
  unreadable `due` as no deadline. Neither drops the file.

Tracked time lives in `.mindgraph/sessions.jsonl` as an append-only log, kept out of
the markdown so stopping a timer doesn't rewrite a note:

```json
{"node_id":"01M0V4BQMAJ000RTB5PNFK2P5N","started_at":1788307701,"stopped_at":1788309201,"seconds":1500,"worker":"agent","agent":"claude-code"}
```

Lines written before the log recorded a worker read as your own work, which is what
they were.

### Layout

```
~/.config/mindgraph/vault/
├── nodes/                # one .md per node — this is your content
└── .mindgraph/
    └── sessions.jsonl    # append-only tracked time, per worker
```

Set `MINDGRAPH_HOME` to put the vault somewhere else. If `HOME` is unavailable,
MindGraph falls back to `./.mindgraph-vault/`.

## Getting started

```bash
cd desktop
./gradlew run
```

The vault directory is created on first launch. There is no import step: drop markdown
files into `nodes/` and they show up, as long as each carries a unique ULID `id` in its
frontmatter. Files without one are skipped rather than rewritten, so an unrelated
markdown file sitting in the folder is left alone.

## Useful commands

```bash
make run
make test
make build
```

Or from `desktop/` directly: `./gradlew run`, `./gradlew test`, `./gradlew build`.

## Project layout

- `model/` — `Node`, its `NodeKind` and optional `TaskFacet`, projected `Edge`s, and
  the `WorkSession` that records who spent the time
- `storage/` — the markdown vault: frontmatter parsing, the node store, the session log
- `state/` — `AppViewModel` (the single source of UI state), the graph layout engine,
  `TaskGraph`, which derives blocked/ready state and ranks what to do next, and the
  linking, filtering and work-summary rules the UI and the tools share
- `mcp/` — the MCP protocol, its loopback HTTP transport, and the tools agents call
- `ui/` — the nav rail and the Graph, Notes, Tasks, and Work destinations

All under `desktop/src/main/kotlin/dev/mindgraph/`.

Rules that both the app and the agents must obey live in `state/`, not in either
caller — so an agent cannot build a graph the app would have refused.

## Documentation

- [AGENTS.md](AGENTS.md) contributor guide and repository conventions
- [HOW_TO_CONTRIBUTE.md](HOW_TO_CONTRIBUTE.md) contributor workflow
- [ROADMAP.md](ROADMAP.md) product direction and upcoming work
- [BENCHMARK.md](BENCHMARK.md) benchmark notes and methodology

## License

[MIT](LICENSE).

## Where this is going

The graph is meant to become the unifying surface over every kind of entity, not a
visualization bolted onto notes. The agent layer is no longer the plan — it is the
current work.

Next: retrieval that follows edges rather than strings, so an agent asking about a
topic gets the neighbourhood around it; and importing the notes coding agents already
write, which sit siloed per project and have never been readable as one graph.
