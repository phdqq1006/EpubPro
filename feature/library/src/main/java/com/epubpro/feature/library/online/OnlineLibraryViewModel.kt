package com.epubpro.feature.library.online

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.epubpro.core.designsystem.R
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.worker.EpubImportScheduler
import com.epubpro.domain.model.Book
import com.epubpro.feature.library.UserMessage
import com.epubpro.domain.model.DownloadState
import com.epubpro.domain.model.OnlineNovelSummary
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnlineLibraryUiState(
    val novels: List<OnlineNovelSummary> = emptyList(),
    val filteredNovels: List<OnlineNovelSummary> = emptyList(),
    val availableGenres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    @StringRes val loadErrorRes: Int? = null,
    val downloadingNovels: Map<String, Int> = emptyMap(), // novelId -> percent
    val downloadedNovelIds: Set<String> = emptySet()
)

/**
 * Xác định các truyện online đã có trong thư viện local bằng định danh bền vững.
 *
 * Sách cũ chưa có onlineNovelId chỉ được đối chiếu theo cặp title/author khi cặp đó là duy nhất
 * ở cả hai nguồn, tránh đánh dấu nhầm các tác phẩm trùng tên.
 *
 * @param novels Danh sách truyện từ server.
 * @param localBooks Danh sách sách đang có trong Room.
 * @return Tập novelId đã được tải xuống thiết bị.
 */
internal fun resolveDownloadedNovelIds(
    novels: List<OnlineNovelSummary>,
    localBooks: List<Book>
): Set<String> {
    val persistedIds = localBooks.mapNotNull(Book::onlineNovelId).toSet()
    val legacyBooks = localBooks.filter { it.onlineNovelId == null }
    val legacyKeyCounts = legacyBooks.groupingBy {
        it.title.trim().lowercase() to it.author.trim().lowercase()
    }.eachCount()
    val onlineByKey = novels.groupBy {
        it.title.trim().lowercase() to it.author.trim().lowercase()
    }
    val inferredIds = onlineByKey.mapNotNull { (key, matches) ->
        matches.singleOrNull()?.novelId?.takeIf { legacyKeyCounts[key] == 1 }
    }
    return persistedIds + inferredIds
}

/**
 * Lọc truyện online theo từ khóa và thể loại hiện tại.
 *
 * @param novels Danh sách truyện nguồn.
 * @param query Từ khóa tìm kiếm theo title, author hoặc originalTitle.
 * @param genre Thể loại đang chọn, null nghĩa là không lọc thể loại.
 * @return Danh sách truyện thỏa mãn toàn bộ điều kiện.
 */
internal fun filterOnlineNovels(
    novels: List<OnlineNovelSummary>,
    query: String,
    genre: String?
): List<OnlineNovelSummary> {
    val normalizedQuery = query.trim()
    return novels.filter { novel ->
        val matchesQuery = normalizedQuery.isBlank() ||
            novel.title.contains(normalizedQuery, ignoreCase = true) ||
            novel.author.contains(normalizedQuery, ignoreCase = true) ||
            novel.originalTitle?.contains(normalizedQuery, ignoreCase = true) == true
        val matchesGenre = genre == null || novel.genres.contains(genre)
        matchesQuery && matchesGenre
    }
}

/**
 * ViewModel quản lý màn hình Kho Truyện Online, xử lý tải danh sách truyện,
 * tìm kiếm, lọc theo thể loại, tải truyện về Tủ Sách và chuẩn bị tải sách lên máy chủ.
 */
