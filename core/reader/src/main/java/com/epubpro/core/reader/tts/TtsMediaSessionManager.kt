package com.epubpro.core.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.epubpro.core.designsystem.R

class TtsMediaSessionManager(
    private val context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit,
    private val onStop: () -> Unit
) {

    val mediaSession: MediaSession = MediaSession(context, "EpubProTtsSession").apply {
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { onPlay() }
            override fun onPause() { onPause() }
            override fun onSkipToNext() { onSkipNext() }
            override fun onSkipToPrevious() { onSkipPrevious() }
            override fun onStop() { onStop() }
        })
        isActive = true
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tts_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.tts_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateMetadata(bookTitle: String, author: String, currentSnippet: String) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, bookTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, author)
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, currentSnippet)
            .build()
        mediaSession.setMetadata(metadata)
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    fun buildNotification(
        bookTitle: String,
        currentSnippet: String,
        isPlaying: Boolean,
        openIntent: PendingIntent?
    ): Notification {
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) context.getString(R.string.tts_action_pause) else context.getString(R.string.tts_action_play)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(bookTitle)
            .setContentText(currentSnippet)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    context.getString(R.string.tts_action_prev),
                    createPendingIntent(ACTION_PREV)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    playPauseIcon,
                    playPauseTitle,
                    createPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    context.getString(R.string.tts_action_next),
                    createPendingIntent(ACTION_NEXT)
                )
            )
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(MediaSessionCompat.Token.fromToken(mediaSession.sessionToken))
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, TtsService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }

    companion object {
        const val CHANNEL_ID = "tts_service_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_PLAY = "com.epubpro.tts.ACTION_PLAY"
        const val ACTION_PAUSE = "com.epubpro.tts.ACTION_PAUSE"
        const val ACTION_NEXT = "com.epubpro.tts.ACTION_NEXT"
        const val ACTION_PREV = "com.epubpro.tts.ACTION_PREV"
        const val ACTION_STOP = "com.epubpro.tts.ACTION_STOP"
    }
}
