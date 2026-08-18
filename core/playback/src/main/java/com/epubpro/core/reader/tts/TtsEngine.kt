package com.epubpro.core.reader.tts

import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice

interface TtsEngine {
    fun initialize(onReady: () -> Unit, onError: (String) -> Unit)
    fun speak(
        chunk: TtsChunk,
        onChunkStart: (Int) -> Unit,
        onChunkDone: (Int) -> Unit,
        onError: (String) -> Unit = {}
    )
    /** Starts background synthesis for the next chunk; Native TTS keeps the default no-op. */
    fun prefetch(chunk: TtsChunk) = Unit
    fun pause()
    fun resume()
    fun stop()
    fun setLanguage(language: String)
    fun setSpeed(speed: Float)
    fun setPitch(pitch: Float)
    fun setVoice(voiceId: String?)
    fun getAvailableVoices(language: String): List<TtsVoice>
    fun shutdown()
}
