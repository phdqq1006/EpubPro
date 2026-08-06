package com.epubpro.core.ai

import com.epubpro.domain.model.AiRule
import com.epubpro.domain.model.AiRuleAction
import com.epubpro.domain.model.AiRuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHtmlProcessorTest {
    @Test
    fun parse_usesLeafBlocksWithoutDuplicatingNestedParagraphs() {
        val processor = AiHtmlProcessor.parse(
            "<html><body><blockquote><p>Đoạn bên trong</p></blockquote><p>Đoạn sau</p></body></html>"
        )

        assertEquals(2, processor.blocks.size)
        assertEquals(listOf("Đoạn bên trong", "Đoạn sau"), processor.blocks.map { it.plainText })
    }

    @Test
    fun render_preservesInlineTagsAndAttributes() {
        val processor = AiHtmlProcessor.parse(
            """<html><body><p>Xin chào <a href="note.html">Long</a>.</p></body></html>"""
        )
        val block = processor.blocks.single()

        val rendered = processor.render(
            replacements = mapOf(
                block.id to """Chào <a href="note.html">Long</a>."""
            ),
            rules = listOf(keepRule("Long"))
        )

        assertTrue(rendered.contains("""<a href="note.html">Long</a>"""))
        assertTrue(rendered.contains("Chào "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateBatch_rejectsChangedHtmlStructure() {
        val processor = AiHtmlProcessor.parse(
            "<html><body><p>Xin chào <em>Long</em>.</p></body></html>"
        )
        val block = processor.blocks.single()

        processor.validateBatch(
            requestedBlocks = listOf(block),
            replacements = mapOf(block.id to "Xin chào <strong>Long</strong>."),
            rules = emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateBatch_rejectsBrokenKeepRule() {
        val processor = AiHtmlProcessor.parse(
            "<html><body><p>Long bước vào đại điện.</p></body></html>"
        )
        val block = processor.blocks.single()

        processor.validateBatch(
            requestedBlocks = listOf(block),
            replacements = mapOf(block.id to "Rồng bước vào đại điện."),
            rules = listOf(keepRule("Long"))
        )
    }

    @Test
    fun chunk_splitsOnBlockAndCharacterLimits() {
        val blocks = List(5) { index ->
            AiTextBlock("block-$index", "12345", "12345")
        }

        val chunks = AiTextChunker.chunk(
            blocks = blocks,
            maxCharacters = 11,
            maxBlocks = 2
        )

        assertEquals(listOf(2, 2, 1), chunks.map { it.size })
    }

    private fun keepRule(source: String) = AiRule(
        id = source,
        scope = AiRuleScope.GLOBAL,
        bookId = null,
        source = source,
        action = AiRuleAction.KEEP,
        replacement = null,
        caseSensitive = true,
        updatedAt = 0L
    )
}
