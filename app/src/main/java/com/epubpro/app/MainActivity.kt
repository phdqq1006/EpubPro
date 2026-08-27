package com.epubpro.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.epubpro.app.navigation.AppNavHost
import com.epubpro.core.designsystem.theme.EpubProTheme
import com.epubpro.core.reader.tts.TtsOpenBookContract
import com.epubpro.core.reader.tts.TtsOpenBookRequest
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsWidgetContract
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.core.storage.TtsBubblePreferencesManager
import com.epubpro.domain.repository.BookRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var bubblePreferencesManager: TtsBubblePreferencesManager

    @Inject
    lateinit var readerPreferencesManager: ReaderPreferencesManager

    @Inject
    lateinit var bookRepository: BookRepository

    private val intentViewModel: MainIntentViewModel by viewModels()
    private var bubbleStartupRestored = false
    private var hasDispatchedStartupBook = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasExplicitOpenBook = dispatchOpenBookRequest(intent)
        val hasExplicitOpenLibrary = dispatchOpenLibraryRequest(intent)

        if (savedInstanceState == null && !hasExplicitOpenBook && !hasExplicitOpenLibrary) {
            checkAndAutoResumeLastBook()
        }

        enableEdgeToEdge()
        setContent {
            EpubProTheme {
                val navController = rememberNavController()
                AppNavHost(
                    navController = navController,
                    openBookRequests = intentViewModel.openBookRequests,
                    openLibraryRequests = intentViewModel.openLibraryRequests
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
        dispatchOpenLibraryRequest(intent)
    }

    /**
     * Kiểm tra cấu hình và tự động mở cuốn sách vừa đọc gần nhất khi khởi động ứng dụng mới.
     */
    private fun checkAndAutoResumeLastBook() {
        if (hasDispatchedStartupBook) return
        val settings = readerPreferencesManager.getSettings()
        if (!settings.autoResumeLastBookOnStartup) return

        lifecycleScope.launch {
            val latestBook = bookRepository.getLatestReadBook() ?: return@launch
            val progress = bookRepository.getReadingProgressDirect(latestBook.id)
            val chapterIndex = progress?.chapterIndex ?: 0
            hasDispatchedStartupBook = true
            intentViewModel.dispatch(
                TtsOpenBookRequest(
                    bookId = latestBook.id,
                    chapterIndex = chapterIndex,
                    openTtsPlayer = false
                )
            )
        }
    }

    /**
     * Phân tích và chuyển tiếp yêu cầu mở sách từ Intent.
     *
     * @param intent Intent nhận được từ hệ thống hoặc widget/notification.
     * @return true nếu intent chứa yêu cầu mở sách hợp lệ, ngược lại false.
     */
    private fun dispatchOpenBookRequest(intent: Intent?): Boolean {
        val request = TtsOpenBookContract.parse(intent) ?: return false
        intentViewModel.dispatch(request)
        intent?.apply {
            action = null
            removeExtra(TtsOpenBookContract.EXTRA_BOOK_ID)
            removeExtra(TtsOpenBookContract.EXTRA_CHAPTER_INDEX)
            removeExtra(TtsOpenBookContract.EXTRA_OPEN_TTS_PLAYER)
        }
        return true
    }

    /**
     * Phân tích và chuyển tiếp yêu cầu mở màn hình kệ sách từ Intent.
     *
     * @param intent Intent nhận được từ hệ thống.
     * @return true nếu intent chứa action mở kệ sách, ngược lại false.
     */
    private fun dispatchOpenLibraryRequest(intent: Intent?): Boolean {
        if (intent?.action != TtsWidgetContract.ACTION_OPEN_LIBRARY) return false
        intentViewModel.dispatchOpenLibrary()
        intent.action = null
        return true
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
    private val libraryChannel = Channel<Unit>(Channel.BUFFERED)
    val openBookRequests: Flow<TtsOpenBookRequest> = requestChannel.receiveAsFlow()
    val openLibraryRequests: Flow<Unit> = libraryChannel.receiveAsFlow()

    fun dispatch(request: TtsOpenBookRequest) {
        requestChannel.trySend(request)
    }

    fun dispatchOpenLibrary() {
        libraryChannel.trySend(Unit)
    }
}
