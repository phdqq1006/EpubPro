package com.epubpro.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.EpubEngine
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.Book
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val message: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val onlineNovelRepository: com.epubpro.domain.repository.OnlineNovelRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.getAllBooks(),
        _searchQuery,
        _userMessage
    ) { books, query, msg ->
        val filtered = if (query.isBlank()) books else books.filter {
            it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
        }
        LibraryUiState(books = filtered, searchQuery = query, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState(isLoading = true))

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun importEpub(uri: Uri, originalName: String?) {
        viewModelScope.launch {
            try {
                val file = storageManager.importEpubFromUri(uri, originalName)
                val book = epubEngine.parseEpubMetadata(file)
                bookRepository.insertBook(book)

                // Memory-safe streaming background FTS indexer
                epubEngine.indexBookContent(file, book.id, searchRepository)
                _userMessage.value = "Đã nạp sách \"${book.title}\" thành công!"
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Lỗi khi nạp file EPUB: ${e.message}"
            }
        }
    }

    fun uploadEpubToServer(uri: Uri, originalName: String?) {
        viewModelScope.launch {
            try {
                val tempFile = storageManager.importEpubFromUri(uri, originalName)
                onlineNovelRepository.uploadEpub(tempFile.absolutePath, isTranslated = true)
                    .onSuccess {
                        _userMessage.value = "Đã tải sách lên server thành công!"
                    }
                    .onFailure {
                        _userMessage.value = "Tải lên server thất bại: ${it.message}"
                    }
            } catch (e: Exception) {
                _userMessage.value = "Lỗi xử lý file upload: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            storageManager.deleteBookFile(book.filePath)
            storageManager.deleteAiBookCache(book.id)
            bookRepository.deleteBook(book.id)
        }
    }
}
