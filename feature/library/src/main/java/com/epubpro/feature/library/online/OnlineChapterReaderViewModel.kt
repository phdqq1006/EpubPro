package com.epubpro.feature.library.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.BookBibleSource
import com.epubpro.domain.model.BookBibleSourceType
import com.epubpro.domain.model.OnlineChapterContent
import com.epubpro.domain.repository.BookBibleRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái giao diện đọc chương online.
 */
data class ChapterReaderUiState(
    val novelId: String = "",
    val chapterIndex: Int = 1,
    val version: String = "translated",
    val content: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel quản lý đọc chương truyện online và tải ngầm bản gốc để gửi phân tích Book Bible.
 */
@HiltViewModel
class OnlineChapterReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val onlineNovelRepository: OnlineNovelRepository,
    private val bookBibleRepository: BookBibleRepository
) : ViewModel() {

    val novelId: String = checkNotNull(savedStateHandle["novelId"])
    val chapterIndex: Int = checkNotNull(savedStateHandle.get<String>("chapterIndex")?.toIntOrNull() ?: 1)

    private val _uiState = MutableStateFlow(
        ChapterReaderUiState(novelId = novelId, chapterIndex = chapterIndex, isLoading = true)
    )
    val uiState: StateFlow<ChapterReaderUiState> = _uiState.asStateFlow()

    init {
        loadContent("translated")
        enqueueOriginalForBookBible()
    }

    /**
     * Nạp nội dung chương truyện theo phiên bản yêu cầu (bản dịch hoặc bản gốc).
     *
     * @param version Phiên bản cần hiển thị ("translated" hoặc "original").
     */
    fun loadContent(version: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, version = version, errorMessage = null) }
            onlineNovelRepository.getChapterContent(novelId, chapterIndex, version)
                .onSuccess { contentDto ->
                    _uiState.update {
                        it.copy(
                            content = contentDto.content,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Không thể tải nội dung chương"
                        )
                    }
                }
        }
    }

    /**
     * Chuyển đổi qua lại giữa bản dịch tiếng Việt và bản gốc.
     */
    fun toggleVersion() {
        val nextVersion = if (_uiState.value.version == "translated") "original" else "translated"
        loadContent(nextVersion)
    }

    /**
     * Tải riêng bản gốc (original) trong luồng nền để gửi phân tích Book Bible, tuyệt đối không dùng bản dịch đang hiển thị.
     */
    private fun enqueueOriginalForBookBible() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val detailRes = onlineNovelRepository.getNovelDetail(novelId).getOrNull()
                val originalRes = onlineNovelRepository.getChapterContent(novelId, chapterIndex, "original").getOrNull()

                if (detailRes != null && originalRes != null && originalRes.content.isNotBlank()) {
                    val totalChapters = detailRes.totalChapters.takeIf { it > 0 }
                        ?: detailRes.chapters.size.takeIf { it > 0 }
                        ?: chapterIndex

                    bookBibleRepository.enqueueChapterSubmission(
                        source = BookBibleSource(BookBibleSourceType.ONLINE_NOVEL, novelId),
                        chapterNumber = chapterIndex,
                        chapterTitle = "Chương $chapterIndex",
                        totalChapters = totalChapters,
                        sourceContent = originalRes.content,
                        bookTitle = detailRes.title,
                        author = detailRes.author
                    )
                }
            }
        }
    }
}
