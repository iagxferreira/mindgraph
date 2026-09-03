# Changelog

All notable changes to MindGraph will be tracked in this file.

## [Unreleased]

### Added

- **`suggest_links` — the edges the vault has evidence for and does not have.** An imported vault
  arrives with its notes and almost none of its connections, and a graph whose value is its edges
  cannot be filled in by hand at 465 nodes. Two signals: a `[[link]]` written to a name that never
  resolved, and one note naming another in prose with no edge between them. Ask it about a node
  for a handful, or sweep the whole vault. There is a chip in the editor that lists a few and
  links the ones you accept.

  Volume was the design problem rather than recall — measured on a real vault, a four character
  name floor offers 1010 pairs and eight offers 362 — so names must be long enough not to match by
  coincidence, matched on word boundaries, and unambiguous: forty-nine notes here are called
  `index`, and a suggestion that cannot say which one it means is not a suggestion. Each one
  states its reason in words rather than a score, because a number invites trust without
  understanding.

  It suggests and never links. An edge is a claim about meaning, so a person makes it.

- **Import any folder of markdown.** The node list can now take a directory — a repository's
  `docs/adr`, an Obsidian vault, a folder of RFCs — choosing what kind the documents are and which
  project they belong to. Both are guessed from the path and shown before anything is written: a
  folder called `adr` holds RFCs, and `~/workspace/tally/docs/adr` belongs to *tally* rather than
  to `adr`.

  The files are copied and the vault owns its copies. `origin` records where each came from, the
  source folder is never written to, and a later edit there does not reach the copy. That is a
  snapshot rather than a mirror on purpose: agents append to nodes, and re-syncing would have to
  either discard what an agent added or refuse to run.

  Vendored and tool directories are skipped — `node_modules`, `.git`, `.obsidian`, `.trash`,
  build output — along with Excalidraw drawings, which are megabytes of JSON wearing a `.md`
  extension. That is the idea the Codex importer was missing when it imported Next.js's
  instructions to its own contributors. A file that already carries a MindGraph id is left alone
  rather than copied under a second identity.

- **The working agreement is served over MCP** as the resource `mindgraph://working-agreement`
  and the prompt `working-agreement`, so an agent in any repository can learn how to use the
  vault without a file being installed. Deliberately not a tool that writes into `~/.claude/` —
  that would be the server reaching into the client's configuration. `make install-agent-skill`
  adds a pointer-only stub for Claude Code, which auto-loads skills but not prompts.

- **The MCP server is registered at user scope**, so an agent working in *any* repository
  reaches the same vault. Registered per project it was reachable only from the repository it
  was registered in, which is precisely the silo MindGraph exists to remove.

- **MindGraph installs as a real desktop app.** `make package` builds a native installer for
  the host — an RPM was added alongside Deb, Msi and Dmg, since Fedora had no installable
  artifact despite being the machine this is developed on. The package bundles its own Java
  runtime, so it does not break when a system JDK is upgraded or removed, and it appears in the
  applications menu with its own icon.

  Three things had to be got right and each failed first: the jlinked runtime shipped without
  `jdk.httpserver` and the installed app crashed on launch, because jlink cannot see a class
  reached only by name; `menuGroup` labels a menu entry but does not create one, so the app
  installed correctly and was invisible; and the desktop cannot match a window to an entry
  without `StartupWMClass`, so the icon reverted to a generic one the moment the app opened.
  jpackage cannot declare that last one, so the entry is written by hand in `packaging/` and
  installed with `make install-desktop-entry`.

- **The logo, used as the app icon and the brand mark.** Generated for each platform from one
  source, and shown in the nav rail in place of the letter it stood in for.

