package com.epubpro.core.reader.tts.bubble

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hides sensitive overlay content while the keyguard is active. */
class DeviceLockTracker(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
    private val mutableDeviceLocked = MutableStateFlow(queryLocked())
    private var registered = false

    val deviceLocked: StateFlow<Boolean> = mutableDeviceLocked.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mutableDeviceLocked.value = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> true
                else -> queryLocked()
            }
        }
    }

    init {
        start()
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        refresh()
    }

    fun refresh() {
        mutableDeviceLocked.value = queryLocked()
    }

    fun release() {
        if (!registered) return
        registered = false
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    override fun close() = release()

    private fun queryLocked(): Boolean = keyguardManager?.isDeviceLocked ?: false
}
