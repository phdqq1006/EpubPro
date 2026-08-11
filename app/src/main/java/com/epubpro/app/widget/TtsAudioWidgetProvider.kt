package com.epubpro.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.epubpro.app.MainActivity
import com.epubpro.app.R
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsWidgetContract
import com.epubpro.core.storage.TtsWidgetPlaybackStatus
import com.epubpro.core.storage.TtsWidgetState
import com.epubpro.core.storage.TtsWidgetStateStore
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class TtsAudioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = TtsWidgetStateStore(context).getState()
        appWidgetIds.forEach { updateWidget(context, manager, it, state) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TtsWidgetContract.ACTION_STATE_CHANGED) updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TtsAudioWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val state = TtsWidgetStateStore(context).getState()
            ids.forEach { updateWidget(context, manager, it, state) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            state: TtsWidgetState
        ) {
            val views = RemoteViews(context.packageName, R.layout.tts_audio_widget)
            views.setTextViewText(
                R.id.tts_widget_title,
                state.bookTitle.ifBlank { context.getString(R.string.tts_widget_default_title) }
            )
            views.setTextViewText(R.id.tts_widget_status, statusText(context, state.playbackStatus))
            views.setProgressBar(
                R.id.tts_widget_progress,
                100,
                (state.normalizedProgress * 100f).roundToInt(),
                state.playbackStatus == TtsWidgetPlaybackStatus.PREPARING
            )
            val isPlaying = state.playbackStatus == TtsWidgetPlaybackStatus.PLAYING ||
                state.playbackStatus == TtsWidgetPlaybackStatus.PREPARING
            views.setImageViewResource(
                R.id.tts_widget_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            val serviceFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.tts_widget_play_pause, playIntent(context, state, serviceFlags))
            views.setOnClickPendingIntent(
                R.id.tts_widget_previous,
                serviceIntent(context, TtsService.ACTION_WIDGET_PREVIOUS, serviceFlags)
            )
            views.setOnClickPendingIntent(
                R.id.tts_widget_next,
                serviceIntent(context, TtsService.ACTION_WIDGET_NEXT, serviceFlags)
            )
            views.setBoolean(R.id.tts_widget_previous, "setEnabled", state.hasSnapshot)
            views.setBoolean(R.id.tts_widget_next, "setEnabled", state.hasSnapshot)
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun playIntent(context: Context, state: TtsWidgetState, flags: Int): PendingIntent {
            return if (state.hasSnapshot) {
                serviceIntent(context, TtsService.ACTION_WIDGET_PLAY_PAUSE, flags)
            } else {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = TtsWidgetContract.ACTION_OPEN_LIBRARY
                    this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                PendingIntent.getActivity(context, REQUEST_LIBRARY, intent, flags)
            }
        }

        private fun serviceIntent(context: Context, action: String, flags: Int): PendingIntent {
            val intent = Intent(context, TtsService::class.java).setAction(action)
            return PendingIntent.getForegroundService(context, action.hashCode(), intent, flags)
        }

        private fun statusText(context: Context, status: TtsWidgetPlaybackStatus): String =
            context.getString(
                when (status) {
                    TtsWidgetPlaybackStatus.IDLE -> R.string.tts_widget_idle
                    TtsWidgetPlaybackStatus.PREPARING -> R.string.tts_widget_preparing
                    TtsWidgetPlaybackStatus.PLAYING -> R.string.tts_widget_playing
                    TtsWidgetPlaybackStatus.PAUSED -> R.string.tts_widget_paused
                    TtsWidgetPlaybackStatus.ERROR -> R.string.tts_widget_error
                    TtsWidgetPlaybackStatus.COMPLETED -> R.string.tts_widget_completed
                }
            )

        private const val REQUEST_LIBRARY = 4100
    }
}
