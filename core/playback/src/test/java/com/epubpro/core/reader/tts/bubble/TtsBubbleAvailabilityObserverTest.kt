package com.epubpro.core.reader.tts.bubble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsBubbleAvailabilityObserverTest {
    @Test
    fun `same value does not report a transition`() {
        val observer = TtsBubbleAvailabilityObserver(initial = true)

        assertFalse(observer.update(true))
        assertFalse(observer.update(true))
    }

    @Test
    fun `revocation is reported exactly once`() {
        val observer = TtsBubbleAvailabilityObserver(initial = true)

        assertTrue(observer.update(false))
        assertFalse(observer.update(false))
    }

    @Test
    fun `grant is reported exactly once`() {
        val observer = TtsBubbleAvailabilityObserver(initial = false)

        assertTrue(observer.update(true))
        assertFalse(observer.update(true))
    }
}
