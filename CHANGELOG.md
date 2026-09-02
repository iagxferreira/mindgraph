# Changelog

All notable changes to MindGraph will be tracked in this file.

## [Unreleased]

### Added

- **An MCP server hosted by the running app**, on loopback for as long as the window is
  open, so coding agents work the graph directly: `list_ready_tasks`, `create_task`,
  `link_nodes`, and `update_status`.
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
