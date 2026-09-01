package dev.mindgraph.mcp

import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.Vault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises the socket, not just the protocol — the transport is where a working server breaks. */
class McpHttpServerTest {

    private val client: HttpClient = HttpClient.newHttpClient()
    private var server: McpHttpServer? = null
    private lateinit var store: NodeStore

    @AfterTest
    fun tearDown() {
        server?.stop()
    }

    /** Port 0 so the test never collides with a real MindGraph running on the machine. */
    private fun startServer(): McpHttpServer {
        store = NodeStore(Vault(Files.createTempDirectory("mindgraph-mcp-http")))
        val started = McpHttpServer(McpDispatcher(mindGraphTools(StoreVault(store))), port = 0)
        assertTrue(started.start(), "server failed to start")
        server = started
        return started
    }

    private fun post(endpoint: String, body: String, origin: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        origin?.let { builder.header("Origin", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun createsATaskOverRealHttp() {
        val server = startServer()

        val response = post(
            server.endpoint,
            """
            {"jsonrpc":"2.0","id":7,"method":"tools/call",
             "params":{"name":"create_task","arguments":{"title":"Ship the MCP server"}}}
            """.trimIndent(),
        )

        assertEquals(200, response.statusCode())
        val result = Json.parseToJsonElement(response.body()).jsonObject["result"]!!.jsonObject
        assertEquals(false, result["isError"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("Ship the MCP server", runBlocking { store.load() }.single().title)
    }

    @Test
    fun aNotificationIsAcceptedWithNoBody() {
        val server = startServer()

        val response = post(
            server.endpoint,
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
        )

        assertEquals(202, response.statusCode())
        assertTrue(response.body().isEmpty())
    }

    @Test
    fun malformedJsonGetsAParseError() {
        val server = startServer()

        val response = post(server.endpoint, "not json at all")

        assertEquals(400, response.statusCode())
        val error = Json.parseToJsonElement(response.body()).jsonObject["error"]!!.jsonObject
        assertEquals(McpDispatcher.PARSE_ERROR, error["code"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun aBrowserPageOnAnotherOriginIsRefused() {
        val server = startServer()

        val response = post(
            server.endpoint,
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            origin = "https://evil.example.com",
        )

        assertEquals(403, response.statusCode())
    }

    @Test
    fun aLocalhostOriginIsAllowed() {
        val server = startServer()

        val response = post(
            server.endpoint,
            """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            origin = "http://localhost:5173",
        )

        assertEquals(200, response.statusCode())
        val tools = Json.parseToJsonElement(response.body())
            .jsonObject["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(
            listOf("list_ready_tasks", "create_task", "link_nodes", "update_status"),
            tools.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun getIsDeclinedBecauseWeDoNotOfferAnSseStream() {
        val server = startServer()

        val response = client.send(
            HttpRequest.newBuilder(URI(server.endpoint)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertEquals(405, response.statusCode())
    }
}
