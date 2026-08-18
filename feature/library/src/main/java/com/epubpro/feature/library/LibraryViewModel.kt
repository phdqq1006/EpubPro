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

data class BookItemUiState(
    val book: Book,
    val currentChapter: Int = 0,
    val totalChapters: Int = 0,
    val progressPercentage: Float = 0f
)

data class LibraryUiState(
    val books: List<BookItemUiState> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val storageManager: EpubStorageManager,
    private val epubEngine: EpubEngine,
    private val snapshotStore: com.epubpro.core.storage.ReaderResumeSnapshotStore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.getAllBooks(),
        bookRepository.getAllReadingProgress(),
        _searchQuery
    ) { books, progressList, query ->
        val progressMap = progressList.associateBy { it.bookId }
        val items = books.map { book ->
            val progress = progressMap[book.id]
            val currentChapter = if (progress != null) progress.chapterIndex + 1 else 0
            val totalChapters = if (progress != null && progress.totalChapters > 0) {
                progress.totalChapters
            } else {
                book.totalChapters
            }
            val pct = progress?.progressPercentage ?: 0f
            BookItemUiState(
                book = book,
                currentChapter = currentChapter,
                totalChapters = totalChapters,
                progressPercentage = pct
            )
        }
        val filtered = if (query.isBlank()) items else items.filter {
            it.book.title.contains(query, ignoreCase = true) || it.book.author.contains(query, ignoreCase = true)
        }
        LibraryUiState(books = filtered, searchQuery = query)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBook(item: BookItemUiState) {
        viewModelScope.launch {
            epubEngine.deleteBookCache(item.book.filePath)
            snapshotStore.deleteSnapshot(item.book.id)
            storageManager.deleteBookFile(item.book.filePath)
            storageManager.deleteAiBookCache(item.book.id)
            bookRepository.deleteBook(item.book.id)
        }
    }
}
