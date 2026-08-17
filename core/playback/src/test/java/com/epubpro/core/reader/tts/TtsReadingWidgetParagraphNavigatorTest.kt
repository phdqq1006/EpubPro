package com.epubpro.core.reader.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsReadingWidgetParagraphNavigatorTest {

    @Test
    fun `forward move within same chapter returns correct paragraph`() = runBlocking {
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 0,
            currentParagraphIndex = 2,
            relativeMove = 3,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(0, result.chapterIndex)
        assertEquals(5, result.paragraphIndex)
    }

    @Test
    fun `forward move overflowing current chapter carries over to next chapter`() = runBlocking {
        // Chapter 0 has 10 paragraphs (indices 0..9). From 8, move +3 -> target 11 -> overflow 1 in Chapter 1
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 0,
            currentParagraphIndex = 8,
            relativeMove = 3,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(1, result.chapterIndex)
        assertEquals(1, result.paragraphIndex)
    }

    @Test
    fun `forward move overflowing multiple chapters carries over correctly`() = runBlocking {
        // Chap 0 (5 paras), Chap 1 (5 paras), Chap 2 (10 paras). From chap 0 para 3, move +10:
        // Chap 0: 3 + 10 = 13 -> overflow 8
        // Chap 1: 8 - 5 = overflow 3
        // Chap 2: para 3
        val chapterCounts = mapOf(0 to 5, 1 to 5, 2 to 10)
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 0,
            currentParagraphIndex = 3,
            relativeMove = 10,
            totalChapters = 3,
            getParagraphCount = { chapterCounts[it] ?: 0 }
        )
        assertEquals(2, result.chapterIndex)
        assertEquals(3, result.paragraphIndex)
    }

    @Test
    fun `forward move clamped at end of book`() = runBlocking {
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 2,
            currentParagraphIndex = 8,
            relativeMove = 100,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(2, result.chapterIndex)
        assertEquals(9, result.paragraphIndex) // Last paragraph of chapter 2
    }

    @Test
    fun `backward move within same chapter returns correct paragraph`() = runBlocking {
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 1,
            currentParagraphIndex = 5,
            relativeMove = -3,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(1, result.chapterIndex)
        assertEquals(2, result.paragraphIndex)
    }

    @Test
    fun `backward move underflowing current chapter carries over to previous chapter`() = runBlocking {
        // Chap 0 (10 paras), Chap 1 (10 paras). From chap 1 para 1, move -3:
        // target -2 -> underflow 2 in Chap 0 -> para 10 - 2 = 8
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 1,
            currentParagraphIndex = 1,
            relativeMove = -3,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(0, result.chapterIndex)
        assertEquals(8, result.paragraphIndex)
    }

    @Test
    fun `backward move clamped at start of book`() = runBlocking {
        val result = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 0,
            currentParagraphIndex = 2,
            relativeMove = -50,
            totalChapters = 3,
            getParagraphCount = { 10 }
        )
        assertEquals(0, result.chapterIndex)
        assertEquals(0, result.paragraphIndex)
    }

    @Test
    fun `empty chapters are skipped automatically during forward and backward carry`() = runBlocking {
        // Chap 0 (5 paras), Chap 1 (0 paras - empty), Chap 2 (5 paras)
        val chapterCounts = mapOf(0 to 5, 1 to 0, 2 to 5)

        // Forward from Chap 0 para 4 + 2 -> skips Chap 1 -> Chap 2 para 1
        val fwdResult = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 0,
            currentParagraphIndex = 4,
            relativeMove = 2,
            totalChapters = 3,
            getParagraphCount = { chapterCounts[it] ?: 0 }
        )
        assertEquals(2, fwdResult.chapterIndex)
        assertEquals(1, fwdResult.paragraphIndex)

        // Backward from Chap 2 para 0 - 2 -> skips Chap 1 -> Chap 0 para 3 (5 - 2 = 3)
        val bwdResult = TtsReadingWidgetParagraphNavigator.calculateTargetPosition(
            currentChapterIndex = 2,
            currentParagraphIndex = 0,
            relativeMove = -2,
            totalChapters = 3,
            getParagraphCount = { chapterCounts[it] ?: 0 }
        )
        assertEquals(0, bwdResult.chapterIndex)
        assertEquals(3, bwdResult.paragraphIndex)
    }
}
