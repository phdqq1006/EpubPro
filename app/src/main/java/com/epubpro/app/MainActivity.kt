package com.epubpro.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.rememberNavController
import com.epubpro.app.navigation.AppNavHost
import com.epubpro.core.designsystem.theme.EpubProTheme
import com.epubpro.core.reader.tts.TtsOpenBookContract
import com.epubpro.core.reader.tts.TtsOpenBookRequest
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.storage.TtsBubblePreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bubblePreferencesManager: TtsBubblePreferencesManager

    private val intentViewModel: MainIntentViewModel by viewModels()
    private var bubbleStartupRestored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchOpenBookRequest(intent)

        enableEdgeToEdge()
        setContent {
            EpubProTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    openBookRequests = intentViewModel.openBookRequests
                )
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!bubbleStartupRestored) {
            bubbleStartupRestored = true
            restoreEnabledAudioBubble()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchOpenBookRequest(intent)
    }

    private fun dispatchOpenBookRequest(intent: Intent?) {
        val request = TtsOpenBookContract.parse(intent) ?: return
        intentViewModel.dispatch(request)
        intent?.apply {
            action = null
            removeExtra(TtsOpenBookContract.EXTRA_BOOK_ID)
            removeExtra(TtsOpenBookContract.EXTRA_CHAPTER_INDEX)
            removeExtra(TtsOpenBookContract.EXTRA_OPEN_TTS_PLAYER)
        }
    }

    private fun restoreEnabledAudioBubble() {
        if (
            bubblePreferencesManager.getPreferences().enabled &&
            Settings.canDrawOverlays(this)
        ) {
            TtsService.syncBubbleState(this, enabled = true)
        }
    }

}

internal class MainIntentViewModel : ViewModel() {
    private val requestChannel = Channel<TtsOpenBookRequest>(Channel.BUFFERED)
    val openBookRequests: Flow<TtsOpenBookRequest> = requestChannel.receiveAsFlow()

    fun dispatch(request: TtsOpenBookRequest) {
        requestChannel.trySend(request)
    }
}
