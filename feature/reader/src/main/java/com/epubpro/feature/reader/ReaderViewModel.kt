package com.epubpro.feature.reader

import android.util.Log
import com.epubpro.core.ai.AiVietnameseService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.EpubChapterHeader
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.AiPreferencesManager
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.AiRuleRepository
import com.epubpro.domain.repository.BookmarkRepository
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsTextParser
import com.epubpro.core.storage.TtsPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class ReaderContentVersion {
    ORIGINAL,
    AI
}

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<EpubChapterHeader> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String = "",
    val previousChapterHtml: String? = null,
    val nextChapterHtml: String? = null,
    val currentPageInChapter: Int = 1,
    val initialPageRequest: Int = 1,
    val totalPagesInChapter: Int = 1,
    val firstVisibleParagraphIndex: Int = 0,
    val currentCfi: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val showControls: Boolean = true,
    val isLoading: Boolean = true,
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
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val preferencesManager: ReaderPreferencesManager,
    private val ttsPreferencesManager: TtsPreferencesManager,
    private val aiPreferencesManager: AiPreferencesManager,
    private val aiVietnameseService: AiVietnameseService,
    private val aiRuleRepository: AiRuleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private var bookFile: File? = null
    private var chapterLoadJob: Job? = null
    private var aiProcessingJob: Job? = null

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            settings = preferencesManager.getSettings(),
            filterPreferences = preferencesManager.getFilterPreferences()
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
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId)
            if (book != null) {
                bookRepository.updateLastRead(bookId, System.currentTimeMillis())
                val file = storageManager.getBookFile(book.filePath)
                bookFile = file
                val headers = epubEngine.extractChapterHeaders(file)

                val savedProgress = bookRepository.getReadingProgress(bookId).firstOrNull()
                val initialIndex = (savedProgress?.chapterIndex ?: 0).coerceIn(0, (headers.size - 1).coerceAtLeast(0))
                val initialPage = (savedProgress?.pageIndex ?: 1).coerceAtLeast(1)
                val savedSettings = preferencesManager.getSettings()

                Log.d("EpubPro_VM", "Restoring progress for bookId=$bookId: savedChapter=${savedProgress?.chapterIndex}, savedPage=${savedProgress?.pageIndex} -> finalChapter=$initialIndex, finalPage=$initialPage, totalChapters=${headers.size}")

                val chapterBundle = if (headers.isNotEmpty()) {
                    loadChapterBundle(file, headers, initialIndex)
                } else {
                    ChapterHtmlBundle("", null, null)
                }
                val cachedAi = if (chapterBundle.current.isNotBlank()) {
                    aiVietnameseService.loadCachedChapter(bookId, initialIndex, chapterBundle.current)
                } else {
                    null
                }

                _uiState.update {
                    it.copy(
                        book = book,
                        chapters = headers,
                        currentChapterIndex = initialIndex,
                        currentChapterHtml = chapterBundle.current,
                        previousChapterHtml = chapterBundle.previous,
                        nextChapterHtml = chapterBundle.next,
                        aiChapterHtml = cachedAi?.html,
                        contentVersion = ReaderContentVersion.ORIGINAL,
                        aiCreatedWithOldConfiguration = cachedAi?.createdWithOldConfiguration == true,
                        currentPageInChapter = initialPage,
                        initialPageRequest = initialPage,
                        currentCfi = savedProgress?.currentCfi ?: "",
                        settings = savedSettings,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onChapterSelected(index: Int, openAtLastPage: Boolean = false, autoStartTts: Boolean = false, ttsService: TtsService? = null) {
        val state = _uiState.value
        val file = bookFile
        if (file != null && index in 0 until state.chapters.size) {
            chapterLoadJob?.cancel()
            aiProcessingJob?.cancel()
            chapterLoadJob = viewModelScope.launch {
                val chapterBundle = loadChapterBundle(file, state.chapters, index)
                val cachedAi = aiVietnameseService.loadCachedChapter(
                    bookId,
                    index,
                    chapterBundle.current
                )
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index,
                        currentChapterHtml = chapterBundle.current,
                        previousChapterHtml = chapterBundle.previous,
                        nextChapterHtml = chapterBundle.next,
                        aiChapterHtml = cachedAi?.html,
                        contentVersion = if (
                            it.contentVersion == ReaderContentVersion.AI && cachedAi != null
                        ) {
                            ReaderContentVersion.AI
                        } else {
                            ReaderContentVersion.ORIGINAL
                        },
                        aiCreatedWithOldConfiguration = cachedAi?.createdWithOldConfiguration == true,
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
                
                if (autoStartTts) {
                    ttsPreferencesManager.saveLastTtsChunkIndex(bookId, index, 0)
                    startTtsServicePlayback(ttsService)
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

    fun updatePageMetrics(currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) {
        val currentState = _uiState.value
        if (currentState.isLoading) return
        
        // Save the visible chunk index for TTS resuming
        if (!currentState.chapters.isEmpty()) {
            ttsPreferencesManager.saveLastTtsChunkIndex(bookId, currentState.currentChapterIndex, firstVisibleChunkIndex)
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
                firstVisibleParagraphIndex = firstVisibleChunkIndex
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
            epubEngine.loadChapterHtml(file, header.entryName)
        }
        val next = headers.getOrNull(index + 1)?.let { header ->
            epubEngine.loadChapterHtml(file, header.entryName)
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
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isLoading || state.chapters.isEmpty()) {
                Log.d("EpubPro_VM", "Skip saveProgress: isLoading=${state.isLoading}, chaptersSize=${state.chapters.size}")
                return@launch
            }

            val totalChapters = state.chapters.size.coerceAtLeast(1)
            val chapterProgress = state.currentChapterIndex.toFloat() / totalChapters
            val pageProgress = ((state.currentPageInChapter.toFloat() - 1) / state.totalPagesInChapter.coerceAtLeast(1)) / totalChapters
            val overallProgress = (chapterProgress + pageProgress).coerceIn(0f, 1f)

            Log.d("EpubPro_VM", "Saving reading progress: chapterIndex=${state.currentChapterIndex}, pageIndex=${state.currentPageInChapter}, progress=$overallProgress")

            bookRepository.saveReadingProgress(
                ReadingProgress(
                    bookId = bookId,
                    currentCfi = state.currentCfi,
                    chapterIndex = state.currentChapterIndex,
                    pageIndex = state.currentPageInChapter,
                    progressPercentage = overallProgress
                )
            )
        }
    }

    fun addBookmark() {
        viewModelScope.launch {
            val state = _uiState.value
            val chapterTitle = state.chapters.getOrNull(state.currentChapterIndex)?.title ?: "Chapter ${state.currentChapterIndex + 1}"
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
                totalPagesInChapter = 1
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

    fun onTtsIconButtonClicked() {
        val ttsSettings = ttsPreferencesManager.getSettings()
        if (!ttsSettings.isConfigured) {
            _uiState.update { it.copy(showTtsSetupBottomSheet = true) }
        } else {
            _uiState.update { it.copy(showTtsPlayerScreen = true) }
        }
    }

    fun dismissTtsSetupBottomSheet() {
        _uiState.update { it.copy(showTtsSetupBottomSheet = false) }
    }

    fun openTtsSetupBottomSheet() {
        _uiState.update { it.copy(showTtsSetupBottomSheet = true) }
    }

    fun openTtsPlayerScreen() {
        _uiState.update { it.copy(showTtsPlayerScreen = true) }
    }

    fun closeTtsPlayerScreen() {
        _uiState.update { it.copy(showTtsPlayerScreen = false) }
    }

    fun updateTtsSettings(newSettings: TtsSettings, ttsService: TtsService?) {
        ttsPreferencesManager.saveSettings(newSettings)
        ttsService?.updateSettings(newSettings)
    }

    fun onStartListeningFromSetup(newSettings: TtsSettings, ttsService: TtsService?) {
        ttsPreferencesManager.saveSettings(newSettings)
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
        val chunks = TtsTextParser.parseHtmlToChunks(html)
        val title = state.book?.title ?: "EpubPro Book"
        val author = state.book?.author ?: "Tác giả"

        // Retrieve the last saved chunk index for this specific book and chapter
        val savedChunkIndex = ttsPreferencesManager.getLastTtsChunkIndex(bookId, state.currentChapterIndex)
        val validIndex = savedChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        ttsService?.loadContent(
            id = bookId,
            title = _uiState.value.book?.title ?: "EpubPro",
            bookAuthor = _uiState.value.book?.author ?: "Unknown",
            parsedChunks = chunks,
            startIndex = validIndex,
            chapterIndex = state.currentChapterIndex,
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

    fun seekTtsChunk(index: Int, ttsService: TtsService?) {
        ttsService?.seekToChunk(index)
    }

    fun setSleepTimerOption(option: SleepTimerOption, ttsService: TtsService?) {
        _uiState.update { it.copy(selectedSleepTimer = option) }
        ttsService?.setSleepTimer(option)
    }
}
