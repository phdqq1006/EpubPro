package com.epubpro.core.reader.tts

import com.epubpro.core.reader.tts.bubble.TtsBubblePlaybackStatus
import com.epubpro.core.storage.TtsWidgetPlaybackStatus
import com.epubpro.domain.model.TtsPlayerState

/** Trạng thái phát dành cho giao diện, được dùng chung bởi widget, bubble và các nút điều khiển. */
internal data class TtsPlaybackPresentation(
    val isPlaybackRunning: Boolean,
    val widgetStatus: TtsWidgetPlaybackStatus,
    val bubbleStatus: TtsBubblePlaybackStatus
)

/**
 * Chuyển trạng thái kỹ thuật của player thành trạng thái giao diện thống nhất, đồng thời giữ ý định
 * phát của người dùng trong lúc chương hoặc snapshot vẫn đang được tải.
 */
internal fun TtsPlayerState.toPlaybackPresentation(
    isRestoringSnapshot: Boolean,
    loadingPlayWhenReady: Boolean
): TtsPlaybackPresentation {
    if (isRestoringSnapshot) {
        return TtsPlaybackPresentation(
            isPlaybackRunning = true,
            widgetStatus = TtsWidgetPlaybackStatus.PREPARING,
            bubbleStatus = TtsBubblePlaybackStatus.PREPARING
        )
    }

    return when (this) {
        TtsPlayerState.Idle -> TtsPlaybackPresentation(
            isPlaybackRunning = false,
            widgetStatus = TtsWidgetPlaybackStatus.IDLE,
            bubbleStatus = TtsBubblePlaybackStatus.IDLE
        )
        TtsPlayerState.Loading -> if (loadingPlayWhenReady) {
            TtsPlaybackPresentation(
                isPlaybackRunning = true,
                widgetStatus = TtsWidgetPlaybackStatus.PREPARING,
                bubbleStatus = TtsBubblePlaybackStatus.PREPARING
            )
        } else {
            TtsPlaybackPresentation(
                isPlaybackRunning = false,
                widgetStatus = TtsWidgetPlaybackStatus.PAUSED,
                bubbleStatus = TtsBubblePlaybackStatus.PAUSED
            )
        }
        is TtsPlayerState.Preparing -> TtsPlaybackPresentation(
            isPlaybackRunning = true,
            widgetStatus = TtsWidgetPlaybackStatus.PREPARING,
            bubbleStatus = TtsBubblePlaybackStatus.PREPARING
        )
        is TtsPlayerState.Playing -> TtsPlaybackPresentation(
            isPlaybackRunning = true,
            widgetStatus = TtsWidgetPlaybackStatus.PLAYING,
            bubbleStatus = TtsBubblePlaybackStatus.PLAYING
        )
        is TtsPlayerState.Paused -> TtsPlaybackPresentation(
            isPlaybackRunning = false,
            widgetStatus = TtsWidgetPlaybackStatus.PAUSED,
            bubbleStatus = TtsBubblePlaybackStatus.PAUSED
        )
        is TtsPlayerState.Error -> TtsPlaybackPresentation(
            isPlaybackRunning = false,
            widgetStatus = TtsWidgetPlaybackStatus.ERROR,
            bubbleStatus = TtsBubblePlaybackStatus.ERROR
        )
        is TtsPlayerState.Completed -> TtsPlaybackPresentation(
            isPlaybackRunning = false,
            widgetStatus = TtsWidgetPlaybackStatus.COMPLETED,
            bubbleStatus = TtsBubblePlaybackStatus.COMPLETED
        )
    }
}
