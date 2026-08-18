package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk

/** Maps the private playback snapshot cursor back to a freshly segmented chapter. */
internal object TtsPlaybackCursorResolver {

    fun sentenceIndexInParagraph(chunks: List<TtsChunk>, currentIndex: Int): Int {
        val current = chunks.getOrNull(currentIndex) ?: return 0
        return chunks.asSequence()
            .take(currentIndex)
            .count { it.paragraphIndex == current.paragraphIndex }
    }

    fun resolveChunkIndex(
        chunks: List<TtsChunk>,
        paragraphIndex: Int,
        sentenceIndex: Int
    ): Int? {
        if (chunks.isEmpty()) return null

        val matchingIndices = chunks.indices.filter {
            chunks[it].paragraphIndex == paragraphIndex
        }
        if (matchingIndices.isNotEmpty()) {
            return matchingIndices[sentenceIndex.coerceIn(0, matchingIndices.lastIndex)]
        }

        return chunks.indexOfFirst { it.paragraphIndex >= paragraphIndex }
            .takeIf { it >= 0 }
            ?: chunks.lastIndex
    }
}
