package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsPlaybackCursorResolverTest {

    private val chunks = listOf(
        TtsChunk(id = 0, paragraphIndex = 2, text = "A"),
        TtsChunk(id = 1, paragraphIndex = 2, text = "B"),
        TtsChunk(id = 2, paragraphIndex = 5, text = "C")
    )

    @Test
    fun `sentence index is scoped to the paragraph`() {
        assertEquals(0, TtsPlaybackCursorResolver.sentenceIndexInParagraph(chunks, 0))
        assertEquals(1, TtsPlaybackCursorResolver.sentenceIndexInParagraph(chunks, 1))
        assertEquals(0, TtsPlaybackCursorResolver.sentenceIndexInParagraph(chunks, 2))
    }

    @Test
    fun `restore resolves exact sentence and clamps within paragraph`() {
        assertEquals(1, TtsPlaybackCursorResolver.resolveChunkIndex(chunks, 2, 1))
        assertEquals(1, TtsPlaybackCursorResolver.resolveChunkIndex(chunks, 2, 99))
    }

    @Test
    fun `restore falls forward then falls back to final chunk`() {
        assertEquals(2, TtsPlaybackCursorResolver.resolveChunkIndex(chunks, 3, 0))
        assertEquals(2, TtsPlaybackCursorResolver.resolveChunkIndex(chunks, 8, 0))
    }

    @Test
    fun `empty chapter has no cursor`() {
        assertNull(TtsPlaybackCursorResolver.resolveChunkIndex(emptyList(), 0, 0))
    }
}
