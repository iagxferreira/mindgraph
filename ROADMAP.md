# Roadmap

This file tracks the next MindGraph milestones. Keep items small, shippable, and
focused on one layer at a time.

The live version of this list is the vault itself — MindGraph plans MindGraph. Ask
`list_ready_tasks` with the app running and the graph will tell you what is startable
and what it unblocks. This file is the readable summary, not the source of truth.

## Current State

- MindGraph is a Kotlin/Compose Multiplatform Desktop app over a markdown vault. Every
  node is one `.md` file whose frontmatter fully describes it.
- **The running app hosts an MCP server** on loopback, so coding agents read and write
  the graph directly: `list_ready_tasks`, `get_node`, `create_task`, `create_note`,
  `append_node_body`, `link_nodes`, `update_status`. Agents add — a note beside the work,
  or text on the end of it — and never rewrite what a human wrote.
- **The vault is watched.** A node written outside the window, by an agent in another
  process or an editor, reaches the graph without a reload. Markdown stays the source of
  truth in fact and not just in principle.
- Nodes have a **kind** — note, RFC, or reference — drawn on the canvas as shape, with
  a filter that narrows the graph and sections that group the list.
- **Readiness is derived**, never declared: a task whose dependencies are unfinished is
  blocked, and cycles are refused when created. Ready work is ordered overdue first,
  then by how much finishing it unblocks. There is no priority field, on purpose.
- **Tracked time records who spent it.** Moving a task to `doing` starts the clock;
  closing it stops the clock. A change made in the window is your work, the same change
  over MCP is the machine's, and an agent can name itself. The Work screen shows the
  split, per node and per agent.
- Nodes can be **archived** — kept with their id, links and tracked time, out of the
  graph and out of ready work — and **assigned** to a person or an agent, which filters
  what you are shown without ever gating what is ready.
- **Claude Code's memory notes import into the vault**, re-runnably and read-only
  upstream, keeping `origin`, `originProject`, `memoryName` and `memoryType` in
  frontmatter. `memoryType` is deliberately *not* mapped onto `kind`: one says what a fact
  is about, the other what a document is, and only `reference` means the same in both.
- `.claude/skills/mindgraph-workflow` records the working agreement: the node exists
  before the work does.

## Next Milestone

The memory import has landed; the plans import and wikilink resolution are what it
unblocks.

- **Import `~/.claude/plans/*.md` as RFC nodes.** They are RFCs already, without the
  label.
- **Resolve imported wikilinks into edges**, slug-aware, or the import lands as
  disconnected dots.
- **Retrieval over MCP**: `search_notes`, then `related_notes` — walking edges rather
  than matching strings, and crossing projects, which per-project memory structurally
  cannot do.

## After That

- Restoring archived nodes from the node list, and sorting it by more than recency.
- Packaging (`packageDeb`/`packageMsi`/`packageDmg`) so the app outlives the terminal
  that launched it, and registering the MCP server at user scope so agents in other
  repositories can reach the graph.

## Later

- Clustering the graph by origin project, once imported notes from many repositories
  make that a balanced grouping. Clustering by kind was considered and rejected: it
  fights the force-directed layout and the kinds are lopsided.
- A review lane for agent-created nodes, if unattended creation turns out to fill the
  vault with noise. Worth deciding from a week of real use rather than in advance.
- Whether MindGraph subsumes Claude's per-project memory entirely. Two memory systems
  is worse than either alone; this is a decision to make before retrieval ships.

## Notes

- Do not relist storage as new work; the markdown vault, the frontmatter parser and the
  append-only session log all exist.
- Treat MindGraph as the product name everywhere in the repository.
- Keep `AppViewModel` as the single source of UI state.
- Rules both the app and the agents must obey live in `state/`, not in either caller —
  an agent must not be able to build a graph the app would have refused.
- A new field is not finished until it round-trips through storage, can be set, and is
  visible somewhere. `due` and `kind` each shipped missing one of the three.
- Update this file when a feature is started, split, or finished — and update the vault
  first, since that is what the tools read.
