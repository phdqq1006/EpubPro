package com.epubpro.core.reader.tts

import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.TtsVoiceCatalog
import com.epubpro.core.tts.VoiceModelDownloader
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class PiperTtsEngineWrapper @Inject constructor(
    private val sherpaTtsEngine: SherpaTtsEngine,
    private val downloader: VoiceModelDownloader
) : TtsEngine {

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isEngineReady = false
    private var currentVoiceId: String? = null
    private var currentLanguage = "vi"
    private var currentSpeed = 1.0f

    private var speakJob: Job? = null
    private var initializeJob: Job? = null
    private var isInitializing = false
    private var onReadyCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var pendingSpeech: PendingSpeech? = null
    private val prefetchLock = Any()

    private var prefetchedAudio: PrefetchedAudio? = null

    private data class PrefetchedAudio(
        val key: String,
        val audio: Deferred<ByteArray>
    )

    private data class PendingSpeech(
        val chunk: TtsChunk,
        val onChunkStart: (Int) -> Unit,
        val onChunkDone: (Int) -> Unit,
        val onError: (String) -> Unit
    )

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        onReadyCallback = onReady
        onErrorCallback = onError
        if (isEngineReady) {
            onReady()
            playPendingSpeech()
            return
        }
        if (isInitializing) return

        val voiceId = currentVoiceId
        if (voiceId == null) {
            pendingSpeech = null
            return
        }
        if (TtsVoiceCatalog.find(voiceId)?.language != currentLanguage) {
            pendingSpeech = null
            return
        }

        isInitializing = true
        initializeJob = engineScope.launch {
            try {
                if (!downloader.isModelDownloaded(voiceId)) {
                    pendingSpeech = null
                    onErrorCallback?.invoke("Voice model not downloaded yet: $voiceId")
                    return@launch
                }
                if (!downloader.isEspeakDataReady()) {
                    downloader.downloadEspeakNgDataIfNeeded()
                }
                sherpaTtsEngine.initialize(
                    onnxPath = downloader.getModelPath(voiceId),
                    tokensPath = downloader.getTokensPath(voiceId),
                    dataDirPath = downloader.getEspeakDataDir()
                )
                isEngineReady = true
                onReadyCallback?.invoke()
                playPendingSpeech()
            } catch (error: Exception) {
                if (error !is CancellationException) {
                    pendingSpeech = null
                    onErrorCallback?.invoke("Failed to initialize Piper TTS: ${error.message}")
                }
            } finally {
                isInitializing = false
                initializeJob = null
            }
        }
    }

    override fun prefetch(chunk: TtsChunk) {
        if (!isEngineReady || currentVoiceId == null) return
        val speed = currentSpeed
        val key = prefetchKey(chunk, speed)
        synchronized(prefetchLock) {
            if (prefetchedAudio?.key == key) return
            prefetchedAudio?.audio?.cancel()
            prefetchedAudio = PrefetchedAudio(
                key = key,
                audio = engineScope.async { sherpaTtsEngine.synthesize(chunk.text, speed) }
            )
        }
    }

    override fun speak(
        chunk: TtsChunk,
        onChunkStart: (Int) -> Unit,
        onChunkDone: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentVoiceId == null) {
            onError("Chưa chọn giọng AI Offline")
            return
        }
        if (!isEngineReady) {
            pendingSpeech = PendingSpeech(chunk, onChunkStart, onChunkDone, onError)
            initialize(onReadyCallback ?: {}, onErrorCallback ?: {})
            return
        }

        speakJob?.cancel()
        speakJob = engineScope.launch {
            var completedNormally = false
            try {
                val speed = currentSpeed
                val cached = takePrefetched(prefetchKey(chunk, speed))
                val pcm = try {
                    cached?.audio?.await() ?: sherpaTtsEngine.synthesize(chunk.text, speed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    cached?.audio?.cancel()
                    sherpaTtsEngine.synthesize(chunk.text, speed)
                }
                sherpaTtsEngine.playPcm(
                    pcm = pcm,
                    onAudioStarted = { onChunkStart(chunk.id) }
                )
                completedNormally = true
            } catch (error: Exception) {
                if (error !is CancellationException) {
                    onError(error.message ?: "Không thể phát giọng AI Offline")
                }
            } finally {
                if (completedNormally) onChunkDone(chunk.id)
            }
        }
    }

    private fun prefetchKey(chunk: TtsChunk, speed: Float): String =
        "${chunk.id}:${chunk.text.hashCode()}:$speed"

    private fun takePrefetched(key: String): PrefetchedAudio? = synchronized(prefetchLock) {
        val cached = prefetchedAudio
        prefetchedAudio = null
        if (cached?.key == key) cached else {
            cached?.audio?.cancel()
            null
        }
    }

    private fun clearPrefetch() {
        synchronized(prefetchLock) {
            prefetchedAudio?.audio?.cancel()
            prefetchedAudio = null
        }
    }

    private fun playPendingSpeech() {
        val speech = pendingSpeech ?: return
        pendingSpeech = null
        speak(speech.chunk, speech.onChunkStart, speech.onChunkDone, speech.onError)
    }

    override fun pause() = stopPlayback()

    override fun resume() = Unit

    override fun stop() = stopPlayback()

    private fun stopPlayback() {
        pendingSpeech = null
        clearPrefetch()
        speakJob?.cancel()
        speakJob = null
        initializeJob?.cancel()
        initializeJob = null
        isInitializing = false
        sherpaTtsEngine.stop()
    }

    override fun setLanguage(language: String) {
        currentLanguage = if (language.equals("vi", ignoreCase = true)) "vi" else language.lowercase()
        if (currentLanguage != "vi") setVoice(null)
    }

    override fun setSpeed(speed: Float) {
        if (currentSpeed != speed) clearPrefetch()
        currentSpeed = speed
    }

    override fun setPitch(pitch: Float) = Unit

    override fun setVoice(voiceId: String?) {
        val supportedVoiceId = TtsVoiceCatalog.find(voiceId)
            ?.takeIf { it.language == currentLanguage }
            ?.id
        if (currentVoiceId == supportedVoiceId) return

        currentVoiceId = supportedVoiceId
        clearPrefetch()
        pendingSpeech = null
        speakJob?.cancel()
        speakJob = null
        initializeJob?.cancel()
        initializeJob = null
        isInitializing = false
        if (isEngineReady) sherpaTtsEngine.release()
        isEngineReady = false
    }

    override fun getAvailableVoices(language: String): List<TtsVoice> =
        TtsVoiceCatalog.forLanguage(language).map { model ->
            TtsVoice(
                id = model.id,
                name = model.displayName,
                language = model.language,
                isNetworkRequired = false,
                isDownloaded = downloader.isModelDownloaded(model.id)
            )
        }

    override fun shutdown() {
        clearPrefetch()
        pendingSpeech = null
        isInitializing = false
        speakJob?.cancel()
        initializeJob?.cancel()
        sherpaTtsEngine.release()
        isEngineReady = false
        engineScope.cancel()
    }
}
