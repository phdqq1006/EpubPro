package com.epubpro.core.reader.tts

import com.epubpro.core.storage.TtsBubblePowerMode

internal enum class TtsPowerPlaybackState {
    IDLE,
    COMPLETED,
    PAUSED,
    ACTIVE
}

internal data class TtsPowerPolicyInput(
    val powerMode: TtsBubblePowerMode,
    val playbackState: TtsPowerPlaybackState,
    val bubbleEnabled: Boolean,
    val bubbleAvailable: Boolean,
    val appVisible: Boolean
)

internal object TtsPowerPolicy {
    fun shouldScheduleIdleTimeout(input: TtsPowerPolicyInput): Boolean =
        input.powerMode == TtsBubblePowerMode.BATTERY_SAVER &&
            input.playbackState.isIdleEligible &&
            input.bubbleEnabled &&
            input.bubbleAvailable &&
            !input.appVisible

    fun shouldShutdownIdleRuntime(input: TtsPowerPolicyInput): Boolean =
        shouldScheduleIdleTimeout(input)

    private val TtsPowerPlaybackState.isIdleEligible: Boolean
        get() = this == TtsPowerPlaybackState.IDLE || this == TtsPowerPlaybackState.COMPLETED
}
