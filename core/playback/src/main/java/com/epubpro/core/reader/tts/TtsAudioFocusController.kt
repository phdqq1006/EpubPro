package com.epubpro.core.reader.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class TtsAudioFocusController(
    context: Context,
    private val onFocusLost: (shouldAutoResume: Boolean) -> Unit,
    private val onFocusGained: () -> Unit,
    private val onBecomingNoisy: () -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var receiverRegistered = false
    private var focusHeld = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusGained()
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusHeld = false
                unregisterNoisyReceiver()
                onFocusLost(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusLost(true)
        }
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
        .setAcceptsDelayedFocusGain(false)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onBecomingNoisy()
            }
        }
    }

    fun requestFocus(): Boolean {
        if (focusHeld) return true
        focusHeld = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusHeld) registerNoisyReceiver()
        return focusHeld
    }

    fun abandonFocus() {
        if (focusHeld) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
        unregisterNoisyReceiver()
    }

    private fun registerNoisyReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        receiverRegistered = false
    }
}
