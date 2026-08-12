package com.epubpro.core.reader.tts

import com.epubpro.core.storage.TtsBubblePowerMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPowerPolicyTest {
    @Test
    fun `battery saver schedules only idle background bubble`() {
        assertTrue(input().let(TtsPowerPolicy::shouldScheduleIdleTimeout))
        assertFalse(input(appVisible = true).let(TtsPowerPolicy::shouldScheduleIdleTimeout))
        assertFalse(input(playbackState = TtsPowerPlaybackState.PAUSED).let(TtsPowerPolicy::shouldScheduleIdleTimeout))
        assertFalse(input(bubbleAvailable = false).let(TtsPowerPolicy::shouldScheduleIdleTimeout))
    }

    @Test
    fun `always on never schedules idle shutdown`() {
        assertFalse(
            input(powerMode = TtsBubblePowerMode.ALWAYS_ON)
                .let(TtsPowerPolicy::shouldScheduleIdleTimeout)
        )
    }

    @Test
    fun `completed is idle eligible`() {
        assertTrue(
            input(playbackState = TtsPowerPlaybackState.COMPLETED)
                .let(TtsPowerPolicy::shouldShutdownIdleRuntime)
        )
    }

    private fun input(
        powerMode: TtsBubblePowerMode = TtsBubblePowerMode.BATTERY_SAVER,
        playbackState: TtsPowerPlaybackState = TtsPowerPlaybackState.IDLE,
        bubbleEnabled: Boolean = true,
        bubbleAvailable: Boolean = true,
        appVisible: Boolean = false
    ) = TtsPowerPolicyInput(
        powerMode = powerMode,
        playbackState = playbackState,
        bubbleEnabled = bubbleEnabled,
        bubbleAvailable = bubbleAvailable,
        appVisible = appVisible
    )
}
