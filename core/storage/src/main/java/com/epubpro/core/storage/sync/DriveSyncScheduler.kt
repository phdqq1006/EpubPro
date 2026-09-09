package com.epubpro.core.storage.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Đăng ký unique background work để không chạy chồng nhiều phiên sync. */
@Singleton
class DriveSyncScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Đưa backup vào unique work `epub-sync` với policy KEEP.
     */
    fun enqueueBackup() = enqueue(DriveSyncWorker.ACTION_BACKUP)

    /**
     * Đưa restore vào unique work `epub-sync` với policy KEEP.
     */
    fun enqueueRestore() = enqueue(DriveSyncWorker.ACTION_RESTORE)

    private fun enqueue(action: String) {
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(DriveSyncWorker.KEY_ACTION to action))
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        /** Tên unique work public cho app và debug tooling. */
        const val UNIQUE_WORK_NAME = "epub-sync"
    }
}
