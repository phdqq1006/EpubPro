package com.epubpro.core.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsMediaPlaybackContinuityPolicyTest {
    @Test
    fun `initial preparation exposes buffering`() {
        assertTrue(
            TtsMediaPlaybackContinuityPolicy.shouldShowBuffering(
                hasPlaybackStartedInSession = false
            )
        )
    }

    @Test
    fun `preparation after playback started remains continuous`() {
        assertFalse(
            TtsMediaPlaybackContinuityPolicy.shouldShowBuffering(
                hasPlaybackStartedInSession = true
            )
        )
    }
}
