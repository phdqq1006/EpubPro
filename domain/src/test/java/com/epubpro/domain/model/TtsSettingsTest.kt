package com.epubpro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsSettingsTest {

    @Test
    fun `default settings use native Vietnamese voice without selected model`() {
        val settings = TtsSettings()

        assertEquals(false, settings.isAiVoice)
        assertEquals("vi", settings.language)
        assertNull(settings.voiceId)
        assertEquals(1.0f, settings.speed)
        assertEquals(1.0f, settings.pitch)
    }

    @Test
    fun `AI settings force Vietnamese and neutral pitch without adding fallback voice`() {
        val normalized = TtsSettings(
            isAiVoice = true,
            language = "en",
            voiceId = null,
            speed = 3.0f,
            pitch = 1.5f
        ).normalizedForPlayback()

        assertEquals("vi", normalized.language)
        assertNull(normalized.voiceId)
        assertEquals(2.0f, normalized.speed)
        assertEquals(1.0f, normalized.pitch)
    }

    @Test
    fun `native settings retain supported language and clamp audio values`() {
        val normalized = TtsSettings(
            language = "en",
            voiceId = "  system-voice  ",
            speed = 0.1f,
            pitch = 2.0f
        ).normalizedForPlayback()

        assertEquals("en", normalized.language)
        assertEquals("system-voice", normalized.voiceId)
        assertEquals(0.5f, normalized.speed)
        assertEquals(1.5f, normalized.pitch)
    }
}
