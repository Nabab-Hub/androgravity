package dev.pocketagent.core.providers

import dev.pocketagent.core.model.ChatRequest
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.core.providers.gemini.GeminiProvider
import io.ktor.client.HttpClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeminiProviderTest {

    @Test
    fun resolveGeminiModel_mapsLegacyOrInvalidModelsSafely() {
        assertEquals("gemini-3.5-flash", GeminiProvider.resolveGeminiModel(""))
        assertEquals("gemini-3.5-flash", GeminiProvider.resolveGeminiModel("gemini-3.5-flash"))
        assertEquals("gemini-3.5-flash-lite", GeminiProvider.resolveGeminiModel("gemini-3.5-flash-lite"))
        assertEquals("gemini-3.8-flash", GeminiProvider.resolveGeminiModel("gemini-3.8-flash"))
        assertEquals("gemini-3.7-flash", GeminiProvider.resolveGeminiModel("gemini-3.7-flash"))
        // Legacy deprecated models map to default:
        assertEquals("gemini-3.5-flash", GeminiProvider.resolveGeminiModel("gemini-1.5-flash"))
        assertEquals("gemini-3.5-flash", GeminiProvider.resolveGeminiModel("gemini-2.0-flash"))
    }

    @Test
    fun parseKeys_handlesMultipleDelimiters() {
        val input = "key1, key2\nkey3; key4\r\nkey5"
        val keys = GeminiProvider.parseKeys(input)
        assertEquals(listOf("key1", "key2", "key3", "key4", "key5"), keys)
    }

    @Test
    fun buildGeminiRequestBody_preservesThoughtSignatureInToolCalls() {
        val provider = GeminiProvider(apiKeyProvider = { "dummy_key" })
        val toolCall = ToolCall(
            id = "call_1",
            name = "fs_list_dir",
            argsJson = "{\"path\":\".\"}",
            thoughtSignature = "sig_12345"
        )
        val assistantMsg = Message.Assistant(
            id = "a1",
            text = "Listing directory",
            toolCalls = listOf(toolCall)
        )
        val request = ChatRequest(
            model = "gemini-3.5-flash",
            messages = listOf(Message.User(id = "u1", text = "List files"), assistantMsg)
        )

        val body = provider.buildGeminiRequestBody(request)
        val contents = body["contents"]?.jsonArray
        assertNotNull(contents)
        assertEquals(2, contents!!.size)

        val modelContent = contents[1].jsonObject
        val parts = modelContent["parts"]?.jsonArray
        assertNotNull(parts)
        assertEquals(2, parts!!.size)

        val toolCallPart = parts[1].jsonObject
        assertEquals("sig_12345", toolCallPart["thoughtSignature"]?.jsonPrimitive?.content)
        val functionCall = toolCallPart["functionCall"]?.jsonObject
        assertNotNull(functionCall)
        assertEquals("fs_list_dir", functionCall!!["name"]?.jsonPrimitive?.content)
    }
}
