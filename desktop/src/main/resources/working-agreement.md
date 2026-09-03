# Working through MindGraph

You are connected to a MindGraph vault: one graph of markdown notes spanning every project,
reachable over MCP. This is the working agreement for using it. It applies wherever you are —
the vault is not scoped to the repository you happen to be in.

## Before the work

**Load the context.** `related_notes` with what you are about to do. It walks the graph outward
and returns the neighbourhood as one document cut to a token budget, so a decision already made
in another repository reaches you instead of being re-derived. `search_notes` finds a node by
text when you only have a phrase; `get_node` reads one in full.

This is the point of the graph. Skipping it and asking the user what they already wrote down is
the failure the vault exists to prevent.

## Recording work

**The node exists before the work does.** If you are about to change something and no node
describes it, `create_task` first, with a body that says *why* rather than only what. Then
`link_nodes` with `depends_on` for anything it genuinely cannot start without.

Not everything is work. `create_note` records a finding, a decision or a piece of reference as a
note, RFC or reference with no status, so it never appears in ready work as a task nobody will
do. Reach for it rather than minting a task you do not intend anyone to perform.

**Mark it `doing` with `update_status`, naming yourself in `agent`.** Time between `doing` and
closing is attributed to you, and nothing else records it. Close with `done`, or `dropped` for an
approach abandoned — that releases dependents the same way and keeps the record that it was
tried.

**Keep the record on the node.** As you learn something the next session would otherwise
rediscover — a decision and its reason, an approach that failed and why — `append_node_body` it
onto the node while it is fresh.

## What you may not do

**Agents are append-only.** Do not rewrite an existing node's title, body, kind, deadline or
assignee; those edits belong to the person, in the app. There are two deliberate exceptions:
`update_status` on the task you are working, and `append_node_body`, which only ever adds and
can never alter or remove what is already there.

New context arrives as another node with an edge, never as an edit to someone else's writing.

## Building a context set

`context_for` marks a note as background to load when working on something else — *load this
first*, which is not the same as `relates_to`. It is how a briefing is assembled deliberately
rather than derived, and `related_notes` puts curated notes ahead of inferred ones.

## Scope you did not deliver

If part of a node's "done when" is unmet, do not quietly close it and do not silently widen it.
`create_task` for the remainder, link it to the node it came out of, and say so. A split-out node
is honest; an unmet checklist item inside a closed node is a lie the graph repeats back later.

## Judgment

Not everything is a node. A typo, a rename, a one-line fix asked for directly — just do it.
Create a node when the work has a *why* worth keeping, when it will take more than one commit,
or when something else will depend on it.

## If the tools stop answering

The MCP server lives inside the MindGraph desktop app and is reachable only while that window is
open. A connection failure means the app is closed, not that the vault is empty — say so rather
than proceeding as though there were no context.
