package dev.pocketagent.core.providers

import dev.pocketagent.core.providers.gemini.GeminiProvider
import org.junit.Assert.assertEquals
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
}
