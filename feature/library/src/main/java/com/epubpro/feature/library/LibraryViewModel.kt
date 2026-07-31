package com.epubpro.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.reader.engine.ReadiumEngine
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.Book
import com.epubpro.domain.repository.BookRepository
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.ReaderEngineType

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val searchRepository: SearchRepository,
    private val storageManager: EpubStorageManager,
    private val readiumEngine: ReadiumEngine,
    val preferencesManager: ReaderPreferencesManager
) : ViewModel() {

    fun isEngineConfigured(): Boolean = preferencesManager.isEngineConfigured()

    fun getSavedEngineType(): ReaderEngineType = preferencesManager.getSettings().engineType

    fun saveEnginePreference(engineType: ReaderEngineType) {
        val currentSettings = preferencesManager.getSettings()
        preferencesManager.saveSettings(currentSettings.copy(engineType = engineType))
    }

    fun onBookCardClicked(
        book: Book,
        onOpenDirectly: (Book, ReaderEngineType) -> Unit,
        onShowSelectionSheet: (Book) -> Unit
    ) {
        if (isEngineConfigured()) {
            onOpenDirectly(book, getSavedEngineType())
        } else {
            onShowSelectionSheet(book)
        }
    }

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(
        bookRepository.getAllBooks(),
        _searchQuery
    ) { books, query ->
        val filtered = if (query.isBlank()) books else books.filter {
            it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
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
                val book = readiumEngine.parseEpubMetadata(file)
                bookRepository.insertBook(book)

                // Memory-safe streaming background FTS indexer
                readiumEngine.indexBookContent(file, book.id, searchRepository)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            storageManager.deleteBookFile(book.filePath)
            bookRepository.deleteBook(book.id)
        }
    }
}