@HiltViewModel
class OnlineLibraryViewModel @Inject constructor(
    private val onlineNovelRepository: OnlineNovelRepository,
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val epubEngine: EpubEngine,
    private val epubImportScheduler: EpubImportScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineLibraryUiState(isLoading = true))
    val uiState: StateFlow<OnlineLibraryUiState> = _uiState.asStateFlow()

    private val _events = Channel<UserMessage>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var localBooks: List<Book> = emptyList()
    private var loadNovelsJob: Job? = null
    init {
        loadNovels()
        observeDownloadedBooks()
    }

    /**
     * Lắng nghe thay đổi danh sách sách trong thư viện cục bộ để cập nhật trạng thái đã tải.
     */
    private fun observeDownloadedBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                localBooks = books
                refreshDownloadedNovelIds()
            }
        }
    }

    /**
     * Tính lại trạng thái đã tải mỗi khi danh sách online hoặc thư viện local thay đổi.
     */
    private fun refreshDownloadedNovelIds() {
        _uiState.update { state ->
            state.copy(
                downloadedNovelIds = resolveDownloadedNovelIds(
                    novels = state.novels,
                    localBooks = localBooks
                )
            )
        }
    }

    /**
     * Tải danh sách truyện từ máy chủ backend hoặc làm mới lại danh mục truyện online.
     *
     * @param isRefresh True nếu là thao tác làm mới danh sách (kéo để làm mới hoặc bấm nút reload).
     */
    fun loadNovels(isRefresh: Boolean = false) {
        if (loadNovelsJob?.isActive == true) return

        loadNovelsJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = !isRefresh && state.novels.isEmpty(),
                    isRefreshing = isRefresh,
                    loadErrorRes = null
                )
            }

            val result = onlineNovelRepository.getNovels()
            val novels = result.getOrNull()
            if (novels != null) {
                val genres = novels.flatMap(OnlineNovelSummary::genres).distinct().sorted()
                _uiState.update { state ->
                    val selectedGenre = state.selectedGenre?.takeIf(genres::contains)
                    state.copy(
                        novels = novels,
                        filteredNovels = filterOnlineNovels(
                            novels = novels,
                            query = state.searchQuery,
                            genre = selectedGenre
                        ),
                        availableGenres = genres,
                        selectedGenre = selectedGenre,
                        isLoading = false,
                        isRefreshing = false,
                        loadErrorRes = null,
                        downloadedNovelIds = resolveDownloadedNovelIds(
                            novels = novels,
                            localBooks = localBooks
                        )
                    )
                }
            } else {
                val hasContent = _uiState.value.novels.isNotEmpty()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadErrorRes = if (hasContent) null else R.string.online_library_load_failed
                    )
                }
                if (hasContent) {
                    _events.send(UserMessage(R.string.online_library_refresh_failed))
                }
            }
        }
    }

    /**
     * Cập nhật từ khóa tìm kiếm và áp dụng bộ lọc lên danh sách truyện online.
     *
     * @param query Từ khóa tìm kiếm theo tên truyện, tác giả hoặc tên gốc.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    /**
     * Chọn hoặc bỏ chọn một thể loại truyện để lọc danh sách.
     *
     * @param genre Tên thể loại cần lọc, hoặc null nếu muốn hủy lọc thể loại.
     */
    fun onGenreSelected(genre: String?) {
        _uiState.update {
            it.copy(selectedGenre = if (it.selectedGenre == genre) null else genre)
        }
        applyFilter()
    }

    /**
     * Áp dụng đồng thời từ khóa tìm kiếm và thể loại đang chọn lên danh sách truyện nguồn.
     */
    private fun applyFilter() {
        _uiState.update { state ->
            state.copy(
                filteredNovels = filterOnlineNovels(
                    novels = state.novels,
                    query = state.searchQuery,
                    genre = state.selectedGenre
                )
            )
        }
    }

    /**
     * Tải file EPUB của bộ truyện online về máy, sau đó tự động nạp vào cơ sở dữ liệu Room và lập chỉ mục FTS.
     *
     * @param novel Đối tượng tóm tắt của bộ truyện cần tải xuống.
     */
    fun downloadNovel(novel: OnlineNovelSummary) {
        if (_uiState.value.downloadingNovels.containsKey(novel.novelId)) return

        _uiState.update { state ->
            state.copy(
                downloadingNovels = state.downloadingNovels + (novel.novelId to 0)
            )
        }
        viewModelScope.launch {
            val fileName = "${novel.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.epub"
            try {
                onlineNovelRepository.downloadEpub(novel.novelId, fileName).collect { downloadState ->
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            _uiState.update { state ->
                                state.copy(
                                    downloadingNovels = state.downloadingNovels +
                                        (novel.novelId to downloadState.progressPercent)
                                )
                            }
                        }
                        is DownloadState.Success -> {
                            importDownloadedNovel(
                                novel = novel,
                                filePath = downloadState.filePath
                            )
                        }
                        is DownloadState.Error -> {
                            _events.send(
                                UserMessage(
                                    textRes = R.string.online_library_download_failed,
                                    formatArgs = listOf(novel.title)
                                )
                            )
                        }
                        is DownloadState.Idle -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(
                    UserMessage(
                        textRes = R.string.online_library_download_failed,
                        formatArgs = listOf(novel.title)
                    )
                )
            } finally {
                _uiState.update { state ->
                    state.copy(
                        downloadingNovels = state.downloadingNovels - novel.novelId
                    )
                }
            }
        }
    }

    /**
     * Import file vừa tải vào Room, gắn novelId bền vững và lập chỉ mục tìm kiếm.
     *
     * @param novel Truyện online đang được tải.
     * @param filePath Đường dẫn file EPUB đã tải thành công.
     * @throws CancellationException Khi ViewModel bị hủy trong lúc import.
     */
    private suspend fun importDownloadedNovel(
        novel: OnlineNovelSummary,
        filePath: String
    ) {
        val file = File(filePath)
        val book = try {
            epubEngine.parseEpubMetadata(file).copy(onlineNovelId = novel.novelId)
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            throw error
        } catch (_: Exception) {
            withContext(Dispatchers.IO) { file.delete() }
            _events.send(
                UserMessage(
                    textRes = R.string.online_library_import_failed,
                    formatArgs = listOf(novel.title)
                )
            )
            return
        }

        try {
            bookRepository.insertBook(book)
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            throw error
        } catch (_: Exception) {
            withContext(Dispatchers.IO) { file.delete() }
            _events.send(
                UserMessage(
                    textRes = R.string.online_library_import_failed,
                    formatArgs = listOf(novel.title)
                )
            )
            return
        }

        val indexSucceeded = try {
            epubEngine.indexBookContent(file, book.id, searchRepository)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }

        _uiState.update { state ->
            state.copy(
                downloadedNovelIds = state.downloadedNovelIds + novel.novelId
            )
        }
        _events.send(
            UserMessage(
                textRes = if (indexSucceeded) {
                    R.string.online_library_download_success
                } else {
                    R.string.online_library_index_failed
                },
                formatArgs = listOf(novel.title)
            )
        )
    }

    /**
     * Chuẩn bị và lập lịch tải file EPUB lên máy chủ backend thông qua [EpubImportScheduler].
     *
     * @param uri URI của file EPUB trên thiết bị.
     * @param originalName Tên gốc của file được chọn.
     * @param isTranslated Đánh dấu truyện đã được dịch sang tiếng Việt hay chưa.
     */
    fun uploadEpub(uri: Uri, originalName: String?, isTranslated: Boolean) {
        if (_uiState.value.isUploading) return

        _uiState.update { it.copy(isUploading = true) }
        viewModelScope.launch {
            try {
                val enqueued = epubImportScheduler.enqueue(
                    uri = uri,
                    originalName = originalName,
                    isTranslated = isTranslated,
                    autoScanCharacters = true
                )
                _events.send(
                    UserMessage(
                        if (enqueued) {
                            R.string.upload_started_background
                        } else {
                            R.string.upload_already_running
                        }
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _events.send(UserMessage(R.string.upload_prepare_failed))
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }
}
