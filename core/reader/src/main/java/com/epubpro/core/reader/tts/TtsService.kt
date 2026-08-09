package com.epubpro.core.reader.tts

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.epubpro.core.designsystem.R
import com.epubpro.core.reader.filter.ContentSanitizer
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.core.storage.TtsPreferencesManager
import com.epubpro.domain.model.SleepTimerOption
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsPlayerState
import com.epubpro.domain.model.TtsSettings
import com.epubpro.domain.model.normalizedForPlayback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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

    @Inject
    lateinit var chapterPlaybackCoordinator: TtsChapterPlaybackCoordinator

    private val binder = TtsBinder()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var nativeTtsEngine: AndroidNativeTtsEngine
    private lateinit var currentEngine: TtsEngine
    private lateinit var mediaSessionManager: TtsMediaSessionManager
    private lateinit var audioFocusController: TtsAudioFocusController

    private var chunks: List<TtsChunk> = emptyList()
    private var currentIndex: Int = 0
    private var currentChapterIndex: Int = 0
    private var bookId: String = ""
    private var bookTitle: String = ""
    private var author: String = "EpubPro Reader"
    private var preferAiContent: Boolean = false

    private var activeSettings: TtsSettings = TtsSettings()
    private var sleepTimerOption: SleepTimerOption = SleepTimerOption.OFF
    private var sleepTimerJob: Job? = null
    private var notificationProgressJob: Job? = null
    private var chapterPreparation: Deferred<Result<Unit>>? = null
    private var remainingSleepSeconds: Int = 0
    private var playbackGeneration: Long = 0
    private var pausedBySystem: Boolean = false
    private var startedSession: Boolean = false

    private var estimatedChunkDurationsMs: LongArray = LongArray(0)
    private var estimatedChunkStartPositionsMs: LongArray = LongArray(0)
    private var estimatedTotalDurationMs: Long = 0L
    private var estimatedTimelineSpeed: Float = Float.NaN
    private var currentChunkStartPositionMs: Long = 0L
    private var currentTimelinePositionMs: Long = 0L
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
            onStop = { stopSession() }
        )
        audioFocusController = TtsAudioFocusController(
            context = applicationContext,
            onFocusLost = { shouldAutoResume ->
                pauseFromSystem(autoResumeOnFocusGain = shouldAutoResume)
            },
            onFocusGained = {
                if (pausedBySystem) {
                    pausedBySystem = false
                    resume()
                }
            },
            onBecomingNoisy = {
                pauseFromSystem(autoResumeOnFocusGain = false)
                audioFocusController.abandonFocus()
            }
        )

        activeSettings = preferencesManager.getSettings().normalizedForPlayback()
        currentEngine = engineFor(activeSettings)
        applySettingsToEngines(activeSettings)

        nativeTtsEngine.initialize(
            onReady = { applySettingsToEngines(activeSettings) },
            onError = ::handleEngineInitializationError
        )
        piperTtsEngineWrapper.initialize(
            onReady = { applySettingsToEngines(activeSettings) },
            onError = ::handleEngineInitializationError
        )

        serviceScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                updateSettings(settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> Unit
            TtsMediaSessionManager.ACTION_PLAY -> resume()
            TtsMediaSessionManager.ACTION_PAUSE -> pause()
            TtsMediaSessionManager.ACTION_NEXT -> nextChunk()
            TtsMediaSessionManager.ACTION_PREV -> previousChunk()
            TtsMediaSessionManager.ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    fun loadContent(
        id: String,
        title: String,
        bookAuthor: String,
        parsedChunks: List<TtsChunk>,
        startIndex: Int = 0,
        chapterIndex: Int = 0,
        preferAiContent: Boolean = false
    ) {
        ensureStartedSession()
        invalidatePlayback()
        currentEngine.stop()

        bookId = id
        bookTitle = title
        author = bookAuthor
        currentChapterIndex = chapterIndex
        this.preferAiContent = preferAiContent
        activeSettings = preferencesManager.getSettings().normalizedForPlayback()
        applySettingsToEngines(activeSettings)

        chunks = TtsSentenceSegmenter.segment(parsedChunks, activeSettings.language)
        currentIndex = chunks.indexOfFirst { it.paragraphIndex >= startIndex }
            .takeIf { it >= 0 }
            ?: 0
        rebuildEstimatedTimeline(activeSettings.speed)

        chapterPreparation?.cancel()
        chapterPreparation = serviceScope.async {
            runCatching {
                chapterPlaybackCoordinator.prepare(bookId, preferAiContent)
            }
        }

        if (chunks.isEmpty()) {
            showPlaybackError("Chương hiện tại không có nội dung để đọc")
            return
        }
        playCurrentChunk()
    }

    fun playCurrentChunk() {
        if (chunks.isEmpty() || currentIndex !in chunks.indices) return
        ensureStartedSession()

        val originalChunk = chunks[currentIndex]
        preferencesManager.saveLastTtsChunkIndex(
            bookId,
            currentChapterIndex,
            originalChunk.paragraphIndex
        )
        val filterPrefs = readerPreferencesManager.getFilterPreferences()
        val text = if (filterPrefs.isFilterEnabled) {
            ContentSanitizer.sanitize(originalChunk.text, filterPrefs)
        } else {
            originalChunk.text
        }
        if (text.isBlank()) {
            serviceScope.launch { advancePastCurrentSentence() }
            return
        }
        val chunkToSpeak = originalChunk.copy(text = text)

        if (!audioFocusController.requestFocus()) {
            showPlaybackError(getString(R.string.tts_audio_focus_error))
            return
        }

        currentEngine = engineFor(activeSettings)
        ensureEstimatedTimeline(activeSettings.speed)
        currentChunkStartPositionMs =
            estimatedChunkStartPositionsMs.getOrElse(currentIndex) { 0L }
        currentTimelinePositionMs = currentChunkStartPositionMs
        playbackStartedAtElapsedRealtimeMs = null
        notificationProgressJob?.cancel()

        val expectedIndex = currentIndex
        val expectedChapterIndex = currentChapterIndex
        val playbackId = ++playbackGeneration

        _playerState.value = TtsPlayerState.Preparing(
            bookId = bookId,
            chapterIndex = currentChapterIndex,
            currentChunkIndex = currentIndex,
            totalChunks = chunks.size,
            currentChunk = chunkToSpeak
        )
        mediaSessionManager.updateMetadata(
            bookTitle = bookTitle,
            author = author,
            currentSnippet = chunkToSpeak.text,
            durationMs = estimatedTotalDurationMs
        )
        mediaSessionManager.updatePreparingState(currentChunkStartPositionMs)
        showForegroundNotification(
            text = getString(R.string.tts_preparing_voice),
            isPlaying = true
        )

        currentEngine.speak(
            chunk = chunkToSpeak,
            onChunkStart = { startedChunkId ->
                serviceScope.launch {
                    if (!isCurrentPlayback(
                            playbackId,
                            expectedChapterIndex,
                            expectedIndex,
                            startedChunkId,
                            chunkToSpeak.id
                        )
                    ) {
                        return@launch
                    }

                    playbackStartedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    _playerState.value = TtsPlayerState.Playing(
                        bookId = bookId,
                        chapterIndex = currentChapterIndex,
                        currentChunkIndex = currentIndex,
                        totalChunks = chunks.size,
                        currentChunk = chunkToSpeak
                    )
                    mediaSessionManager.updatePlaybackState(
                        isPlaying = true,
                        positionMs = currentChunkStartPositionMs,
                        playbackSpeed = 1.0f
                    )
                    showForegroundNotification(
                        text = chunkToSpeak.text,
                        isPlaying = true
                    )
                    startNotificationProgressUpdates(
                        playbackId = playbackId,
                        expectedChapterIndex = expectedChapterIndex,
                        expectedIndex = expectedIndex
                    )
                }
            },
            onChunkDone = { completedChunkId ->
                serviceScope.launch {
                    if (!isCurrentPlayback(
                            playbackId,
                            expectedChapterIndex,
                            expectedIndex,
                            completedChunkId,
                            chunkToSpeak.id
                        )
                    ) {
                        return@launch
                    }
                    currentTimelinePositionMs =
                        estimatedChunkStartPositionsMs.getOrElse(currentIndex) { 0L } +
                            estimatedChunkDurationsMs.getOrElse(currentIndex) { 0L }
                    advancePastCurrentSentence()
                }
            },
            onError = { message ->
                serviceScope.launch {
                    if (playbackId == playbackGeneration) {
                        showPlaybackError(message)
                    }
                }
            }
        )
    }

    fun pause() {
        pauseInternal(autoResumeOnFocusGain = false, abandonAudioFocus = true)
    }

    private fun pauseFromSystem(autoResumeOnFocusGain: Boolean) {
        pauseInternal(
            autoResumeOnFocusGain = autoResumeOnFocusGain,
            abandonAudioFocus = false
        )
    }

    private fun pauseInternal(
        autoResumeOnFocusGain: Boolean,
        abandonAudioFocus: Boolean
    ) {
        val state = _playerState.value
        if (state !is TtsPlayerState.Playing && state !is TtsPlayerState.Preparing) return

        currentTimelinePositionMs = currentPlaybackPositionMs()
        pausedBySystem = autoResumeOnFocusGain
        invalidatePlayback()
        currentEngine.pause()
        playbackStartedAtElapsedRealtimeMs = null
        if (abandonAudioFocus) audioFocusController.abandonFocus()

        val currentChunk = chunks.getOrNull(currentIndex) ?: return
        _playerState.value = TtsPlayerState.Paused(
            bookId = bookId,
            chapterIndex = currentChapterIndex,
            currentChunkIndex = currentIndex,
            totalChunks = chunks.size,
            currentChunk = currentChunk
        )
        mediaSessionManager.updatePlaybackState(
            isPlaying = false,
            positionMs = currentTimelinePositionMs
        )
        showForegroundNotification(
            text = currentChunk.text,
            isPlaying = false
        )
    }

    fun resume() {
        val state = _playerState.value
        if (state !is TtsPlayerState.Paused && state !is TtsPlayerState.Error) return
        if (pausedBySystem) return
        pausedBySystem = false
        playCurrentChunk()
    }

    fun stop() {
        stopSession()
    }

    private fun stopSession() {
        invalidatePlayback()
        sleepTimerJob?.cancel()
        chapterPreparation?.cancel()
        currentEngine.stop()
        audioFocusController.abandonFocus()
        chapterPlaybackCoordinator.clear()

        chunks = emptyList()
        currentIndex = 0
        currentChapterIndex = 0
        currentTimelinePositionMs = 0L
        playbackStartedAtElapsedRealtimeMs = null
        pausedBySystem = false
        sleepTimerOption = SleepTimerOption.OFF

        _playerState.value = TtsPlayerState.Idle
        mediaSessionManager.updateStoppedState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        startedSession = false
        stopSelf()
    }

    private fun moveToChunk(index: Int) {
        if (index !in chunks.indices) {
            if (index >= chunks.size) {
                val expectedGeneration = playbackGeneration
                serviceScope.launch { advanceToNextChapter(expectedGeneration) }
            }
            return
        }
        invalidatePlayback()
        currentEngine.stop()
        currentIndex = index
        playCurrentChunk()
    }

    fun nextChunk() = moveToChunk(currentIndex + 1)

    fun previousChunk() = moveToChunk(currentIndex - 1)

    fun seekToChunk(index: Int) = moveToChunk(index)

    fun getAvailableVoices(
        isAiVoice: Boolean? = null,
        language: String = "vi"
    ): List<com.epubpro.domain.model.TtsVoice> {
        val engine = when (isAiVoice) {
            true -> piperTtsEngineWrapper
            false -> nativeTtsEngine
            null -> currentEngine
        }
        return engine.getAvailableVoices(language)
    }

    fun speakPreview(
        sampleText: String = "Xin chào, đây là giọng đọc thử nghiệm của ứng dụng EpubPro.",
        settings: TtsSettings
    ) {
        val normalized = settings.normalizedForPlayback()
        val testEngine = engineFor(normalized)
        testEngine.setLanguage(normalized.language)
        testEngine.setSpeed(normalized.speed)
        testEngine.setPitch(normalized.pitch)
        testEngine.setVoice(normalized.voiceId)
        testEngine.speak(
            chunk = TtsChunk(id = PREVIEW_CHUNK_ID, paragraphIndex = 0, text = sampleText),
            onChunkStart = {},
            onChunkDone = {},
            onError = {}
        )
    }

    fun updateSettings(settings: TtsSettings) {
        val normalized = settings.normalizedForPlayback()
        if (normalized == activeSettings) return

        val previousState = _playerState.value
        val shouldRestart =
            previousState is TtsPlayerState.Playing ||
                previousState is TtsPlayerState.Preparing

        invalidatePlayback()
        currentEngine.stop()
        activeSettings = normalized
        applySettingsToEngines(normalized)
        currentEngine = engineFor(normalized)
        rebuildEstimatedTimeline(normalized.speed)

        if (shouldRestart && chunks.isNotEmpty()) {
            playCurrentChunk()
        } else if (previousState is TtsPlayerState.Paused && chunks.isNotEmpty()) {
            currentChunkStartPositionMs =
                estimatedChunkStartPositionsMs.getOrElse(currentIndex) { 0L }
            currentTimelinePositionMs = currentChunkStartPositionMs
            val currentChunk = chunks[currentIndex]
            _playerState.value = previousState.copy(
                currentChunk = currentChunk,
                totalChunks = chunks.size
            )
            mediaSessionManager.updateMetadata(
                bookTitle = bookTitle,
                author = author,
                currentSnippet = currentChunk.text,
                durationMs = estimatedTotalDurationMs
            )
            mediaSessionManager.updatePlaybackState(
                isPlaying = false,
                positionMs = currentTimelinePositionMs
            )
        }
    }

    fun setSleepTimer(option: SleepTimerOption) {
        sleepTimerOption = option
        sleepTimerJob?.cancel()
        if (option == SleepTimerOption.OFF || option == SleepTimerOption.END_OF_CHAPTER) {
            remainingSleepSeconds = 0
            return
        }

        remainingSleepSeconds = option.minutes.coerceAtLeast(0) * 60
        if (remainingSleepSeconds > 0) {
            sleepTimerJob = serviceScope.launch {
                while (remainingSleepSeconds > 0) {
                    delay(1_000)
                    remainingSleepSeconds--
                }
                stopSession()
            }
        }
    }

    private fun advancePastCurrentSentence() {
        if (currentIndex < chunks.lastIndex) {
            currentIndex++
            playCurrentChunk()
        } else {
            val expectedGeneration = playbackGeneration
            serviceScope.launch { advanceToNextChapter(expectedGeneration) }
        }
    }

    private suspend fun advanceToNextChapter(expectedGeneration: Long) {
        if (expectedGeneration != playbackGeneration) return
        invalidatePlayback()
        val navigationGeneration = playbackGeneration
        currentEngine.stop()
        playbackStartedAtElapsedRealtimeMs = null

        if (sleepTimerOption == SleepTimerOption.END_OF_CHAPTER) {
            finishPlayback()
            return
        }

        val preparationResult = chapterPreparation?.await()
            ?: runCatching {
                chapterPlaybackCoordinator.prepare(bookId, preferAiContent)
            }
        if (navigationGeneration != playbackGeneration) return
        if (preparationResult.isFailure) {
            showPlaybackError(
                preparationResult.exceptionOrNull()?.message
                    ?: "Không thể chuẩn bị chương kế tiếp"
            )
            return
        }

        var nextChapterIndex = currentChapterIndex + 1
        while (true) {
            val nextChapterResult = runCatching {
                chapterPlaybackCoordinator.loadChapter(nextChapterIndex)
            }
            if (navigationGeneration != playbackGeneration) return
            val nextChapter = nextChapterResult.getOrElse { error ->
                showPlaybackError(error.message ?: "Không thể tải chương kế tiếp")
                return
            }

            if (nextChapter == null) {
                finishPlayback()
                return
            }

            val nextChunks = TtsSentenceSegmenter.segment(
                nextChapter.chunks,
                activeSettings.language
            )
            if (nextChunks.isNotEmpty()) {
                chapterPlaybackCoordinator.saveChapterProgress(nextChapter.chapterIndex)
                if (navigationGeneration != playbackGeneration) return
                currentChapterIndex = nextChapter.chapterIndex
                chunks = nextChunks
                currentIndex = 0
                rebuildEstimatedTimeline(activeSettings.speed)
                preferencesManager.saveLastTtsChunkIndex(
                    bookId,
                    currentChapterIndex,
                    0
                )
                playCurrentChunk()
                return
            }
            nextChapterIndex++
        }
    }

    private fun finishPlayback() {
        invalidatePlayback()
        currentEngine.stop()
        audioFocusController.abandonFocus()
        playbackStartedAtElapsedRealtimeMs = null
        currentTimelinePositionMs = estimatedTotalDurationMs

        _playerState.value = TtsPlayerState.Completed(
            bookId = bookId,
            chapterIndex = currentChapterIndex
        )
        mediaSessionManager.updateStoppedState(estimatedTotalDurationMs)
        stopForeground(STOP_FOREGROUND_REMOVE)
        startedSession = false
        stopSelf()
    }

    private fun showPlaybackError(message: String) {
        invalidatePlayback()
        currentEngine.stop()
        audioFocusController.abandonFocus()
        pausedBySystem = false
        playbackStartedAtElapsedRealtimeMs = null

        _playerState.value = TtsPlayerState.Error(message)
        mediaSessionManager.updateErrorState(
            positionMs = currentPlaybackPositionMs(),
            message = message
        )
        if (startedSession) {
            showForegroundNotification(
                text = message,
                isPlaying = false
            )
        }
    }

    private fun handleEngineInitializationError(message: String) {
        serviceScope.launch {
            val state = _playerState.value
            if (state is TtsPlayerState.Preparing || state is TtsPlayerState.Playing) {
                showPlaybackError(message)
            }
        }
    }

    private fun applySettingsToEngines(settings: TtsSettings) {
        nativeTtsEngine.setLanguage(settings.language)
        nativeTtsEngine.setSpeed(settings.speed)
        nativeTtsEngine.setPitch(settings.pitch)
        nativeTtsEngine.setVoice(settings.voiceId.takeUnless { settings.isAiVoice })

        piperTtsEngineWrapper.setLanguage(settings.language)
        piperTtsEngineWrapper.setSpeed(settings.speed)
        piperTtsEngineWrapper.setPitch(1.0f)
        piperTtsEngineWrapper.setVoice(settings.voiceId.takeIf { settings.isAiVoice })
    }

    private fun engineFor(settings: TtsSettings): TtsEngine =
        if (settings.isAiVoice) piperTtsEngineWrapper else nativeTtsEngine

    private fun ensureStartedSession() {
        if (startedSession) return
        startedSession = true
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, TtsService::class.java).apply {
                action = ACTION_START_SESSION
            }
        )
    }

    private fun showForegroundNotification(text: String, isPlaying: Boolean) {
        val notification = mediaSessionManager.buildNotification(
            bookTitle = bookTitle,
            currentSnippet = text,
            isPlaying = isPlaying,
            openIntent = null
        )
        startForeground(TtsMediaSessionManager.NOTIFICATION_ID, notification)
    }

    private fun isCurrentPlayback(
        playbackId: Long,
        expectedChapterIndex: Int,
        expectedIndex: Int,
        callbackChunkId: Int,
        expectedChunkId: Int
    ): Boolean =
        playbackId == playbackGeneration &&
            currentChapterIndex == expectedChapterIndex &&
            currentIndex == expectedIndex &&
            callbackChunkId == expectedChunkId

    private fun invalidatePlayback() {
        playbackGeneration++
        notificationProgressJob?.cancel()
        notificationProgressJob = null
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
        currentChunkStartPositionMs =
            estimatedChunkStartPositionsMs.getOrElse(currentIndex) { 0L }
        currentTimelinePositionMs = currentChunkStartPositionMs
    }

    private fun estimateChunkDurationMs(text: String, speed: Float): Long =
        ((text.length / ESTIMATED_CHARACTERS_PER_SECOND / speed) * 1_000f)
            .toLong()
            .coerceAtLeast(MIN_CHUNK_DURATION_MS)

    private fun currentPlaybackPositionMs(): Long {
        val startedAt = playbackStartedAtElapsedRealtimeMs
            ?: return currentTimelinePositionMs.coerceIn(
                0L,
                estimatedTotalDurationMs.coerceAtLeast(0L)
            )
        val elapsedInChunkMs =
            (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val currentChunkDurationMs =
            estimatedChunkDurationsMs.getOrElse(currentIndex) { 0L }
        return (currentChunkStartPositionMs +
            elapsedInChunkMs.coerceAtMost(currentChunkDurationMs))
            .coerceIn(0L, estimatedTotalDurationMs.coerceAtLeast(0L))
    }

    private fun startNotificationProgressUpdates(
        playbackId: Long,
        expectedChapterIndex: Int,
        expectedIndex: Int
    ) {
        notificationProgressJob?.cancel()
        notificationProgressJob = serviceScope.launch {
            while (playbackId == playbackGeneration &&
                currentChapterIndex == expectedChapterIndex &&
                currentIndex == expectedIndex &&
                _playerState.value is TtsPlayerState.Playing
            ) {
                delay(NOTIFICATION_PROGRESS_UPDATE_INTERVAL_MS)
                if (playbackId != playbackGeneration ||
                    currentChapterIndex != expectedChapterIndex ||
                    currentIndex != expectedIndex ||
                    _playerState.value !is TtsPlayerState.Playing
                ) {
                    break
                }

                currentTimelinePositionMs = currentPlaybackPositionMs()
                mediaSessionManager.updatePlaybackState(
                    isPlaying = true,
                    positionMs = currentTimelinePositionMs,
                    playbackSpeed = 1.0f
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        sleepTimerJob?.cancel()
        chapterPreparation?.cancel()
        audioFocusController.abandonFocus()
        nativeTtsEngine.shutdown()
        piperTtsEngineWrapper.shutdown()
        serviceJob.cancel()
        mediaSessionManager.release()
        _playerState.value = TtsPlayerState.Idle
        super.onDestroy()
    }

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    companion object {
        private const val ACTION_START_SESSION = "com.epubpro.tts.ACTION_START_SESSION"
        private const val PREVIEW_CHUNK_ID = 9999
        private const val ESTIMATED_CHARACTERS_PER_SECOND = 15f
        private const val MIN_TIMELINE_SPEED = 0.5f
        private const val MIN_CHUNK_DURATION_MS = 2_000L
        private const val NOTIFICATION_PROGRESS_UPDATE_INTERVAL_MS = 1_000L

        private val _playerState = MutableStateFlow<TtsPlayerState>(TtsPlayerState.Idle)
        val playerState: StateFlow<TtsPlayerState> = _playerState.asStateFlow()
    }
}