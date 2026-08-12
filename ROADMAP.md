# Roadmap

This file tracks the next planned work for Forge. Keep items small and shippable.

## Now

- Finish the dashboard polish for the pomodoro panel.
- Add better task details formatting and wrapping.

## Next

- Implement a real notifications screen.
- Add a workspace management screen with create, rename, and delete flows.
- Persist workspace-specific configuration.

## Later

- Make pomodoro settings configurable per workspace.
- Add task search and filtering.
- Add plugin loading and lifecycle hooks.
- Introduce a command palette for less common actions.

## Notes

- Keep `AppState` as the single source of truth for UI state.
- Prefer adding new behavior behind services and storage interfaces.
- Update this file when a feature is started, split, or finished.
