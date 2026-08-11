package com.epubpro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsWidgetStateCodecTest {
    @Test
    fun `widget state round trips unicode title and progress`() {
        val state = TtsWidgetState(
            bookTitle = "Đấu La Đại Lục 3",
            chapterTitle = "Chương 142",
            playbackStatus = TtsWidgetPlaybackStatus.PLAYING,
            progress = 0.42f,
            positionMs = 2292000L,
            durationMs = 5745000L,
            hasSnapshot = true,
            coverPath = "/storage/emulated/0/Android/data/com.epubpro/covers/book1.jpg",
            paragraphIndex = 5,
            totalParagraphs = 42,
            paragraphText = "Đường Tam đứng ở trên đài, ánh mắt kiên định nhìn về phía xa."
        )

        assertEquals(state, TtsWidgetStateCodec.decode(TtsWidgetStateCodec.encode(state)))
    }

    @Test
    fun `widget state decodes legacy 5-field state without coverPath`() {
        val legacyState = TtsWidgetState(
            bookTitle = "Sách Cũ",
            playbackStatus = TtsWidgetPlaybackStatus.PAUSED,
            progress = 0.5f,
            hasSnapshot = true,
            coverPath = null
        )
        val encoded5Fields = TtsWidgetStateCodec.encode(legacyState).split("|").take(5).joinToString("|")
        assertEquals(legacyState, TtsWidgetStateCodec.decode(encoded5Fields))
    }

    @Test
    fun `widget state clamps invalid progress`() {
        assertEquals(0f, TtsWidgetState(progress = -2f).normalizedProgress)
        assertEquals(1f, TtsWidgetState(progress = 2f).normalizedProgress)
        assertEquals(0f, TtsWidgetState(progress = Float.NaN).normalizedProgress)
    }

    @Test
    fun `invalid widget state is rejected`() {
        assertNull(TtsWidgetStateCodec.decode("broken"))
        assertNull(TtsWidgetStateCodec.decode("2||IDLE|0|0"))
        assertNull(TtsWidgetStateCodec.decode("1||BROKEN|0|0"))
    }
}
