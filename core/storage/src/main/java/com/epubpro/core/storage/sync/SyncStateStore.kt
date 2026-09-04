package com.epubpro.core.storage.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.epubpro.domain.sync.SyncManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Dữ liệu baseline và cấu hình Drive không chứa access token. */
data class PersistedSyncState(
    val driveFolderId: String? = null,
    val baseline: SyncManifest? = null,
    val lastSyncAt: Long? = null,
    val lastSyncId: String? = null
)

/**
 * Lưu state sync bằng DataStore để tránh đưa token hoặc trạng thái phiên lên Drive.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context,
    private val manifestCodec: ManifestJsonCodec
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(FILE_NAME)
    }

    /** Flow state bền vững dùng cho coordinator. */
    val state: Flow<PersistedSyncState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences.toDomain() }

    /**
     * Đọc state hiện tại một lần.
     *
     * @return State đã lưu hoặc state mặc định.
     */
    suspend fun read(): PersistedSyncState = state.first()

    /**
     * Lưu ID folder Drive đã được người dùng chọn hoặc app tạo.
     *
     * @param folderId ID folder Drive, null để xóa liên kết.
     */
    suspend fun saveDriveFolderId(folderId: String?) {
        dataStore.edit { preferences ->
            if (folderId.isNullOrBlank()) preferences.remove(KEY_FOLDER_ID)
            else preferences[KEY_FOLDER_ID] = folderId
        }
    }

    /**
     * Lưu baseline sau khi manifest đã được xác nhận thành công.
     *
     * @param manifest Manifest đã sync xong.
     * @param syncId ID phiên sync.
     * @param timestamp Thời điểm hoàn tất theo epoch milliseconds.
     */
    suspend fun saveBaseline(manifest: SyncManifest, syncId: String, timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_BASELINE] = manifestCodec.encode(manifest)
            preferences[KEY_LAST_SYNC_ID] = syncId
            preferences[KEY_LAST_SYNC_AT] = timestamp
        }
    }

    private fun Preferences.toDomain() = PersistedSyncState(
        driveFolderId = this[KEY_FOLDER_ID],
        baseline = this[KEY_BASELINE]?.let { runCatching { manifestCodec.decode(it) }.getOrNull() },
        lastSyncAt = this[KEY_LAST_SYNC_AT],
        lastSyncId = this[KEY_LAST_SYNC_ID]
    )

    companion object {
        private const val FILE_NAME = "epub_sync_state"
        private val KEY_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        private val KEY_BASELINE = stringPreferencesKey("manifest_baseline")
        private val KEY_LAST_SYNC_ID = stringPreferencesKey("last_sync_id")
        private val KEY_LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }
}
