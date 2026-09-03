package dev.mindgraph.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Serves [McpDispatcher] over HTTP so the running desktop app *is* the MCP server.
 *
 * stdio is not an option here: with stdio the client spawns the server and owns its pipes, and
 * an app the user already launched has no pipes to hand over. So MCP reaches us over a socket —
 * bound to loopback only, and never to a public interface.
 *
 * This is the JDK's own HTTP server rather than a servlet container, because the entire surface
 * is one POST endpoint speaking JSON-RPC.
 */
class McpHttpServer(
    private val dispatcher: McpDispatcher,
    private val port: Int = defaultPort(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var server: HttpServer? = null

    /** The port actually bound, which differs from [port] when [port] is 0 (pick one for me). */
    val boundPort: Int get() = server?.address?.port ?: port

    val endpoint: String get() = "http://127.0.0.1:$boundPort$PATH"

    /** Returns true if the server is now listening. Never throws — a busy port must not stop the app. */
    fun start(): Boolean {
        if (server != null) return true
        return try {
            val http = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
            http.createContext(PATH, ::handle)
            // One thread: it serialises vault writes, and an agent's tool calls are not a
            // throughput problem worth a pool.
            http.executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "mindgraph-mcp").apply { isDaemon = true }
            }
            http.start()
            server = http
            true
        } catch (e: IOException) {
            // Loud on stderr, fatal to nothing. The app is still a perfectly good app without MCP.
            System.err.println("MindGraph: could not start the MCP server on port $port: ${e.message}")
            false
        } catch (e: LinkageError) {
            // A packaged build whose runtime image was jlinked without `jdk.httpserver` cannot
            // load HttpServer at all, and a missing class arrives as an Error rather than an
            // Exception - so the IOException arm above let it past and it reached the user as a
            // fatal dialog reading `com.sun.net.httpserver.HttpServer`.
            //
            // The rule this class already stated is the right one: no MCP is a degraded app, not
            // a broken one. It has to hold for a runtime missing the module as well as for a
            // port already in use.
            System.err.println(
                "MindGraph: the MCP server is unavailable in this build - the Java runtime is " +
                    "missing ${e.message}. The app works; agents cannot reach it.",
            )
            false
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handle(exchange: HttpExchange) {
        try {
            // Anything with a browser Origin is a page on the user's machine probing our port,
            // not an MCP client. Loopback binding hides us from the network; this covers the
            // one caller that is already inside it.
            val origin = exchange.requestHeaders.getFirst("Origin")
            if (origin != null && !isLoopbackOrigin(origin)) {
                respond(exchange, 403, """{"error":"forbidden origin"}""")
                return
            }

            if (exchange.requestMethod != "POST") {
                // 405 on GET is how a server declines to offer the optional SSE stream.
                respond(exchange, 405, """{"error":"use POST"}""")
                return
            }

            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val request = runCatching { json.parseToJsonElement(body) as JsonObject }.getOrNull()
            if (request == null) {
                respond(exchange, 400, parseError())
                return
            }

            val response = dispatcher.handle(request)
            if (response == null) {
                // A notification. Accepted, nothing to say.
                respond(exchange, 202, "")
            } else {
                respond(exchange, 200, json.encodeToString(JsonObject.serializer(), response))
            }
        } catch (e: Exception) {
            System.err.println("MindGraph: MCP request failed: ${e.message}")
            runCatching { respond(exchange, 500, """{"error":"internal error"}""") }
        } finally {
            exchange.close()
        }
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        if (bytes.isEmpty()) {
            exchange.sendResponseHeaders(status, -1)
        } else {
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun parseError(): String =
        """{"jsonrpc":"2.0","id":null,"error":{"code":${McpDispatcher.PARSE_ERROR},""" +
            """"message":"Invalid JSON"}}"""

    private fun isLoopbackOrigin(origin: String): Boolean {
        val host = runCatching { URI(origin).host }.getOrNull() ?: return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    companion object {
        const val PATH = "/mcp"
        const val DEFAULT_PORT = 4319

        fun defaultPort(): Int =
            System.getenv("MINDGRAPH_MCP_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    }
}
