package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import org.jsoup.Jsoup

object TtsTextParser {

    /**
     * Parse XHTML/HTML content of an EPUB chapter into a list of TtsChunks.
     * Each chunk represents a clean sentence or paragraph with plain text.
     */
    fun parseHtmlToChunks(htmlContent: String): List<TtsChunk> {
        if (htmlContent.isBlank()) return emptyList()

        val chunks = mutableListOf<TtsChunk>()
        
        try {
            val document = Jsoup.parse(htmlContent)
            // Use the exact same selector as JS querySelectorAll in CssInjector.kt
            // to ensure paragraphIndex matches 1:1 with JS elements
            val elements = document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
            
            var chunkId = 0
            var paragraphIndex = 0

            for (element in elements) {
                val plainText = element.text().trim()
                if (plainText.isNotBlank() && plainText.length > 1) {
                    chunks.add(
                        TtsChunk(
                            id = chunkId++,
                            paragraphIndex = paragraphIndex,
                            text = plainText
                        )
                    )
                }
                paragraphIndex++
            }
            
            if (chunks.isEmpty() && elements.isEmpty()) {
                // Fallback for raw text without tags
                val rawText = document.text()
                val lines = rawText.split("\n")
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && trimmed.length > 1) {
                        chunks.add(
                            TtsChunk(
                                id = chunkId++,
                                paragraphIndex = paragraphIndex++,
                                text = trimmed
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chunks
    }
}
