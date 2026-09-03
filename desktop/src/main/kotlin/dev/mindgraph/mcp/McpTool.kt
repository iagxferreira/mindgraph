package dev.mindgraph.mcp

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.NodeKind
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.LinkOutcome
import dev.mindgraph.state.LinkSuggestions
import dev.mindgraph.state.NodeSearch
import dev.mindgraph.state.Retrieval
import dev.mindgraph.state.TaskGraph
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One tool an agent can call. [schema] is the JSON Schema the model reads to learn how to call
 * it, and [execute] returns the text the model sees back — so the return value is a sentence
 * written for a reader, not a status code.
 */
class McpTool(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val execute: (JsonObject) -> String,
)

/**
 * Everything the tools need from the running app, as one interface — so the tool layer never
 * has to know whether it is talking to a live view model or a bare store, and stays testable
 * without standing up Compose.
 */
interface VaultAccess {
    suspend fun createTask(title: String, body: String, due: String?, assignee: String?): Node

    /**
     * A document with no task facet. [kind] is what the document *is*; task-ness is the other
     * axis entirely, and a node created here has neither a status nor a place in the ready queue.
     */
    suspend fun createNote(title: String, body: String, kind: NodeKind, assignee: String?): Node

    /**
     * Adds to the end of a node's body. Strictly additive: every other field, and every byte
     * already in the body, is left exactly as it was. Null when the node is gone.
     */
    suspend fun appendToBody(nodeId: NodeId, content: String): Node?
    suspend fun nodes(): List<Node>

    /** Total tracked seconds on a node, both yours and every agent's. */
    suspend fun trackedSeconds(nodeId: NodeId): Long
    suspend fun link(sourceId: NodeId, targetId: NodeId, kind: EdgeKind): LinkOutcome
    suspend fun setStatus(
        nodeId: NodeId,
        status: TaskStatus,
        due: String?,
        agent: String?,
        assignee: String?,
    ): Node?
}

/** The tools MindGraph exposes to agents. */
fun mindGraphTools(vault: VaultAccess): List<McpTool> = listOf(
    listReadyTasksTool(vault),
    searchNotesTool(vault),
    relatedNotesTool(vault),
    suggestLinksTool(vault),
    getNodeTool(vault),
    createTaskTool(vault),
    createNoteTool(vault),
    appendNodeBodyTool(vault),
    linkNodesTool(vault),
    updateStatusTool(vault),
)

