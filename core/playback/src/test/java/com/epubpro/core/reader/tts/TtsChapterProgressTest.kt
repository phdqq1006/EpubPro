package com.epubpro.core.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsChapterProgressTest {
    @Test
    fun `first chapter starts at zero percent`() {
        assertEquals(0f, chapterStartProgress(chapterIndex = 0, totalChapters = 4), 0f)
    }

    @Test
    fun `last chapter is not complete when it only started`() {
        assertEquals(0.75f, chapterStartProgress(chapterIndex = 3, totalChapters = 4), 0f)
    }

    @Test
    fun `invalid values are safely bounded`() {
        assertEquals(0f, chapterStartProgress(chapterIndex = -1, totalChapters = 4), 0f)
        assertEquals(0.75f, chapterStartProgress(chapterIndex = 99, totalChapters = 4), 0f)
        assertEquals(0f, chapterStartProgress(chapterIndex = 0, totalChapters = 0), 0f)
    }
}
