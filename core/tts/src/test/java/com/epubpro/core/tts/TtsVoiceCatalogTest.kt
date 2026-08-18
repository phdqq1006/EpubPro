package com.epubpro.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceCatalogTest {

    @Test
    fun `voice ids are unique and model files are mapped`() {
        val voices = TtsVoiceCatalog.aiVoices

        assertEquals(voices.size, voices.map { it.id }.distinct().size)
        assertTrue(voices.all { it.onnxFileName.endsWith(".onnx") })
        assertEquals("ngocngan3701.onnx", TtsVoiceCatalog.find("ngoc_ngan")?.onnxFileName)
    }

    @Test
    fun `AI catalog only exposes supported language`() {
        assertEquals(7, TtsVoiceCatalog.forLanguage("vi").size)
        assertTrue(TtsVoiceCatalog.forLanguage("en").isEmpty())
    }

    @Test
    fun `missing or blank selection has no fallback`() {
        assertNull(TtsVoiceCatalog.find(null))
        assertNull(TtsVoiceCatalog.find(""))
        assertNull(TtsVoiceCatalog.find("unknown"))
    }
}
