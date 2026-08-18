package com.epubpro.feature.reader

import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.ai.AiVietnameseService
import com.epubpro.core.reader.engine.EpubCacheFingerprint
import com.epubpro.core.reader.engine.EpubChapterHeader
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.reader.filter.EpubHtmlSanitizer
import com.epubpro.core.reader.filter.SanitizedEpubHtml
import com.epubpro.core.reader.tts.TtsOpenBookContract
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsTextParser
import com.epubpro.core.reader.tts.TtsWidgetContract
import com.epubpro.core.storage.AiPreferencesManager
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.core.storage.ReaderResumeSnapshotStore
import com.epubpro.core.storage.TtsPlaybackSnapshot
import com.epubpro.core.storage.TtsPlaybackSnapshotStore
import com.epubpro.core.storage.TtsPreferencesManager
import com.epubpro.core.storage.TtsWidgetPlaybackStatus
import com.epubpro.core.storage.TtsWidgetState
import com.epubpro.core.storage.TtsWidgetStateStore
import com.epubpro.domain.model.AiRule
import com.epubpro.domain.model.AiRuleAction
import com.epubpro.domain.model.AiRuleScope
import com.epubpro.domain.model.AiSettings
import com.epubpro.domain.model.Book
import com.epubpro.domain.model.BookBibleSource
import com.epubpro.domain.model.BookBibleSourceType
import com.epubpro.domain.model.Bookmark
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.Highlight
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReadingProgress
import com.epubpro.domain.model.SleepTimerOption
import com.epubpro.domain.model.TtsChunk
import com.epubpro.domain.model.TtsPlayerState
import com.epubpro.domain.model.TtsSettings
import com.epubpro.domain.repository.AiRuleRepository
import com.epubpro.domain.repository.BookBibleRepository
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import org.jsoup.Jsoup

private const val WIDGET_TEXT_MAX_CHARS = 800
private const val SANITIZED_CHAPTER_CACHE_MAX_BYTES = 4 * 1024 * 1024

enum class ReaderContentVersion {
    ORIGINAL,
    AI
}

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<EpubChapterHeader> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String = "",
    val sanitizedCurrentChapterHtml: SanitizedEpubHtml? = null,
    val previousChapterHtml: String? = null,
    val nextChapterHtml: String? = null,
    val sanitizedPreviousChapterHtml: SanitizedEpubHtml? = null,
    val sanitizedNextChapterHtml: SanitizedEpubHtml? = null,
    val currentPageInChapter: Int = 1,
    val initialPageRequest: Int = 1,
    val totalPagesInChapter: Int = 1,
    val firstVisibleParagraphIndex: Int = 0,
    val currentCfi: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val showControls: Boolean = true,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isTtsSpeaking: Boolean = false,
    val showTtsSetupBottomSheet: Boolean = false,
    val showTtsPlayerScreen: Boolean = false,
    val ttsSettings: TtsSettings = TtsSettings(),
    val ttsPlayerState: TtsPlayerState = TtsPlayerState.Idle,
    val selectedSleepTimer: SleepTimerOption = SleepTimerOption.OFF,
    val showAiBottomSheet: Boolean = false,
    val aiSettings: AiSettings = AiSettings(),
    val aiRules: List<AiRule> = emptyList(),
    val aiChapterHtml: String? = null,
    val contentVersion: ReaderContentVersion = ReaderContentVersion.ORIGINAL,
    val aiCreatedWithOldConfiguration: Boolean = false,
    val isAiProcessing: Boolean = false,
    val aiCompletedParts: Int = 0,
    val aiTotalParts: Int = 0,
    val aiError: String? = null,
    val isTestingAiConnection: Boolean = false,
    val aiConnectionMessage: String? = null,
    val filterPreferences: ContentFilterPreferences = ContentFilterPreferences()
) {
    val displayedChapterHtml: String
        get() = if (contentVersion == ReaderContentVersion.AI) {
            aiChapterHtml ?: currentChapterHtml
        } else {
            currentChapterHtml
        }
}

