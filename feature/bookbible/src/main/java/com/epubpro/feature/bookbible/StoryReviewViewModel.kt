package com.epubpro.feature.bookbible

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.BookBibleReviewEvent
import com.epubpro.domain.model.BookBibleReviewEventEdit
import com.epubpro.domain.repository.BookBibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Các thông báo ngắn sau khi một thao tác duyệt hoàn tất.
 */
enum class StoryReviewMessage {
    /** Một event đã được duyệt. */
    APPROVED,

    /** Một event đã bị từ chối. */
    REJECTED,

    /** Một event đã được cập nhật. */
    UPDATED,

    /** Tất cả event pending của sách đã được duyệt. */
    APPROVED_ALL
}

/**
 * Trạng thái hiển thị của màn hình duyệt tiến trình.
 *
 * @property bookId Mã sách đang được duyệt.
 * @property events Danh sách event pending của sách.
 * @property isLoading Cờ đang tải danh sách lần đầu hoặc sau khi làm mới.
 * @property isApprovingAll Cờ khóa thao tác duyệt toàn bộ.
 * @property busyEventIds Các event đang có request cập nhật.
 * @property errorMessage Lỗi gần nhất khi tải hoặc cập nhật dữ liệu.
 * @property message Thông báo thành công cần hiển thị một lần.
 */
data class StoryReviewUiState(
    val bookId: String,
    val events: List<BookBibleReviewEvent> = emptyList(),
    val isLoading: Boolean = true,
    val isApprovingAll: Boolean = false,
    val busyEventIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val message: StoryReviewMessage? = null
)

/**
 * ViewModel điều phối việc tải và xử lý hàng đợi duyệt event của một cuốn sách.
 */
@HiltViewModel
class StoryReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookBibleRepository: BookBibleRepository
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle[BOOK_ID_ARGUMENT])
    private val _uiState = MutableStateFlow(StoryReviewUiState(bookId = bookId))
    val uiState: StateFlow<StoryReviewUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadEvents()
    }

    /**
     * Tải lại danh sách event pending của sách hiện tại.
     */
    fun retry() {
        loadEvents()
    }

    /**
     * Duyệt một event và loại event đó khỏi hàng đợi pending sau khi backend xác nhận.
     *
     * @param event Event cần duyệt.
     * @param edit Dữ liệu value hoặc evidence người dùng muốn gửi kèm.
     */
    fun approve(event: BookBibleReviewEvent, edit: BookBibleReviewEventEdit = BookBibleReviewEventEdit()) {
        executeEventAction(event.eventId, StoryReviewMessage.APPROVED) {
            bookBibleRepository.approveReviewEvent(event.eventId, edit)
        }
    }

    /**
     * Cập nhật event nhưng giữ event trong danh sách để người dùng duyệt tiếp.
     *
     * @param event Event cần cập nhật.
     * @param edit Dữ liệu mới của event.
     */
    fun update(event: BookBibleReviewEvent, edit: BookBibleReviewEventEdit) {
        executeEventAction(event.eventId, StoryReviewMessage.UPDATED, removeFromList = false) {
            bookBibleRepository.updateReviewEvent(event.eventId, edit)
        }
    }

    /**
     * Từ chối một event và loại event đó khỏi hàng đợi pending.
     *
     * @param event Event cần từ chối.
     */
    fun reject(event: BookBibleReviewEvent) {
        executeEventAction(event.eventId, StoryReviewMessage.REJECTED) {
            bookBibleRepository.rejectReviewEvent(event.eventId)
        }
    }

    /**
     * Duyệt nhanh toàn bộ event pending của sách hiện tại.
     */
    fun approveAll() {
        if (_uiState.value.isApprovingAll || _uiState.value.events.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isApprovingAll = true, errorMessage = null, message = null) }
            bookBibleRepository.approveAllReviewEvents(bookId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            events = emptyList(),
                            isApprovingAll = false,
                            message = StoryReviewMessage.APPROVED_ALL
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isApprovingAll = false,
                            errorMessage = error.message ?: error.toString()
                        )
                    }
                }
        }
    }

    /**
     * Xóa thông báo thành công sau khi UI đã hiển thị.
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Tải danh sách event từ repository và thay thế dữ liệu đang hiển thị.
     */
    private fun loadEvents() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, message = null) }
            bookBibleRepository.getReviewEvents(bookId = bookId)
                .onSuccess { events ->
                    _uiState.update {
                        it.copy(events = events, isLoading = false, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: error.toString()
                        )
                    }
                }
        }
    }

    /**
     * Chạy một thao tác trên event, khóa đúng dòng đang xử lý và cập nhật state sau khi thành công.
     *
     * @param eventId Mã event cần thao tác.
     * @param successMessage Thông báo thành công tương ứng.
     * @param removeFromList Có loại event khỏi danh sách pending hay không.
     * @param action Request repository cần thực thi.
     */
    private fun executeEventAction(
        eventId: String,
        successMessage: StoryReviewMessage,
        removeFromList: Boolean = true,
        action: suspend () -> Result<BookBibleReviewEvent>
    ) {
        if (eventId in _uiState.value.busyEventIds) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busyEventIds = it.busyEventIds + eventId,
                    errorMessage = null,
                    message = null
                )
            }
            action()
                .onSuccess { updatedEvent ->
                    _uiState.update { state ->
                        val nextEvents = if (removeFromList) {
                            state.events.filterNot { it.eventId == eventId }
                        } else {
                            state.events.map { current ->
                                if (current.eventId == eventId) updatedEvent else current
                            }
                        }
                        state.copy(
                            events = nextEvents,
                            busyEventIds = state.busyEventIds - eventId,
                            message = successMessage
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            busyEventIds = it.busyEventIds - eventId,
                            errorMessage = error.message ?: error.toString()
                        )
                    }
                }
        }
    }

    /**
     * Hủy request tải dữ liệu khi ViewModel bị hủy khỏi back stack.
     */
    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val BOOK_ID_ARGUMENT = "bookId"
    }
}
