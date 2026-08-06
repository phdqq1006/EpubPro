package com.epubpro.core.reader.tts

import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.VoiceModelDownloader
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class PiperTtsEngineWrapper @Inject constructor(
    private val sherpaTtsEngine: SherpaTtsEngine,
    private val downloader: VoiceModelDownloader
) : TtsEngine {

    private val engineScope = CoroutineScope(Dispatchers.IO + Job())
    private var isEngineReady = false
    private var currentVoiceId = "ngoc_ngan"
    private var currentSpeed = 1.0f

    private var speakJob: Job? = null
    private var onChunkDoneCallback: ((Int) -> Unit)? = null
    private var currentChunkId: Int = -1
    private var isInitializing = false
    private var onReadyCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var pendingSpeech: PendingSpeech? = null

    private data class PendingSpeech(
        val chunk: TtsChunk,
        val onChunkStart: (Int) -> Unit,
        val onChunkDone: (Int) -> Unit
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
        isInitializing = true
        engineScope.launch {
            try {
                if (downloader.isModelDownloaded(currentVoiceId)) {
                    // Tải espeak-ng-data nếu chưa có
                    if (!downloader.isEspeakDataReady()) {
                        downloader.downloadEspeakNgDataIfNeeded()
                    }
                    val onnxPath = downloader.getModelPath(currentVoiceId)
                    val tokensPath = downloader.getTokensPath(currentVoiceId)
                    val espeakDataDir = downloader.getEspeakDataDir()
                    sherpaTtsEngine.initialize(
                        onnxPath = onnxPath,
                        tokensPath = tokensPath,
                        dataDirPath = espeakDataDir
                    )
                    isEngineReady = true
                    isInitializing = false
                    onReadyCallback?.invoke()
                    playPendingSpeech()
                } else {
                    isInitializing = false
                    pendingSpeech = null
                    onErrorCallback?.invoke("Voice model not downloaded yet: $currentVoiceId")
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    isInitializing = false
                    pendingSpeech = null
                    onErrorCallback?.invoke("Failed to initialize Piper TTS: ${e.message}")
                }
            }
        }
    }

    override fun speak(chunk: TtsChunk, onChunkStart: (Int) -> Unit, onChunkDone: (Int) -> Unit) {
        if (!isEngineReady) {
            pendingSpeech = PendingSpeech(chunk, onChunkStart, onChunkDone)
            initialize(
                onReady = onReadyCallback ?: {},
                onError = onErrorCallback ?: {}
            )
            return
        }

        // Hủy job speak đang chạy trước đó để không bị phát đè/trùng luồng
        speakJob?.cancel()

        currentChunkId = chunk.id
        onChunkDoneCallback = onChunkDone

        speakJob = engineScope.launch {
            onChunkStart(chunk.id)
            var completedNormally = false
            try {
                sherpaTtsEngine.speak(chunk.text, speed = currentSpeed)
                completedNormally = true
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
            } finally {
                // Chỉ gọi onChunkDone nếu job hoàn thành bình thường, không bị cancel do pause/stop
                if (completedNormally) {
                    onChunkDoneCallback?.invoke(currentChunkId)
                }
            }
        }
    }

    private fun playPendingSpeech() {
        val speech = pendingSpeech ?: return
        pendingSpeech = null
        speak(speech.chunk, speech.onChunkStart, speech.onChunkDone)
    }

    override fun pause() {
        pendingSpeech = null
        speakJob?.cancel()
        speakJob = null
        sherpaTtsEngine.stop()
    }

    override fun resume() {
        // TtsService sẽ gọi playCurrentChunk() -> speak()
    }

    override fun stop() {
        pendingSpeech = null
        speakJob?.cancel()
        speakJob = null
        sherpaTtsEngine.stop()
    }

    override fun setSpeed(speed: Float) {
        this.currentSpeed = speed
    }

    override fun setPitch(pitch: Float) {
        // Sherpa-onnx không hỗ trợ setPitch trực tiếp qua API Android hiện tại.
    }

    override fun setVoice(voiceId: String) {
        if (currentVoiceId == voiceId) return
        this.currentVoiceId = voiceId
        if (isEngineReady) {
            isEngineReady = false
            initialize(
                onReady = onReadyCallback ?: {},
                onError = onErrorCallback ?: {}
            )
        }
    }

    override fun getAvailableVoices(language: String): List<TtsVoice> {
        return listOf(
            TtsVoice(id = "ngoc_ngan", name = "Ngọc Ngạn", language = "vi", isNetworkRequired = false),
            TtsVoice(id = "quang_minh", name = "Quang Minh", language = "vi", isNetworkRequired = false)
        )
    }

    override fun shutdown() {
        pendingSpeech = null
        isInitializing = false
        speakJob?.cancel()
        speakJob = null
        sherpaTtsEngine.release()
        isEngineReady = false
    }
}
