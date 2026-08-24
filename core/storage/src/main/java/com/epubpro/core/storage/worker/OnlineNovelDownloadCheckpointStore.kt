package com.epubpro.core.storage.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lưu checkpoint bền vững cho từng job tải truyện online.
 *
 * @param context Context ứng dụng dùng để mở SharedPreferences.
 */
@Singleton
class OnlineNovelDownloadCheckpointStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /**
     * Đọc phase và chương cuối đã lập chỉ mục của một truyện.
     *
     * @param novelId Định danh truyện online.
     * @return Checkpoint hiện tại hoặc trạng thái mặc định nếu chưa có.
     */
    @Synchronized
    fun get(novelId: String): Checkpoint {
        val key = keyFor(novelId)
        return Checkpoint(
            phase = preferences.getString(key + PHASE_SUFFIX, PHASE_DOWNLOAD) ?: PHASE_DOWNLOAD,
            lastIndexedChapter = preferences.getInt(key + CHAPTER_SUFFIX, -1)
        )
    }

    /**
     * Cập nhật phase xử lý mà không làm mất checkpoint chương.
     *
     * @param novelId Định danh truyện online.
     * @param phase Phase mới.
     */
    @Synchronized
    fun savePhase(novelId: String, phase: String) {
        preferences.edit().putString(keyFor(novelId) + PHASE_SUFFIX, phase).apply()
    }

    /**
     * Ghi nhận chương cuối đã lập chỉ mục thành công.
     *
     * @param novelId Định danh truyện online.
     * @param chapterIndex Chỉ số chương cuối đã hoàn tất.
     */
    @Synchronized
    fun saveIndexedChapter(novelId: String, chapterIndex: Int) {
        val key = keyFor(novelId)
        preferences.edit()
            .putString(key + PHASE_SUFFIX, PHASE_INDEX)
            .putInt(key + CHAPTER_SUFFIX, chapterIndex)
            .apply()
    }

    /**
     * Xóa checkpoint sau khi job hoàn tất hoặc file bị xác định là hỏng.
     *
     * @param novelId Định danh truyện online.
     */
    @Synchronized
    fun clear(novelId: String) {
        val key = keyFor(novelId)
        preferences.edit()
            .remove(key + PHASE_SUFFIX)
            .remove(key + CHAPTER_SUFFIX)
            .apply()
    }

    private fun keyFor(novelId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(novelId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return "novel_" + digest
    }

    data class Checkpoint(
        val phase: String,
        val lastIndexedChapter: Int
    )

    companion object {
        const val PHASE_DOWNLOAD = "DOWNLOAD"
        const val PHASE_IMPORT = "IMPORT"
        const val PHASE_INDEX = "INDEX"

        private const val PREFERENCES_NAME = "online_novel_download_checkpoints"
        private const val PHASE_SUFFIX = "_phase"
        private const val CHAPTER_SUFFIX = "_chapter"
    }
}