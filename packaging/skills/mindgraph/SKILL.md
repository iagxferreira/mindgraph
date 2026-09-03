---
name: mindgraph
description: Use whenever working in a repository connected to the MindGraph vault - before starting work, to load the context already recorded about it, and before finishing, to record what was done. MindGraph is one graph of notes spanning every project, reached over MCP.
---

# MindGraph

A MindGraph vault is reachable over MCP as the `mindgraph` server. It holds one graph of notes
spanning every project, so context recorded while working elsewhere is available here.

**Read the working agreement before acting on the vault.** It is served by the server itself, at
the resource `mindgraph://working-agreement` — or as the prompt `working-agreement`, which in
Claude Code is `/mcp__mindgraph__working-agreement`.

This file deliberately carries no rules of its own. The agreement lives with the app that
enforces it, so it cannot describe a version of the tools that no longer exists — and every MCP
client gets the same text, not only the ones that understand skill files.

The short version, so you know whether to fetch it:

- Load context first with `related_notes`, rather than asking for what is already written down.
- The node exists before the work does: `create_task` before changing things, `update_status` to
  `doing` naming yourself, and close it when finished.
- Agents are append-only. Never rewrite someone else's node; add a new one with an edge, or
  `append_node_body`.

If the tools do not answer, the MindGraph app is closed. That means the context is unavailable,
not that it is empty — say so rather than proceeding as though the vault were bare.
