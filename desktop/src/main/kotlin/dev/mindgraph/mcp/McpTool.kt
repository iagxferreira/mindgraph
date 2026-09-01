package dev.mindgraph.mcp

import dev.mindgraph.model.Node
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
 * Creating a task, expressed as a single suspending call so the tool layer never has to know
 * whether it is talking to a live view model or a bare store. That keeps the tools testable
 * without standing up Compose.
 */
fun interface TaskCreator {
    suspend fun create(title: String, body: String): Node
}

/** The tools MindGraph exposes to agents. One, for now. */
fun mindGraphTools(creator: TaskCreator): List<McpTool> = listOf(createTaskTool(creator))

private fun createTaskTool(creator: TaskCreator) = McpTool(
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
    val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    require(title.isNotBlank()) { "title is required and must not be blank" }
    val body = arguments["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

    val node = runBlocking { creator.create(title, body) }
    "Created task \"${node.title}\" with id ${node.id.value}."
}
