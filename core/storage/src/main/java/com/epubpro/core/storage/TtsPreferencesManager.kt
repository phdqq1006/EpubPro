package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.epubpro.domain.model.TtsSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tts_settings_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = kotlinx.coroutines.flow.MutableStateFlow(getSettings())
    val settingsFlow: kotlinx.coroutines.flow.StateFlow<TtsSettings> = _settingsFlow.asStateFlow()

    fun getSettings(): TtsSettings {
        val isConfigured = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        val isAiVoice = prefs.getBoolean(KEY_IS_AI_VOICE, false)
        val language = prefs.getString(KEY_LANGUAGE, "vi") ?: "vi"
        val voiceId = prefs.getString(KEY_VOICE_ID, null)
        val speed = prefs.getFloat(KEY_SPEED, 1.0f)
        val pitch = prefs.getFloat(KEY_PITCH, 1.0f)

        return TtsSettings(
            isConfigured = isConfigured,
            isAiVoice = isAiVoice,
            language = language,
            voiceId = voiceId,
            speed = speed,
            pitch = pitch
        )
    }

    fun saveSettings(settings: TtsSettings) {
        prefs.edit()
            .putBoolean(KEY_IS_CONFIGURED, settings.isConfigured)
            .putBoolean(KEY_IS_AI_VOICE, settings.isAiVoice)
            .putString(KEY_LANGUAGE, settings.language)
            .putString(KEY_VOICE_ID, settings.voiceId)
            .putFloat(KEY_SPEED, settings.speed)
            .putFloat(KEY_PITCH, settings.pitch)
            .apply()
        _settingsFlow.value = settings
    }

    fun saveLastTtsChunkIndex(bookId: String, chapterIndex: Int, chunkIndex: Int) {
        prefs.edit().putInt("tts_chunk_${bookId}_$chapterIndex", chunkIndex).apply()
    }

    fun getLastTtsChunkIndex(bookId: String, chapterIndex: Int): Int {
        return prefs.getInt("tts_chunk_${bookId}_$chapterIndex", 0)
    }

    companion object {
        private const val KEY_IS_CONFIGURED = "is_configured"
        private const val KEY_IS_AI_VOICE = "is_ai_voice"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_SPEED = "speed"
        private const val KEY_PITCH = "pitch"
    }
}
