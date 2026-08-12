# Roadmap

This file tracks the next MindGraph milestones. Keep items small, shippable, and focused on one layer at a time.

## Current State

- MindGraph already ships as a Ratatui-based TUI shell.
- The current user-facing surfaces are dashboard, tasks, notifications, workspaces, launcher, theme switching, and Pomodoro tracking.
- File-backed persistence already exists for tasks, workspaces, vaults, notes, and links.
- Vault, note, and link repositories already exist in storage, but there is no user-facing workflow for them yet.
- The plugin trait exists, but plugin loading and execution are not wired into the app.

## Next Milestone

- Expose the existing vault, note, and link storage through services.
- Add note and vault UI surfaces.
- Add note creation, editing, deletion, and selection flows.
- Surface backlinks and outgoing links in the UI.

## After That

- Add markdown editing and note navigation improvements.
- Add search over notes and links.
- Add daily notes and templates if the note model proves stable.
- Add task views that can derive from notes if the separate task model becomes redundant.

## Later

- Add richer graph navigation and relationship visualization.
- Add plugin loading and lifecycle hooks once the core navigation model settles.
- Add sync or multi-vault workflows only after the local model is stable.
- Add AI-assisted retrieval or generation only if it meaningfully improves the local workflow.

## Notes

- Do not relist storage tables as new work; the file-backed repositories already exist.
- Keep `AppState` as the single source of truth for UI state.
- Prefer adding behavior behind services and storage interfaces.
- Update this file when a feature is started, split, or finished.
