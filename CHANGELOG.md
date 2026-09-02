# Changelog

All notable changes to MindGraph will be tracked in this file.

## [Unreleased]

### Added

- **A Cluster layout mode**, grouping the graph by the repository each node was imported
  from, with every group named on the canvas. Nodes written in the vault itself group as
  "This vault", which is the split that matters once other projects' notes are in.

- **Claude's plan documents import as RFC nodes.** `~/.claude/plans/*.md` are design
  documents already — context, decision, rationale — and land as `kind: rfc`, linked to
  what the vault knows about the project their title names. A project mentioned only in the
  body is not treated as the subject: against the real plans that produced a wrong edge.

- **Imported wikilinks resolve into edges.** A node can now answer to more than one name:
  `aliases` in frontmatter, and the `memoryName` an import records. Claude's memory notes
  link each other by slug while their titles are full sentences, so those links resolved to
  nothing before — 20 of the 25 in a real vault become edges now. Shown in the editor under
  "Also" and reported by `get_node`.

- **Claude Code's memory notes import into the vault.** `~/.claude/projects/*/memory/*.md`
  was markdown with frontmatter and `[[wikilinks]]` already, sealed in a directory per
  project; the node list has an import button that brings it in as one graph. Re-runnable
  — files already imported are skipped — and read-only upstream: `~/.claude` is never
  written back to. `origin`, `originProject`, `memoryName` and `memoryType` are kept in
  frontmatter, so nothing about where a note came from is lost.

- **The vault is watched.** A node written, edited or deleted outside the window — by an
  agent over MCP, an editor, or a git checkout — reaches the graph on its own, with no
  manual reload. Bursts are coalesced, so one save is one reload.

- **`create_note` and `append_node_body` over MCP.** Agents can record what is not work —
  a note, RFC or reference with no status, so it never lands in ready work as a task
  nobody will do — and keep a running record on the node they are working. Appending only
  ever adds; it cannot alter a node's fields or a word already in its body.
- Node creation can set the kind, so an RFC or reference can be created as one rather than
  relabelled after the fact.

- A hide-done control on the all-nodes list and both graph modes, so completed task
  nodes can be removed from working views without changing the vault.

- **An MCP server hosted by the running app**, on loopback for as long as the window is
  open, so coding agents work the graph directly: `list_ready_tasks`, `create_task`,
  `link_nodes`, and `update_status`. Agents create new context nodes rather than
  rewriting existing notes; human edits remain in the app.
- **Node kinds** — note, RFC, or reference — drawn on the canvas as shape, with a filter
  that narrows the graph and sections that group the node list.
- **Deadlines.** `due` is read from frontmatter and ranks ready work: overdue first, then
  due soon, then by how much finishing a task unblocks. Deliberately not a priority field.
- **Machine labour.** Moving a task to `doing` starts the clock and closing it stops the
  clock. A change made in the window is your work; the same change over MCP is the
  machine's, and an agent can name itself. The Work screen shows the split per node and
  per agent.
- **Archiving.** A node can be put away and keep its id, links and tracked time. Archived
  work stops blocking whatever depended on it.
- **Assignment.** A node can belong to a person or an agent; `list_ready_tasks` narrows to
  an assignee. Assignment filters, it never gates.
- A `mindgraph-workflow` skill recording the working agreement for agents on this repo.
- Agent setup docs for Claude, Codex, and other Streamable HTTP MCP clients, including
  how to reuse the MindGraph workflow skill outside Claude.

- A pannable/zoomable graph view of notes and links, with node size encoding
  cumulative tracked time per note, in Mind (force-directed) and Flow (layered by
  dependency) modes.
- Per-note markdown editing, link creation with a relationship label, and
  start/pause/stop time tracking.

### Changed

- Replaced the Rust/Ratatui TUI with a Kotlin/Compose Multiplatform Desktop app,
  living in `desktop/`.
- Replaced the `data.json` store with a markdown vault: one `.md` file per node, whose
  frontmatter fully describes it, and an append-only session log for tracked time.

### Removed

- The Rust/Ratatui TUI shell (dashboard, tasks, mind, run, pomodoro, notifications,
  and workspaces screens) and its launcher overlay. Tasks and Workspaces screens are
  planned to return in the new app; see [ROADMAP.md](ROADMAP.md).

## [0.1.0] (Rust TUI, historical)

### Added

- Pane-driven terminal shell with dashboard, tasks, mind, run, pomodoro, notifications, and workspaces screens.
- File-backed task, workspace, note, link, pomodoro session, and work-item persistence with reducer-driven state updates.
- Launcher overlay with screen-scoped actions and a dedicated Run workflow.
- Markdown note editing with filesystem-backed note paths.
- Contributor docs for workflow, roadmap, and benchmark notes.
