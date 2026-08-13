package com.epubpro.core.reader.tts

/** Ngăn quá trình chuẩn bị câu nội bộ bị SystemUI hiển thị thành trạng thái buffering lặp lại. */
internal object TtsMediaPlaybackContinuityPolicy {
    /** Trả về `true` chỉ khi đoạn đọc đầu tiên của phiên phát hiện tại chưa bắt đầu. */
    fun shouldShowBuffering(hasPlaybackStartedInSession: Boolean): Boolean =
        !hasPlaybackStartedInSession
}
