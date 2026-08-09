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
