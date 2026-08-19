package com.epubpro.feature.bookbible

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.BookBibleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái giao diện tổng hợp cho màn hình Book Bible.
 *
 * @property source Nguồn sách hiện tại.
 * @property chapterNumber Mốc chương hiện tại (1-based index).
 * @property snapshot Dữ liệu snapshot hồ sơ nhân vật.
 * @property isLoading Cờ đang nạp dữ liệu ban đầu.
 * @property isRefreshing Cờ đang làm mới từ mạng ngầm.
 * @property isPolling Cờ đang tự động thăm dò (polling) trạng thái phân tích AI.
 * @property selectedCharacterId Mã nhân vật đang được chọn xem chi tiết (null nếu ở màn danh sách).
 * @property selectedTab Tab đang chọn (0: Hồ sơ, 1: Dòng thời gian).
 * @property selectedTimeline Dữ liệu dòng thời gian của nhân vật đang chọn.
 * @property isLoadingTimeline Cờ đang nạp dòng thời gian.
 * @property errorMessage Thông điệp lỗi nếu có.
 */
data class BookBibleUiState(
    val source: BookBibleSource = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, ""),
    val chapterNumber: Int = 1,
    val snapshot: BookBibleSnapshot? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPolling: Boolean = false,
    val selectedCharacterId: String? = null,
    val selectedTab: Int = 0,
    val selectedTimeline: CharacterTimeline? = null,
    val isLoadingTimeline: Boolean = false,
    val errorMessage: String? = null
) {
    /** Nhân vật đang được chọn xem chi tiết */
    val selectedCharacter: CharacterProfile?
        get() = snapshot?.characters?.find { it.id == selectedCharacterId }

    /** Danh sách nhân vật đã sắp xếp: Luôn ưu tiên Nhân vật chính lên đầu tiên, sau đó đến nhân vật có tiến triển ở chương hiện tại, và theo tên */
    val sortedCharacters: List<CharacterProfile>
        get() = snapshot?.characters?.sortedWith(
            compareByDescending<CharacterProfile> { it.isProtagonist }
                .thenByDescending { it.changedInCurrentChapter }
                .thenBy { it.name }
        ) ?: emptyList()
}

/**
 * ViewModel quản lý logic và trạng thái màn hình Book Bible chống spoiler.
 */
@HiltViewModel
class BookBibleViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val bookBibleRepository: BookBibleRepository
) : ViewModel() {

    private val sourceTypeStr: String = checkNotNull(savedStateHandle["sourceType"])
    private val sourceId: String = checkNotNull(savedStateHandle["sourceId"])
    private val chapterNumber: Int = checkNotNull(
        savedStateHandle.get<String>("chapterNumber")?.toIntOrNull()
            ?: savedStateHandle.get<Int>("chapterNumber")
            ?: 1
    )

    private val source = BookBibleSource(
        type = runCatching { BookBibleSourceType.valueOf(sourceTypeStr) }.getOrDefault(BookBibleSourceType.LOCAL_EPUB),
        sourceId = sourceId
    )

    private val _uiState = MutableStateFlow(
        BookBibleUiState(
            source = source,
            chapterNumber = chapterNumber,
            selectedCharacterId = savedStateHandle[KEY_SELECTED_CHARACTER_ID],
            selectedTab = savedStateHandle[KEY_SELECTED_TAB] ?: 0
        )
    )
    val uiState: StateFlow<BookBibleUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var timelineJob: Job? = null

    init {
        observeCachedSnapshot()
        refreshSnapshot()
    }

    /**
     * Quan sát dữ liệu snapshot từ bộ nhớ đệm Room DB (Cache-first).
     */
    private fun observeCachedSnapshot() {
        viewModelScope.launch {
            bookBibleRepository.observeSnapshot(source, chapterNumber).collect { cached ->
                _uiState.update { state ->
                    state.copy(
                        snapshot = cached ?: state.snapshot,
                        isLoading = cached == null && state.isLoading
                    )
                }
                if (cached != null && (cached.status == SnapshotStatus.PROCESSING || (cached.status == SnapshotStatus.PARTIAL && cached.characters.isEmpty()))) {
                    startPolling()
                } else {
                    stopPolling()
                }
            }
        }
    }

    /**
     * Làm mới dữ liệu snapshot từ máy chủ backend qua mạng.
     */
    fun refreshSnapshot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            bookBibleRepository.refreshSnapshot(source, chapterNumber)
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    if (snapshot.status == SnapshotStatus.PROCESSING || (snapshot.status == SnapshotStatus.PARTIAL && snapshot.characters.isEmpty())) {
                        startPolling()
                    } else {
                        stopPolling()
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = if (it.snapshot == null) err.message else null
                        )
                    }
                }
        }
    }

    /**
     * Chọn một nhân vật để xem chi tiết hoặc quay lại danh sách (khi truyền `null`).
     *
     * @param characterId Mã định danh nhân vật hoặc `null`.
     */
    fun selectCharacter(characterId: String?) {
        savedStateHandle[KEY_SELECTED_CHARACTER_ID] = characterId
        _uiState.update { it.copy(selectedCharacterId = characterId) }
        if (characterId != null && _uiState.value.selectedTab == 1) {
            loadTimeline(characterId)
        }
    }

    /**
     * Chuyển đổi giữa các tab trong màn hình chi tiết nhân vật (0: Hồ sơ, 1: Dòng thời gian).
     *
     * @param tabIndex Chỉ số tab (0 hoặc 1).
     */
    fun selectTab(tabIndex: Int) {
        savedStateHandle[KEY_SELECTED_TAB] = tabIndex
        _uiState.update { it.copy(selectedTab = tabIndex) }
        val charId = _uiState.value.selectedCharacterId
        if (tabIndex == 1 && charId != null) {
            loadTimeline(charId)
        }
    }

    /**
     * Nạp dữ liệu dòng thời gian tiến trình của một nhân vật.
     *
     * @param characterId Mã định danh nhân vật.
     */
    fun loadTimeline(characterId: String) {
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTimeline = true) }
            bookBibleRepository.getCharacterTimeline(source, characterId, chapterNumber)
                .onSuccess { timeline ->
                    _uiState.update {
                        it.copy(
                            selectedTimeline = timeline,
                            isLoadingTimeline = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingTimeline = false) }
                }
        }
    }

    /**
     * Bắt đầu tiến trình thăm dò (polling) định kỳ mỗi 2 giây tối đa 60 giây khi dữ liệu đang ở trạng thái PROCESSING.
     */
    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true) }
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < MAX_POLLING_DURATION_MS) {
                delay(POLLING_INTERVAL_MS)
                bookBibleRepository.refreshSnapshot(source, chapterNumber)
                    .onSuccess { res ->
                        _uiState.update { it.copy(snapshot = res) }
                        if (res.status != SnapshotStatus.PROCESSING) {
                            stopPolling()
                            return@launch
                        }
                    }
            }
            stopPolling()
        }
    }

    /**
     * Dừng tiến trình thăm dò polling.
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    companion object {
        private const val KEY_SELECTED_CHARACTER_ID = "selected_character_id"
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val POLLING_INTERVAL_MS = 2000L
        private const val MAX_POLLING_DURATION_MS = 60000L
    }
}