- **`related_notes` over MCP — context as a document, not a list of hits.** Give it a topic and
  it finds the starting node, walks the graph outwards and returns the neighbourhood as one
  markdown document to read straight into context: nearest first, a curated `context_for` link
  outranking an inferred one at the same distance, cut to a token budget, with whatever did not
  fit listed at the end rather than silently dropped.

  Traversal rather than matching, so a note that never repeats your words still arrives — and it
  crosses every project, which per-project agent memory structurally cannot. Edges are walked in
  both directions, because `context_for` points from the note to the work it serves and the
  curated case is entirely incoming.

  Its acceptance test is the Goldfish test: hand a blank agent only this document and it should
  be able to start. That is why the bundle carries the bodies of what it names and not just the
  titles — a title is a second lookup, and a goldfish cannot make one.

- **`context_for` edges — the first half of the context builder.** A project is a node, and its
  context is its edges: starting one means creating a node and linking in what an agent should
  load before working on it. Deliberately not `relates_to` — "these two ideas are related" is not
  "load this first", and reusing association would put every incidental link into the bundle,
  which is how a context window fills with things nobody chose. The edge is held on the node that
  *is* the context, so a note copied out of the vault still says what it was for and one note can
  brief several projects without being duplicated. Never inferred from a `[[wikilink]]`.

  It is drawn on the canvas in its own hue, offered in the link dialog as an explained choice
  rather than an unlabelled third button, and available over MCP: `link_nodes` takes
  `context_for`, and `get_node` reports both what a note serves and — the useful direction — the
  bundle to load when starting on it.

- **A control that hides node labels.** At vault scale the canvas drew a title under every
  node and they overlapped into noise. The Graph tab's action rail can now turn them off, so
  the shape of the graph reads without the text; selecting a node still names it in the card,
  so nothing becomes unidentifiable. Cluster captions stay — there are few of them and the
  mode means nothing without them.

### Changed

- **Flow mode is now a dependency tree over tasks.** It used to rank every visible node by
  dependency depth, but a real vault barely has dependencies — 11 edges against 104 nodes — so
  all but a handful landed at depth 0, each claiming its own column, and the mode rendered as
  one row 8,820px wide with the structure lost inside it. Flow now draws only the tasks that
  participate in a dependency: each chain laid out as its own tree, prerequisites centred over
  the work waiting on them, and the trees packed into rows that wrap. Tasks with no dependency
  either way are counted on the pill rather than drawn, since a lone task is not a tree, and an
  empty Flow says so instead of showing a blank canvas.

  Finished tasks inside a chain stay as faded ghosts when done work is hidden. Seven of the
  nine nodes in this vault's one real tree are done, so hiding them severed it and left the
  live tasks as orphans with no visible reason to be where they are. The kind filter is gone
  from this tab for the same reason: narrowing a chain to one kind cuts trees in half.

- **Filtering now rebuilds the layout.** Hiding archived nodes, done tasks or a kind used to
  leave the survivors exactly where they were, laid out for a count that no longer existed —
  and for Flow and Cluster the layout was never recomputed at all, because the effect was
  keyed on the unfiltered vault. The engine is now driven by the visible set: ranks and
  groups come from the visible subgraph, and a filter toggle reflows the unpinned nodes so
  the picture is built from the number of notes actually on screen. Pinned nodes keep their
  position — a pin is a placement you chose by dragging. Blocking is still computed over the
  whole vault, since a hidden node still blocks what depends on it.

- **A Cluster layout mode**, grouping the graph by the repository each node was imported
  from, with every group named on the canvas. Nodes written in the vault itself group as
  "This vault", which is the split that matters once other projects' notes are in.

- **`search_notes` over MCP.** Agents can now scan the current vault by title, alias, or
  body text and receive each match's id, title, kind, and bounded context snippet. It is
  deliberately a straight scan rather than a cache, so external vault edits are visible on
  the next call.


- **Codex repository instructions import.** The node list can now import nested
  `AGENTS.md` files from `CODEX_WORKSPACE_ROOT` (defaulting to `~/workspace`) as
  read-only, re-runnable context notes with their source path preserved.

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
