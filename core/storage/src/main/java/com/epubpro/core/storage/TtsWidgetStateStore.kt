package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Base64

enum class TtsWidgetPlaybackStatus {
    IDLE,
    PREPARING,
    PLAYING,
    PAUSED,
    ERROR,
    COMPLETED
}

data class TtsWidgetState(
    val bookTitle: String = "",
    val playbackStatus: TtsWidgetPlaybackStatus = TtsWidgetPlaybackStatus.IDLE,
    val progress: Float = 0f,
    val hasSnapshot: Boolean = false
) {
    val normalizedProgress: Float
        get() = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
}

/** Stores the small, non-textual playback projection used by the home-screen widget. */
@Singleton
class TtsWidgetStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _stateFlow = MutableStateFlow(readState())
    val stateFlow: StateFlow<TtsWidgetState> = _stateFlow.asStateFlow()

    fun getState(): TtsWidgetState = _stateFlow.value

    @Synchronized
    fun saveState(state: TtsWidgetState): Boolean {
        val normalized = state.copy(progress = state.normalizedProgress)
        if (normalized == _stateFlow.value) return false

        val committed = preferences.edit()
            .putString(KEY_STATE, TtsWidgetStateCodec.encode(normalized))
            .commit()
        if (committed) {
            _stateFlow.value = normalized
        }
        return committed
    }

    private fun readState(): TtsWidgetState {
        val encoded = preferences.getString(KEY_STATE, null) ?: return TtsWidgetState()
        return TtsWidgetStateCodec.decode(encoded) ?: TtsWidgetState()
    }

    private companion object {
        const val PREFERENCES_NAME = "tts_widget_state"
        const val KEY_STATE = "widget_state_v1"
    }
}

internal object TtsWidgetStateCodec {
    private const val SCHEMA_VERSION = "1"
    private const val FIELD_SEPARATOR = '|'

    fun encode(state: TtsWidgetState): String {
        val normalized = state.copy(progress = state.normalizedProgress)
        val title = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(normalized.bookTitle.toByteArray(Charsets.UTF_8))
        return listOf(
            SCHEMA_VERSION,
            title,
            normalized.playbackStatus.name,
            normalized.progress.toString(),
            if (normalized.hasSnapshot) "1" else "0"
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    fun decode(encoded: String): TtsWidgetState? {
        val fields = encoded.split(FIELD_SEPARATOR)
        if (fields.size < 5 || fields[0] != SCHEMA_VERSION) return null

        val title = runCatching {
            String(
                Base64.getUrlDecoder().decode(fields[1]),
                Charsets.UTF_8
            )
        }.getOrNull() ?: return null
        val status = runCatching {
            TtsWidgetPlaybackStatus.valueOf(fields[2])
        }.getOrNull() ?: return null
        val progress = fields[3].toFloatOrNull() ?: return null
        val hasSnapshot = when (fields[4]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        return TtsWidgetState(
            bookTitle = title,
            playbackStatus = status,
            progress = progress,
            hasSnapshot = hasSnapshot
        )
    }
}
