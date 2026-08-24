package com.epubpro.feature.library.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.storage.AiPreferencesManager
import com.epubpro.core.storage.worker.OnlineNovelDownloadScheduler
import com.epubpro.core.storage.worker.OnlineNovelDownloadWorker
import com.epubpro.domain.model.OnlineNovelDetail
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.work.WorkInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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
    private val aiPreferencesManager: AiPreferencesManager,
    private val onlineDownloadScheduler: OnlineNovelDownloadScheduler
) : ViewModel() {

    private val novelId: String = checkNotNull(savedStateHandle["novelId"])

    private val _uiState = MutableStateFlow(NovelDetailUiState(isLoading = true))
    val uiState: StateFlow<NovelDetailUiState> = _uiState.asStateFlow()

    private var checkDownloadedJob: Job? = null
    private var activeDownloadWorkId: UUID? = null

    init {
        loadDetail()
        checkIfDownloaded()
        observeDownloadWork()
    }

    /**
     * Kiểm tra sách đã tải theo onlineNovelId; sách cũ chưa có ID được đối chiếu thêm title/author.
     */
    private fun checkIfDownloaded() {
        checkDownloadedJob?.cancel()
        checkDownloadedJob = viewModelScope.launch {
            bookRepository.getAllBooks().collect { localBooks ->
                val detail = _uiState.value.novelDetail
                val downloaded = localBooks.any { book ->
                    book.onlineNovelId == novelId ||
                        (book.onlineNovelId == null &&
                            detail != null &&
                            book.title.trim().equals(detail.title.trim(), ignoreCase = true) &&
                            book.author.trim().equals(detail.author.trim(), ignoreCase = true))
                }
                _uiState.update { it.copy(isDownloaded = downloaded) }
            }
        }
    }

    /**
     * Quan sát WorkManager để hiển thị progress và kết quả ngay cả khi màn hình được mở lại.
     */
    private fun observeDownloadWork() {
        viewModelScope.launch {
            onlineDownloadScheduler.observeNovel(novelId).collect { workInfos ->
                val workInfo = workInfos.firstOrNull { !it.state.isFinished }
                    ?: workInfos.maxByOrNull { it.runAttemptCount }
                if (workInfo == null) return@collect

                if (!workInfo.state.isFinished) {
                    val progress = workInfo.progress.getInt(
                        OnlineNovelDownloadWorker.KEY_PROGRESS,
                        0
                    )
                    _uiState.update { it.copy(downloadPercent = progress) }
                    return@collect
                }

                if (workInfo.id != activeDownloadWorkId) return@collect
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> _uiState.update {
                        it.copy(
                            downloadPercent = null,
                            isDownloaded = true,
                            userMessage = "Tải toàn bộ file EPUB thành công! Sách đã có trong Tủ Sách."
                        )
                    }

                    WorkInfo.State.FAILED -> _uiState.update {
                        it.copy(
                            downloadPercent = null,
                            errorMessage = workInfo.outputData.getString(
                                OnlineNovelDownloadWorker.KEY_ERROR_MESSAGE
                            ) ?: "Không thể tải file EPUB"
                        )
                    }

                    WorkInfo.State.CANCELLED -> _uiState.update {
                        it.copy(downloadPercent = null)
                    }

                    else -> Unit
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
     * Lập lịch tải toàn bộ EPUB trong Worker foreground và giữ checkpoint để retry.
     */
    fun downloadFullEpub() {
        val detail = _uiState.value.novelDetail ?: return
        if (_uiState.value.downloadPercent != null) return

        _uiState.update { it.copy(downloadPercent = 0, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                onlineDownloadScheduler.enqueue(
                    novelId = detail.novelId,
                    title = detail.title,
                    author = detail.author
                )
            }.onSuccess { workId ->
                activeDownloadWorkId = workId
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        downloadPercent = null,
                        errorMessage = error.message ?: "Không thể lập lịch tải file EPUB"
                    )
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
