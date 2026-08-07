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
            val blockTagsSet = setOf("body", "section", "article", "main", "center", "td", "th", "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "div")
            val paragraphs = mutableListOf<Pair<org.jsoup.nodes.Element, String>>()
            var currentBlock: org.jsoup.nodes.Element? = null
            var currentText = java.lang.StringBuilder()

            document.body().traverse(object : org.jsoup.select.NodeVisitor {
                override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                    if (node is org.jsoup.nodes.TextNode) {
                        var block: org.jsoup.nodes.Element? = node.parent() as? org.jsoup.nodes.Element
                        while (block != null && !blockTagsSet.contains(block.tagName().lowercase())) {
                            block = block.parent() as? org.jsoup.nodes.Element
                        }
                        if (block != null) {
                            if (block != currentBlock) {
                                if (currentBlock != null && currentText.toString().trim().length > 1) {
                                    paragraphs.add(Pair(currentBlock!!, currentText.toString()))
                                }
                                currentBlock = block
                                currentText = java.lang.StringBuilder()
                            }
                            currentText.append(node.wholeText)
                        }
                    } else if (node is org.jsoup.nodes.Element && node.tagName().lowercase() == "br") {
                        if (currentBlock != null) {
                            currentText.append(" ")
                        }
                    }
                }
                override fun tail(node: org.jsoup.nodes.Node, depth: Int) {}
            })

            if (currentBlock != null && currentText.toString().trim().length > 1) {
                paragraphs.add(Pair(currentBlock!!, currentText.toString()))
            }
            
            println("TTS_DEBUG: found paragraphs=${paragraphs.size}")

            var chunkId = 0
            var paragraphIndex = 0

            for (pair in paragraphs) {
                val plainText = pair.second.replace(Regex("\\s+"), " ").trim()
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
            
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return chunks
    }
}