private fun suggestLinksTool(vault: VaultAccess) = McpTool(
    name = "suggest_links",
    description =
        "Find edges the vault has evidence for and does not have: notes that name each other " +
            "in prose without linking, and `[[links]]` written to a name that never resolved. " +
            "Give it a node to ask what that one should connect to, or omit it to sweep the " +
            "whole vault. It proposes only - create the edge with link_nodes if the suggestion " +
            "is right, and ignore it if it is not.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("node") {
                put("type", "string")
                put(
                    "description",
                    "The node to find connections for: its id, or its exact title. Omit to " +
                        "sweep the whole vault, which is a much longer list.",
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many to return. Default ${LinkSuggestions.DEFAULT_LIMIT}.")
            }
        }
        putJsonArray("required") { }
        put("additionalProperties", false)
    },
) { arguments ->
    val reference = arguments["node"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val limit = arguments.optionalInt("limit") ?: LinkSuggestions.DEFAULT_LIMIT

    runBlocking {
        val nodes = vault.nodes()
        val suggestions = if (reference == null) {
            LinkSuggestions.across(nodes, limit)
        } else {
            LinkSuggestions.forNode(nodes, resolve(nodes, reference), limit)
        }

        if (suggestions.isEmpty()) {
            val scope = reference?.let { "\"$it\"" } ?: "the vault"
            "No unlinked connections found for $scope. Either everything with evidence behind " +
                "it is already linked, or the notes do not mention each other by name."
        } else {
            buildString {
                append("${suggestions.size} suggested link(s)")
                reference?.let { append(" for \"$it\"") }
                append(":\n")
                suggestions.forEach { suggestion ->
                    val why = when (suggestion.reason) {
                        // Said plainly, because a suggestion whose reason cannot be read is one
                        // the caller has to take on trust.
                        LinkSuggestions.Reason.DanglingLink ->
                            "links to \"${suggestion.evidence}\", which resolves to nothing"

                        LinkSuggestions.Reason.UnlinkedMention ->
                            "says \"${suggestion.evidence}\" without linking it"
                    }
                    append("- \"${suggestion.from.title}\" $why\n")
                    append("    -> \"${suggestion.to.title}\" (${suggestion.to.id.value})\n")
                }
                append("\nNothing was linked. Use link_nodes for the ones that are right.")
            }
        }
    }
}

private fun getNodeTool(vault: VaultAccess) = McpTool(
    name = "get_node",
    description =
        "Read one node from the user's MindGraph vault in full — its body, kind, status, " +
            "deadline, assignee, and what it depends on. list_ready_tasks gives you a line; " +
            "some nodes are a page, and the reason for a piece of work is usually in the body.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("node") {
                put("type", "string")
                put("description", "The node: its id, or its exact title.")
            }
        }
        putJsonArray("required") { add("node") }
        put("additionalProperties", false)
    },
) { arguments ->
    val reference = arguments.requiredString("node")

    runBlocking {
        val nodes = vault.nodes()
        val node = resolve(nodes, reference)
        val graph = TaskGraph(nodes)
        val byId = nodes.associateBy { it.id }

        buildString {
            append("# ${node.title}\n")
            append("id: ${node.id.value}\n")
            append("kind: ${node.kind.slug}\n")
            node.task?.let { facet ->
                append("status: ${facet.status.name.lowercase()}")
                // Readiness is derived, so it is reported rather than stored — and it is the
                // thing a caller most often wants to know next.
                if (facet.status.isOpen && !node.archived) {
                    append(if (graph.isBlocked(node.id)) " (blocked)" else " (ready)")
                }
                append("\n")
                facet.due?.let { append("due: $it\n") }
            }
            node.assignee?.let { append("assignee: $it\n") }
            // The names its siblings link it by. An imported note's title is a whole sentence,
            // so the alias is often the only name that appears in anyone else's prose.
            if (node.aliases.isNotEmpty()) append("also known as: ${node.aliases.joinToString(", ")}\n")
            if (node.archived) append("archived: yes\n")

            val blockers = graph.blockers(node.id)
            if (blockers.isNotEmpty()) {
                append("waiting on: ")
                append(blockers.joinToString(", ") { "\"${it.title}\" (${it.id.value})" })
                append("\n")
            }
            val dependencies = node.dependsOn.mapNotNull { byId[it] }.filter { it !in blockers }
            if (dependencies.isNotEmpty()) {
                append("depends on (finished): ")
                append(dependencies.joinToString(", ") { "\"${it.title}\"" })
                append("\n")
            }
            val dependents = graph.dependents(node.id)
            if (dependents.isNotEmpty()) {
                append("blocking: ")
                append(dependents.joinToString(", ") { "\"${it.title}\" (${it.id.value})" })
                append("\n")
            }
            val related = node.relatesTo.mapNotNull { byId[it] }
            if (related.isNotEmpty()) {
                append("related: ")
                append(related.joinToString(", ") { "\"${it.title}\"" })
                append("\n")
            }

            // Both directions, because they answer different questions. Outgoing says what this
            // note serves; incoming is the bundle - what an agent starting on this node should
            // read first - and that is the one worth asking for.
            val servesAsContext = node.contextFor.mapNotNull { byId[it] }
            if (servesAsContext.isNotEmpty()) {
                append("is context for: ")
                append(servesAsContext.joinToString(", ") { "\"${it.title}\"" })
                append("\n")
            }
            val bundle = nodes.filter { node.id in it.contextFor }
            if (bundle.isNotEmpty()) {
                append("context to load (${bundle.size}): ")
                append(bundle.joinToString(", ") { "\"${it.title}\" (${it.id.value})" })
                append("\n")
            }

            val body = node.body.trim()
            append("\n")
            append(if (body.isEmpty()) "(no body)" else body)
        }
    }
}

