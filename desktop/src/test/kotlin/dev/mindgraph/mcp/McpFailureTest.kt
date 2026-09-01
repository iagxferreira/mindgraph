package dev.mindgraph.mcp

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How a failing tool reads to the model on the other end. */
class McpFailureTest {

    private fun dispatcherThatThrows(failure: Throwable): McpDispatcher =
        McpDispatcher(
            listOf(
                McpTool("boom", "Always fails.", buildJsonObject { put("type", "object") }) {
                    throw failure
                },
            ),
        )

    private fun callBoom(failure: Throwable): String {
        val response = dispatcherThatThrows(failure).handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", buildJsonObject { put("name", "boom") })
            },
        )!!["result"]!!.jsonObject

        assertEquals(true, response["isError"]!!.jsonPrimitive.content.toBoolean())
        return response["content"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content
    }

    @Test
    fun aDeliberateRefusalIsPassedThroughAsWritten() {
        val text = callBoom(IllegalStateException("Refused: that would close a dependency cycle."))

        assertEquals("Refused: that would close a dependency cycle.", text)
    }

    @Test
    fun aBadArgumentIsPassedThroughAsWritten() {
        val text = callBoom(IllegalArgumentException("title is required and must not be blank"))

        assertEquals("title is required and must not be blank", text)
    }

    @Test
    fun anUnexpectedFaultIsNamedByTypeBecauseItsMessageMayBeMeaningless() {
        // Exactly what a stale build directory produced: a message that is only a class name.
        val text = callBoom(NoClassDefFoundError("dev/mindgraph/state/AppViewModel\$createNodeNow\$1"))

        assertTrue(text.startsWith("NoClassDefFoundError: "), text)
        assertTrue("AppViewModel" in text, text)
    }

    @Test
    fun aFaultWithNoMessageStillSaysSomething() {
        val text = callBoom(NullPointerException())

        assertEquals("NullPointerException: no detail", text)
    }
}
