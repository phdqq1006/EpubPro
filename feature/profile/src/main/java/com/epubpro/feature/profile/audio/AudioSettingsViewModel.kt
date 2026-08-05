package com.epubpro.feature.profile.audio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.storage.TtsPreferencesManager
import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.VoiceModelDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioSettingsUiState(
    val isAiVoice: Boolean = true,
    val selectedVoiceId: String = "ngoc_ngan",
    val isModelDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val isPlaying: Boolean = false,
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f
)

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    private val voiceModelDownloader: VoiceModelDownloader,
    private val ttsEngine: SherpaTtsEngine,
    private val preferencesManager: TtsPreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "EpubProTTS"
    }

    private val _uiState = MutableStateFlow(AudioSettingsUiState())
    val uiState: StateFlow<AudioSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                val voiceId = settings.voiceId ?: "ngoc_ngan"
                val voiceChanged = _uiState.value.selectedVoiceId != voiceId
                _uiState.value = _uiState.value.copy(
                    isAiVoice = settings.isAiVoice,
                    selectedVoiceId = voiceId,
                    speechSpeed = settings.speed,
                    speechPitch = settings.pitch
                )
                if (voiceChanged || !_uiState.value.isModelDownloaded) {
                    checkModelStatus(voiceId)
                }
            }
        }
    }
    private fun saveSettings() {
        val state = _uiState.value
        val settings = preferencesManager.getSettings().copy(
            isAiVoice = state.isAiVoice,
            voiceId = state.selectedVoiceId,
            speed = state.speechSpeed,
            pitch = state.speechPitch
        )
        preferencesManager.saveSettings(settings)
    }

    private fun checkModelStatus(voiceId: String) {
        val downloaded = voiceModelDownloader.isModelDownloaded(voiceId)
        Log.d(TAG, "checkModelStatus for '$voiceId': downloaded=$downloaded")
        _uiState.value = _uiState.value.copy(
            selectedVoiceId = voiceId,
            isModelDownloaded = downloaded,
            isDownloading = false,
            downloadProgress = if (downloaded) 1f else 0f
        )
    }

    fun onAiVoiceToggled(isAi: Boolean) {
        Log.d(TAG, "onAiVoiceToggled: $isAi")
        _uiState.value = _uiState.value.copy(isAiVoice = isAi)
        saveSettings()
    }

    fun onVoiceSelected(voiceId: String) {
        Log.d(TAG, "onVoiceSelected: $voiceId")
        checkModelStatus(voiceId)
        saveSettings()
    }

    fun onSpeedChanged(speed: Float) {
        _uiState.value = _uiState.value.copy(speechSpeed = speed)
        saveSettings()
    }

    fun onPitchChanged(pitch: Float) {
        _uiState.value = _uiState.value.copy(speechPitch = pitch)
        saveSettings()
    }

    fun downloadCurrentVoice() {
        val voiceId = _uiState.value.selectedVoiceId
        val (onnxName, voiceName) = getModelDetails(voiceId)
        Log.d(TAG, "downloadCurrentVoice starting for '$voiceId' ($voiceName), onnxName=$onnxName")

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadError = null
        )

        viewModelScope.launch {
            try {
                val success = voiceModelDownloader.downloadModel(
                    modelName = voiceId,
                    onnxUrl = "https://huggingface.co/doof-ferb/nghitts-copy/resolve/main/sherpa-onnx/$onnxName",
                    tokensUrl = "https://huggingface.co/doof-ferb/nghitts-copy/resolve/main/sherpa-onnx/tokens.txt",
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(downloadProgress = progress)
                    }
                )

                if (success) {
                    Log.d(TAG, "downloadCurrentVoice success for '$voiceId'")
                    checkModelStatus(voiceId)
                } else {
                    Log.e(TAG, "downloadCurrentVoice failed for '$voiceId'")
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = "Không thể tải giọng đọc. Vui lòng kiểm tra kết nối mạng."
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception during downloadCurrentVoice", t)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadError = "Lỗi khi tải: ${t.localizedMessage}"
                )
            }
        }
    }

    private fun getModelDetails(voiceId: String): Pair<String, String> {
        return when (voiceId) {
            "ngoc_ngan" -> Pair("ngocngan3701.onnx", "Ngọc Ngạn")
            "quang_minh" -> Pair("minhquang.onnx", "Quang Minh")
            "ngoc_huyen" -> Pair("ngochuyen.onnx", "Ngọc Huyền")
            "phuong_mai" -> Pair("maiphuong.onnx", "Phương Mai")
            "lac_phi" -> Pair("lacphi.onnx", "Lạc Phi")
            "duy" -> Pair("duyoryx3175.onnx", "Duy")
            "vais1000" -> Pair("minhkhang.onnx", "Vais1000")
            else -> Pair("ngocngan3701.onnx", "Ngọc Ngạn")
        }
    }

    fun checkDownloadComplete() {
        checkModelStatus(_uiState.value.selectedVoiceId)
    }

    fun testVoice() {
        if (!_uiState.value.isModelDownloaded) {
            Log.w(TAG, "testVoice called but model is not downloaded!")
            return
        }
        val voiceId = _uiState.value.selectedVoiceId
        Log.d(TAG, "testVoice: starting for '$voiceId'")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaying = true, downloadError = null)
            try {
                // Đảm bảo espeak-ng-data đã có (tải nếu thiếu)
                if (!voiceModelDownloader.isEspeakDataReady()) {
                    Log.d(TAG, "testVoice: espeak-ng-data not ready, downloading...")
                    voiceModelDownloader.downloadEspeakNgDataIfNeeded()
                }

                val onnxPath = voiceModelDownloader.getModelPath(voiceId)
                val tokensPath = voiceModelDownloader.getTokensPath(voiceId)
                val espeakDataDir = voiceModelDownloader.getEspeakDataDir()
                Log.d(TAG, "testVoice: onnx=$onnxPath, tokens=$tokensPath, espeakDataDir=$espeakDataDir")

                ttsEngine.initialize(
                    onnxPath = onnxPath,
                    tokensPath = tokensPath,
                    dataDirPath = espeakDataDir
                )
                Log.d(TAG, "testVoice: ttsEngine initialized, speaking...")

                ttsEngine.speak(
                    text = "Xin chào, đây là giọng đọc thử của ứng dụng EpubPro. Giọng đọc tự nhiên, rõ ràng và sử dụng hoàn toàn ngoại tuyến.",
                    speed = _uiState.value.speechSpeed
                )
                Log.d(TAG, "testVoice: speak finished successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "testVoice failed with exception", t)
                _uiState.value = _uiState.value.copy(
                    downloadError = "Lỗi khi phát thử: ${t.localizedMessage ?: t.javaClass.simpleName}",
                    isModelDownloaded = false
                )
            } finally {
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
        }
    }

    private var systemTts: android.speech.tts.TextToSpeech? = null

    fun testSystemVoice(context: android.content.Context) {
        if (systemTts == null) {
            systemTts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    systemTts?.language = java.util.Locale("vi", "VN")
                    speakSystemVoice()
                }
            }
        } else {
            speakSystemVoice()
        }
    }

    private fun speakSystemVoice() {
        val text = "Xin chào, đây là giọng đọc hệ thống của thiết bị. Giọng đọc dùng ngay không cần tải."
        systemTts?.setSpeechRate(_uiState.value.speechSpeed)
        systemTts?.setPitch(_uiState.value.speechPitch)
        systemTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test_system_tts")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: releasing ttsEngine and systemTts")
        ttsEngine.release()
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
    }
}
