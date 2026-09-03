package dev.mindgraph.mcp

import dev.mindgraph.storage.NodeStore
import dev.mindgraph.storage.SessionLog
import dev.mindgraph.storage.Vault
import java.nio.file.Files
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The working agreement, served rather than installed. */
class McpDocumentsTest {

    private fun dispatcher(): McpDispatcher {
        val vault = Vault(Files.createTempDirectory("mindgraph-docs"))
        return McpDispatcher(mindGraphTools(StoreVault(NodeStore(vault), SessionLog(vault))))
    }

    private fun call(method: String, params: JsonObject? = null): JsonObject =
        dispatcher().handle(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", method)
                params?.let { put("params", it) }
            },
        )!!

    @Test
    fun theServerAdvertisesResourcesAndPrompts() {
        // A client that is not told about a capability will never ask for it, so the whole
        // feature is invisible without this.
        val caps = call("initialize")["result"]!!.jsonObject["capabilities"]!!.jsonObject
        assertTrue("resources" in caps, caps.toString())
        assertTrue("prompts" in caps, caps.toString())
        assertTrue("tools" in caps, caps.toString())
    }

    @Test
    fun theWorkingAgreementIsListedAsAResource() {
        val resources = call("resources/list")["result"]!!.jsonObject["resources"]!!.jsonArray
        val entry = resources.single().jsonObject
        assertEquals(McpDocuments.WORKING_AGREEMENT_URI, entry["uri"]!!.jsonPrimitive.content)
        assertEquals("text/markdown", entry["mimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun readingTheResourceReturnsTheAgreement() {
        val params = buildJsonObject { put("uri", McpDocuments.WORKING_AGREEMENT_URI) }
        val contents = call("resources/read", params)["result"]!!
            .jsonObject["contents"]!!.jsonArray.single().jsonObject
        val text = contents["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("Working through MindGraph"), text.take(120))
        assertTrue(text.contains("append-only"), "the rule agents most need must be in it")
    }

    @Test
    fun anUnknownResourceIsAnErrorRatherThanEmptyContent() {
        val params = buildJsonObject { put("uri", "mindgraph://nope") }
        val response = call("resources/read", params)
        assertTrue("error" in response, response.toString())
        assertEquals(
            McpDispatcher.RESOURCE_NOT_FOUND,
            response["error"]!!.jsonObject["code"]!!.jsonPrimitive.int,
        )
    }

    @Test
    fun theSameTextIsAvailableAsAPrompt() {
        val prompts = call("prompts/list")["result"]!!.jsonObject["prompts"]!!.jsonArray
        assertEquals(McpDocuments.WORKING_AGREEMENT_NAME, prompts.single().jsonObject["name"]!!.jsonPrimitive.content)

        val params = buildJsonObject { put("name", McpDocuments.WORKING_AGREEMENT_NAME) }
        val message = call("prompts/get", params)["result"]!!
            .jsonObject["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals(
            McpDocuments.workingAgreement,
            message["content"]!!.jsonObject["text"]!!.jsonPrimitive.content,
            "the prompt and the resource must not drift apart",
        )
    }

    @Test
    fun anUnknownPromptIsRefused() {
        val params = buildJsonObject { put("name", "something-else") }
        assertTrue("error" in call("prompts/get", params))
    }

    @Test
    fun theServedAgreementIsNotTheRepositorySkill() {
        // The repo skill is about building MindGraph. An agent in an unrelated repository being
        // told to run ./gradlew is worse than being told nothing at all.
        val text = McpDocuments.workingAgreement
        assertFalse(text.contains("gradlew"), "the served agreement must not carry build commands")
        assertFalse(text.contains("worktree"), "nor this repository's commit conventions")
        assertTrue(text.contains("related_notes"), "it must tell an agent to load context first")
    }
}
