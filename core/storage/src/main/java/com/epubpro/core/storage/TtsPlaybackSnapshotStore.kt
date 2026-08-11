package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TtsPlaybackSnapshot(
    val bookId: String,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val sentenceIndex: Int,
    val preferAiContent: Boolean,
    val timelinePositionMs: Long
)

internal fun TtsPlaybackSnapshot.normalizedOrNull(): TtsPlaybackSnapshot? {
    if (bookId.isBlank()) return null
    return copy(
        chapterIndex = chapterIndex.coerceAtLeast(0),
        paragraphIndex = paragraphIndex.coerceAtLeast(0),
        sentenceIndex = sentenceIndex.coerceAtLeast(0),
        timelinePositionMs = timelinePositionMs.coerceAtLeast(0L)
    )
}

/** Stores only the cursor required to rebuild a stopped TTS session; no book text or HTML. */
@Singleton
class TtsPlaybackSnapshotStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _snapshotFlow = MutableStateFlow(readSnapshot())
    val snapshotFlow: StateFlow<TtsPlaybackSnapshot?> = _snapshotFlow.asStateFlow()

    fun getSnapshot(): TtsPlaybackSnapshot? = _snapshotFlow.value

    /**
     * Writes a complete snapshot as one synchronously committed preference value.
     * Returns false for an invalid book id or if the platform could not persist the update.
     */
    @Synchronized
    fun saveSnapshot(snapshot: TtsPlaybackSnapshot): Boolean {
        val normalized = snapshot.normalizedOrNull() ?: return false
        if (normalized == _snapshotFlow.value) return true

        val committed = prefs.edit()
            .putString(KEY_SNAPSHOT, TtsPlaybackSnapshotCodec.encode(normalized))
            .commit()
        if (committed) {
            _snapshotFlow.value = normalized
        }
        return committed
    }

    @Synchronized
    fun clearSnapshot(): Boolean {
        if (_snapshotFlow.value == null && !prefs.contains(KEY_SNAPSHOT)) return true

        val committed = prefs.edit().remove(KEY_SNAPSHOT).commit()
        if (committed) {
            _snapshotFlow.value = null
        }
        return committed
    }

    private fun readSnapshot(): TtsPlaybackSnapshot? {
        val encoded = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        return TtsPlaybackSnapshotCodec.decode(encoded)
    }

    private companion object {
        const val PREFERENCES_NAME = "tts_playback_snapshot"
        const val KEY_SNAPSHOT = "playback_snapshot_v1"
    }
}

internal object TtsPlaybackSnapshotCodec {
    private const val SCHEMA_VERSION = "1"
    private const val FIELD_SEPARATOR = '\n'

    fun encode(snapshot: TtsPlaybackSnapshot): String {
        val value = requireNotNull(snapshot.normalizedOrNull()) { "Snapshot requires a book id" }
        return listOf(
            SCHEMA_VERSION,
            encodeBookId(value.bookId),
            value.chapterIndex.toString(),
            value.paragraphIndex.toString(),
            value.sentenceIndex.toString(),
            if (value.preferAiContent) "1" else "0",
            value.timelinePositionMs.toString()
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    fun decode(encoded: String): TtsPlaybackSnapshot? {
        val fields = encoded.split(FIELD_SEPARATOR)
        if (fields.size < 7 || fields[0] != SCHEMA_VERSION) return null

        val bookId = decodeBookId(fields[1]) ?: return null
        val chapterIndex = fields[2].toIntOrNull() ?: return null
        val paragraphIndex = fields[3].toIntOrNull() ?: return null
        val sentenceIndex = fields[4].toIntOrNull() ?: return null
        val preferAiContent = when (fields[5]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val timelinePositionMs = fields[6].toLongOrNull() ?: return null

        return TtsPlaybackSnapshot(
            bookId = bookId,
            chapterIndex = chapterIndex,
            paragraphIndex = paragraphIndex,
            sentenceIndex = sentenceIndex,
            preferAiContent = preferAiContent,
            timelinePositionMs = timelinePositionMs
        ).normalizedOrNull()
    }

    private fun encodeBookId(bookId: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bookId.toByteArray(StandardCharsets.UTF_8))

    private fun decodeBookId(encoded: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
    }.getOrNull()
}
