package com.epubpro.core.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsReadingWidgetMoveQueueTest {
    @Test
    fun `rapid next actions are accumulated`() {
        val queue = TtsReadingWidgetMoveQueue(maxPendingMove = 100)

        queue.enqueue(1)
        queue.enqueue(1)

        assertEquals(2, queue.drain())
        assertEquals(0, queue.drain())
    }

    @Test
    fun `opposite actions preserve their net movement`() {
        val queue = TtsReadingWidgetMoveQueue(maxPendingMove = 100)

        queue.enqueue(1)
        queue.enqueue(-1)

        assertEquals(0, queue.drain())
    }

    @Test
    fun `pending movement is bounded`() {
        val queue = TtsReadingWidgetMoveQueue(maxPendingMove = 2)

        repeat(10) { queue.enqueue(1) }

        assertEquals(2, queue.drain())
    }
}