private fun relatedNotesTool(vault: VaultAccess) = McpTool(
    name = "related_notes",
    description =
        "Assemble the context for a piece of work as one document. Give it a topic or a node; " +
            "it finds the starting point, walks the graph outwards, and returns the " +
            "neighbourhood as markdown you can read straight into your context - nearest " +
            "first, cut to a token budget, with what did not fit listed at the end. Use this " +
            "instead of search_notes when you are about to start work and want the background " +
            "rather than a list of hits: it follows edges rather than matching strings, so a " +
            "note that never repeats your words still arrives, and it crosses every project.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("topic") {
                put("type", "string")
                put(
                    "description",
                    "What you are about to work on. A node id or exact title is used directly; " +
                        "anything else is matched against the vault and the best hit is used.",
                )
            }
            putJsonObject("hops") {
                put("type", "integer")
                put(
                    "description",
                    "How far to walk from the starting node. Default ${Retrieval.DEFAULT_HOPS}, " +
                        "maximum ${Retrieval.MAX_HOPS}. Raise it for background, lower it to stay tight.",
                )
            }
            putJsonObject("budget_tokens") {
                put("type", "integer")
                put(
                    "description",
                    "About how much of your context to spend. Approximate - measured in " +
                        "characters at roughly ${Retrieval.CHARACTERS_PER_TOKEN} per token. " +
                        "Default ${Retrieval.DEFAULT_BUDGET_CHARACTERS / Retrieval.CHARACTERS_PER_TOKEN}.",
                )
            }
        }
        putJsonArray("required") { add("topic") }
        put("additionalProperties", false)
    },
) { arguments ->
    val topic = arguments.requiredString("topic")
    val hops = arguments.optionalInt("hops") ?: Retrieval.DEFAULT_HOPS
    val budgetTokens = arguments.optionalInt("budget_tokens")
    val budget = budgetTokens?.let { it * Retrieval.CHARACTERS_PER_TOKEN }
        ?: Retrieval.DEFAULT_BUDGET_CHARACTERS

    runBlocking {
        val nodes = vault.nodes()
        if (nodes.isEmpty()) throw IllegalStateException("The vault is empty; there is no context to assemble.")

        // An id or exact title is what the caller meant. Anything else is a topic, and the
        // search that already exists is a better matcher than a second one written here.
        val seed = runCatching { resolve(nodes, topic) }.getOrElse {
            NodeSearch.search(nodes, topic, limit = 1).firstOrNull()?.node
                ?: throw IllegalArgumentException(
                    "Nothing in the vault matches \"$topic\". Try search_notes with a broader term.",
                )
        }

        Retrieval.markdown(Retrieval.bundle(nodes, seed, hops, budget))
    }
}

