# MindGraph

MindGraph is a knowledge-base builder: a graph of linked notes correlated with time
tracking, so you can see where study and work time actually went.

It is built with Kotlin and Compose Multiplatform for Desktop. The app lives in
`desktop/`, with `model/`, `storage/`, `state/`, and `ui/` packages under
`desktop/src/main/kotlin/dev/mindgraph/`.

## What You Get

- A pannable/zoomable graph of notes and the links between them
- Node size encodes cumulative tracked time for that note — the graph doubles as a
  picture of where your time went
- Click-to-select a note, click-two-nodes to link them with a relationship label
- Per-note markdown editing (title + body)
- Per-note start/pause/stop time tracking, logged as pomodoro sessions
- File-backed persistence for tasks, workspaces, vaults, notes, links, pomodoro
  sessions, and work items

## Getting Started

```bash
cd desktop
./gradlew run
```

By default MindGraph stores data in `~/.config/mindgraph/`.

On first launch, MindGraph creates the storage directory structure if it does not
already exist.

- `config.json` stores app-level configuration.
- `data.json` stores tasks, workspaces, vaults, notes, links, pomodoro sessions, and
  work items.
- `workspaces/` is the default workspace root under the storage directory.

To change the storage location, set `MINDGRAPH_HOME` to a different directory.
If `HOME` is unavailable, MindGraph falls back to `./.mindgraph/`.

## Useful Commands

```bash
make run
make test
make build
```

Or from `desktop/` directly: `./gradlew run`, `./gradlew test`, `./gradlew build`.

## Documentation

- [AGENTS.md](AGENTS.md) contributor guide and repository conventions
- [HOW_TO_CONTRIBUTE.md](HOW_TO_CONTRIBUTE.md) contributor workflow
- [ROADMAP.md](ROADMAP.md) product direction and upcoming work
- [BENCHMARK.md](BENCHMARK.md) benchmark notes and methodology

## Project Layout

- `desktop/src/main/kotlin/dev/mindgraph/model/` defines the domain models, matching
  the Rust-era `data.json` schema field-for-field
- `desktop/src/main/kotlin/dev/mindgraph/storage/` owns JSON-backed persistence and
  repositories
- `desktop/src/main/kotlin/dev/mindgraph/state/` holds `AppViewModel` (the single
  source of truth for UI state) and the force-directed graph layout engine
- `desktop/src/main/kotlin/dev/mindgraph/ui/` renders the graph canvas, note detail
  panel, and time-tracking panel

## Current Focus

The current product is the notes graph plus time tracking: a canvas of notes and
links where node size reflects tracked time, a detail panel for editing a note and
managing its links, and start/pause/stop tracking per note.

Deferred for a later pass: a Tasks screen, a Workspaces screen, notifications, and a
command launcher — all still supported by the storage layer, just without dedicated
UI yet.
