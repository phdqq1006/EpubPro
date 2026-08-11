package com.epubpro.core.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsForegroundEpisodePolicyTest {
    @Test
    fun `adding a foreground type does not restart the episode`() {
        assertFalse(
            TtsForegroundEpisodePolicy.shouldRestart(
                isForeground = true,
                currentTypes = 1,
                requestedTypes = 3,
                force = false
            )
        )
    }

    @Test
    fun `removing a foreground type restarts the episode`() {
        assertTrue(
            TtsForegroundEpisodePolicy.shouldRestart(
                isForeground = true,
                currentTypes = 3,
                requestedTypes = 2,
                force = false
            )
        )
    }

    @Test
    fun `force only restarts an active foreground episode`() {
        assertTrue(
            TtsForegroundEpisodePolicy.shouldRestart(
                isForeground = true,
                currentTypes = 1,
                requestedTypes = 1,
                force = true
            )
        )
        assertFalse(
            TtsForegroundEpisodePolicy.shouldRestart(
                isForeground = false,
                currentTypes = 0,
                requestedTypes = 1,
                force = true
            )
        )
    }
}
