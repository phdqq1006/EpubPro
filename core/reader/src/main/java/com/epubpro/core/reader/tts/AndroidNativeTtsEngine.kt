package com.epubpro.core.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice
import java.util.Locale

class AndroidNativeTtsEngine(
    private val context: Context
) : TtsEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onReadyCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private var currentOnChunkStart: ((Int) -> Unit)? = null
    private var currentOnChunkDone: ((Int) -> Unit)? = null

    private var pendingSpeed: Float = 1.0f
    private var pendingPitch: Float = 1.0f
    private var pendingVoiceId: String? = null

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        this.onReadyCallback = onReady
        this.onErrorCallback = onError
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        } else if (isInitialized) {
            onReady()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English or default locale
                tts?.language = Locale.getDefault()
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val chunkId = utteranceId?.toIntOrNull() ?: return
                    currentOnChunkStart?.invoke(chunkId)
                }

                override fun onDone(utteranceId: String?) {
                    val chunkId = utteranceId?.toIntOrNull() ?: return
                    currentOnChunkDone?.invoke(chunkId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val chunkId = utteranceId?.toIntOrNull() ?: return
                    currentOnChunkDone?.invoke(chunkId)
                }
            })

            isInitialized = true
            setSpeed(pendingSpeed)
            setPitch(pendingPitch)
            pendingVoiceId?.let { setVoice(it) }

            onReadyCallback?.invoke()
        } else {
            isInitialized = false
            onErrorCallback?.invoke("Không thể khởi tạo Android TextToSpeech Engine (mã lỗi: $status).")
        }
    }

    override fun speak(chunk: TtsChunk, onChunkStart: (Int) -> Unit, onChunkDone: (Int) -> Unit) {
        if (!isInitialized || tts == null) return
        this.currentOnChunkStart = onChunkStart
        this.currentOnChunkDone = onChunkDone

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunk.id.toString())
        }
        tts?.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, params, chunk.id.toString())
    }

    override fun pause() {
        tts?.stop()
    }

    override fun resume() {
        // Resume handled at service level by re-submitting current chunk
    }

    override fun stop() {
        tts?.stop()
    }

    override fun setSpeed(speed: Float) {
        pendingSpeed = speed
        if (isInitialized) {
            tts?.setSpeechRate(speed)
        }
    }

    override fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    override fun setVoice(voiceId: String) {
        pendingVoiceId = voiceId
        if (isInitialized && tts != null) {
            val voice = tts?.voices?.find { it.name == voiceId }
            if (voice != null) {
                tts?.voice = voice
            }
        }
    }

    override fun getAvailableVoices(language: String): List<TtsVoice> {
        if (!isInitialized || tts == null) return emptyList()
        val targetLocale = if (language.equals("vi", ignoreCase = true)) Locale("vi", "VN") else Locale.ENGLISH
        val voices = tts?.voices ?: return emptyList()
        
        return voices
            .filter { it.locale.language == targetLocale.language }
            .map { voice ->
                TtsVoice(
                    id = voice.name,
                    name = voice.name.replace(voice.locale.language, "").replace("-", " ").trim().ifEmpty { voice.name },
                    language = voice.locale.displayLanguage,
                    isNetworkRequired = voice.isNetworkConnectionRequired
                )
            }
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
