package dev.mindgraph.mcp

import dev.mindgraph.model.EdgeKind
import dev.mindgraph.model.Node
import dev.mindgraph.model.NodeId
import dev.mindgraph.model.TaskStatus
import dev.mindgraph.state.LinkOutcome
import dev.mindgraph.state.TaskGraph
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
    suspend fun createTask(title: String, body: String): Node
    suspend fun nodes(): List<Node>
    suspend fun link(sourceId: NodeId, targetId: NodeId, kind: EdgeKind): LinkOutcome
    suspend fun setStatus(nodeId: NodeId, status: TaskStatus): Node?
}

/** The tools MindGraph exposes to agents. */
fun mindGraphTools(vault: VaultAccess): List<McpTool> = listOf(
    listReadyTasksTool(vault),
    createTaskTool(vault),
    linkNodesTool(vault),
    updateStatusTool(vault),
)

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
        }
        putJsonArray("required") { add("title") }
        put("additionalProperties", false)
    },
) { arguments ->
    val title = arguments.requiredString("title")
    val body = arguments["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

    val node = runBlocking { vault.createTask(title, body) }
    "Created task \"${node.title}\" with id ${node.id.value}."
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
                putJsonArray("enum") { add(DEPENDS_ON); add(RELATES_TO) }
                put("description", "'depends_on' orders the work; 'relates_to' does not.")
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
        else -> throw IllegalArgumentException(
            "kind must be '$DEPENDS_ON' or '$RELATES_TO', not \"$raw\".",
        )
    }

    runBlocking {
        val nodes = vault.nodes()
        val source = resolve(nodes, fromRef)
        val target = resolve(nodes, toRef)

        when (vault.link(source.id, target.id, kind)) {
            LinkOutcome.Linked ->
                if (kind == EdgeKind.DependsOn) {
                    "\"${source.title}\" now depends on \"${target.title}\". It stays blocked " +
                        "until that is done."
                } else {
                    "Linked \"${source.title}\" to \"${target.title}\"."
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
        }
        putJsonArray("required") {}
        put("additionalProperties", false)
    },
) { arguments ->
    val limit = arguments["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 100)
        ?: DEFAULT_LIMIT

    runBlocking {
        val nodes = vault.nodes()
        val graph = TaskGraph(nodes)
        val ready = graph.readyTasks()
            .sortedWith(compareByDescending<Node> { graph.unblockedCount(it.id) }.thenBy { it.title })
        val blocked = nodes.count { it.task?.status?.isOpen == true && graph.isBlocked(it.id) }

        if (ready.isEmpty()) {
            if (blocked == 0) {
                "No open tasks."
            } else {
                "Nothing is ready: all $blocked open tasks are blocked by unfinished dependencies."
            }
        } else {
            buildString {
                append("${ready.size} task(s) ready")
                if (blocked > 0) append(", $blocked blocked")
                append(":\n")
                ready.take(limit).forEach { node ->
                    val unblocks = graph.unblockedCount(node.id)
                    append("- ${node.title} (${node.id.value})")
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
        }
        putJsonArray("required") { add("node"); add("status") }
        put("additionalProperties", false)
    },
) { arguments ->
    val reference = arguments.requiredString("node")
    val raw = arguments.requiredString("status")
    val status = TaskStatus.parse(raw)
        ?: throw IllegalArgumentException(
            "status must be one of ${STATUSES.joinToString(", ")}, not \"$raw\".",
        )

    runBlocking {
        val before = vault.nodes()
        val node = resolve(before, reference)
        val readyBefore = TaskGraph(before).readyTasks().map { it.id }.toSet()

        val saved = vault.setStatus(node.id, status)
            ?: throw IllegalStateException("Refused: \"${node.title}\" no longer exists.")

        // What the change freed up. This is the whole reason the edges are worth storing, so
        // the agent that closed the task is told directly rather than having to ask again.
        val after = vault.nodes()
        val freed = TaskGraph(after).readyTasks()
            .filter { it.id !in readyBefore && it.id != saved.id }

        buildString {
            append("\"${saved.title}\" is now ${status.name.lowercase()}.")
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
private const val DEFAULT_LIMIT = 10
private val STATUSES = TaskStatus.entries.map { it.name.lowercase() }

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
