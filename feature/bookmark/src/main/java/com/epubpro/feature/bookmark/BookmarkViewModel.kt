package com.epubpro.feature.bookmark

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.Bookmark
import com.epubpro.domain.model.Highlight
import com.epubpro.domain.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarkUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val selectedTab: Int = 0
)

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val bookId: String = savedStateHandle.get<String>("bookId") ?: ""

    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<BookmarkUiState> = combine(
        if (bookId.isNotEmpty()) bookmarkRepository.getBookmarksForBook(bookId) else flowOf(emptyList()),
        if (bookId.isNotEmpty()) bookmarkRepository.getHighlightsForBook(bookId) else flowOf(emptyList()),
        _selectedTab
    ) { bookmarks, highlights, tab ->
        BookmarkUiState(bookmarks = bookmarks, highlights = highlights, selectedTab = tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookmarkUiState())

    fun onTabSelected(tab: Int) {
        _selectedTab.value = tab
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
        }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch {
            bookmarkRepository.deleteHighlight(id)
        }
    }
}
