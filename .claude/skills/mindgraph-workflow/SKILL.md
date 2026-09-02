---
name: mindgraph-workflow
description: Use when doing any development work on MindGraph - before writing code, and before claiming work is finished. Records the work in the vault through the MCP server so the graph stays a true account of what was built and who built it.
---

# Working through MindGraph

## Overview

MindGraph's own vault is the plan for MindGraph. Work that never enters the graph is
work the graph cannot rank, block, or account for — and a second brain you route
around is just a folder.

**Core principle: the node exists before the work does.**

The tools are only useful if they are reached for while building, not narrated
afterwards. This project's MCP server is the thing being built; not using it is the
one bug tests cannot catch.

## The Iron Law

```
NO CODE BEFORE THE NODE
```

If you are about to edit a file for a feature or fix with no node in the vault,
stop and create one.

## Prerequisites

The MCP server lives inside the running app, so **the app must be open**:

```
./gradlew -p desktop run
```

If tools fail with `ConnectionRefused`, the app is closed. If they fail with
`NoClassDefFoundError`, see Traps below.

## MCP client setup

Claude can install the server with:

```
claude mcp add --transport http mindgraph http://127.0.0.1:4319/mcp
```

Codex can install the same server with:

```
codex mcp add mindgraph --url http://127.0.0.1:4319/mcp
```

Other agents should configure a Streamable HTTP MCP server named `mindgraph` at
`http://127.0.0.1:4319/mcp`. If the agent cannot load Claude-style skills, treat this
Markdown file as the project workflow instructions and follow the loop below exactly.

## The loop

**1. Orient.** `list_ready_tasks` before deciding what to do. Readiness is computed
from the graph — overdue first, then by how much finishing a thing unblocks. If the
user named the work, still check whether a node for it already exists.

**2. Record.** Find the node, or `create_task` with a body that says *why*, not just
what. Then `link_nodes` with `depends_on` for anything it genuinely cannot start
without. An unlinked node is a dot; the edges are the product.

**3. Start the clock.** `update_status` to `doing`, with `agent` set to your own name.
This is what logs the work as machine labour — time between `doing` and closing is
attributed to the agent, and nothing else records it.

**4. Build.** Follow the repo's own conventions: one layer per commit, Conventional
Commits, tests beside the layer they cover.

**5. Close.** `update_status` to `done` — it reports what became unblocked. Use
`dropped` for an approach abandoned; it releases dependents the same way, and keeps
the record that it was tried.

## Scope you did not deliver

If part of a node's "done when" is unmet, do not quietly close it and do not silently
widen it. `create_task` for the remainder, `link_nodes` it to the node it came out of,
and say so. A split-out node is honest; an unmet checklist item inside a closed node
is a lie the graph will repeat back later.

## Judgment

Not everything is a node. A typo, a rename, a one-line fix the user asked for
directly — just do it. Create a node when the work has a *why* worth keeping, when it
will take more than one commit, or when something else will depend on it.

Archive rather than delete when work stops mattering: `archived` keeps the id, the
links and the tracked time, and stops the node blocking anything downstream.

## Traps this project actually sets

**Rebuilding under the running app breaks it.** Gradle overwrites `build/classes`
while the JVM is still lazily loading from it, and the next tool call fails with
`NoClassDefFoundError: dev/mindgraph/...`. It is not a code bug. Restart the app after
any rebuild, before calling the tools again.

**A field the UI can neither show nor set is half a feature.** `due` shipped ranked but
invisible; `kind` shipped drawn but unsettable. Both were caught by the user, not by
the tests. When adding a field, check three paths: storage round-trip, a way to set it,
and a place it is visible.

**UI cannot be verified from here.** Screenshots are blocked in this environment, so
anything visual is reasoned from code and unproven. Say so plainly, and ask the user
for a screenshot rather than implying the layout was checked.

**Verify atomicity mechanically.** Before claiming commits are atomic, check each one
out into a `git worktree` and run `./gradlew test` at that commit. Splitting by eye is
not the same as a bisectable history.
