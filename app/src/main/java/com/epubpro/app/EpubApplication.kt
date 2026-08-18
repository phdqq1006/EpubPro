package com.epubpro.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Lớp Application chính của ứng dụng EpubPro, khởi tạo Hilt Dependency Injection và cấu hình WorkManager.
 */
@HiltAndroidApp
class EpubApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Cung cấp cấu hình tùy chỉnh cho WorkManager với HiltWorkerFactory để hỗ trợ Dependency Injection trong Worker.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
