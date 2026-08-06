package com.epubpro.core.reader.tts

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.epubpro.core.storage.TtsPreferencesManager
import com.epubpro.domain.model.SleepTimerOption
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsPlayerState
import com.epubpro.domain.model.TtsSettings
import com.epubpro.core.designsystem.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TtsService : Service() {

    @Inject
    lateinit var preferencesManager: TtsPreferencesManager

    @Inject
    lateinit var piperTtsEngineWrapper: PiperTtsEngineWrapper

    private val binder = TtsBinder()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var nativeTtsEngine: AndroidNativeTtsEngine
    private lateinit var currentEngine: TtsEngine
    private lateinit var mediaSessionManager: TtsMediaSessionManager

    private var chunks: List<TtsChunk> = emptyList()
    private var currentIndex: Int = 0
    private var bookTitle: String = ""
    private var author: String = "EpubPro Reader"

    private var sleepTimerJob: Job? = null
    private var remainingSleepSeconds: Int = 0
    private var playbackGeneration: Long = 0

    override fun onCreate() {
        super.onCreate()
        bookTitle = getString(R.string.tts_default_book_title)
        nativeTtsEngine = AndroidNativeTtsEngine(applicationContext)
        mediaSessionManager = TtsMediaSessionManager(
            context = applicationContext,
            onPlay = { resume() },
            onPause = { pause() },
            onSkipNext = { nextChunk() },
            onSkipPrevious = { previousChunk() },
            onStop = { stopSelf() }
        )

        val settings = preferencesManager.getSettings()
        currentEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine
        nativeTtsEngine.setSpeed(settings.speed)
        nativeTtsEngine.setPitch(settings.pitch)
        settings.voiceId?.takeUnless { settings.isAiVoice }?.let(nativeTtsEngine::setVoice)
        piperTtsEngineWrapper.setSpeed(settings.speed)
        piperTtsEngineWrapper.setPitch(settings.pitch)
        settings.voiceId?.takeIf { settings.isAiVoice }?.let(piperTtsEngineWrapper::setVoice)


        nativeTtsEngine.initialize(
            onReady = {
                if (!settings.isAiVoice) {
                    nativeTtsEngine.setSpeed(settings.speed)
                    nativeTtsEngine.setPitch(settings.pitch)
                    settings.voiceId?.let { nativeTtsEngine.setVoice(it) }
                }
            },
            onError = { error ->
                if (!settings.isAiVoice) _playerState.value = TtsPlayerState.Error(error)
            }
        )
        
        piperTtsEngineWrapper.initialize(
            onReady = {
                if (settings.isAiVoice) {
                    piperTtsEngineWrapper.setSpeed(settings.speed)
                    piperTtsEngineWrapper.setPitch(settings.pitch)
                    settings.voiceId?.let { piperTtsEngineWrapper.setVoice(it) }
                }
            },
            onError = { error ->
                if (settings.isAiVoice) _playerState.value = TtsPlayerState.Error(error)
            }
        )
        
        serviceScope.launch {
            preferencesManager.settingsFlow.collect { newSettings ->
                updateSettings(newSettings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TtsMediaSessionManager.ACTION_PLAY -> resume()
            TtsMediaSessionManager.ACTION_PAUSE -> pause()
            TtsMediaSessionManager.ACTION_NEXT -> nextChunk()
            TtsMediaSessionManager.ACTION_PREV -> previousChunk()
            TtsMediaSessionManager.ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    fun loadContent(title: String, bookAuthor: String, parsedChunks: List<TtsChunk>, startIndex: Int = 0) {
        playbackGeneration++
        currentEngine.stop()
        this.bookTitle = title
        this.author = bookAuthor
        this.chunks = parsedChunks
        this.currentIndex = startIndex.coerceIn(0, (parsedChunks.size - 1).coerceAtLeast(0))

        if (chunks.isEmpty()) {
            _playerState.value = TtsPlayerState.Idle
            return
        }
        playCurrentChunk()
    }

    fun playCurrentChunk() {
        if (chunks.isEmpty() || currentIndex !in chunks.indices) return
        val currentChunk = chunks[currentIndex]

        val expectedIndex = currentIndex
        val playbackId = ++playbackGeneration
        _playerState.value = TtsPlayerState.Playing(
            currentChunkIndex = currentIndex,
            totalChunks = chunks.size,
            currentChunk = currentChunk
        )

        mediaSessionManager.updateMetadata(bookTitle, author, currentChunk.text)
        mediaSessionManager.updatePlaybackState(isPlaying = true)

        val notification = mediaSessionManager.buildNotification(
            bookTitle = bookTitle,
            currentSnippet = currentChunk.text,
            isPlaying = true,
            openIntent = null
        )
        startForeground(TtsMediaSessionManager.NOTIFICATION_ID, notification)

        val settings = preferencesManager.getSettings()
        currentEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine

        currentEngine.speak(
            chunk = currentChunk,
            onChunkStart = { _ -> },
            onChunkDone = { completedChunkId ->
                serviceScope.launch {
                    if (playbackId != playbackGeneration ||
                        currentIndex != expectedIndex ||
                        currentChunk.id != completedChunkId
                    ) return@launch

                    if (currentIndex < chunks.size - 1) {
                        currentIndex++
                        playCurrentChunk()
                    } else {
                        _playerState.value = TtsPlayerState.Idle
                        mediaSessionManager.updatePlaybackState(isPlaying = false)
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
            }
        )
    }

    fun pause() {
        playbackGeneration++
        currentEngine.pause()
        if (chunks.isNotEmpty() && currentIndex in chunks.indices) {
            val currentChunk = chunks[currentIndex]
            _playerState.value = TtsPlayerState.Paused(
                currentChunkIndex = currentIndex,
                totalChunks = chunks.size,
                currentChunk = currentChunk
            )
            mediaSessionManager.updatePlaybackState(isPlaying = false)

            val notification = mediaSessionManager.buildNotification(
                bookTitle = bookTitle,
                currentSnippet = currentChunk.text,
                isPlaying = false,
                openIntent = null
            )
            startForeground(TtsMediaSessionManager.NOTIFICATION_ID, notification)
        }
    }

    fun stop() {
        playbackGeneration++
        currentEngine.stop()
        _playerState.value = TtsPlayerState.Idle
        mediaSessionManager.updatePlaybackState(isPlaying = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resume() {
        if (_playerState.value is TtsPlayerState.Paused || _playerState.value is TtsPlayerState.Idle) {
            playCurrentChunk()
        }
    }

    private fun moveToChunk(index: Int) {
        if (index !in chunks.indices) return
        playbackGeneration++
        currentEngine.stop()
        currentIndex = index
        playCurrentChunk()
    }

    fun nextChunk() = moveToChunk(currentIndex + 1)

    fun previousChunk() = moveToChunk(currentIndex - 1)

    fun seekToChunk(index: Int) = moveToChunk(index)

    fun getAvailableVoices(language: String = "vi"): List<com.epubpro.domain.model.TtsVoice> {
        return currentEngine.getAvailableVoices(language)
    }

    fun speakPreview(sampleText: String = "Xin chào, đây là giọng đọc thử nghiệm của ứng dụng EpubPro.", settings: TtsSettings) {
        val testEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine
        testEngine.setSpeed(settings.speed)
        testEngine.setPitch(settings.pitch)
        settings.voiceId?.let { testEngine.setVoice(it) }
        testEngine.speak(
            chunk = TtsChunk(id = 9999, paragraphIndex = 0, text = sampleText),
            onChunkStart = {},
            onChunkDone = {}
        )
    }

    fun updateSettings(settings: TtsSettings) {
        val nextEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine
        if (currentEngine !== nextEngine) {
            currentEngine.stop()
            currentEngine = nextEngine
        }
        currentEngine.setSpeed(settings.speed)
        currentEngine.setPitch(settings.pitch)
        settings.voiceId?.let { currentEngine.setVoice(it) }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF) {
            remainingSleepSeconds = 0
            return
        }
        val minutes = option.minutes
        if (minutes > 0) {
            remainingSleepSeconds = minutes * 60
            sleepTimerJob = serviceScope.launch {
                while (remainingSleepSeconds > 0) {
                    delay(1000)
                    remainingSleepSeconds--
                }
                // Stop service on timer expiry
                pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        sleepTimerJob?.cancel()
        nativeTtsEngine.shutdown()
        piperTtsEngineWrapper.shutdown()
        serviceJob.cancel()
        mediaSessionManager.release()
    }

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    companion object {
        private val _playerState = MutableStateFlow<TtsPlayerState>(TtsPlayerState.Idle)
        val playerState: StateFlow<TtsPlayerState> = _playerState.asStateFlow()
    }
}
