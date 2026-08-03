package com.epubpro.feature.reader

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.EpubChapterHeader
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.BookmarkRepository
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.core.reader.tts.TtsTextParser
import com.epubpro.core.storage.TtsPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<EpubChapterHeader> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentChapterHtml: String = "",
    val currentPageInChapter: Int = 1,
    val totalPagesInChapter: Int = 1,
    val currentCfi: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val showControls: Boolean = true,
    val isLoading: Boolean = true,
    val isTtsSpeaking: Boolean = false,
    val showTtsSetupBottomSheet: Boolean = false,
    val showTtsPlayerScreen: Boolean = false,
    val ttsSettings: TtsSettings = TtsSettings(),
    val ttsPlayerState: TtsPlayerState = TtsPlayerState.Idle,
    val selectedSleepTimer: SleepTimerOption = SleepTimerOption.OFF
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val preferencesManager: ReaderPreferencesManager,
    private val ttsPreferencesManager: TtsPreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private var bookFile: File? = null

    private val _uiState = MutableStateFlow(ReaderUiState(settings = preferencesManager.getSettings()))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val initialTtsSettings = ttsPreferencesManager.getSettings()
        _uiState.update { it.copy(ttsSettings = initialTtsSettings) }

        viewModelScope.launch {
            TtsService.playerState.collect { state ->
                if (state is TtsPlayerState.Playing) {
                    val chunkIndex = state.currentChunk.paragraphIndex
                    ttsPreferencesManager.saveLastTtsChunkIndex(bookId, _uiState.value.currentChapterIndex, chunkIndex)
                }
                _uiState.update {
                    it.copy(
                        ttsPlayerState = state,
                        isTtsSpeaking = state is TtsPlayerState.Playing
                    )
                }
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

                val initialHtml = if (headers.isNotEmpty()) {
                    epubEngine.loadChapterHtml(file, headers[initialIndex].entryName)
                } else ""

                _uiState.update {
                    it.copy(
                        book = book,
                        chapters = headers,
                        currentChapterIndex = initialIndex,
                        currentChapterHtml = initialHtml,
                        currentPageInChapter = initialPage,
                        currentCfi = savedProgress?.currentCfi ?: "",
                        settings = savedSettings,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onChapterSelected(index: Int) {
        val state = _uiState.value
        val file = bookFile
        if (file != null && index in 0 until state.chapters.size) {
            viewModelScope.launch {
                val header = state.chapters[index]
                val html = epubEngine.loadChapterHtml(file, header.entryName)
                _uiState.update {
                    it.copy(
                        currentChapterIndex = index,
                        currentChapterHtml = html,
                        currentPageInChapter = 1,
                        totalPagesInChapter = 1
                    )
                }
                saveProgress()
            }
        }
    }

    fun nextChapter() {
        onChapterSelected(_uiState.value.currentChapterIndex + 1)
    }

    fun previousChapter() {
        onChapterSelected(_uiState.value.currentChapterIndex - 1)
    }

    fun updatePageMetrics(currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) {
        val currentState = _uiState.value
        if (currentState.isLoading) return
        
        // Save the visible chunk index for TTS resuming
        if (!currentState.chapters.isEmpty()) {
            ttsPreferencesManager.saveLastTtsChunkIndex(bookId, currentState.currentChapterIndex, firstVisibleChunkIndex)
        }
        
        if (currentState.currentPageInChapter == currentPage && currentState.totalPagesInChapter == totalPages) {
            return
        }
        _uiState.update {
            it.copy(
                currentPageInChapter = currentPage,
                totalPagesInChapter = totalPages
            )
        }
        saveProgress()
    }

    fun updateSettings(newSettings: ReaderSettings) {
        _uiState.update { it.copy(settings = newSettings) }
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
        val html = state.currentChapterHtml
        val chunks = TtsTextParser.parseHtmlToChunks(html)
        val title = state.book?.title ?: "EpubPro Book"
        val author = state.book?.author ?: "Tác giả"

        // Retrieve the last saved chunk index for this specific book and chapter
        val savedChunkIndex = ttsPreferencesManager.getLastTtsChunkIndex(bookId, state.currentChapterIndex)
        val validIndex = savedChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        ttsService?.loadContent(
            title = title,
            bookAuthor = author,
            parsedChunks = chunks,
            startIndex = validIndex
        )
    }

    fun toggleTtsPlayback(ttsService: TtsService?) {
        val state = _uiState.value
        when (state.ttsPlayerState) {
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
