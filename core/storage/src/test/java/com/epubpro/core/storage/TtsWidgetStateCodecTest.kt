package com.epubpro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsWidgetStateCodecTest {
    @Test
    fun `widget state round trips unicode title and progress`() {
        val state = TtsWidgetState(
            bookTitle = "Đấu La Đại Lục",
            playbackStatus = TtsWidgetPlaybackStatus.PLAYING,
            progress = 0.42f,
            hasSnapshot = true
        )

        assertEquals(state, TtsWidgetStateCodec.decode(TtsWidgetStateCodec.encode(state)))
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
