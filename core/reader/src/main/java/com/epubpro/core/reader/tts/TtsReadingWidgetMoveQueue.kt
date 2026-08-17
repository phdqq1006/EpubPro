package com.epubpro.core.reader.tts

/** Accumulates reading-widget moves while the current projection is being loaded. */
internal class TtsReadingWidgetMoveQueue(
    private val maxPendingMove: Int
) {
    private var pendingMove: Int = 0

    fun enqueue(relativeMove: Int) {
        pendingMove = (pendingMove + relativeMove).coerceIn(-maxPendingMove, maxPendingMove)
    }

    fun drain(): Int = pendingMove.also { pendingMove = 0 }

    fun clear() {
        pendingMove = 0
    }
}