private fun searchNotesTool(vault: VaultAccess) = McpTool(
    name = "search_notes",
    description =
        "Find notes, RFCs, and tasks in the MindGraph vault by text in their title, aliases, " +
            "or body. Returns each matching node's id, title, kind, and a small context snippet " +
            "so an agent can choose what to read next.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Text to find in node titles, aliases, and bodies.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many matches to return. Defaults to 10, maximum 100.")
            }
        }
        putJsonArray("required") { add("query") }
        put("additionalProperties", false)
    },
) { arguments ->
    val query = arguments.requiredString("query")
    val limit = arguments["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: DEFAULT_LIMIT

    runBlocking {
        val matches = NodeSearch.search(vault.nodes(), query, limit)
        if (matches.isEmpty()) {
            "No nodes match \"$query\"."
        } else {
            buildString {
                append("${matches.size} node(s) match \"$query\":\n")
                matches.forEach { match ->
                    append("- ${match.node.title} (${match.node.id.value}) — ${match.node.kind.slug}\n")
                    append("  ${match.snippet}\n")
                }
            }.trimEnd()
        }
    }
}

private fun createTaskTool(vault: VaultAccess) = McpTool(
    name = "create_task",
    description =
        "Create a task in the user's MindGraph vault. The task is written to the vault as a " +
            "markdown file and appears on the graph immediately. Returns the new task's id.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short title for the task, e.g. 'Write the MCP server'.")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Optional markdown body with the detail behind the task.")
            }
            putJsonObject("due") {
                put("type", "string")
                put("description", "Optional deadline as a date, e.g. 2026-09-04. Ready work is ordered by it.")
            }
            putJsonObject("assignee") {
                put("type", "string")
                put("description", ASSIGNEE_HINT)
            }
        }
        putJsonArray("required") { add("title") }
        put("additionalProperties", false)
    },
) { arguments ->
    val title = arguments.requiredString("title")
    val body = arguments["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val due = arguments.optionalDueDate()
    val assignee = arguments.optionalName("assignee")

    val node = runBlocking { vault.createTask(title, body, due, assignee) }
    buildString {
        append("Created task \"${node.title}\" with id ${node.id.value}.")
        due?.let { append(" Due $it.") }
        assignee?.let { append(" Assigned to $it.") }
    }
}

private fun createNoteTool(vault: VaultAccess) = McpTool(
    name = "create_note",
    description =
        "Record something in the user's MindGraph vault that is not work to be done — a " +
            "finding, a decision, a piece of reference. Use this rather than create_task when " +
            "nobody has to do anything about it: a note carries no status, so it never appears " +
            "in list_ready_tasks as work that will never be picked up. Link it with link_nodes " +
            "to whatever it is about; an unlinked note is a dot. Returns the new note's id.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short title, e.g. 'Gradle overwrites classes under the running app'.")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Markdown body. This is the note; say why it matters, not just what.")
            }
            putJsonObject("kind") {
                put("type", "string")
                putJsonArray("enum") { NodeKind.entries.forEach { add(it.slug) } }
                put("description",
                    "What the document is: 'note' for an observation, 'rfc' for a design with " +
                        "a decision and its rationale, 'reference' for material you will look " +
                        "up again. Defaults to note.")
            }
            putJsonObject("assignee") {
                put("type", "string")
                put("description", ASSIGNEE_HINT)
            }
        }
        putJsonArray("required") { add("title") }
        put("additionalProperties", false)
    },
) { arguments ->
    val title = arguments.requiredString("title")
    val body = arguments["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val assignee = arguments.optionalName("assignee")
    val kind = arguments["kind"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { raw ->
            NodeKind.parse(raw) ?: throw IllegalArgumentException(
                "kind must be one of ${NodeKind.entries.joinToString(", ") { it.slug }}, not \"$raw\".",
            )
        }
        ?: NodeKind.Note

    val node = runBlocking { vault.createNote(title, body, kind, assignee) }
    buildString {
        append("Created ${kind.slug} \"${node.title}\" with id ${node.id.value}.")
        assignee?.let { append(" Assigned to $it.") }
        append(" It is not a task, so it will not appear in ready work.")
    }
}

private fun appendNodeBodyTool(vault: VaultAccess) = McpTool(
    name = "append_node_body",
    description =
        "Add to the end of a node's body in the user's MindGraph vault — what you tried, what " +
            "you found, what the next session needs to know. This only ever adds: it cannot " +
            "change the title, kind, status, deadline or assignee, and it cannot alter or " +
            "remove a single word already written there. Use it to keep a running record on " +
            "the work itself; when the context has genuinely changed, create_note and link it " +
            "instead.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("node") {
                put("type", "string")
                put("description", "The node to add to: its id, or its exact title.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description",
                    "Markdown to add at the end. It lands as its own entry, separated from " +
                        "what is already there.")
            }
        }
        putJsonArray("required") { add("node"); add("content") }
        put("additionalProperties", false)
    },
) { arguments ->
    val reference = arguments.requiredString("node")
    val content = arguments.requiredString("content")

    runBlocking {
        val node = resolve(vault.nodes(), reference)
        val saved = vault.appendToBody(node.id, content)
            ?: throw IllegalStateException("Refused: \"${node.title}\" no longer exists.")

        "Added ${content.trim().length} characters to the end of \"${saved.title}\". " +
            "Nothing else about it changed."
    }
}

