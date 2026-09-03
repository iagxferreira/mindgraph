package dev.mindgraph.mcp

import dev.mindgraph.model.TaskStatus
import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpDispatcherTest {

    private fun newFixture(): Pair<McpDispatcher, NodeStore> {
        val vault = Vault(Files.createTempDirectory("mindgraph-mcp"))
        val store = NodeStore(vault)
        return McpDispatcher(mindGraphTools(StoreVault(store, SessionLog(vault)))) to store
    }

    private fun request(id: Int, method: String, params: JsonObject? = null): JsonObject =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            params?.let { put("params", it) }
        }

    private fun callCreateTask(id: Int, arguments: JsonObject): JsonObject =
        request(
            id,
            "tools/call",
            buildJsonObject {
                put("name", "create_task")
                put("arguments", arguments)
            },
        )

    @Test
    fun initializeAgreesWithTheClientsProtocolVersion() {
        val (dispatcher, _) = newFixture()

        val response = dispatcher.handle(
            request(1, "initialize", buildJsonObject { put("protocolVersion", "2025-03-26") }),
        )

        val result = assertNotNull(response)["result"]!!.jsonObject
        assertEquals("2025-03-26", result["protocolVersion"]!!.jsonPrimitive.content)
        assertNotNull(result["capabilities"]!!.jsonObject["tools"])
        assertEquals("mindgraph", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun initializeFallsBackToOurVersionWhenTheClientNamesNone() {
        val (dispatcher, _) = newFixture()

        val result = dispatcher.handle(request(1, "initialize"))!!["result"]!!.jsonObject

        assertEquals(
            McpDispatcher.DEFAULT_PROTOCOL_VERSION,
            result["protocolVersion"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun toolsListAdvertisesCreateTaskWithASchema() {
        val (dispatcher, _) = newFixture()

        val tools = dispatcher.handle(request(1, "tools/list"))!!["result"]!!.jsonObject["tools"]!!.jsonArray

        assertEquals(
            listOf(
                "list_ready_tasks", "search_notes", "related_notes", "suggest_links", "get_node", "create_task", "create_note",
                "append_node_body", "link_nodes", "update_status",
            ),
            tools.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )

        val tool = tools.first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "create_task"
        }.jsonObject
        val schema = tool["inputSchema"]!!.jsonObject
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertNotNull(schema["properties"]!!.jsonObject["title"])
        assertEquals("title", schema["required"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun callingCreateTaskWritesATaskToTheVault() {
        val (dispatcher, store) = newFixture()

        val response = dispatcher.handle(
            callCreateTask(
                1,
                buildJsonObject {
                    put("title", "Write the MCP server")
                    put("body", "Over HTTP, in-process.")
                },
            ),
        )

        val result = assertNotNull(response)["result"]!!.jsonObject
        assertEquals(false, result["isError"]!!.jsonPrimitive.content.toBoolean())

        val node = runBlocking { store.load() }.single()
        assertEquals("Write the MCP server", node.title)
        assertEquals(TaskStatus.Todo, node.task?.status)
        assertEquals("Over HTTP, in-process.", node.body.trim())

        // The model gets the id back, so it can link or update the task on a later call.
        val text = result["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(node.id.value in text, "expected the new id in: $text")
    }

    @Test
    fun aBlankTitleIsReportedToTheModelRatherThanFailingTheRequest() {
        val (dispatcher, store) = newFixture()

        val result = dispatcher.handle(
            callCreateTask(1, buildJsonObject { put("title", "   ") }),
        )!!["result"]!!.jsonObject

        // A tool that runs and fails is a result the model can read and recover from — not a
        // JSON-RPC error, which it never sees.
        assertEquals(true, result["isError"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(runBlocking { store.load() }.isEmpty())
    }

    @Test
    fun anUnknownToolIsAProtocolError() {
        val (dispatcher, _) = newFixture()

        val response = dispatcher.handle(
            request(
                1,
                "tools/call",
                buildJsonObject {
                    put("name", "delete_everything")
                    putJsonObject("arguments") {}
                },
            ),
        )

        val error = assertNotNull(response)["error"]!!.jsonObject
        assertEquals(McpDispatcher.INVALID_PARAMS, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun anUnknownMethodIsRejected() {
        val (dispatcher, _) = newFixture()

        // Deliberately not a real MCP method name. This assertion used `resources/list`, which
        // then became supported and turned the test into a null dereference rather than a
        // failure that said what had changed.
        val error = dispatcher.handle(request(1, "vault/teleport"))!!["error"]!!.jsonObject

        assertEquals(McpDispatcher.METHOD_NOT_FOUND, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun notificationsGetNoReply() {
        val (dispatcher, _) = newFixture()

        val notification = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }

        assertNull(dispatcher.handle(notification))
    }
}
