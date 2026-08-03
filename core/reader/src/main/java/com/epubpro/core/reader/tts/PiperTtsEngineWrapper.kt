package com.epubpro.core.reader.tts

import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.VoiceModelDownloader
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice
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
    
    // Lưu lại callback để gọi khi play xong
    private var onChunkDoneCallback: ((Int) -> Unit)? = null
    private var currentChunkId: Int = -1

    override fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
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
                    onReady()
                } else {
                    onError("Voice model not downloaded yet: $currentVoiceId")
                }
            } catch (e: Exception) {
                onError("Failed to initialize Piper TTS: ${e.message}")
            }
        }
    }

    override fun speak(chunk: TtsChunk, onChunkStart: (Int) -> Unit, onChunkDone: (Int) -> Unit) {
        if (!isEngineReady) {
            onChunkDone(chunk.id)
            return
        }
        
        currentChunkId = chunk.id
        onChunkDoneCallback = onChunkDone
        
        engineScope.launch {
            onChunkStart(chunk.id)
            try {
                sherpaTtsEngine.speak(chunk.text, speed = currentSpeed)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onChunkDoneCallback?.invoke(currentChunkId)
            }
        }
    }

    override fun pause() {
        // SherpaTtsEngine hiện tại chưa hỗ trợ pause luồng byte array tốt, ta sẽ tạm bỏ qua hoặc stop
    }

    override fun resume() {
        // Tương tự
    }

    override fun stop() {
        sherpaTtsEngine.release()
        isEngineReady = false
    }

    override fun setSpeed(speed: Float) {
        this.currentSpeed = speed
    }

    override fun setPitch(pitch: Float) {
        // Sherpa-onnx không hỗ trợ setPitch trực tiếp qua API Android hiện tại.
    }

    override fun setVoice(voiceId: String) {
        this.currentVoiceId = voiceId
        // Khi đổi giọng, nên re-initialize
        if (isEngineReady) {
            isEngineReady = false
            initialize(onReady = {}, onError = {})
        }
    }

    override fun getAvailableVoices(language: String): List<TtsVoice> {
        return listOf(
            TtsVoice(id = "ngoc_ngan", name = "Ngọc Ngạn", language = "vi", isNetworkRequired = false),
            TtsVoice(id = "quang_minh", name = "Quang Minh", language = "vi", isNetworkRequired = false)
        )
    }

    override fun shutdown() {
        stop()
    }
}
