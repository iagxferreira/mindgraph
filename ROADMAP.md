# Roadmap

This file tracks the merged Forge + MindGraph direction. Keep items small, shippable, and focused on one layer at a time.

## Product Shape

- `Forge` is the terminal workspace shell and execution layer.
- `MindGraph` is the knowledge model layered on top of that shell.
- The merged product keeps tasks, workspaces, dashboarding, and plugins while expanding the core around notes, links, and graph-backed navigation.

## What Stays

- Ratatui-based TUI shell
- Central `AppState` and reducer-style event flow
- SQLite-backed persistence
- Dashboard, task, and workspace surfaces
- Async services and storage boundaries
- Plugin hook surface

## What Gets Added

- Vaults and note storage
- Markdown notes as the base knowledge unit
- Wiki links and backlinks
- Graph indexing and relationship discovery
- Note explorer and note editor panes
- Full-text search over notes and links
- Command palette for common actions
- Later, AI-assisted retrieval and generation

## Now

- Define the merged core domain model:
  - vaults
  - notes
  - links
  - tasks as note-native data
  - workspaces as contexts over a vault
- Add storage tables and repositories for notes and links.
- Keep the existing task and workspace flows intact while the new model lands.

Deliverable:

- A knowledge-first data model that coexists with the current Forge workspace shell.

## Next

- Replace the current task-centric dashboard with a knowledge dashboard.
- Add a note explorer pane with create, rename, move, and delete actions.
- Add a Markdown editor pane with wiki-link support.
- Generate backlinks from note content.
- Add basic full-text search.

Deliverable:

- A functional terminal knowledge workspace with navigable notes and backlinks.

## Later

- Add graph visualization in stages:
  - ASCII graph
  - terminal layout graph
  - richer interactive rendering
- Add command palette actions for note workflows.
- Add daily notes and templates.
- Add task views that operate over notes instead of a separate task silo.
- Add plugin lifecycle hooks for note-aware extensions.

## Much Later

- Add semantic search and embeddings.
- Add AI summarization and relationship discovery.
- Add external integrations such as calendar, email, and RSS.
- Add sync and multi-vault workflows if the core model proves stable.

## Notes

- Prefer merging features into the note graph rather than creating parallel systems.
- Keep `AppState` as the single source of truth for UI state.
- Prefer adding new behavior behind services and storage interfaces.
- Update this file when a feature is started, split, or finished.