private fun linkNodesTool(vault: VaultAccess) = McpTool(
    name = "link_nodes",
    description =
        "Link two nodes in the user's MindGraph vault. Use kind 'depends_on' when the first " +
            "node cannot start until the second is finished — that is what makes the graph " +
            "compute which work is blocked and which is ready. Use 'relates_to' for an " +
            "association that implies no ordering. Nodes may be named by id or exact title. " +
            "A dependency that would close a cycle is refused.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("from") {
                put("type", "string")
                put("description", "The dependent node: its id, or its exact title.")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "The node depended on: its id, or its exact title.")
            }
            putJsonObject("kind") {
                put("type", "string")
                putJsonArray("enum") { add(DEPENDS_ON); add(RELATES_TO); add(CONTEXT_FOR) }
                put(
                    "description",
                    "'depends_on' orders the work; 'relates_to' does not; 'context_for' marks " +
                        "the source as background to load when working on the target.",
                )
            }
        }
        putJsonArray("required") { add("from"); add("to"); add("kind") }
        put("additionalProperties", false)
    },
) { arguments ->
    val fromRef = arguments.requiredString("from")
    val toRef = arguments.requiredString("to")
    val kind = when (val raw = arguments.requiredString("kind")) {
        DEPENDS_ON -> EdgeKind.DependsOn
        RELATES_TO -> EdgeKind.RelatesTo
        CONTEXT_FOR -> EdgeKind.ContextFor
        else -> throw IllegalArgumentException(
            "kind must be '$DEPENDS_ON', '$RELATES_TO' or '$CONTEXT_FOR', not \"$raw\".",
        )
    }

    runBlocking {
        val nodes = vault.nodes()
        val source = resolve(nodes, fromRef)
        val target = resolve(nodes, toRef)

        when (vault.link(source.id, target.id, kind)) {
            LinkOutcome.Linked -> when (kind) {
                EdgeKind.DependsOn ->
                    "\"${source.title}\" now depends on \"${target.title}\". It stays blocked " +
                        "until that is done."

                EdgeKind.ContextFor ->
                    "\"${source.title}\" is now context for \"${target.title}\", so it is part " +
                        "of what gets loaded when working on that."

                EdgeKind.RelatesTo -> "Linked \"${source.title}\" to \"${target.title}\"."
            }

            // Already true is the state the caller wanted, so it is not a failure.
            LinkOutcome.AlreadyLinked ->
                "\"${source.title}\" was already linked to \"${target.title}\"; nothing changed."

            // The rest are thrown so the result carries isError and the model can tell the
            // edge does not exist, rather than reading a sentence and assuming it worked.
            LinkOutcome.WouldCycle -> throw IllegalStateException(
                "Refused: \"${source.title}\" depending on \"${target.title}\" would close a " +
                    "dependency cycle, which would leave every task in it permanently blocked.",
            )
            LinkOutcome.SelfLink -> throw IllegalStateException(
                "Refused: a node cannot depend on itself.",
            )
            LinkOutcome.UnknownNode -> throw IllegalStateException(
                "Refused: one of those nodes no longer exists.",
            )
        }
    }
}

