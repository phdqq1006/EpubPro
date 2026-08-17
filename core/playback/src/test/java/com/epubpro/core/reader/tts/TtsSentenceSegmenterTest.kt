package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSentenceSegmenterTest {

    @Test
    fun segment_preservesParagraphAndAssignsStableSentenceIds() {
        val chunks = listOf(
            TtsChunk(7, 3, "Câu thứ nhất. Câu thứ hai!"),
            TtsChunk(8, 4, "Đoạn tiếp theo?")
        )

        val result = TtsSentenceSegmenter.segment(chunks)

        assertEquals(listOf(0, 1, 2), result.map { it.id })
        assertEquals(listOf(3, 3, 4), result.map { it.paragraphIndex })
        assertTrue(result[0].text.endsWith('.'))
        assertTrue(result[1].text.endsWith('!'))
    }

    @Test
    fun splitSentences_returnsNormalizedTextWhenThereIsNoPunctuation() {
        assertEquals(
            listOf("Một câu không có dấu kết thúc"),
            TtsSentenceSegmenter.splitSentences("  Một câu   không có dấu kết thúc  ")
        )
    }
}
