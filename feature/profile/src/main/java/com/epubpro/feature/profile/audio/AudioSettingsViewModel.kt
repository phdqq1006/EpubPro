package com.epubpro.feature.profile.audio

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

    private val _uiState = MutableStateFlow(AudioSettingsUiState())
    val uiState: StateFlow<AudioSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val settings = preferencesManager.getSettings()
        val voiceId = settings.voiceId ?: "ngoc_ngan"
        _uiState.value = _uiState.value.copy(
            isAiVoice = settings.isAiVoice,
            selectedVoiceId = voiceId,
            speechSpeed = settings.speed,
            speechPitch = settings.pitch
        )
        checkModelStatus(voiceId)
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
        _uiState.value = _uiState.value.copy(
            selectedVoiceId = voiceId,
            isModelDownloaded = downloaded,
            isDownloading = false
        )
    }

    fun onAiVoiceToggled(isAi: Boolean) {
        _uiState.value = _uiState.value.copy(isAiVoice = isAi)
        saveSettings()
    }

    fun onVoiceSelected(voiceId: String) {
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
        val (onnxName, _) = getModelDetails(voiceId) ?: return
        
        _uiState.value = _uiState.value.copy(isDownloading = true)
        
        viewModelScope.launch {
            try {
                voiceModelDownloader.downloadModel(
                    modelName = voiceId,
                    onnxUrl = "https://huggingface.co/doof-ferb/nghitts-copy/resolve/main/sherpa-onnx/$onnxName",
                    tokensUrl = "https://huggingface.co/doof-ferb/nghitts-copy/resolve/main/sherpa-onnx/tokens.txt"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getModelDetails(voiceId: String): Pair<String, String>? {
        return when (voiceId) {
            "ngoc_ngan" -> Pair("ngocngan3701.onnx", "Ngọc Ngạn")
            "quang_minh" -> Pair("minhquang.onnx", "Quang Minh")
            else -> null
        }
    }

    fun checkDownloadComplete() {
        checkModelStatus(_uiState.value.selectedVoiceId)
    }

    fun testVoice() {
        if (!_uiState.value.isModelDownloaded) return
        val voiceId = _uiState.value.selectedVoiceId
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaying = true)
            try {
                val onnxPath = voiceModelDownloader.getModelPath(voiceId)
                val tokensPath = voiceModelDownloader.getTokensPath(voiceId)
                
                ttsEngine.initialize(onnxPath = onnxPath, tokensPath = tokensPath)
                ttsEngine.speak(
                    text = "Xin chào, đây là giọng đọc trí tuệ nhân tạo, đang được thử nghiệm trên ứng dụng.",
                    speed = _uiState.value.speechSpeed
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsEngine.release()
    }
}
