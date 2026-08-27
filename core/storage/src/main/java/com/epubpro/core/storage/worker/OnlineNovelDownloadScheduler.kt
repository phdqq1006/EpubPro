package com.epubpro.core.storage.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lập lịch và quan sát job tải EPUB online độc lập theo từng novelId.
 *
 * @param context Context ứng dụng dùng để truy cập WorkManager.
 */
@Singleton
class OnlineNovelDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val enqueueMutex = Mutex()
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Enqueue một job tải truyện; nếu job cùng novelId đang chạy thì trả lại job hiện tại.
     *
     * @param novelId Định danh truyện online.
     * @param title Tiêu đề hiển thị.
     * @param author Tác giả để giữ metadata đầu vào cho worker.
     * @param coverUrl URL ảnh bìa từ danh mục truyện online.
     * @param forceUpdate True nếu cần tải lại file EPUB dù bản cũ đã tồn tại.
     * @return UUID của WorkRequest đang xử lý.
     */
    suspend fun enqueue(
        novelId: String,
        title: String,
        author: String,
        coverUrl: String? = null,
        forceUpdate: Boolean = false
    ): UUID = withContext(Dispatchers.IO) {
        enqueueMutex.withLock {
            val uniqueName = uniqueWorkName(novelId)
            val existing = workManager.getWorkInfosForUniqueWork(uniqueName).get()
                .firstOrNull { !it.state.isFinished }
            if (existing != null) return@withContext existing.id

            val request = OneTimeWorkRequestBuilder<OnlineNovelDownloadWorker>()
                .setInputData(
                    workDataOf(
                        OnlineNovelDownloadWorker.KEY_NOVEL_ID to novelId,
                        OnlineNovelDownloadWorker.KEY_TITLE to title,
                        OnlineNovelDownloadWorker.KEY_AUTHOR to author,
                        OnlineNovelDownloadWorker.KEY_COVER_URL to coverUrl,
                        OnlineNovelDownloadWorker.KEY_FORCE_UPDATE to forceUpdate
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .addTag(TAG)
                .addTag(tagForNovel(novelId))
                .build()

            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
            request.id
        }
    }

    /**
     * Quan sát tất cả job tải truyện online để hiển thị tiến độ tổng hợp.
     *
     * @return Flow danh sách WorkInfo của các job.
     */
    fun observeAll(): Flow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(TAG)

    /**
     * Quan sát job của một truyện cụ thể.
     *
     * @param novelId Định danh truyện online.
     * @return Flow danh sách WorkInfo của truyện.
     */
    fun observeNovel(novelId: String): Flow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(tagForNovel(novelId))

    /**
     * Hủy job của một truyện nhưng giữ nguyên file .part để lần enqueue sau tiếp tục.
     *
     * @param novelId Định danh truyện online.
     */
    fun cancel(novelId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(novelId))
    }

    private fun uniqueWorkName(novelId: String): String =
        "online-novel-download-" + digest(novelId)

    private fun tagForNovel(novelId: String): String =
        NOVEL_TAG_PREFIX + novelId

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)

    companion object {
        const val TAG = "online-novel-download"
        const val NOVEL_TAG_PREFIX = "online-novel-id:"
    }
}
