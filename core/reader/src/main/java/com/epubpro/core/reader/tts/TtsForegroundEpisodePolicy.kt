package com.epubpro.core.reader.tts

internal object TtsForegroundEpisodePolicy {
    fun shouldRestart(
        isForeground: Boolean,
        currentTypes: Int,
        requestedTypes: Int,
        force: Boolean
    ): Boolean {
        if (!isForeground) return false
        val removesType = (currentTypes and requestedTypes) != currentTypes
        return force || removesType
    }
}
