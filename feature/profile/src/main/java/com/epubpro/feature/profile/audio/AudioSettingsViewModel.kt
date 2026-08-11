package com.epubpro.feature.profile.audio

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.tts.AndroidNativeTtsEngine
import com.epubpro.core.storage.TtsBubblePreferencesManager
import com.epubpro.core.storage.TtsPreferencesManager
import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.TtsVoiceCatalog
import com.epubpro.core.tts.VoiceModelDownloader
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiVoiceUiItem(
    val id: String,
    val name: String,
    val size: String,
    val isDownloaded: Boolean
)

data class AudioSettingsUiState(
    val isAiVoice: Boolean = false,
    val language: String = "vi",
    val selectedVoiceId: String? = null,
    val aiVoices: List<AiVoiceUiItem> = emptyList(),
    val systemVoices: List<TtsVoice> = emptyList(),
    val isSystemTtsReady: Boolean = false,
    val isModelDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val isPlaying: Boolean = false,
    val speechSpeed: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val isBubbleEnabled: Boolean = false,
    val isBubbleEnablePending: Boolean = false
)

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val voiceModelDownloader: VoiceModelDownloader,
    private val ttsEngine: SherpaTtsEngine,
    private val preferencesManager: TtsPreferencesManager,
    private val bubblePreferencesManager: TtsBubblePreferencesManager
) : ViewModel() {

    companion object {
        private const val TAG = "EpubProTTS"
    }

    private val nativeTtsEngine = AndroidNativeTtsEngine(context.applicationContext)
    private val initialBubblePreferences = bubblePreferencesManager.getPreferences()
    private val _uiState = MutableStateFlow(
        AudioSettingsUiState(
            isBubbleEnabled = initialBubblePreferences.enabled,
            isBubbleEnablePending = initialBubblePreferences.pendingEnable
        )
    )
    val uiState: StateFlow<AudioSettingsUiState> = _uiState.asStateFlow()

    init {
        refreshAiVoices()
        val initialSettings = preferencesManager.getSettings()
        if (initialSettings.isAiVoice && TtsVoiceCatalog.find(initialSettings.voiceId) == null) {
            preferencesManager.saveSettings(
                initialSettings.copy(isAiVoice = false, voiceId = null)
            )
        }
        val effectiveInitialSettings = preferencesManager.getSettings()
        nativeTtsEngine.setLanguage(effectiveInitialSettings.language)
        nativeTtsEngine.initialize(
            onReady = {
                _uiState.value = _uiState.value.copy(isSystemTtsReady = true)
                applyNativeSettings()
                refreshSystemVoices()
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    isSystemTtsReady = false,
                    downloadError = error
                )
            }
        )

        viewModelScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                val selectedVoiceId = if (settings.isAiVoice) {
                    TtsVoiceCatalog.find(settings.voiceId)?.id
                } else {
                    settings.voiceId
                }
                _uiState.value = _uiState.value.copy(
                    isAiVoice = settings.isAiVoice,
                    language = if (settings.isAiVoice) "vi" else settings.language,
                    selectedVoiceId = selectedVoiceId,
                    speechSpeed = settings.speed,
                    speechPitch = if (settings.isAiVoice) 1.0f else settings.pitch,
                    downloadError = null
                )
                refreshAiVoices()
                applyNativeSettings()
                refreshSystemVoices()
            }
        }

        viewModelScope.launch {
            bubblePreferencesManager.preferencesFlow.collect { preferences ->
                _uiState.value = _uiState.value.copy(
                    isBubbleEnabled = preferences.enabled,
                    isBubbleEnablePending = preferences.pendingEnable
                )
            }
        }
    }

    private fun saveSettings() {
        val state = _uiState.value
        if (state.isAiVoice && TtsVoiceCatalog.find(state.selectedVoiceId) == null) return
        preferencesManager.saveSettings(
            preferencesManager.getSettings().copy(
                isConfigured = true,
                isAiVoice = state.isAiVoice,
                language = if (state.isAiVoice) "vi" else state.language,
                voiceId = state.selectedVoiceId,
                speed = state.speechSpeed,
                pitch = if (state.isAiVoice) 1.0f else state.speechPitch
            )
        )
    }

    private fun refreshAiVoices() {
        val voices = TtsVoiceCatalog.aiVoices.map { model ->
            AiVoiceUiItem(
                id = model.id,
                name = model.displayName,
                size = model.downloadSize,
                isDownloaded = voiceModelDownloader.isModelDownloaded(model.id)
            )
        }

        val selectedVoice = voices.firstOrNull { it.id == _uiState.value.selectedVoiceId }
        _uiState.value = _uiState.value.copy(
            aiVoices = voices,
            isModelDownloaded = selectedVoice?.isDownloaded == true,
            isDownloading = false,
            downloadProgress = if (selectedVoice?.isDownloaded == true) 1f else 0f
        )
    }

    private fun refreshSystemVoices() {
        if (!_uiState.value.isSystemTtsReady) return
        val voices = nativeTtsEngine.getAvailableVoices(_uiState.value.language)
        _uiState.value = _uiState.value.copy(systemVoices = voices)
    }

    private fun applyNativeSettings() {
        val state = _uiState.value
        if (!state.isSystemTtsReady) return
        nativeTtsEngine.setLanguage(state.language)
        nativeTtsEngine.setSpeed(state.speechSpeed)
        nativeTtsEngine.setPitch(state.speechPitch)
        nativeTtsEngine.setVoice(state.selectedVoiceId.takeUnless { state.isAiVoice })
    }

    fun onAiVoiceToggled(isAi: Boolean) {
        if (_uiState.value.isAiVoice == isAi) return
        Log.d(TAG, "onAiVoiceToggled: $isAi")
        _uiState.value = _uiState.value.copy(
            isAiVoice = isAi,
            language = if (isAi) "vi" else _uiState.value.language,
            selectedVoiceId = null,
            speechPitch = if (isAi) 1.0f else _uiState.value.speechPitch,
            downloadError = null,
            isModelDownloaded = false
        )
        if (!isAi) refreshSystemVoices()
        saveSettings()
    }

    fun onLanguageChanged(language: String) {
        val supportedLanguage = if (_uiState.value.isAiVoice) "vi" else language.takeIf { it == "en" } ?: "vi"
        if (_uiState.value.language == supportedLanguage) return
        _uiState.value = _uiState.value.copy(
            language = supportedLanguage,
            selectedVoiceId = null,
            downloadError = null
        )
        nativeTtsEngine.setLanguage(supportedLanguage)
        nativeTtsEngine.setVoice(null)
        refreshSystemVoices()
        saveSettings()
    }

    fun onVoiceSelected(voiceId: String?) {
        val validVoiceId = if (_uiState.value.isAiVoice) {
            TtsVoiceCatalog.find(voiceId)?.id
        } else {
            voiceId?.takeIf { id -> _uiState.value.systemVoices.any { it.id == id } }
        }
        Log.d(TAG, "onVoiceSelected: $validVoiceId")
        _uiState.value = _uiState.value.copy(
            selectedVoiceId = validVoiceId,
            downloadError = null
        )
        if (_uiState.value.isAiVoice) refreshAiVoices() else applyNativeSettings()
        saveSettings()
    }

    fun onSpeedChanged(speed: Float) {
        _uiState.value = _uiState.value.copy(speechSpeed = speed)
        applyNativeSettings()
        saveSettings()
    }

    fun onPitchChanged(pitch: Float) {
        if (_uiState.value.isAiVoice) return
        _uiState.value = _uiState.value.copy(speechPitch = pitch)
        applyNativeSettings()
        saveSettings()
    }

    fun requestBubbleEnable(): Boolean {
        return bubblePreferencesManager.setPendingEnable(true)
    }

    fun onBubbleOverlayPermissionChecked(isGranted: Boolean): Boolean {
        val preferences = bubblePreferencesManager.getPreferences()
        return when {
            preferences.pendingEnable -> {
                val saved = bubblePreferencesManager.setEnabled(isGranted)
                isGranted && saved
            }
            preferences.enabled && !isGranted -> {
                bubblePreferencesManager.setEnabled(false)
                false
            }
            else -> false
        }
    }

    fun disableBubble() {
        bubblePreferencesManager.setEnabled(false)
    }

    fun downloadCurrentVoice() {
        val model = TtsVoiceCatalog.find(_uiState.value.selectedVoiceId)
        if (model == null) {
            _uiState.value = _uiState.value.copy(downloadError = "Vui lòng chọn một giọng AI trước khi tải.")
            return
        }
        Log.d(TAG, "downloadCurrentVoice starting for '${model.id}' (${model.displayName})")
        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadError = null
        )

        viewModelScope.launch {
            try {
                val success = voiceModelDownloader.downloadModel(
                    modelName = model.id,
                    onnxUrl = TtsVoiceCatalog.modelUrl(model),
                    tokensUrl = TtsVoiceCatalog.TOKENS_URL,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(downloadProgress = progress)
                    }
                )
                if (success) {
                    refreshAiVoices()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = "Không thể tải giọng đọc. Vui lòng kiểm tra kết nối mạng."
                    )
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Exception during downloadCurrentVoice", error)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadError = "Lỗi khi tải: ${error.localizedMessage}"
                )
            }
        }
    }

    fun checkDownloadComplete() = refreshAiVoices()

    fun testVoice() {
        val voiceId = _uiState.value.selectedVoiceId
        if (voiceId == null || !_uiState.value.isModelDownloaded) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaying = true, downloadError = null)
            try {
                if (!voiceModelDownloader.isEspeakDataReady()) {
                    voiceModelDownloader.downloadEspeakNgDataIfNeeded()
                }
                ttsEngine.initialize(
                    onnxPath = voiceModelDownloader.getModelPath(voiceId),
                    tokensPath = voiceModelDownloader.getTokensPath(voiceId),
                    dataDirPath = voiceModelDownloader.getEspeakDataDir()
                )
                ttsEngine.speak(
                    text = "Xin chào, đây là giọng đọc thử của ứng dụng EpubPro.",
                    speed = _uiState.value.speechSpeed
                )
            } catch (error: Throwable) {
                Log.e(TAG, "testVoice failed", error)
                _uiState.value = _uiState.value.copy(
                    downloadError = "Lỗi khi phát thử: ${error.localizedMessage ?: error.javaClass.simpleName}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
        }
    }

    fun testSystemVoice() {
        if (!_uiState.value.isSystemTtsReady) return
        applyNativeSettings()
        nativeTtsEngine.speak(
            chunk = TtsChunk(
                id = Int.MAX_VALUE,
                paragraphIndex = 0,
                text = if (_uiState.value.language == "en") {
                    "Hello, this is the selected system voice for EpubPro."
                } else {
                    "Xin chào, đây là giọng đọc hệ thống đã chọn cho EpubPro."
                }
            ),
            onChunkStart = {},
            onChunkDone = {}
        )
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared: releasing TTS engines")
        ttsEngine.release()
        nativeTtsEngine.shutdown()
        super.onCleared()
    }
}
