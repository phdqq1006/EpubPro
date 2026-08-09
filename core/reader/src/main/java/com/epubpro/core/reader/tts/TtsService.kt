package com.epubpro.core.reader.tts

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.epubpro.core.reader.filter.ContentSanitizer
import com.epubpro.core.storage.ReaderPreferencesManager
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
    lateinit var readerPreferencesManager: ReaderPreferencesManager

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
    private var bookId: String = ""
    private var bookTitle: String = ""
    private var author: String = "EpubPro Reader"

    private var sleepTimerJob: Job? = null
    private var remainingSleepSeconds: Int = 0
    private var notificationProgressJob: Job? = null
    private var playbackGeneration: Long = 0
    private var estimatedChunkDurationsMs: LongArray = LongArray(0)
    private var estimatedChunkStartPositionsMs: LongArray = LongArray(0)
    private var estimatedTotalDurationMs: Long = 0L
    private var estimatedTimelineSpeed: Float = Float.NaN
    private var currentChunkStartPositionMs: Long = 0L
    private var playbackStartedAtElapsedRealtimeMs: Long? = null

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
        nativeTtsEngine.setLanguage(settings.language)
        nativeTtsEngine.setSpeed(settings.speed)
        nativeTtsEngine.setPitch(settings.pitch)
        nativeTtsEngine.setVoice(settings.voiceId.takeUnless { settings.isAiVoice })
        piperTtsEngineWrapper.setLanguage(settings.language)
        piperTtsEngineWrapper.setSpeed(settings.speed)
        piperTtsEngineWrapper.setPitch(1.0f)
        piperTtsEngineWrapper.setVoice(settings.voiceId.takeIf { settings.isAiVoice })


        nativeTtsEngine.initialize(
            onReady = {
                if (!settings.isAiVoice) {
                    nativeTtsEngine.setLanguage(settings.language)
                    nativeTtsEngine.setSpeed(settings.speed)
                    nativeTtsEngine.setPitch(settings.pitch)
                    nativeTtsEngine.setVoice(settings.voiceId)
                }
            },
            onError = { error ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    android.widget.Toast.makeText(applicationContext, "Lỗi giọng đọc: $error", android.widget.Toast.LENGTH_LONG).show()
                }
                if (currentEngine == nativeTtsEngine) stop()
            }
        )
        
        piperTtsEngineWrapper.initialize(
            onReady = {
                if (settings.isAiVoice) {
                    piperTtsEngineWrapper.setLanguage(settings.language)
                    piperTtsEngineWrapper.setSpeed(settings.speed)
                    piperTtsEngineWrapper.setPitch(1.0f)
                    piperTtsEngineWrapper.setVoice(settings.voiceId)
                }
            },
            onError = { error ->
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    val msg = if (error.contains("not downloaded")) {
                        "Giọng AI này chưa được tải. Vui lòng vào Cài đặt âm thanh để tải về!"
                    } else {
                        "Lỗi giọng đọc: $error"
                    }
                    android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_LONG).show()
                }
                if (currentEngine == piperTtsEngineWrapper) stop()
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

    fun loadContent(id: String, title: String, bookAuthor: String, parsedChunks: List<TtsChunk>, startIndex: Int = 0) {
        playbackGeneration++
        currentEngine.stop()
        this.bookId = id
        this.bookTitle = title
        this.author = bookAuthor
        this.chunks = parsedChunks
        this.currentIndex = startIndex.coerceIn(0, (parsedChunks.size - 1).coerceAtLeast(0))
        rebuildEstimatedTimeline(preferencesManager.getSettings().speed)

        if (chunks.isEmpty()) {
            _playerState.value = TtsPlayerState.Idle
            return
        }
        playCurrentChunk()
    }

    fun playCurrentChunk() {
        if (chunks.isEmpty() || currentIndex !in chunks.indices) return
        val currentChunk = chunks[currentIndex]

        var chunkToSpeak = currentChunk
        val filterPrefs = readerPreferencesManager.getFilterPreferences()
        if (filterPrefs.isFilterEnabled) {
            val sanitized = ContentSanitizer.sanitize(currentChunk.text, filterPrefs)
            if (sanitized.isBlank()) {
                // Tự động skip đoạn rỗng nếu toàn bộ từ trong đoạn đều bị lọc
                if (currentIndex < chunks.size - 1) {
                    currentIndex++
                    playCurrentChunk()
                } else {
                    _playerState.value = TtsPlayerState.Completed(bookId)
                    mediaSessionManager.updatePlaybackState(
                        isPlaying = false,
                        positionMs = currentPlaybackPositionMs()
                    )
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                return
            }
            if (sanitized != currentChunk.text) {
                chunkToSpeak = currentChunk.copy(text = sanitized)
            }
        }

        val expectedIndex = currentIndex
        val playbackId = ++playbackGeneration
        _playerState.value = TtsPlayerState.Playing(
            bookId = bookId,
            currentChunkIndex = currentIndex,
            totalChunks = chunks.size,
            currentChunk = chunkToSpeak
        )

        val settings = preferencesManager.getSettings()
        ensureEstimatedTimeline(settings.speed)
        currentChunkStartPositionMs =
            estimatedChunkStartPositionsMs.getOrElse(currentIndex) { 0L }
        playbackStartedAtElapsedRealtimeMs = null
        notificationProgressJob?.cancel()

        mediaSessionManager.updateMetadata(
            bookTitle = bookTitle,
            author = author,
            currentSnippet = chunkToSpeak.text,
            durationMs = estimatedTotalDurationMs
        )
        mediaSessionManager.updatePlaybackState(
            isPlaying = true,
            positionMs = currentChunkStartPositionMs,
            playbackSpeed = 0.0f
        )

        val notification = mediaSessionManager.buildNotification(
            bookTitle = bookTitle,
            currentSnippet = chunkToSpeak.text,
            isPlaying = true,
            openIntent = null
        )
        startForeground(TtsMediaSessionManager.NOTIFICATION_ID, notification)

        currentEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine

        currentEngine.speak(
            chunk = chunkToSpeak,
            onChunkStart = { startedChunkId ->
                serviceScope.launch {
                    if (playbackId != playbackGeneration ||
                        currentIndex != expectedIndex ||
                        chunkToSpeak.id != startedChunkId
                    ) return@launch

                    playbackStartedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    mediaSessionManager.updatePlaybackState(
                        isPlaying = true,
                        positionMs = currentChunkStartPositionMs,
                        playbackSpeed = 1.0f
                    )
                    startNotificationProgressUpdates(playbackId, expectedIndex)
                }
            },
            onChunkDone = { completedChunkId ->
                serviceScope.launch {
                    if (playbackId != playbackGeneration ||
                        currentIndex != expectedIndex ||
                        chunkToSpeak.id != completedChunkId
                    ) return@launch

                    if (currentIndex < chunks.size - 1) {
                        currentIndex++
                        playCurrentChunk()
                    } else {
                        _playerState.value = TtsPlayerState.Completed(bookId)
                        mediaSessionManager.updatePlaybackState(
                        isPlaying = false,
                        positionMs = currentPlaybackPositionMs()
                    )
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                }
            }
        )
    }

    fun pause() {
        val pausedPositionMs = currentPlaybackPositionMs()
        playbackGeneration++
        notificationProgressJob?.cancel()
        playbackStartedAtElapsedRealtimeMs = null
        currentEngine.pause()
        if (chunks.isNotEmpty() && currentIndex in chunks.indices) {
            val currentChunk = chunks[currentIndex]
            _playerState.value = TtsPlayerState.Paused(
                bookId = bookId,
                currentChunkIndex = currentIndex,
                totalChunks = chunks.size,
                currentChunk = currentChunk
            )
            mediaSessionManager.updatePlaybackState(
                isPlaying = false,
                positionMs = pausedPositionMs
            )

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
        val stoppedPositionMs = currentPlaybackPositionMs()
        playbackGeneration++
        notificationProgressJob?.cancel()
        playbackStartedAtElapsedRealtimeMs = null
        currentEngine.stop()
        _playerState.value = TtsPlayerState.Idle
        mediaSessionManager.updatePlaybackState(
            isPlaying = false,
            positionMs = stoppedPositionMs
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun resume() {
        if (_playerState.value is TtsPlayerState.Paused || _playerState.value is TtsPlayerState.Idle) {
            playCurrentChunk()
        }
    }

    private fun moveToChunk(index: Int) {
        if (index >= chunks.size) {
            _playerState.value = TtsPlayerState.Completed(bookId)
            currentEngine.stop()
            mediaSessionManager.updatePlaybackState(
                isPlaying = false,
                positionMs = currentPlaybackPositionMs()
            )
            stopForeground(STOP_FOREGROUND_DETACH)
            return
        }
        if (index !in chunks.indices) return
        playbackGeneration++
        currentEngine.stop()
        currentIndex = index
        playCurrentChunk()
    }

    fun nextChunk() = moveToChunk(currentIndex + 1)

    fun previousChunk() = moveToChunk(currentIndex - 1)

    fun seekToChunk(index: Int) = moveToChunk(index)

    fun getAvailableVoices(isAiVoice: Boolean? = null, language: String = "vi"): List<com.epubpro.domain.model.TtsVoice> {
        val engine = if (isAiVoice != null) {
            if (isAiVoice) piperTtsEngineWrapper else nativeTtsEngine
        } else {
            currentEngine
        }
        return engine.getAvailableVoices(language)
    }

    fun speakPreview(sampleText: String = "Xin chào, đây là giọng đọc thử nghiệm của ứng dụng EpubPro.", settings: TtsSettings) {
        val testEngine = if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine
        testEngine.setLanguage(settings.language)
        testEngine.setSpeed(settings.speed)
        testEngine.setPitch(if (settings.isAiVoice) 1.0f else settings.pitch)
        testEngine.setVoice(settings.voiceId)
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
        currentEngine.setLanguage(settings.language)
        currentEngine.setSpeed(settings.speed)
        currentEngine.setPitch(if (settings.isAiVoice) 1.0f else settings.pitch)
        currentEngine.setVoice(settings.voiceId)
    }

    private fun ensureEstimatedTimeline(speed: Float) {
        val normalizedSpeed = speed.coerceAtLeast(MIN_TIMELINE_SPEED)
        if (estimatedChunkDurationsMs.size != chunks.size ||
            estimatedTimelineSpeed != normalizedSpeed
        ) {
            rebuildEstimatedTimeline(normalizedSpeed)
        }
    }

    private fun rebuildEstimatedTimeline(speed: Float) {
        val normalizedSpeed = speed.coerceAtLeast(MIN_TIMELINE_SPEED)
        var accumulatedMs = 0L
        estimatedChunkStartPositionsMs = LongArray(chunks.size)
        estimatedChunkDurationsMs = LongArray(chunks.size) { index ->
            estimatedChunkStartPositionsMs[index] = accumulatedMs
            estimateChunkDurationMs(chunks[index].text, normalizedSpeed).also {
                accumulatedMs += it
            }
        }
        estimatedTotalDurationMs = accumulatedMs
        estimatedTimelineSpeed = normalizedSpeed
    }

    private fun estimateChunkDurationMs(text: String, speed: Float): Long =
        ((text.length / ESTIMATED_CHARACTERS_PER_SECOND / speed) * 1000f)
            .toLong()
            .coerceAtLeast(MIN_CHUNK_DURATION_MS)

    private fun currentPlaybackPositionMs(): Long {
        val elapsedInChunkMs = playbackStartedAtElapsedRealtimeMs
            ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
            ?: 0L
        val currentChunkDurationMs =
            estimatedChunkDurationsMs.getOrElse(currentIndex) { 0L }
        return (currentChunkStartPositionMs +
            elapsedInChunkMs.coerceAtMost(currentChunkDurationMs))
            .coerceIn(0L, estimatedTotalDurationMs.coerceAtLeast(0L))
    }

    private fun startNotificationProgressUpdates(
        playbackId: Long,
        expectedIndex: Int
    ) {
        notificationProgressJob?.cancel()
        notificationProgressJob = serviceScope.launch {
            while (playbackId == playbackGeneration &&
                currentIndex == expectedIndex &&
                _playerState.value is TtsPlayerState.Playing
            ) {
                delay(NOTIFICATION_PROGRESS_UPDATE_INTERVAL_MS)
                if (playbackId != playbackGeneration ||
                    currentIndex != expectedIndex ||
                    _playerState.value !is TtsPlayerState.Playing
                ) break

                mediaSessionManager.updatePlaybackState(
                    isPlaying = true,
                    positionMs = currentPlaybackPositionMs(),
                    playbackSpeed = 1.0f
                )
            }
        }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stop()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimerJob?.cancel()
        nativeTtsEngine.shutdown()
        piperTtsEngineWrapper.shutdown()
        serviceJob.cancel()
        mediaSessionManager.release()
        _playerState.value = TtsPlayerState.Idle
    }

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    companion object {
        private const val ESTIMATED_CHARACTERS_PER_SECOND = 15f
        private const val MIN_TIMELINE_SPEED = 0.5f
        private const val MIN_CHUNK_DURATION_MS = 2_000L
        private const val NOTIFICATION_PROGRESS_UPDATE_INTERVAL_MS = 1_000L
        private val _playerState = MutableStateFlow<TtsPlayerState>(TtsPlayerState.Idle)
        val playerState: StateFlow<TtsPlayerState> = _playerState.asStateFlow()
    }
}
