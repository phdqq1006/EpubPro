package com.epubpro.feature.library.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.AiPreferencesManager
import com.epubpro.domain.model.DownloadState
import com.epubpro.domain.model.OnlineNovelDetail
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class NovelDetailUiState(
    val novelDetail: OnlineNovelDetail? = null,
    val isLoading: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadPercent: Int? = null,
    val translatingChapterIndex: Int? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null
)

@HiltViewModel
class OnlineNovelDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val onlineNovelRepository: OnlineNovelRepository,
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val epubEngine: EpubEngine,
    private val aiPreferencesManager: AiPreferencesManager
) : ViewModel() {

    private val novelId: String = checkNotNull(savedStateHandle["novelId"])

    private val _uiState = MutableStateFlow(NovelDetailUiState(isLoading = true))
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
        checkIfDownloaded()
    }

    private fun checkIfDownloaded() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { localBooks ->
                val currentTitle = _uiState.value.novelDetail?.title?.trim()?.lowercase()
                if (currentTitle != null) {
                    val downloaded = localBooks.any { it.title.trim().lowercase() == currentTitle }
                    _uiState.update { it.copy(isDownloaded = downloaded) }
                }
            }
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            onlineNovelRepository.getNovelDetail(novelId)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(novelDetail = detail, isLoading = false, errorMessage = null)
                    }
                    checkIfDownloaded()
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Không thể tải chi tiết truyện"
                        )
                    }
                }
        }
    }

    fun downloadFullEpub() {
        val detail = _uiState.value.novelDetail ?: return
        if (_uiState.value.downloadPercent != null) return

        viewModelScope.launch {
            val fileName = "${detail.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.epub"
            onlineNovelRepository.downloadEpub(detail.novelId, fileName).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.update { it.copy(downloadPercent = state.progressPercent) }
                    }
                    is DownloadState.Success -> {
                        _uiState.update {
                            it.copy(
                                downloadPercent = null,
                                isDownloaded = true,
                                userMessage = "Tải toàn bộ file EPUB thành công! Sách đã có trong Tủ Sách."
                            )
                        }
                        runCatching {
                            val file = File(state.filePath)
                            val book = epubEngine.parseEpubMetadata(file)
                            bookRepository.insertBook(book)
                            epubEngine.indexBookContent(file, book.id, searchRepository)
                        }
                    }
                    is DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                downloadPercent = null,
                                errorMessage = state.message
                            )
                        }
                    }
                    is DownloadState.Idle -> Unit
                }
            }
        }
    }

    fun translateChapter(
        chapterIndex: Int,
        provider: String = "gemini",
        model: String = "gemini-flash-latest"
    ) {
        val apiKey = aiPreferencesManager.getApiKey() ?: ""
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Vui lòng nhập API Key AI trong Cài đặt Cá nhân trước khi dịch.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(translatingChapterIndex = chapterIndex, errorMessage = null) }
            onlineNovelRepository.translateChapter(
                novelId = novelId,
                chapterIndex = chapterIndex,
                apiKey = apiKey,
                provider = provider,
                model = model
            ).onSuccess { res ->
                _uiState.update { state ->
                    val updatedChapters = state.novelDetail?.chapters?.map { ch ->
                        if (ch.chapterIndex == chapterIndex) {
                            res.chapter ?: ch.copy(status = "completed")
                        } else ch
                    }
                    val updatedDetail = state.novelDetail?.copy(
                        chapters = updatedChapters ?: emptyList(),
                        translatedChapters = (state.novelDetail.translatedChapters + 1).coerceAtMost(state.novelDetail.totalChapters)
                    )
                    state.copy(
                        translatingChapterIndex = null,
                        novelDetail = updatedDetail,
                        userMessage = "Dịch chương $chapterIndex thành công!"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        translatingChapterIndex = null,
                        errorMessage = "Dịch thất bại: ${err.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
