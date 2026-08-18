package com.epubpro.core.reader.tts

import com.epubpro.core.reader.tts.bubble.TtsBubblePlaybackStatus
import com.epubpro.core.storage.TtsWidgetPlaybackStatus
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsPlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackPresentationTest {

    @Test
    fun `loading with play when ready exposes preparing and pause control`() {
        val presentation = TtsPlayerState.Loading.toPlaybackPresentation(
            isRestoringSnapshot = false,
            loadingPlayWhenReady = true
        )

        assertTrue(presentation.isPlaybackRunning)
        assertEquals(TtsWidgetPlaybackStatus.PREPARING, presentation.widgetStatus)
        assertEquals(TtsBubblePlaybackStatus.PREPARING, presentation.bubbleStatus)
    }

    @Test
    fun `loading after pause exposes paused and play control`() {
        val presentation = TtsPlayerState.Loading.toPlaybackPresentation(
            isRestoringSnapshot = false,
            loadingPlayWhenReady = false
        )

        assertFalse(presentation.isPlaybackRunning)
        assertEquals(TtsWidgetPlaybackStatus.PAUSED, presentation.widgetStatus)
        assertEquals(TtsBubblePlaybackStatus.PAUSED, presentation.bubbleStatus)
    }

    @Test
    fun `snapshot restore takes precedence over idle state`() {
        val presentation = TtsPlayerState.Idle.toPlaybackPresentation(
            isRestoringSnapshot = true,
            loadingPlayWhenReady = false
        )

        assertTrue(presentation.isPlaybackRunning)
        assertEquals(TtsWidgetPlaybackStatus.PREPARING, presentation.widgetStatus)
        assertEquals(TtsBubblePlaybackStatus.PREPARING, presentation.bubbleStatus)
    }

    @Test
    fun `paused state stays paused outside chapter loading`() {
        val state = TtsPlayerState.Paused(
            currentChunkIndex = 0,
            totalChunks = 1,
            currentChunk = TtsChunk(id = 1, paragraphIndex = 0, text = "Sentence")
        )

        val presentation = state.toPlaybackPresentation(
            isRestoringSnapshot = false,
            loadingPlayWhenReady = true
        )

        assertFalse(presentation.isPlaybackRunning)
        assertEquals(TtsWidgetPlaybackStatus.PAUSED, presentation.widgetStatus)
        assertEquals(TtsBubblePlaybackStatus.PAUSED, presentation.bubbleStatus)
    }
}
