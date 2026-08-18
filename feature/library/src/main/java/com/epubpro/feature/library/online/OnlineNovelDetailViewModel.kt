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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Trạng thái giao diện của màn hình Chi Tiết Truyện Online.
 *
 * @property novelDetail Dữ liệu chi tiết truyện và danh sách các chương.
 * @property isLoading Cờ cho biết đang tải dữ liệu từ server.
 * @property isDownloaded Cờ cho biết sách đã được tải về máy hay chưa.
 * @property downloadPercent Phần trăm tiến độ tải file EPUB (null nếu không trong tiến trình tải).
 * @property translatingChapterIndex Vị trí chương đang được AI dịch (null nếu không có chương nào đang dịch).
 * @property errorMessage Thông báo lỗi khi gọi API.
 * @property userMessage Thông báo thành công hiển thị cho người dùng.
 */
data class NovelDetailUiState(
    val novelDetail: OnlineNovelDetail? = null,
    val isLoading: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadPercent: Int? = null,
    val translatingChapterIndex: Int? = null,
    val errorMessage: String? = null,
    val userMessage: String? = null
)

/**
 * ViewModel quản lý logic và trạng thái màn hình Chi Tiết Truyện Online.
 */
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

    private var checkDownloadedJob: Job? = null

    init {
        loadDetail()
        checkIfDownloaded()
    }

    /**
     * Kiểm tra xem cuốn truyện hiện tại đã có trong cơ sở dữ liệu Tủ Sách nội bộ hay chưa.
     * Tự động hủy collector trước đó để tránh rò rỉ hoặc chồng chéo coroutine.
     */
    private fun checkIfDownloaded() {
        checkDownloadedJob?.cancel()
        checkDownloadedJob = viewModelScope.launch {
            bookRepository.getAllBooks().collect { localBooks ->
                val currentTitle = _uiState.value.novelDetail?.title?.trim()?.lowercase()
                if (currentTitle != null) {
                    val downloaded = localBooks.any { it.title.trim().lowercase() == currentTitle }
                    _uiState.update { it.copy(isDownloaded = downloaded) }
                }
            }
        }
    }

    /**
     * Tải thông tin chi tiết và mục lục chương của bộ truyện từ server backend.
     */
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

    /**
     * Tải toàn bộ file EPUB của bộ truyện về máy và tự động nạp vào Tủ Sách offline.
     */
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

    /**
     * Yêu cầu máy chủ backend gọi AI dịch một chương cụ thể.
     *
     * @param chapterIndex Thứ tự của chương cần dịch (1-indexed).
     * @param provider Nhà cung cấp AI (mặc định: `gemini`).
     * @param model Tên model AI sử dụng (mặc định: `gemini-flash-latest`).
     */
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

    /**
     * Xóa các thông báo lỗi và thông báo trạng thái hiện tại.
     */
    fun clearMessages() {
        _uiState.update { it.copy(userMessage = null, errorMessage = null) }
    }
}
