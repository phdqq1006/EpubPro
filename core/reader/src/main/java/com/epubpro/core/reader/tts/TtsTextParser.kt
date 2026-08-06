package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import org.jsoup.Jsoup

object TtsTextParser {

    private const val DEFAULT_MAX_CHUNK_LENGTH = 280

    internal fun splitTextForSpeech(
        text: String,
        maxLength: Int = DEFAULT_MAX_CHUNK_LENGTH
    ): List<String> {
        require(maxLength > 0)
        var remaining = text.replace(Regex("\\s+"), " ").trim()
        if (remaining.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        while (remaining.length > maxLength) {
            val sentenceBoundary = listOf(". ", "! ", "? ", "\u2026 ")
                .maxOf { remaining.lastIndexOf(it, startIndex = maxLength - 1) }
            val wordBoundary = remaining.lastIndexOf(' ', startIndex = maxLength)
            val cutIndex = when {
                sentenceBoundary >= maxLength / 3 -> sentenceBoundary + 1
                wordBoundary >= maxLength / 3 -> wordBoundary
                else -> maxLength
            }
            result += remaining.substring(0, cutIndex).trim()
            remaining = remaining.substring(cutIndex).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }

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
                    splitTextForSpeech(plainText).forEach { speechText ->
                        chunks.add(
                            TtsChunk(
                                id = chunkId++,
                                paragraphIndex = paragraphIndex,
                                text = speechText
                            )
                        )
                    }
                }
                paragraphIndex++
            }

            val bodyText = document.body().text().trim()
            val selectedTextLength = elements.sumOf { it.text().trim().length }
            if (bodyText.length > selectedTextLength + 20) {
                chunks.clear()
                chunkId = 0
                splitTextForSpeech(bodyText).forEach { speechText ->
                    chunks.add(
                        TtsChunk(
                            id = chunkId++,
                            paragraphIndex = 0,
                            text = speechText
                        )
                    )
                }
            }
            
            if (chunks.isEmpty() && elements.isEmpty()) {
                // Fallback for raw text without tags
                val rawText = document.text()
                val lines = rawText.split("\n")
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && trimmed.length > 1) {
                        splitTextForSpeech(trimmed).forEach { speechText ->
                            chunks.add(
                                TtsChunk(
                                    id = chunkId++,
                                    paragraphIndex = paragraphIndex,
                                    text = speechText
                                )
                            )
                        }
                        paragraphIndex++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chunks
    }
}
