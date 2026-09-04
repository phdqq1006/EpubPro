package com.epubpro.core.storage.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.epubpro.domain.sync.SyncCoordinator
import com.epubpro.domain.sync.SyncOptions
import com.epubpro.domain.sync.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Worker chạy backup/restore với constraint mạng do scheduler đặt. */
@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: SyncCoordinator
) : CoroutineWorker(appContext, workerParams) {

    /** Chạy một phiên sync background và trả policy retry theo domain status. */
    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val action = inputData.getString(KEY_ACTION) ?: ACTION_CHECK
        val result = when (action) {
            ACTION_BACKUP -> coordinator.backup(SyncOptions())
            ACTION_RESTORE -> coordinator.restore(SyncOptions())
            else -> coordinator.check().let {
                com.epubpro.domain.sync.SyncResult(it.status, conflictKeys = it.comparison.blockingKeys, message = it.message)
            }
        }
        return when (result.status) {
            SyncStatus.SYNCED, SyncStatus.READY, SyncStatus.CHANGES_PENDING, SyncStatus.DRIVE_PENDING -> Result.success(output(result.message))
            SyncStatus.AUTH_REQUIRED, SyncStatus.CONFLICT -> Result.failure(output(result.message))
            else -> if (runAttemptCount < 3) Result.retry() else Result.failure(output(result.message))
        }
    }

    private fun output(message: String?): Data = Data.Builder().putString("message", message).build()

    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(com.epubpro.core.designsystem.R.string.sync_notification_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(com.epubpro.core.designsystem.R.string.sync_notification_title))
            .setContentText(applicationContext.getString(com.epubpro.core.designsystem.R.string.sync_notification_running))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_CHECK = "check"
        const val ACTION_BACKUP = "backup"
        const val ACTION_RESTORE = "restore"
        private const val CHANNEL_ID = "epub_sync"
        private const val NOTIFICATION_ID = 4101
    }
}
