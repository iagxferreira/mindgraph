# Roadmap

This file tracks the next MindGraph milestones. Keep items small, shippable, and focused on one layer at a time.

## Current State

- MindGraph already ships as a Ratatui-based TUI shell.
- The current user-facing surfaces are dashboard, tasks, mind, run, pomodoro, notifications, workspaces, launcher, and theme switching.
- File-backed persistence already exists for tasks, workspaces, vaults, notes, links, pomodoro sessions, and work items.
- Notes are stored as markdown files and work items now link a task to a note with persisted run state.
- The plugin trait exists, but plugin loading and execution are not wired into the app.

## Next Milestone

- Make work-item creation and inspection easier from the Run screen.
- Surface linked work-item state on task and note screens.
- Add direct task-to-note linking shortcuts where the current workflow still requires too many steps.
- Improve note search and backlinks now that the markdown model is stable.

## After That

- Add search over notes, work items, and links.
- Add daily notes and templates if the note model proves stable.
- Decide whether tasks should remain distinct from notes or converge into a shared work-item model.
- Add workspace onboarding polish if the startup path still feels awkward.

## Later

- Add richer graph navigation and relationship visualization.
- Add plugin loading and lifecycle hooks once the core navigation model settles.
- Add sync or multi-vault workflows only after the local model is stable.
- Add AI-assisted retrieval or generation only if it meaningfully improves the local workflow.

## Notes

- Do not relist storage tables as new work; the file-backed repositories already exist.
- Treat MindGraph as the product name everywhere in the repository.
- Keep `AppState` as the single source of truth for UI state.
- Prefer adding behavior behind services and storage interfaces.
- Update this file when a feature is started, split, or finished.
