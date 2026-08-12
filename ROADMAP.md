# Roadmap

This file tracks the next planned work for Forge. Keep items small and shippable.

## Done

- Dashboard, tasks, and workspace screens are wired into the TUI.
- Task and workspace state is persisted through SQLite.
- Keyboard navigation and command hints are in place.

## Now

- Finish the dashboard polish for the pomodoro panel.
- Add better task details formatting and wrapping.

## Next

- Implement a real notifications screen.
- Persist workspace-specific configuration.
- Refine workspace screen empty states and details formatting.

## Later

- Make pomodoro settings configurable per workspace.
- Add task search and filtering.
- Add plugin loading and lifecycle hooks.
- Introduce a command palette for less common actions.

## Notes

- Keep `AppState` as the single source of truth for UI state.
- Prefer adding new behavior behind services and storage interfaces.
- Update this file when a feature is started, split, or finished.
