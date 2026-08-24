package com.epubpro.core.storage.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.epubpro.core.designsystem.R
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.DownloadState
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Worker chạy foreground cho một truyện online, gồm tải EPUB, import Room và lập chỉ mục FTS.
 *
 * Mỗi novelId có một Worker riêng nên nhiều truyện có thể tải đồng thời. File .part và
 * checkpoint được giữ lại khi coroutine bị hủy để lần chạy sau tiếp tục từ dữ liệu đã có.
 */
@HiltWorker
class OnlineNovelDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val onlineNovelRepository: OnlineNovelRepository,
    private val storageManager: EpubStorageManager,
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val epubEngine: EpubEngine,
    private val checkpointStore: OnlineNovelDownloadCheckpointStore
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Thực thi pipeline tải, import và lập chỉ mục của một truyện online.
     *
     * @return Kết quả WorkManager, retry nếu lỗi tạm thời.
     */
    override suspend fun doWork(): Result {
        val novelId = inputData.getString(KEY_NOVEL_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: novelId
        val files = storageManager.getOnlineDownloadFiles(novelId)
        val notificationId = notificationIdFor(novelId)

        createNotificationChannel()
        updateForeground(title, appContext.getString(R.string.online_download_notification_starting), 0, notificationId)

        try {
            var completedFile = files.completed
            if (!completedFile.isFile) {
                checkpointStore.savePhase(novelId, OnlineNovelDownloadCheckpointStore.PHASE_DOWNLOAD)
                var terminalError: DownloadState.Error? = null
                onlineNovelRepository.downloadEpub(
                    novelId = novelId,
                    saveFileName = title + ".epub",
                    resumeFilePath = files.temporary.absolutePath
                ).collect { state ->
                    when (state) {
                        is DownloadState.Downloading -> {
                            setProgress(
                                workDataOf(
                                    KEY_NOVEL_ID to novelId,
                                    KEY_PHASE to PHASE_DOWNLOAD,
                                    KEY_PROGRESS to state.progressPercent,
                                    KEY_BYTES_DOWNLOADED to state.bytesDownloaded,
                                    KEY_TOTAL_BYTES to (state.totalBytes ?: -1L)
                                )
                            )
                            updateForeground(
                                title = title,
                                step = appContext.getString(
                                    R.string.online_download_notification_downloading,
                                    state.progressPercent
                                ),
                                progress = state.progressPercent,
                                notificationId = notificationId
                            )
                        }

                        is DownloadState.Success -> {
                            completedFile = storageManager.promoteOnlineDownload(files)
                        }

                        is DownloadState.Error -> terminalError = state
                        DownloadState.Idle -> Unit
                    }
                }

                terminalError?.let { error ->
                    if (error.isRetryable) {
                        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                            return Result.retry()
                        }
                        // Giữ .part và checkpoint để lần enqueue thủ công tiếp tục từ byte đã có.
                        showErrorNotification(title, error.message, notificationId)
                        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to error.message))
                    }
                    showErrorNotification(title, error.message, notificationId)
                    storageManager.deleteOnlineDownloadFiles(novelId)
                    checkpointStore.clear(novelId)
                    return Result.failure(workDataOf(KEY_ERROR_MESSAGE to error.message))
                }
            }

            checkpointStore.savePhase(novelId, OnlineNovelDownloadCheckpointStore.PHASE_IMPORT)
            val book = try {
                epubEngine.parseEpubMetadataStrict(completedFile).copy(onlineNovelId = novelId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                storageManager.deleteOnlineDownloadFiles(novelId)
                checkpointStore.clear(novelId)
                showErrorNotification(
                    title,
                    error.message ?: "File EPUB không hợp lệ",
                    notificationId
                )
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to (error.message ?: "File EPUB không hợp lệ"))
                )
            }

            bookRepository.insertBook(book)
            checkpointStore.savePhase(novelId, OnlineNovelDownloadCheckpointStore.PHASE_INDEX)
            val checkpoint = checkpointStore.get(novelId)
            val totalChapters = book.totalChapters.coerceAtLeast(1)
            epubEngine.indexBookContentResumable(
                file = completedFile,
                bookId = book.id,
                searchRepository = searchRepository,
                startChapterIndex = (checkpoint.lastIndexedChapter + 1).coerceAtLeast(0)
            ) { lastChapterIndex ->
                checkpointStore.saveIndexedChapter(novelId, lastChapterIndex)
                val progress = ((lastChapterIndex + 1) * 100 / totalChapters).coerceIn(1, 99)
                setProgress(
                    workDataOf(
                        KEY_NOVEL_ID to novelId,
                        KEY_PHASE to PHASE_INDEX,
                        KEY_PROGRESS to progress,
                        KEY_LAST_INDEXED_CHAPTER to lastChapterIndex
                    )
                )
                updateForeground(
                    title = title,
                    step = appContext.getString(
                        R.string.online_download_notification_indexing,
                        lastChapterIndex + 1
                    ),
                    progress = progress,
                    notificationId = notificationId
                )
            }

            checkpointStore.clear(novelId)
            setProgress(
                workDataOf(
                    KEY_NOVEL_ID to novelId,
                    KEY_PHASE to PHASE_COMPLETE,
                    KEY_PROGRESS to 100,
                    KEY_FILE_PATH to completedFile.absolutePath
                )
            )
            showSuccessNotification(title, notificationId)
            return Result.success(
                workDataOf(
                    KEY_NOVEL_ID to novelId,
                    KEY_TITLE to title,
                    KEY_PROGRESS to 100,
                    KEY_FILE_PATH to completedFile.absolutePath
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                return Result.retry()
            }
            showErrorNotification(
                title,
                error.message ?: "Lỗi khi xử lý truyện offline",
                notificationId
            )
            return Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to (error.message ?: "Lỗi khi xử lý truyện offline"))
            )
        }
    }

    private suspend fun updateForeground(
        title: String,
        step: String,
        progress: Int,
        notificationId: Int
    ) {
        setForeground(createForegroundInfo(title, step, progress, notificationId))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.online_download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.online_download_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(
        title: String,
        step: String,
        progress: Int,
        notificationId: Int
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.online_download_notification_title, title))
            .setContentText(step)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createLaunchAppPendingIntent())
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showSuccessNotification(title: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.online_download_channel_name))
            .setContentText(appContext.getString(R.string.online_download_notification_success, title))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(createLaunchAppPendingIntent())
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun showErrorNotification(title: String, error: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.online_download_notification_title, title))
            .setContentText(appContext.getString(R.string.online_download_notification_failed, title, error))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(createLaunchAppPendingIntent())
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createLaunchAppPendingIntent(): PendingIntent {
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?: Intent()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(appContext, notificationIdFor(appContext.packageName), launchIntent, flags)
    }

    private fun notificationIdFor(value: String): Int =
        value.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)

    companion object {
        const val KEY_NOVEL_ID = "online_download_novel_id"
        const val KEY_TITLE = "online_download_title"
        const val KEY_AUTHOR = "online_download_author"
        const val KEY_PHASE = "online_download_phase"
        const val KEY_PROGRESS = "online_download_progress"
        const val KEY_BYTES_DOWNLOADED = "online_download_bytes_downloaded"
        const val KEY_TOTAL_BYTES = "online_download_total_bytes"
        const val KEY_LAST_INDEXED_CHAPTER = "online_download_last_indexed_chapter"
        const val KEY_FILE_PATH = "online_download_file_path"
        const val KEY_ERROR_MESSAGE = "online_download_error_message"

        const val PHASE_DOWNLOAD = "DOWNLOAD"
        const val PHASE_INDEX = "INDEX"
        const val PHASE_COMPLETE = "COMPLETE"

        private const val CHANNEL_ID = "online_novel_downloads"
        private const val MAX_RETRY_ATTEMPTS = 4
    }
}