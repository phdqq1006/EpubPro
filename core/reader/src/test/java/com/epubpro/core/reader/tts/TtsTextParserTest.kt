package com.epubpro.core.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextParserTest {

    @Test
    fun splitTextForSpeech_keepsChunksWithinLimitWithoutLosingWords() {
        val text = "First sentence has several words. Second sentence is also long enough to split safely. Third sentence ends here."

        val chunks = TtsTextParser.splitTextForSpeech(text, maxLength = 35)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 35 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun parseHtmlToChunks_splitsLongParagraphAndKeepsParagraphIndex() {
        val paragraph = List(80) { "word$it" }.joinToString(" ")
        val html = "<html><body><p>$paragraph</p><p>Second paragraph.</p></body></html>"

        val chunks = TtsTextParser.parseHtmlToChunks(html)
        val firstParagraphChunks = chunks.filter { it.paragraphIndex == 0 }

        assertTrue(firstParagraphChunks.size > 1)
        assertTrue(firstParagraphChunks.all { it.text.length <= 280 })
        assertEquals(paragraph, firstParagraphChunks.joinToString(" ") { it.text })
        assertEquals("Second paragraph.", chunks.last().text)
        assertEquals(1, chunks.last().paragraphIndex)
        assertEquals(chunks.indices.toList(), chunks.map { it.id })
    }

    @Test
    fun parseHtmlToChunks_fallsBackToBodyWhenContentUsesDivAndBreaks() {
        val content = List(90) { "content$it" }.joinToString(" ")
        val html = "<html><body><h1>Chapter title</h1><div>$content</div></body></html>"

        val chunks = TtsTextParser.parseHtmlToChunks(html)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.joinToString(" ") { it.text }.contains(content))
        assertEquals(chunks.indices.toList(), chunks.map { it.id })
    }
}
