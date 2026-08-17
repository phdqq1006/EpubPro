package com.epubpro.core.reader.tts.bubble

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsBubbleReducerTest {
    @Test
    fun `disabled toggle always disables overlay`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(
                enabled = false,
                expansionRequested = true
            )
        )

        assertEquals(TtsBubbleState.DISABLED, state)
    }

    @Test
    fun `missing overlay permission disables overlay`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(overlayPermissionGranted = false)
        )

        assertEquals(TtsBubbleState.DISABLED, state)
    }

    @Test
    fun `visible app hides overlay without disabling preference`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(
                appVisible = true,
                expansionRequested = true
            )
        )

        assertEquals(TtsBubbleState.HIDDEN, state)
    }

    @Test
    fun `locked device hides overlay`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(deviceLocked = true)
        )

        assertEquals(TtsBubbleState.HIDDEN, state)
    }

    @Test
    fun `session hide flag wins over expansion request`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(
                hiddenForCurrentSession = true,
                expansionRequested = true
            )
        )

        assertEquals(TtsBubbleState.HIDDEN, state)
    }

    @Test
    fun `expansion request expands eligible overlay`() {
        val state = TtsBubbleReducer.reduce(
            visibleEnvironment().copy(expansionRequested = true)
        )

        assertEquals(TtsBubbleState.EXPANDED, state)
    }

    @Test
    fun `eligible overlay is collapsed by default`() {
        assertEquals(
            TtsBubbleState.COLLAPSED,
            TtsBubbleReducer.reduce(visibleEnvironment())
        )
    }

    private fun visibleEnvironment() = TtsBubbleEnvironment(
        enabled = true,
        overlayPermissionGranted = true,
        appVisible = false,
        deviceLocked = false,
        hiddenForCurrentSession = false,
        expansionRequested = false
    )
}
