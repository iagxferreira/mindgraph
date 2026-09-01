package dev.mindgraph.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The MCP protocol, minus the transport. Takes a decoded JSON-RPC request and returns the
 * response to send back — or null for a notification, which by definition gets no reply.
 *
 * Keeping this free of sockets is what makes the protocol testable: every case below is
 * reachable from a unit test with a plain [JsonObject].
 */
class McpDispatcher(
    private val tools: List<McpTool>,
    private val serverVersion: String = "1.0.0",
) {

    fun handle(request: JsonObject): JsonObject? {
        val id = request["id"]?.takeUnless { it is JsonNull }
        val method = request["method"]?.jsonPrimitive?.contentOrNull

        // No id means a notification (`notifications/initialized` and friends). The spec says
        // to process it and stay silent, so returning null here is the whole handling.
        if (id == null) return null
        if (method == null) return failure(id, INVALID_REQUEST, "Request is missing a method")

        return when (method) {
            "initialize" -> success(id, initialize(request))
            "tools/list" -> success(id, buildJsonObject { put("tools", toolDescriptors()) })
            "tools/call" -> callTool(id, request)
            "ping" -> success(id, JsonObject(emptyMap()))
            else -> failure(id, METHOD_NOT_FOUND, "Unknown method: $method")
        }
    }

    private fun initialize(request: JsonObject): JsonObject {
        // Echo the client's protocol version when it names one. We speak a subset every current
        // revision shares, so agreeing with the client is more useful than insisting on ours.
        val requested = request["params"]?.jsonObject
            ?.get("protocolVersion")?.jsonPrimitive?.contentOrNull

        return buildJsonObject {
            put("protocolVersion", requested ?: DEFAULT_PROTOCOL_VERSION)
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", false) }
            }
            putJsonObject("serverInfo") {
                put("name", "mindgraph")
                put("version", serverVersion)
            }
        }
    }

    private fun toolDescriptors(): JsonElement = buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("inputSchema", tool.schema)
                },
            )
        }
    }

    private fun callTool(id: JsonElement, request: JsonObject): JsonObject {
        val params = request["params"]?.jsonObject
            ?: return failure(id, INVALID_PARAMS, "tools/call requires params")
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: return failure(id, INVALID_PARAMS, "tools/call requires a tool name")

        // An unknown tool is a protocol mistake, so it is a JSON-RPC error. A tool that runs and
        // fails is a *result* with isError set — that difference is what lets the model read the
        // failure and try something else instead of the request simply blowing up.
        val tool = tools.find { it.name == name }
            ?: return failure(id, INVALID_PARAMS, "Unknown tool: $name")

        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())

        return runCatching { tool.execute(arguments) }.fold(
            onSuccess = { success(id, toolResult(it, isError = false)) },
            onFailure = { success(id, toolResult(describe(it), isError = true)) },
        )
    }

    /**
     * What the model reads when a tool fails. The two exceptions a tool raises deliberately
     * carry a sentence written for the reader, so they are passed through untouched; anything
     * else is a genuine fault whose message alone can be meaningless — a NoClassDefFoundError's
     * message is just a class name — so it is named by type.
     */
    private fun describe(failure: Throwable): String = when (failure) {
        is IllegalArgumentException, is IllegalStateException ->
            failure.message ?: failure.toString()
        else -> "${failure::class.simpleName ?: "Error"}: ${failure.message ?: "no detail"}"
    }

    private fun toolResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
        put("isError", isError)
    }

    private fun success(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun failure(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    companion object {
        const val DEFAULT_PROTOCOL_VERSION = "2025-06-18"

        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
    }
}
