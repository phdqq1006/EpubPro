package com.epubpro.feature.library.online

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.AiPreferencesManager
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.DownloadState
import com.epubpro.domain.model.OnlineNovelSummary
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class OnlineLibraryUiState(
    val novels: List<OnlineNovelSummary> = emptyList(),
    val filteredNovels: List<OnlineNovelSummary> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val downloadingNovels: Map<String, Int> = emptyMap(), // novelId -> percent
    val downloadedNovelIds: Set<String> = emptySet()
)

@HiltViewModel
class OnlineLibraryViewModel @Inject constructor(
    private val onlineNovelRepository: OnlineNovelRepository,
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val aiPreferencesManager: AiPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineLibraryUiState(isLoading = true))
    val uiState: StateFlow<OnlineLibraryUiState> = _uiState.asStateFlow()

    init {
        loadNovels()
        observeDownloadedBooks()
    }

    private fun observeDownloadedBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { localBooks ->
                val localTitles = localBooks.map { it.title.trim().lowercase() }.toSet()
                _uiState.update { state ->
                    val downloadedIds = state.novels.filter {
                        localTitles.contains(it.title.trim().lowercase())
                    }.map { it.novelId }.toSet()
                    state.copy(downloadedNovelIds = downloadedIds)
                }
            }
        }
    }

    fun loadNovels(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh && it.novels.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            onlineNovelRepository.getNovels()
                .onSuccess { novels ->
                    val genres = novels.flatMap { it.genres }.distinct().sorted()
                    _uiState.update { state ->
                        state.copy(
                            novels = novels,
                            availableGenres = genres,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    applyFilter()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Không thể kết nối đến máy chủ"
                        )
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun onGenreSelected(genre: String?) {
        _uiState.update {
            it.copy(selectedGenre = if (it.selectedGenre == genre) null else genre)
        }
        applyFilter()
    }

    private fun applyFilter() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        val genre = state.selectedGenre

        val filtered = state.novels.filter { novel ->
            val matchesQuery = query.isBlank() ||
                    novel.title.contains(query, ignoreCase = true) ||
                    novel.author.contains(query, ignoreCase = true) ||
                    (novel.originalTitle?.contains(query, ignoreCase = true) == true)
            
            val matchesGenre = genre == null || novel.genres.contains(genre)
            matchesQuery && matchesGenre
        }

        _uiState.update { it.copy(filteredNovels = filtered) }
    }

    fun downloadNovel(novel: OnlineNovelSummary) {
        if (_uiState.value.downloadingNovels.containsKey(novel.novelId)) return

        viewModelScope.launch {
            val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.epub"
            onlineNovelRepository.downloadEpub(novel.novelId, fileName).collect { downloadState ->
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        _uiState.update { state ->
                            state.copy(
                                downloadingNovels = state.downloadingNovels + (novel.novelId to downloadState.progressPercent)
                            )
                        }
                    }
                    is DownloadState.Success -> {
                        _uiState.update { state ->
                            state.copy(
                                downloadingNovels = state.downloadingNovels - novel.novelId,
                                downloadedNovelIds = state.downloadedNovelIds + novel.novelId,
                                userMessage = "Đã tải \"${novel.title}\" vào Tủ Sách thành công!"
                            )
                        }
                        // Import into local Room database & index FTS
                        runCatching {
                            val file = File(downloadState.filePath)
                            val book = epubEngine.parseEpubMetadata(file)
                            bookRepository.insertBook(book)
                            epubEngine.indexBookContent(file, book.id, searchRepository)
                        }
                    }
                    is DownloadState.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                downloadingNovels = state.downloadingNovels - novel.novelId,
                                errorMessage = downloadState.message
                            )
                        }
                    }
                    is DownloadState.Idle -> Unit
                }
            }
        }
    }

    fun uploadEpub(uri: Uri, originalName: String?, isTranslated: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tempFile = storageManager.importEpubFromUri(uri, originalName)
                onlineNovelRepository.uploadEpub(tempFile.absolutePath, isTranslated)
                    .onSuccess { msg ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                userMessage = "Tải sách lên server thành công!"
                            )
                        }
                        loadNovels(isRefresh = true)
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Upload thất bại: ${err.message}"
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Lỗi xử lý file: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
