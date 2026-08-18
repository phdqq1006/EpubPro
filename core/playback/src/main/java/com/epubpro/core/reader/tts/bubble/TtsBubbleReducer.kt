package com.epubpro.core.reader.tts.bubble

data class TtsBubbleEnvironment(
    val enabled: Boolean,
    val overlayPermissionGranted: Boolean,
    val appVisible: Boolean,
    val deviceLocked: Boolean,
    val hiddenForCurrentSession: Boolean,
    val expansionRequested: Boolean
)

/**
 * Pure policy for deciding whether the system overlay may be shown. Playback state is kept
 * outside this reducer so an idle bubble follows exactly the same visibility rules.
 */
object TtsBubbleReducer {
    fun reduce(environment: TtsBubbleEnvironment): TtsBubbleState = when {
        !environment.enabled || !environment.overlayPermissionGranted -> TtsBubbleState.DISABLED
        environment.appVisible ||
            environment.deviceLocked ||
            environment.hiddenForCurrentSession -> TtsBubbleState.HIDDEN
        environment.expansionRequested -> TtsBubbleState.EXPANDED
        else -> TtsBubbleState.COLLAPSED
    }
}
