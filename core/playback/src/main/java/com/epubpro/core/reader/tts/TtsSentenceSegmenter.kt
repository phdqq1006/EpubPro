package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import java.text.BreakIterator
import java.util.Locale

object TtsSentenceSegmenter {

    fun segment(chunks: List<TtsChunk>, language: String = "vi"): List<TtsChunk> {
        var sentenceId = 0
        return chunks.flatMap { chunk ->
            splitSentences(chunk.text, language).map { sentence ->
                TtsChunk(
                    id = sentenceId++,
                    paragraphIndex = chunk.paragraphIndex,
                    text = sentence
                )
            }
        }
    }

    internal fun splitSentences(text: String, language: String = "vi"): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()

        val locale = if (language.equals("en", ignoreCase = true)) {
            Locale.ENGLISH
        } else {
            Locale("vi", "VN")
        }
        val iterator = BreakIterator.getSentenceInstance(locale).apply {
            setText(normalized)
        }
        val sentences = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            normalized.substring(start, end).trim()
                .takeIf { it.isNotEmpty() }
                ?.let(sentences::add)
            start = end
            end = iterator.next()
        }
        return sentences.ifEmpty { listOf(normalized) }
    }
}
