package dev.pocketagent.core.providers

import dev.pocketagent.core.providers.gemini.GeminiProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiProviderTest {

    @Test
    fun resolveGeminiModel_mapsLegacyOrInvalidModelsSafely() {
        assertEquals("gemini-1.5-flash", GeminiProvider.resolveGeminiModel("gemini-3.7-flash"))
        assertEquals("gemini-1.5-flash", GeminiProvider.resolveGeminiModel("gemini-3.8-flash"))
        assertEquals("gemini-1.5-flash", GeminiProvider.resolveGeminiModel("gemini-2.5-flash"))
        assertEquals("gemini-1.5-flash", GeminiProvider.resolveGeminiModel(""))
        assertEquals("gemini-1.5-flash", GeminiProvider.resolveGeminiModel("gemini-1.5-flash"))
        assertEquals("gemini-1.5-pro", GeminiProvider.resolveGeminiModel("gemini-1.5-pro"))
        assertEquals("gemini-2.0-flash", GeminiProvider.resolveGeminiModel("gemini-2.0-flash"))
        assertEquals("gemini-2.0-flash", GeminiProvider.resolveGeminiModel("gemini-2.0-flash-exp"))
    }
}
