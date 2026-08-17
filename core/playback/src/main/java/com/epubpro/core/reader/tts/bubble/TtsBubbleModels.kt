package com.epubpro.core.reader.tts.bubble

import android.graphics.Bitmap
import com.epubpro.domain.model.TtsPlayerState

/** Playback information rendered by the overlay without exposing the service itself. */
enum class TtsBubblePlaybackStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR,
    COMPLETED
}

enum class TtsBubbleState {
    DISABLED,
    HIDDEN,
    COLLAPSED,
    EXPANDED
}

sealed interface TtsBubbleCommand {
    data object Previous : TtsBubbleCommand
    data object TogglePlayPause : TtsBubbleCommand
    data object Next : TtsBubbleCommand
    data object Stop : TtsBubbleCommand
    data object OpenBook : TtsBubbleCommand
}

data class TtsBubbleUiModel(
    val playbackStatus: TtsBubblePlaybackStatus = TtsBubblePlaybackStatus.IDLE,
    val bookTitle: String = "",
    val currentText: String = "",
    val progress: Float = 0f,
    val coverBitmap: Bitmap? = null,
    val hasPlaybackSnapshot: Boolean = false,
    val canOpenBook: Boolean = false,
    val errorMessage: String? = null
) {
    val normalizedProgress: Float
        get() = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
}

class TtsBubbleOverlayCallbacks(
    val onCommand: (TtsBubbleCommand) -> Unit = {},
    val onExpansionChangeRequested: (expanded: Boolean) -> Unit = {},
    val onTemporarilyHideRequested: () -> Unit = {},
    val onPositionChanged: (TtsBubblePosition) -> Unit = {},
    val onOverlayUnavailable: (Throwable) -> Unit = {}
)

fun TtsPlayerState.toBubblePlaybackStatus(): TtsBubblePlaybackStatus = when (this) {
    TtsPlayerState.Idle -> TtsBubblePlaybackStatus.IDLE
    TtsPlayerState.Loading,
    is TtsPlayerState.Preparing -> TtsBubblePlaybackStatus.PREPARING
    is TtsPlayerState.Playing -> TtsBubblePlaybackStatus.PLAYING
    is TtsPlayerState.Paused -> TtsBubblePlaybackStatus.PAUSED
    is TtsPlayerState.Error -> TtsBubblePlaybackStatus.ERROR
    is TtsPlayerState.Completed -> TtsBubblePlaybackStatus.COMPLETED
}