private fun listReadyTasksTool(vault: VaultAccess) = McpTool(
    name = "list_ready_tasks",
    description =
        "List the tasks in the user's MindGraph vault that can actually be started now — open " +
            "tasks whose dependencies are all finished. Readiness is computed from the graph, " +
            "not declared, so this is the right question to ask before picking up work. " +
            "Results are ranked by how much finishing each one unblocks.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "How many to return. Defaults to 10.")
            }
            putJsonObject("assignee") {
                put("type", "string")
                put("description",
                    "Only tasks assigned to this name. Pass your own to ask what is yours; " +
                        "omit to see everything that is ready, assigned or not.")
            }
        }
        putJsonArray("required") {}
        put("additionalProperties", false)
    },
) { arguments ->
    val limit = arguments["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 100)
        ?: DEFAULT_LIMIT
    val assignee = arguments.optionalName("assignee")

    runBlocking {
        val nodes = vault.nodes()
        val graph = TaskGraph(nodes)
        val today = LocalDate.now()
        // Assignment filters, it never gates: work owned by someone else is still ready,
        // it is just not yours.
        val ready = graph.rankedReadyTasks(today)
            .filter { assignee == null || it.assignee.equals(assignee, ignoreCase = true) }
        val blocked = nodes.count { it.isLiveWork && graph.isBlocked(it.id) }

        if (ready.isEmpty()) {
            if (assignee != null) {
                "Nothing ready is assigned to $assignee."
            } else if (blocked == 0) {
                "No open tasks."
            } else {
                "Nothing is ready: all $blocked open tasks are blocked by unfinished dependencies."
            }
        } else {
            buildString {
                append("${ready.size} task(s) ready")
                assignee?.let { append(" for $it") }
                if (blocked > 0) append(", $blocked blocked")
                append(":\n")
                ready.take(limit).forEach { node ->
                    append("- ${node.title} (${node.id.value})")
                    if (assignee == null) node.assignee?.let { append(" — @$it") }
                    node.task?.dueDate?.let { due ->
                        append(if (due.isBefore(today)) " — OVERDUE, was due $due" else " — due $due")
                    }
                    val unblocks = graph.unblockedCount(node.id)
                    if (unblocks > 0) append(" — finishing it unblocks $unblocks")
                    append("\n")
                }
            }.trimEnd()
        }
    }
}

private fun updateStatusTool(vault: VaultAccess) = McpTool(
    name = "update_status",
    description =
        "Set the status of a task in the user's MindGraph vault: todo, doing, done, or " +
            "dropped. Use this to close work out when it is finished. Reports which tasks the " +
            "change unblocked, since finishing one task can make several others startable. " +
            "A plain note given a status becomes a task.",
    schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("node") {
                put("type", "string")
                put("description", "The task: its id, or its exact title.")
            }
            putJsonObject("status") {
                put("type", "string")
                putJsonArray("enum") { STATUSES.forEach { add(it) } }
                put("description", "'done' and 'dropped' both close the task and unblock its dependents.")
            }
            putJsonObject("due") {
                put("type", "string")
                put("description", "Optional deadline as a date, e.g. 2026-09-04. Omit to leave it as it is.")
            }
            putJsonObject("agent") {
                put("type", "string")
                put("description",
                    "Identify yourself, e.g. 'claude-code'. Time spent while a task is 'doing' " +
                        "is logged against this name, so the vault can tell your work from the " +
                        "user's.")
            }
            putJsonObject("assignee") {
                put("type", "string")
                put("description", ASSIGNEE_HINT + " Omit to leave it as it is.")
            }
        }
        putJsonArray("required") { add("node"); add("status") }
        put("additionalProperties", false)
    },
) { arguments ->
    val reference = arguments.requiredString("node")
    val raw = arguments.requiredString("status")
    val due = arguments.optionalDueDate()
    val agent = arguments.optionalName("agent")
    val assignee = arguments.optionalName("assignee")
    val status = TaskStatus.parse(raw)
        ?: throw IllegalArgumentException(
            "status must be one of ${STATUSES.joinToString(", ")}, not \"$raw\".",
        )

    runBlocking {
        val before = vault.nodes()
        val node = resolve(before, reference)
        val readyBefore = TaskGraph(before).readyTasks().map { it.id }.toSet()

        val saved = vault.setStatus(node.id, status, due, agent, assignee)
            ?: throw IllegalStateException("Refused: \"${node.title}\" no longer exists.")

        // What the change freed up. This is the whole reason the edges are worth storing, so
        // the agent that closed the task is told directly rather than having to ask again.
        val after = vault.nodes()
        val freed = TaskGraph(after).readyTasks()
            .filter { it.id !in readyBefore && it.id != saved.id }

        buildString {
            append("\"${saved.title}\" is now ${status.name.lowercase()}.")
            due?.let { append(" Due $it.") }
            assignee?.let { append(" Assigned to $it.") }
            when (status) {
                TaskStatus.Doing -> append(" The clock is running against ${agent ?: "you"}.")
                else -> vault.trackedSeconds(saved.id).takeIf { it > 0 }?.let {
                    append(" ${formatDuration(it)} tracked on it.")
                }
            }
            if (freed.isNotEmpty()) {
                append(" That unblocked ${freed.size} task(s): ")
                append(freed.joinToString(", ") { "\"${it.title}\" (${it.id.value})" })
                append(".")
            }
        }
    }
}