private data class ChapterHtmlBundle(
    val current: String,
    val previous: String?,
    val next: String?
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val preferencesManager: ReaderPreferencesManager,
    private val ttsPreferencesManager: TtsPreferencesManager,
    private val aiPreferencesManager: AiPreferencesManager,
    private val aiVietnameseService: AiVietnameseService,
    private val aiRuleRepository: AiRuleRepository,
    private val savedStateHandle: SavedStateHandle,
    private val widgetStateStore: TtsWidgetStateStore,
    private val playbackSnapshotStore: TtsPlaybackSnapshotStore,
    private val resumeSnapshotStore: ReaderResumeSnapshotStore,
    private val bookBibleRepository: BookBibleRepository
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val requestedTtsChapterIndex: Int? = if (
        savedStateHandle.get<Boolean>(TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER) == true
    ) {
        savedStateHandle.get<Int>(TtsOpenBookContract.NAV_ARGUMENT_CHAPTER_INDEX)
            ?.takeIf { it >= 0 }
    } else {
        null
    }
    private var bookFile: File? = null
    private var chapterLoadJob: Job? = null
    private var adjacentPreloadJob: Job? = null
    private var chapterPrefetchJob: Job? = null
    private var aiProcessingJob: Job? = null
    private var progressSaveJob: Job? = null
    private var widgetProjectionJob: Job? = null
    private var resumeSnapshotJob: Job? = null
    private var progressSaveVersion: Long = 0L
    private var chapterNavigationGeneration: Int = 0
    private var cachedTtsChunks: Pair<String, List<TtsChunk>>? = null

    private val sanitizedChapterCache = object : LruCache<String, SanitizedEpubHtml>(
        SANITIZED_CHAPTER_CACHE_MAX_BYTES
    ) {
        override fun sizeOf(key: String, value: SanitizedEpubHtml): Int {
            return (value.rawHtml.length * 2).coerceAtLeast(1)
        }
    }

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            settings = preferencesManager.getSettings(),
            filterPreferences = preferencesManager.getFilterPreferences(),
            showTtsPlayerScreen = savedStateHandle.get<Boolean>(STATE_SHOW_TTS_PLAYER) == true
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        viewModelScope.launch {
            preferencesManager.filterPreferences.collect { filterPrefs ->
                _uiState.update { it.copy(filterPreferences = filterPrefs) }
            }
        }

        viewModelScope.launch {
            ttsPreferencesManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(ttsSettings = settings) }
            }
        }

        viewModelScope.launch {
            TtsService.playerState.collect { state ->
                val stateToEmit = when (state) {
                    is TtsPlayerState.Preparing -> if (state.bookId == bookId) state else TtsPlayerState.Idle
                    is TtsPlayerState.Playing -> if (state.bookId == bookId) state else TtsPlayerState.Idle
                    is TtsPlayerState.Paused -> if (state.bookId == bookId) state else TtsPlayerState.Idle
                    is TtsPlayerState.Completed -> if (state.bookId == bookId) state else TtsPlayerState.Idle
                    else -> state
                }

                if (stateToEmit is TtsPlayerState.Completed) {
                    _uiState.update {
                        it.copy(
                            ttsPlayerState = TtsPlayerState.Idle,
                            isTtsSpeaking = false
                        )
                    }
                    ttsPreferencesManager.saveLastTtsChunkIndex(
                        bookId,
                        stateToEmit.chapterIndex,
                        0
                    )
                } else {
                    val playbackChapterIndex = when (stateToEmit) {
                        is TtsPlayerState.Preparing -> stateToEmit.chapterIndex
                        is TtsPlayerState.Playing -> stateToEmit.chapterIndex
                        is TtsPlayerState.Paused -> stateToEmit.chapterIndex
                        else -> null
                    }
                    val currentPlaybackChunk = when (stateToEmit) {
                        is TtsPlayerState.Preparing -> stateToEmit.currentChunk
                        is TtsPlayerState.Playing -> stateToEmit.currentChunk
                        is TtsPlayerState.Paused -> stateToEmit.currentChunk
                        else -> null
                    }
                    if (playbackChapterIndex != null &&
                        playbackChapterIndex != _uiState.value.currentChapterIndex &&
                        playbackChapterIndex in _uiState.value.chapters.indices
                    ) {
                        onChapterSelected(playbackChapterIndex)
                    }
                    if (playbackChapterIndex != null && currentPlaybackChunk != null) {
                        ttsPreferencesManager.saveLastTtsChunkIndex(
                            bookId,
                            playbackChapterIndex,
                            currentPlaybackChunk.paragraphIndex
                        )
                    }

                    _uiState.update {
                        it.copy(
                            ttsPlayerState = stateToEmit,
                            isTtsSpeaking = stateToEmit is TtsPlayerState.Playing ||
                                    stateToEmit is TtsPlayerState.Preparing
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            aiPreferencesManager.settingsFlow.collect { settings ->
                _uiState.update { it.copy(aiSettings = settings) }
                refreshCurrentAiCache()
            }
        }

        viewModelScope.launch {
            aiRuleRepository.observeRulesForBook(bookId).collect { rules ->
                _uiState.update { it.copy(aiRules = rules) }
                refreshCurrentAiCache()
            }
        }
        loadBookData()
    }

    private fun loadBookData() {
        chapterLoadJob?.cancel()
        adjacentPreloadJob?.cancel()
        aiProcessingJob?.cancel()
        resumeSnapshotJob?.cancel()
        chapterPrefetchJob?.cancel()
        val loadGen = ++chapterNavigationGeneration
        viewModelScope.launch {
            try {
                val book = bookRepository.getBookById(bookId)
                    ?: error("Không tìm thấy sách trong thư viện")
                bookRepository.updateLastRead(bookId, System.currentTimeMillis())
                val file = storageManager.getBookFile(book.filePath)
                require(file.isFile) { "Tệp EPUB không còn tồn tại" }
                bookFile = file
                val fingerprint = withContext(Dispatchers.IO) {
                    com.epubpro.core.reader.engine.EpubCacheFingerprint.fromFile(file)
                }
                val headers = epubEngine.extractChapterHeaders(file)
                require(headers.isNotEmpty()) { "Không thể đọc cấu trúc EPUB" }

                val savedProgress = bookRepository.getReadingProgress(bookId).firstOrNull()
                val requestedIndex = requestedTtsChapterIndex?.takeIf { it in headers.indices }
                val initialIndex = requestedIndex
                    ?: (savedProgress?.chapterIndex ?: 0)
                        .coerceIn(0, (headers.size - 1).coerceAtLeast(0))
                val canRestoreSavedLocation = savedProgress?.chapterIndex == initialIndex
                val initialPage = if (canRestoreSavedLocation) {
                    (savedProgress?.pageIndex ?: 1).coerceAtLeast(1)
                } else {
                    1
                }
                val initialCfi =
                    savedProgress?.currentCfi.takeIf { canRestoreSavedLocation }.orEmpty()
                val shouldOpenTtsPlayer = consumeOpenTtsPlayerRequest()
                val savedSettings = preferencesManager.getSettings()

                val entryName = headers[initialIndex].entryName
                val cachedSnapshot = withContext(Dispatchers.IO) {
                    resumeSnapshotStore.loadSnapshot(
                        bookId = bookId,
                        expectedChapterIndex = initialIndex,
                        expectedEntryName = entryName,
                        canonicalPath = fingerprint.canonicalPath,
                        fileLength = fingerprint.fileLength,
                        lastModified = fingerprint.lastModified
                    )
                }
                val snapshotSanitizedHtml = cachedSnapshot?.let { snapshot ->
                    withContext(Dispatchers.Default) {
                        SanitizedEpubHtml.restoreFromSnapshot(
                            html = snapshot.sanitizedHtml,
                            sourceHash = snapshot.sourceHash,
                            actualSourceHtml = snapshot.normalizedHtml,
                            sanitizerVersion = snapshot.sanitizerVersion
                        )
                    }
                }
                val sanitizedCacheKey = sanitizedChapterCacheKey(fingerprint, entryName)
                snapshotSanitizedHtml?.let { sanitizedChapterCache.put(sanitizedCacheKey, it) }
                val restoredSanitizedHtml = snapshotSanitizedHtml ?: sanitizedChapterCache.get(sanitizedCacheKey)
                val currentHtml = cachedSnapshot?.normalizedHtml ?: epubEngine.loadChapterHtml(file, entryName)
                require(currentHtml.isNotBlank()) { "Chương hiện tại không có nội dung" }

                // EMIT CURRENT CHAPTER IMMEDIATELY
                _uiState.update {
                    it.copy(
                        book = book,
                        chapters = headers,
                        currentChapterIndex = initialIndex,
                        currentChapterHtml = currentHtml,
                        sanitizedCurrentChapterHtml = restoredSanitizedHtml,
                        previousChapterHtml = null,
                        nextChapterHtml = null,
                        sanitizedPreviousChapterHtml = null,
                        sanitizedNextChapterHtml = null,
                        aiChapterHtml = null,
                        contentVersion = ReaderContentVersion.ORIGINAL,
                        aiCreatedWithOldConfiguration = false,
                        currentPageInChapter = initialPage,
                        initialPageRequest = initialPage,
                        firstVisibleParagraphIndex = parseParagraphLocator(initialCfi),
                        currentCfi = initialCfi,
                        settings = savedSettings,
                        showTtsPlayerScreen = it.showTtsPlayerScreen || shouldOpenTtsPlayer,
                        isLoading = false,
                        loadError = null
                    )
                }

                if (cachedSnapshot == null || restoredSanitizedHtml == null) {
                    scheduleResumeSnapshot(
                        fingerprint = fingerprint,
                        chapterIndex = initialIndex,
                        entryName = entryName,
                        normalizedHtml = currentHtml,
                        generation = loadGen
                    )
                }

                enqueueBookBibleChapter(
                    book = book,
                    chapterIndex = initialIndex,
                    chapterTitle = headers.getOrNull(initialIndex)?.title ?: "Chương ${initialIndex + 1}",
                    originalHtml = currentHtml
                )

                // Asynchronously preload adjacent chapters and AI cache in background
                adjacentPreloadJob?.cancel()
                adjacentPreloadJob = viewModelScope.launch(Dispatchers.IO) {
                    val prev = headers.getOrNull(initialIndex - 1)?.let { header ->
                        runCatching { epubEngine.loadChapterHtml(file, header.entryName) }
                            .getOrNull()
                            ?.also { html ->
                                runCatching {
                                    sanitizeAndCacheChapter(fingerprint, header.entryName, html)
                                }
                            }
                    }
                    val next = headers.getOrNull(initialIndex + 1)?.let { header ->
                        runCatching { epubEngine.loadChapterHtml(file, header.entryName) }
                            .getOrNull()
                            ?.also { html ->
                                runCatching {
                                    sanitizeAndCacheChapter(fingerprint, header.entryName, html)
                                }
                            }
                    }
                    val cachedAi = aiVietnameseService.loadCachedChapter(bookId, initialIndex, currentHtml)

                    if (chapterNavigationGeneration == loadGen && _uiState.value.currentChapterIndex == initialIndex) {
                        _uiState.update {
                            it.copy(
                                previousChapterHtml = prev,
                                nextChapterHtml = next,
                                sanitizedPreviousChapterHtml = headers.getOrNull(initialIndex - 1)
                                    ?.let { sanitizedChapterCache.get(sanitizedChapterCacheKey(fingerprint, it.entryName)) },
                                sanitizedNextChapterHtml = headers.getOrNull(initialIndex + 1)
                                    ?.let { sanitizedChapterCache.get(sanitizedChapterCacheKey(fingerprint, it.entryName)) },
                                aiChapterHtml = cachedAi?.html,
                                aiCreatedWithOldConfiguration = cachedAi?.createdWithOldConfiguration == true
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("EpubPro_VM", "Failed to load book $bookId", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error.message ?: "Không thể tải nội dung sách"
                    )
                }
            }
        }
    }

    fun retryLoad() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadBookData()
    }

    fun onChapterSelected(
        index: Int,
        openAtLastPage: Boolean = false,
        autoStartTts: Boolean = false,
        ttsService: TtsService? = null
    ) {
        val state = _uiState.value
        val file = bookFile
        if (file != null && index in 0 until state.chapters.size) {
            chapterLoadJob?.cancel()
            adjacentPreloadJob?.cancel()
            aiProcessingJob?.cancel()
            resumeSnapshotJob?.cancel()
            progressSaveJob?.cancel()
            widgetProjectionJob?.cancel()
            progressSaveVersion++
            val requestedContentVersion = state.contentVersion
            val loadGen = ++chapterNavigationGeneration

            chapterLoadJob = viewModelScope.launch {
                val entryName = state.chapters[index].entryName
                val currentHtml = epubEngine.loadChapterHtml(file, entryName)

                val fingerprint = withContext(Dispatchers.IO) {
                    EpubCacheFingerprint.fromFile(file)
                }
                val preSanitizedHtml = sanitizedChapterCache.get(
                    sanitizedChapterCacheKey(fingerprint, entryName)
                )

                _uiState.update {
                    it.copy(
                        currentChapterIndex = index,
                        currentChapterHtml = currentHtml,
                        sanitizedCurrentChapterHtml = preSanitizedHtml,
                        previousChapterHtml = null,
                        nextChapterHtml = null,
                        sanitizedPreviousChapterHtml = null,
                        sanitizedNextChapterHtml = null,
                        aiChapterHtml = null,
                        contentVersion = ReaderContentVersion.ORIGINAL,
                        aiCreatedWithOldConfiguration = false,
                        isAiProcessing = false,
                        aiCompletedParts = 0,
                        aiTotalParts = 0,
                        aiError = null,
                        currentPageInChapter = 1,
                        initialPageRequest = if (openAtLastPage) Int.MAX_VALUE else 1,
                        totalPagesInChapter = 1,
                        firstVisibleParagraphIndex = 0,
                        isLoading = false,
                        ttsPlayerState = if (autoStartTts) TtsPlayerState.Loading else it.ttsPlayerState
                    )
                }

                scheduleResumeSnapshot(
                    fingerprint = fingerprint,
                    chapterIndex = index,
                    entryName = entryName,
                    normalizedHtml = currentHtml,
                    generation = loadGen
                )

                state.book?.let { book ->
                    enqueueBookBibleChapter(
                        book = book,
                        chapterIndex = index,
                        chapterTitle = state.chapters.getOrNull(index)?.title ?: "Chương ${index + 1}",
                        originalHtml = currentHtml
                    )
                }

                if (autoStartTts) {
                    ttsPreferencesManager.saveLastTtsChunkIndex(bookId, index, 0)
                    startTtsServicePlayback(ttsService)
                }

                // Asynchronously preload adjacent preview chapters and AI cache in background
                adjacentPreloadJob = viewModelScope.launch(Dispatchers.IO) {
                    val prev = state.chapters.getOrNull(index - 1)?.let { header ->
                        runCatching { epubEngine.loadChapterHtml(file, header.entryName) }
                            .getOrNull()
                            ?.also { html ->
                                runCatching {
                                    sanitizeAndCacheChapter(fingerprint, header.entryName, html)
                                }
                            }
                    }
                    val next = state.chapters.getOrNull(index + 1)?.let { header ->
                        runCatching { epubEngine.loadChapterHtml(file, header.entryName) }
                            .getOrNull()
                            ?.also { html ->
                                runCatching {
                                    sanitizeAndCacheChapter(fingerprint, header.entryName, html)
                                }
                            }
                    }
                    val cachedAi = aiVietnameseService.loadCachedChapter(bookId, index, currentHtml)

                    if (chapterNavigationGeneration == loadGen && _uiState.value.currentChapterIndex == index) {
                        _uiState.update {
                            it.copy(
                                previousChapterHtml = prev,
                                nextChapterHtml = next,
                                sanitizedPreviousChapterHtml = state.chapters.getOrNull(index - 1)
                                    ?.let { sanitizedChapterCache.get(sanitizedChapterCacheKey(fingerprint, it.entryName)) },
                                sanitizedNextChapterHtml = state.chapters.getOrNull(index + 1)
                                    ?.let { sanitizedChapterCache.get(sanitizedChapterCacheKey(fingerprint, it.entryName)) },
                                aiChapterHtml = cachedAi?.html,
                                contentVersion = if (
                                    requestedContentVersion == ReaderContentVersion.AI && cachedAi != null
                                ) {
                                    ReaderContentVersion.AI
                                } else {
                                    ReaderContentVersion.ORIGINAL
                                },
                                aiCreatedWithOldConfiguration = cachedAi?.createdWithOldConfiguration == true
                            )
                        }
                    }
                }
            }
        }
    }

    fun nextChapter() {
        onChapterSelected(_uiState.value.currentChapterIndex + 1)
    }

    fun previousChapter() {
        onChapterSelected(_uiState.value.currentChapterIndex - 1, openAtLastPage = true)
    }

    /**
     * Nạp trước và sanitize chương đích ngay khi gesture vượt ngưỡng chuyển chương.
     *
     * @param index Chỉ số chương cần chuẩn bị trong danh sách chương hiện tại.
     */
    fun prefetchChapter(index: Int) {
        val state = _uiState.value
        val file = bookFile ?: return
        val header = state.chapters.getOrNull(index) ?: return
        if (index == state.currentChapterIndex) return

        chapterPrefetchJob?.cancel()
        chapterPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val fingerprint = EpubCacheFingerprint.fromFile(file)
            val normalizedHtml = runCatching {
                epubEngine.loadChapterHtml(file, header.entryName)
            }.getOrNull() ?: return@launch
            if (!isActive) return@launch
            sanitizeAndCacheChapter(fingerprint, header.entryName, normalizedHtml)
        }
    }

    fun updatePageMetrics(currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) {
        val currentState = _uiState.value
        if (currentState.isLoading) return

        // Save the visible chunk index for TTS resuming
        if (!currentState.chapters.isEmpty()) {
            ttsPreferencesManager.saveLastTtsChunkIndex(
                bookId,
                currentState.currentChapterIndex,
                firstVisibleChunkIndex
            )
        }

        if (
            currentState.currentPageInChapter == currentPage &&
            currentState.totalPagesInChapter == totalPages &&
            currentState.initialPageRequest == currentPage &&
            currentState.firstVisibleParagraphIndex == firstVisibleChunkIndex
        ) {
            return
        }
        _uiState.update {
            it.copy(
                currentPageInChapter = currentPage,
                initialPageRequest = currentPage,
                totalPagesInChapter = totalPages,
                firstVisibleParagraphIndex = firstVisibleChunkIndex,
                currentCfi = paragraphLocator(firstVisibleChunkIndex)
            )
        }
        saveProgress()
    }

    private suspend fun loadChapterBundle(
        file: File,
        headers: List<EpubChapterHeader>,
        index: Int
    ): ChapterHtmlBundle {
        val current = epubEngine.loadChapterHtml(file, headers[index].entryName)
        val previous = headers.getOrNull(index - 1)?.let { header ->
            runCatching { epubEngine.loadChapterHtml(file, header.entryName) }.getOrNull()
        }
        val next = headers.getOrNull(index + 1)?.let { header ->
            runCatching { epubEngine.loadChapterHtml(file, header.entryName) }.getOrNull()
        }
        return ChapterHtmlBundle(current, previous, next)
    }

    fun updateSettings(newSettings: ReaderSettings) {
        preferencesManager.saveSettings(newSettings)
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun updateCfiPosition(cfi: String) {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(currentCfi = cfi) }
        saveProgress()
    }

    private fun saveProgress() {
        val saveVersion = ++progressSaveVersion
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            val state = _uiState.value
            if (state.isLoading || state.chapters.isEmpty()) {
                return@launch
            }
            kotlinx.coroutines.delay(200) // 200ms debounce
            if (saveVersion != progressSaveVersion) return@launch

            // TTS owns the shared playback/widget projection while it is active.
            if (TtsService.isPlaybackProjectionOwned()) {
                return@launch
            }

            val totalChapters = state.chapters.size.coerceAtLeast(1)
            val chapterProgress = state.currentChapterIndex.toFloat() / totalChapters
            val pageProgress =
                ((state.currentPageInChapter.toFloat() - 1) / state.totalPagesInChapter.coerceAtLeast(
                    1
                )) / totalChapters
            val overallProgress = (chapterProgress + pageProgress).coerceIn(0f, 1f)

            bookRepository.saveReadingProgress(
                ReadingProgress(
                    bookId = bookId,
                    currentCfi = state.currentCfi,
                    chapterIndex = state.currentChapterIndex,
                    pageIndex = state.currentPageInChapter,
                    progressPercentage = overallProgress,
                    totalChapters = state.chapters.size
                )
            )

            scheduleWidgetProjection(state, overallProgress, saveVersion)
        }
    }

    /**
     * Ghi ngay progress cuối cùng, bỏ qua debounce khi reader đi vào background hoặc bị dispose.
     */
    fun flushPendingProgress() {
        val state = _uiState.value
        if (state.isLoading || state.chapters.isEmpty()) return

        progressSaveVersion++
        progressSaveJob?.cancel()
        val totalChapters = state.chapters.size.coerceAtLeast(1)
        val chapterProgress = state.currentChapterIndex.toFloat() / totalChapters
        val pageProgress =
            ((state.currentPageInChapter.toFloat() - 1) /
                state.totalPagesInChapter.coerceAtLeast(1)) / totalChapters
        val overallProgress = (chapterProgress + pageProgress).coerceIn(0f, 1f)

        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.saveReadingProgress(
                ReadingProgress(
                    bookId = bookId,
                    currentCfi = state.currentCfi,
                    chapterIndex = state.currentChapterIndex,
                    pageIndex = state.currentPageInChapter,
                    progressPercentage = overallProgress,
                    totalChapters = state.chapters.size
                )
            )
        }
    }

    private fun scheduleWidgetProjection(state: ReaderUiState, overallProgress: Float, saveVersion: Long) {
        widgetProjectionJob?.cancel()
        widgetProjectionJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500) // 500ms debounce for heavier widget projection
            if (saveVersion != progressSaveVersion || TtsService.isPlaybackProjectionOwned()) {
                return@launch
            }

            val currentChapterTitle =
                state.chapters.getOrNull(state.currentChapterIndex)?.title.orEmpty()
            val coverPath = state.book?.coverPath
            val paragraphIndex = state.firstVisibleParagraphIndex.coerceAtLeast(0)
            val displayedChapterHtml = state.displayedChapterHtml
            val bookTitle = state.book?.title.orEmpty()
            val isTtsSpeaking = state.isTtsSpeaking
            val preferAiContent = state.contentVersion == ReaderContentVersion.AI

            // Memoized TtsChunk parsing to avoid repeated Jsoup DOM allocations
            val chapterChunks = if (cachedTtsChunks?.first == displayedChapterHtml) {
                cachedTtsChunks!!.second
            } else {
                val parsed = TtsTextParser.parseHtmlToChunks(displayedChapterHtml)
                cachedTtsChunks = displayedChapterHtml to parsed
                parsed
            }

            val totalParagraphs = chapterChunks
                .maxOfOrNull { it.paragraphIndex + 1 }
                ?.coerceAtLeast(1)
                ?: 1
            val pageText = buildWidgetParagraphText(
                chunks = chapterChunks,
                paragraphIndex = paragraphIndex,
                fallbackHtml = displayedChapterHtml
            )

            if (saveVersion != progressSaveVersion || TtsService.isPlaybackProjectionOwned()) {
                return@launch
            }

            playbackSnapshotStore.saveSnapshot(
                TtsPlaybackSnapshot(
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    paragraphIndex = paragraphIndex,
                    sentenceIndex = 0,
                    timelinePositionMs = 0L,
                    preferAiContent = preferAiContent
                )
            )

            widgetStateStore.saveState(
                TtsWidgetState(
                    bookTitle = bookTitle,
                    chapterTitle = currentChapterTitle,
                    playbackStatus = if (isTtsSpeaking) TtsWidgetPlaybackStatus.PLAYING else TtsWidgetPlaybackStatus.IDLE,
                    progress = overallProgress,
                    positionMs = 0L,
                    durationMs = 0L,
                    hasSnapshot = true,
                    coverPath = coverPath,
                    paragraphIndex = paragraphIndex,
                    totalParagraphs = totalParagraphs,
                    paragraphText = pageText
                )
            )
            context.sendBroadcast(Intent(TtsWidgetContract.ACTION_STATE_CHANGED).setPackage(context.packageName))
        }
    }


    /**
     * Chuẩn hóa, băm và ghi snapshot chương trên dispatcher nền sau khi current đã được hiển thị.
     *
     * @param fingerprint Dấu vân tay của tệp EPUB.
     * @param chapterIndex Chỉ số chương cần lưu.
     * @param entryName Tên entry chương.
     * @param normalizedHtml HTML đã chuẩn hóa của chương.
     * @param generation Thế hệ điều hướng dùng để loại bỏ kết quả cũ.
     */
    private fun scheduleResumeSnapshot(
        fingerprint: com.epubpro.core.reader.engine.EpubCacheFingerprint,
        chapterIndex: Int,
        entryName: String,
        normalizedHtml: String,
        generation: Int
    ) {
        resumeSnapshotJob?.cancel()
        resumeSnapshotJob = viewModelScope.launch(Dispatchers.Default) {
            val sanitizedHtml = EpubHtmlSanitizer.sanitize(normalizedHtml)
            sanitizedChapterCache.put(
                sanitizedChapterCacheKey(fingerprint, entryName),
                sanitizedHtml
            )
            val sourceHash = com.epubpro.core.storage.ReaderResumeSnapshot.computeContentHash(normalizedHtml)
            if (generation != chapterNavigationGeneration || !isActive) return@launch
            withContext(Dispatchers.IO) {
                resumeSnapshotStore.saveSnapshot(
                    com.epubpro.core.storage.ReaderResumeSnapshot(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        entryName = entryName,
                        canonicalPath = fingerprint.canonicalPath,
                        fileLength = fingerprint.fileLength,
                        lastModified = fingerprint.lastModified,
                        sourceHash = sourceHash,
                        normalizedHtml = normalizedHtml,
                        sanitizedHtml = sanitizedHtml.rawHtml
                    )
                )
            }
        }
    }

    /**
     * Tạo khóa cache sanitizer gắn với đúng phiên bản tệp EPUB và entry chương.
     *
     * @param fingerprint Dấu vân tay hiện tại của tệp EPUB.
     * @param entryName Tên entry chương trong EPUB.
     * @return Khóa ổn định dùng cho cache RAM giới hạn dung lượng.
     */
    private fun sanitizedChapterCacheKey(
        fingerprint: EpubCacheFingerprint,
        entryName: String
    ): String {
        return EpubCacheFingerprint.computeSha256(
            fingerprint.canonicalPath + "|" + fingerprint.fileLength + "|" +
                fingerprint.lastModified + "|" + entryName + "|" +
                EpubHtmlSanitizer.CURRENT_SANITIZER_VERSION
        )
    }

    /**
     * Làm sạch HTML trên dispatcher CPU và lưu kết quả vào cache RAM giới hạn dung lượng.
     *
     * @param fingerprint Dấu vân tay hiện tại của tệp EPUB.
     * @param entryName Tên entry chương.
     * @param normalizedHtml HTML đã chuẩn hóa cần sanitize.
     * @return HTML đã sanitize an toàn.
     */
    private suspend fun sanitizeAndCacheChapter(
        fingerprint: EpubCacheFingerprint,
        entryName: String,
        normalizedHtml: String
    ): SanitizedEpubHtml {
        val sanitizedHtml = withContext(Dispatchers.Default) {
            EpubHtmlSanitizer.sanitize(normalizedHtml)
        }
        sanitizedChapterCache.put(
            sanitizedChapterCacheKey(fingerprint, entryName),
            sanitizedHtml
        )
        return sanitizedHtml
    }

    private fun parseParagraphLocator(locator: String): Int {
        return locator
            .removePrefix(PARAGRAPH_LOCATOR_PREFIX)
            .toIntOrNull()
            ?.takeIf { locator.startsWith(PARAGRAPH_LOCATOR_PREFIX) }
            ?.coerceAtLeast(0)
            ?: 0
    }

    private fun paragraphLocator(index: Int): String =
        PARAGRAPH_LOCATOR_PREFIX + index.coerceAtLeast(0)

    private fun buildWidgetParagraphText(
        chunks: List<TtsChunk>,
        paragraphIndex: Int,
        fallbackHtml: String
    ): String {
        val paragraphText = chunks
            .filter { it.paragraphIndex == paragraphIndex }
            .joinToString(" ") { it.text.trim() }
            .trim()
        if (paragraphText.isNotBlank()) return paragraphText.take(WIDGET_TEXT_MAX_CHARS)

        return runCatching {
            android.text.Html.fromHtml(
                fallbackHtml,
                android.text.Html.FROM_HTML_MODE_LEGACY
            ).toString().trim().take(WIDGET_TEXT_MAX_CHARS)
        }.getOrDefault("")
    }

    fun addBookmark() {
        viewModelScope.launch {
            val state = _uiState.value
            val chapterTitle = state.chapters.getOrNull(state.currentChapterIndex)?.title
                ?: "Chapter ${state.currentChapterIndex + 1}"
            bookmarkRepository.addBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    chapterTitle = chapterTitle,
                    cfi = state.currentCfi,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addHighlight(selectedText: String, colorHex: String = "#FFEB3B", note: String? = null) {
        viewModelScope.launch {
            val state = _uiState.value
            bookmarkRepository.addHighlight(
                Highlight(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterIndex = state.currentChapterIndex,
                    startCfi = state.currentCfi,
                    endCfi = state.currentCfi,
                    selectedText = selectedText,
                    colorHex = colorHex,
                    note = note,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun openAiBottomSheet() {
        _uiState.update {
            it.copy(showAiBottomSheet = true, aiError = null, aiConnectionMessage = null)
        }
    }

    fun dismissAiBottomSheet() {
        _uiState.update { it.copy(showAiBottomSheet = false) }
    }

    fun saveAiConfiguration(apiKey: String?, modelId: String) {
        runCatching { aiPreferencesManager.saveConfiguration(apiKey, modelId) }
            .onSuccess {
                _uiState.update {
                    it.copy(aiConnectionMessage = "Đã lưu cấu hình AI.", aiError = null)
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(aiError = error.message ?: "Không thể lưu cấu hình AI.") }
            }
    }

    fun clearAiApiKey() {
        aiPreferencesManager.clearApiKey()
        _uiState.update { it.copy(aiConnectionMessage = "Đã xóa API key.", aiError = null) }
    }

    fun testAiConnection(apiKey: String?, modelId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isTestingAiConnection = true, aiConnectionMessage = null, aiError = null)
            }
            runCatching { aiVietnameseService.testConnection(apiKey, modelId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isTestingAiConnection = false,
                            aiConnectionMessage = "Kết nối Gemini thành công."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTestingAiConnection = false,
                            aiError = error.message ?: "Không thể kết nối Gemini."
                        )
                    }
                }
        }
    }

    fun setContentVersion(version: ReaderContentVersion) {
        val state = _uiState.value
        if (version == ReaderContentVersion.AI && state.aiChapterHtml == null) return
        _uiState.update {
            it.copy(
                contentVersion = version,
                initialPageRequest = 1,
                currentPageInChapter = 1,
                totalPagesInChapter = 1,
                firstVisibleParagraphIndex = 0,
                currentCfi = ""
            )
        }
    }

    fun startAiPolish() {
        val state = _uiState.value
        if (!state.aiSettings.hasApiKey) {
            _uiState.update { it.copy(aiError = "Hãy nhập Gemini API key trước.") }
            return
        }
        if (state.currentChapterHtml.isBlank() || state.isAiProcessing) return

        val chapterIndex = state.currentChapterIndex
        val sourceHtml = state.currentChapterHtml
        aiProcessingJob?.cancel()
        aiProcessingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAiProcessing = true,
                    aiCompletedParts = 0,
                    aiTotalParts = 0,
                    aiError = null
                )
            }
            try {
                val html = aiVietnameseService.polishChapter(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    sourceHtml = sourceHtml
                ) { progress ->
                    if (_uiState.value.currentChapterIndex == chapterIndex) {
                        _uiState.update {
                            it.copy(
                                aiCompletedParts = progress.completedParts,
                                aiTotalParts = progress.totalParts
                            )
                        }
                    }
                }
                if (_uiState.value.currentChapterIndex == chapterIndex) {
                    _uiState.update {
                        it.copy(
                            aiChapterHtml = html,
                            contentVersion = ReaderContentVersion.AI,
                            aiCreatedWithOldConfiguration = false,
                            isAiProcessing = false,
                            aiError = null
                        )
                    }
                }
            } catch (error: CancellationException) {
                if (_uiState.value.currentChapterIndex == chapterIndex) {
                    _uiState.update { it.copy(isAiProcessing = false) }
                }
                throw error
            } catch (error: Exception) {
                if (_uiState.value.currentChapterIndex == chapterIndex) {
                    _uiState.update {
                        it.copy(
                            isAiProcessing = false,
                            aiError = error.message ?: "Không thể thuần Việt chương này."
                        )
                    }
                }
            }
        }
    }

    fun cancelAiPolish() {
        aiProcessingJob?.cancel()
        _uiState.update { it.copy(isAiProcessing = false) }
    }

    fun deleteCurrentAiChapter() {
        val chapterIndex = _uiState.value.currentChapterIndex
        viewModelScope.launch {
            aiVietnameseService.deleteChapter(bookId, chapterIndex)
            if (_uiState.value.currentChapterIndex == chapterIndex) {
                _uiState.update {
                    it.copy(
                        aiChapterHtml = null,
                        contentVersion = ReaderContentVersion.ORIGINAL,
                        aiCreatedWithOldConfiguration = false,
                        aiCompletedParts = 0,
                        aiTotalParts = 0,
                        aiError = null
                    )
                }
            }
        }
    }

    fun saveAiRule(
        ruleId: String?,
        scope: AiRuleScope,
        source: String,
        action: AiRuleAction,
        replacement: String?,
        caseSensitive: Boolean
    ) {
        val normalizedSource = source.trim()
        val normalizedReplacement = replacement?.trim()
        if (normalizedSource.isBlank()) {
            _uiState.update { it.copy(aiError = "Thuật ngữ không được để trống.") }
            return
        }
        if (action == AiRuleAction.REPLACE && normalizedReplacement.isNullOrBlank()) {
            _uiState.update { it.copy(aiError = "Hãy nhập nội dung thay thế.") }
            return
        }

        val duplicate = _uiState.value.aiRules.any { existing ->
            existing.id != ruleId &&
                    existing.scope == scope &&
                    existing.source.equals(normalizedSource, ignoreCase = !caseSensitive)
        }
        if (duplicate) {
            _uiState.update { it.copy(aiError = "Đã có quy tắc cùng phạm vi cho thuật ngữ này.") }
            return
        }

        viewModelScope.launch {
            aiRuleRepository.upsertRule(
                AiRule(
                    id = ruleId ?: UUID.randomUUID().toString(),
                    scope = scope,
                    bookId = if (scope == AiRuleScope.BOOK) bookId else null,
                    source = normalizedSource,
                    action = action,
                    replacement = normalizedReplacement.takeIf { action == AiRuleAction.REPLACE },
                    caseSensitive = caseSensitive,
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(aiError = null) }
        }
    }

    fun deleteAiRule(ruleId: String) {
        viewModelScope.launch {
            aiRuleRepository.deleteRule(ruleId)
            _uiState.update { it.copy(aiError = null) }
        }
    }

    private suspend fun refreshCurrentAiCache() {
        val state = _uiState.value
        if (state.currentChapterHtml.isBlank() || state.isLoading || state.isAiProcessing) return
        val cached = aiVietnameseService.loadCachedChapter(
            bookId,
            state.currentChapterIndex,
            state.currentChapterHtml
        )
        if (_uiState.value.currentChapterIndex == state.currentChapterIndex) {
            _uiState.update {
                it.copy(
                    aiChapterHtml = cached?.html,
                    contentVersion = if (
                        it.contentVersion == ReaderContentVersion.AI && cached != null
                    ) {
                        ReaderContentVersion.AI
                    } else {
                        ReaderContentVersion.ORIGINAL
                    },
                    aiCreatedWithOldConfiguration = cached?.createdWithOldConfiguration == true
                )
            }
        }
    }

    private fun consumeOpenTtsPlayerRequest(): Boolean {
        if (savedStateHandle.get<Boolean>(TtsOpenBookContract.NAV_ARGUMENT_OPEN_TTS_PLAYER) != true) {
            return false
        }
        if (savedStateHandle.get<Boolean>(STATE_TTS_PLAYER_REQUEST_CONSUMED) == true) {
            return false
        }
        savedStateHandle[STATE_TTS_PLAYER_REQUEST_CONSUMED] = true
        savedStateHandle[STATE_SHOW_TTS_PLAYER] = true
        return true
    }

    fun onTtsIconButtonClicked() {
        val ttsSettings = ttsPreferencesManager.getSettings()
        if (!ttsSettings.isConfigured) {
            _uiState.update { it.copy(showTtsSetupBottomSheet = true) }
        } else {
            setTtsPlayerVisible(true)
        }
    }

    fun dismissTtsSetupBottomSheet() {
        _uiState.update { it.copy(showTtsSetupBottomSheet = false) }
    }

    fun openTtsSetupBottomSheet() {
        _uiState.update { it.copy(showTtsSetupBottomSheet = true) }
    }

    fun openTtsPlayerScreen() {
        setTtsPlayerVisible(true)
    }

    fun closeTtsPlayerScreen() {
        setTtsPlayerVisible(false)
    }

    fun updateTtsSettings(newSettings: TtsSettings, ttsService: TtsService?) {
        ttsPreferencesManager.saveSettings(newSettings)
        ttsService?.updateSettings(newSettings)
    }

    fun onStartListeningFromSetup(newSettings: TtsSettings, ttsService: TtsService?) {
        ttsPreferencesManager.saveSettings(newSettings)
        savedStateHandle[STATE_SHOW_TTS_PLAYER] = true
        _uiState.update {
            it.copy(
                ttsSettings = newSettings,
                showTtsSetupBottomSheet = false,
                showTtsPlayerScreen = true
            )
        }
        ttsService?.updateSettings(newSettings)
        startTtsServicePlayback(ttsService)
    }

    fun startTtsServicePlayback(ttsService: TtsService?) {
        val state = _uiState.value
        val html = state.displayedChapterHtml
        val cachedChunks = cachedTtsChunks?.takeIf { it.first == html }?.second
        if (cachedChunks != null) {
            loadTtsContent(state, cachedChunks, ttsService)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val chunks = TtsTextParser.parseHtmlToChunks(html)
            cachedTtsChunks = html to chunks
            withContext(Dispatchers.Main.immediate) {
                if (_uiState.value.currentChapterIndex == state.currentChapterIndex &&
                    _uiState.value.displayedChapterHtml == html
                ) {
                    loadTtsContent(state, chunks, ttsService)
                }
            }
        }
    }

    /**
     * Gửi các chunk TTS đã chuẩn bị tới service trên Main thread với vị trí đọc hợp lệ.
     *
     * @param state Trạng thái reader tại thời điểm yêu cầu phát.
     * @param chunks Danh sách chunk đã parse.
     * @param ttsService Service TTS đích.
     */
    private fun loadTtsContent(
        state: ReaderUiState,
        chunks: List<TtsChunk>,
        ttsService: TtsService?
    ) {
        val savedChunkIndex =
            ttsPreferencesManager.getLastTtsChunkIndex(bookId, state.currentChapterIndex)
        val validIndex = savedChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        ttsService?.loadContent(
            id = bookId,
            title = state.book?.title ?: "EpubPro",
            bookAuthor = state.book?.author ?: "Unknown",
            parsedChunks = chunks,
            startIndex = validIndex,
            chapterIndex = state.currentChapterIndex,
            chapterTitle = state.chapters.getOrNull(state.currentChapterIndex)?.title.orEmpty(),
            preferAiContent = state.contentVersion == ReaderContentVersion.AI
        )
    }

    fun toggleTtsPlayback(ttsService: TtsService?) {
        val state = _uiState.value
        when (state.ttsPlayerState) {
            is TtsPlayerState.Preparing,
            is TtsPlayerState.Playing -> ttsService?.pause()

            is TtsPlayerState.Paused -> ttsService?.resume()
            else -> startTtsServicePlayback(ttsService)
        }
    }

    fun stopTtsPlayback(ttsService: TtsService?) {
        ttsService?.stop()
        _uiState.update { it.copy(ttsPlayerState = TtsPlayerState.Idle) }
    }

    fun nextTtsChunk(ttsService: TtsService?) {
        ttsService?.nextChunk()
    }

    fun prevTtsChunk(ttsService: TtsService?) {
        ttsService?.previousChunk()
    }

    private companion object {
        const val STATE_TTS_PLAYER_REQUEST_CONSUMED = "tts_player_request_consumed"
        const val STATE_SHOW_TTS_PLAYER = "show_tts_player_screen"
        const val PARAGRAPH_LOCATOR_PREFIX = "epubpro:paragraph:"
    }

    private fun setTtsPlayerVisible(visible: Boolean) {
        savedStateHandle[STATE_SHOW_TTS_PLAYER] = visible
        _uiState.update { it.copy(showTtsPlayerScreen = visible) }
    }

    fun seekTtsChunk(index: Int, ttsService: TtsService?) {
        ttsService?.seekToChunk(index)
    }

    fun setSleepTimerOption(option: SleepTimerOption, ttsService: TtsService?) {
        _uiState.update { it.copy(selectedSleepTimer = option) }
        ttsService?.setSleepTimer(option)
    }

    /**
     * Đưa chương nguồn vừa nạp vào hàng đợi gửi Book Bible theo cơ chế bất đồng bộ, không chặn UI.
     */
    private fun enqueueBookBibleChapter(
        book: Book,
        chapterIndex: Int,
        chapterTitle: String,
        originalHtml: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val plainText = Jsoup.parse(originalHtml).text()
                if (plainText.isNotBlank()) {
                    val totalChapters = book.totalChapters.takeIf { it > 0 } ?: _uiState.value.chapters.size
                    bookBibleRepository.enqueueChapterSubmission(
                        source = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, book.id),
                        chapterNumber = chapterIndex + 1,
                        chapterTitle = chapterTitle,
                        totalChapters = totalChapters,
                        sourceContent = plainText,
                        bookTitle = book.title,
                        author = book.author
                    )
                }
            }
        }
    }
}
