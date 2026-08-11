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
import com.epubpro.core.reader.tts.TtsOpenBookContract
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsWidgetContract
import com.epubpro.core.storage.TtsPlaybackSnapshotStore
import com.epubpro.core.storage.TtsWidgetState
import com.epubpro.core.storage.TtsWidgetStateStore
import kotlin.math.roundToInt

class TtsReadingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = TtsWidgetStateStore(context).getState()
        appWidgetIds.forEach { updateWidget(context, manager, it, state) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == TtsWidgetContract.ACTION_STATE_CHANGED) {
            updateAll(context)
            TtsAudioWidgetProvider.updateAll(context)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TtsReadingWidgetProvider::class.java)
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
            val views = RemoteViews(context.packageName, R.layout.tts_reading_widget)

            // 1. Header Information
            views.setTextViewText(
                R.id.tts_reading_book_title,
                state.bookTitle.ifBlank { context.getString(R.string.tts_widget_default_title) }
            )
            views.setTextViewText(
                R.id.tts_reading_chapter_title,
                state.chapterTitle.ifBlank { context.getString(R.string.tts_widget_idle) }
            )

            val coverBitmap = decodeScaledCover(state.coverPath)
            if (coverBitmap != null) {
                views.setImageViewBitmap(R.id.tts_reading_cover, coverBitmap)
            } else {
                views.setImageViewResource(R.id.tts_reading_cover, R.drawable.ic_widget_book_placeholder)
            }

            // 2. Reader Content Text
            val contentText = state.paragraphText.take(800).ifBlank {
                if (state.hasSnapshot) "Bấm lật trang hoặc mở sách để đọc..." else "Chưa có sách được mở trong thư viện."
            }
            views.setTextViewText(R.id.tts_reading_content_text, contentText)

            // 3. Footer Progress Info
            val percent = (state.normalizedProgress * 100f).roundToInt()
            val progressText = if (state.totalParagraphs > 0) {
                "$percent% • Đoạn ${state.paragraphIndex + 1}/${state.totalParagraphs}"
            } else if (state.hasSnapshot) {
                "$percent%"
            } else {
                ""
            }
            views.setTextViewText(R.id.tts_reading_progress_info, progressText)

            views.setProgressBar(
                R.id.tts_reading_progress_bar,
                100,
                percent,
                false
            )

            // 4. Intent Bindings
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            // Open App Intents (Header Bar & Open App Button)
            val openAppIntent = openAppIntent(context, flags)
            views.setOnClickPendingIntent(R.id.tts_reading_header, openAppIntent)
            views.setOnClickPendingIntent(R.id.tts_reading_open_app, openAppIntent)

            // Touch Zones: Right Touch Zone -> Next, Left Touch Zone -> Prev
            views.setOnClickPendingIntent(
                R.id.tts_reading_touch_right,
                serviceIntent(context, TtsService.ACTION_WIDGET_READING_NEXT, flags)
            )
            views.setOnClickPendingIntent(
                R.id.tts_reading_touch_left,
                serviceIntent(context, TtsService.ACTION_WIDGET_READING_PREVIOUS, flags)
            )

            // Chapter Buttons
            views.setOnClickPendingIntent(
                R.id.tts_reading_prev_chapter,
                serviceIntent(context, TtsService.ACTION_WIDGET_READING_PREVIOUS, flags)
            )
            views.setOnClickPendingIntent(
                R.id.tts_reading_next_chapter,
                serviceIntent(context, TtsService.ACTION_WIDGET_READING_NEXT, flags)
            )

            views.setBoolean(R.id.tts_reading_prev_chapter, "setEnabled", state.hasSnapshot)
            views.setBoolean(R.id.tts_reading_next_chapter, "setEnabled", state.hasSnapshot)

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun decodeScaledCover(coverPath: String?): android.graphics.Bitmap? {
            val path = coverPath?.takeIf { it.isNotBlank() } ?: return null
            val file = java.io.File(path)
            if (!file.isFile) return null
            return runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sampleSize = 1
                val targetPx = 96
                while (bounds.outWidth / sampleSize > targetPx || bounds.outHeight / sampleSize > targetPx) {
                    sampleSize *= 2
                }
                android.graphics.BitmapFactory.decodeFile(
                    file.absolutePath,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            }.getOrNull()
        }

        private fun openAppIntent(context: Context, flags: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val snapshot = TtsPlaybackSnapshotStore(context).getSnapshot()
            if (snapshot != null) {
                TtsOpenBookContract.configureIntent(
                    intent = intent,
                    bookId = snapshot.bookId,
                    chapterIndex = snapshot.chapterIndex,
                    openTtsPlayer = false
                )
            } else {
                intent.action = TtsWidgetContract.ACTION_OPEN_LIBRARY
            }
            return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, flags)
        }

        private fun serviceIntent(context: Context, action: String, flags: Int): PendingIntent {
            val intent = Intent(context, TtsService::class.java).setAction(action)
            return PendingIntent.getForegroundService(context, action.hashCode(), intent, flags)
        }

        private const val REQUEST_OPEN_APP = 4200
    }
}