private const val DEPENDS_ON = "depends_on"
private const val RELATES_TO = "relates_to"
private const val CONTEXT_FOR = "context_for"
private const val DEFAULT_LIMIT = 10
private const val MAX_NAME = 64
private const val ASSIGNEE_HINT =
    "Who should pick this up - a person or an agent name. This is who it belongs to, not who " +
        "is working it right now."

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
private val STATUSES = TaskStatus.entries.map { it.name.lowercase() }

/**
 * A deadline, validated here rather than stored as typed. A date the ranking cannot read would
 * silently do nothing at all, and a model that guessed the format wrong should be told so at
 * the point it guessed — not leave a task that quietly never becomes urgent.
 */
/** A person's or an agent's name, as they choose to write it. Trimmed, capped, never empty. */
private fun JsonObject.optionalName(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME)

private fun JsonObject.optionalDueDate(): String? {
    val raw = this["due"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val parsed = runCatching { LocalDate.parse(raw) }.getOrNull()
        ?: throw IllegalArgumentException(
            "due must be a date like 2026-09-04, not \"$raw\".",
        )
    return parsed.toString()
}

/** An optional whole number, ignoring anything that is not one rather than failing the call. */
private fun JsonObject.optionalInt(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()

private fun JsonObject.requiredString(key: String): String {
    val value = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    require(value.isNotBlank()) { "$key is required and must not be blank" }
    return value
}

/**
 * Nodes are addressable by id or by exact title. Titles are the half a model can actually
 * produce from a conversation, and ids are the half that stays correct after a rename — so
 * both are accepted, and an ambiguous title reports the ids to disambiguate with.
 */
private fun resolve(nodes: List<Node>, reference: String): Node {
    nodes.find { it.id.value.equals(reference, ignoreCase = true) }?.let { return it }

    val matches = nodes.filter { it.title.equals(reference, ignoreCase = true) }
    return when (matches.size) {
        1 -> matches.single()
        0 -> throw IllegalArgumentException(
            "No node matches \"$reference\". Pass a node id, or a title exactly as written.",
        )
        else -> throw IllegalArgumentException(
            "\"$reference\" matches ${matches.size} nodes. Use an id instead: " +
                matches.joinToString(", ") { it.id.value },
        )
    }
}
