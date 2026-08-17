package com.epubpro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TtsWidgetStateCodecTest {

    @Test
    fun `encode and decode full 12 fields preserves complete state accurately`() {
        val original = TtsWidgetState(
            bookTitle = "Đại Chúa Tể",
            chapterTitle = "Chương 1: Bắc Linh Cảnh",
            playbackStatus = TtsWidgetPlaybackStatus.PLAYING,
            progress = 0.45f,
            positionMs = 12000L,
            durationMs = 45000L,
            hasSnapshot = true,
            coverPath = "/data/user/0/com.epubpro/covers/book1.jpg",
            paragraphIndex = 14,
            totalParagraphs = 30,
            paragraphText = "Mục Trần đứng trên quảng trường nhìn về phía xa xăm..."
        )

        val encoded = TtsWidgetStateCodec.encode(original)
        val decoded = TtsWidgetStateCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.bookTitle, decoded!!.bookTitle)
        assertEquals(original.chapterTitle, decoded.chapterTitle)
        assertEquals(original.playbackStatus, decoded.playbackStatus)
        assertEquals(original.progress, decoded.progress, 0.001f)
        assertEquals(original.positionMs, decoded.positionMs)
        assertEquals(original.durationMs, decoded.durationMs)
        assertTrue(decoded.hasSnapshot)
        assertEquals(original.coverPath, decoded.coverPath)
        assertEquals(original.paragraphIndex, decoded.paragraphIndex)
        assertEquals(original.totalParagraphs, decoded.totalParagraphs)
        assertEquals(original.paragraphText, decoded.paragraphText)
    }

    @Test
    fun `decode legacy 5 fields payload populates fallback defaults safely`() {
        // Schema: 1|title_base64|status|progress|hasSnapshot
        val titleB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("Sách Cũ".toByteArray(Charsets.UTF_8))
        val legacyPayload = "1|$titleB64|PAUSED|0.5|1"

        val decoded = TtsWidgetStateCodec.decode(legacyPayload)
        assertNotNull(decoded)
        assertEquals("Sách Cũ", decoded!!.bookTitle)
        assertEquals("", decoded.chapterTitle)
        assertEquals(TtsWidgetPlaybackStatus.PAUSED, decoded.playbackStatus)
        assertEquals(0.5f, decoded.progress, 0.001f)
        assertTrue(decoded.hasSnapshot)
        assertNull(decoded.coverPath)
        assertEquals(0L, decoded.positionMs)
        assertEquals(0, decoded.paragraphIndex)
        assertEquals(0, decoded.totalParagraphs)
        assertEquals("", decoded.paragraphText)
    }

    @Test
    fun `decode legacy 9 fields payload parses time and chapter correctly`() {
        val titleB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("Phàm Nhân Tu Tiên".toByteArray(Charsets.UTF_8))
        val chapterB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("Chương 10".toByteArray(Charsets.UTF_8))
        val coverB64 = Base64.getUrlEncoder().withoutPadding().encodeToString("/cover.png".toByteArray(Charsets.UTF_8))

        val legacy9Payload = "1|$titleB64|PLAYING|0.75|1|$coverB64|$chapterB64|15000|60000"

        val decoded = TtsWidgetStateCodec.decode(legacy9Payload)
        assertNotNull(decoded)
        assertEquals("Phàm Nhân Tu Tiên", decoded!!.bookTitle)
        assertEquals("Chương 10", decoded.chapterTitle)
        assertEquals(TtsWidgetPlaybackStatus.PLAYING, decoded.playbackStatus)
        assertEquals(0.75f, decoded.progress, 0.001f)
        assertEquals(15000L, decoded.positionMs)
        assertEquals(60000L, decoded.durationMs)
        assertEquals("/cover.png", decoded.coverPath)
        assertEquals(0, decoded.paragraphIndex)
        assertEquals(0, decoded.totalParagraphs)
        assertEquals("", decoded.paragraphText)
    }

    @Test
    fun `decode returns null for corrupted payload or invalid schema`() {
        assertNull(TtsWidgetStateCodec.decode(""))
        assertNull(TtsWidgetStateCodec.decode("invalid_data"))
        assertNull(TtsWidgetStateCodec.decode("2|title|PLAYING|0.5|1")) // wrong schema version 2
        assertNull(TtsWidgetStateCodec.decode("1|not_base_64!#%|PLAYING|0.5|1"))
    }

    @Test
    fun `encode normalizes progress and truncates huge text to 800 chars`() {
        val hugeText = "A".repeat(1500)
        val state = TtsWidgetState(
            bookTitle = "Test",
            progress = 2.5f, // Out of bounds -> should normalize to 1f
            paragraphText = hugeText
        )

        val encoded = TtsWidgetStateCodec.encode(state)
        val decoded = TtsWidgetStateCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(1.0f, decoded!!.progress, 0.001f)
        assertEquals(800, decoded.paragraphText.length)
    }
}
