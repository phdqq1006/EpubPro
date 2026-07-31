package com.epubpro.domain.model

data class TtsChunk(
    val id: Int,
    val paragraphIndex: Int,
    val text: String
)

data class TtsVoice(
    val id: String,
    val name: String,
    val language: String,
    val isNetworkRequired: Boolean = false
)

data class TtsSettings(
    val isConfigured: Boolean = false,
    val isAiVoice: Boolean = false,
    val language: String = "vi",
    val voiceId: String? = null,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f
)

sealed class TtsPlayerState {
    object Idle : TtsPlayerState()
    object Loading : TtsPlayerState()
    data class Playing(
        val currentChunkIndex: Int,
        val totalChunks: Int,
        val currentChunk: TtsChunk,
        val progressMs: Long = 0L,
        val totalMs: Long = 0L
    ) : TtsPlayerState()
    data class Paused(
        val currentChunkIndex: Int,
        val totalChunks: Int,
        val currentChunk: TtsChunk
    ) : TtsPlayerState()
    data class Error(val message: String) : TtsPlayerState()
}

enum class SleepTimerOption(val label: String, val minutes: Int) {
    OFF("Tắt", 0),
    MIN_15("15 phút", 15),
    MIN_30("30 phút", 30),
    MIN_45("45 phút", 45),
    MIN_60("60 phút", 60),
    END_OF_CHAPTER("Hết chương", -1)
}
