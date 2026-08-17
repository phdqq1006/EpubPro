package com.epubpro.core.reader.tts.notification

internal enum class TtsNotificationUpdateAction {
    START_FOREGROUND,
    UPDATE_FOREGROUND_TYPES,
    UPDATE_IN_PLACE,
    RESTART_FOREGROUND
}

/** Chọn cách cập nhật foreground notification hiện tại mà không detach service khi không cần thiết. */
internal object TtsNotificationUpdatePolicy {
    /** Xác định thao tác cần thực hiện dựa trên trạng thái foreground và thay đổi service type. */
    fun resolve(
        isForeground: Boolean,
        currentTypes: Int,
        requestedTypes: Int,
        forceRestart: Boolean
    ): TtsNotificationUpdateAction {
        if (!isForeground) return TtsNotificationUpdateAction.START_FOREGROUND
        val removesType = (currentTypes and requestedTypes) != currentTypes
        if (forceRestart || removesType) return TtsNotificationUpdateAction.RESTART_FOREGROUND
        if (currentTypes != requestedTypes) {
            return TtsNotificationUpdateAction.UPDATE_FOREGROUND_TYPES
        }
        return TtsNotificationUpdateAction.UPDATE_IN_PLACE
    }
}
