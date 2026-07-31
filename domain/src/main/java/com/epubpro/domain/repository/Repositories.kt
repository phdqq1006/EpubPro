package com.epubpro.domain.repository

import com.epubpro.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getBookById(id: String): Book?
    suspend fun insertBook(book: Book)
    suspend fun deleteBook(id: String)
    suspend fun updateLastRead(id: String, timestamp: Long)

    fun getReadingProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun saveReadingProgress(progress: ReadingProgress)
}

interface BookmarkRepository {
    fun getBookmarksForBook(bookId: String): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(id: String)

    fun getHighlightsForBook(bookId: String): Flow<List<Highlight>>
    suspend fun addHighlight(highlight: Highlight)
    suspend fun deleteHighlight(id: String)
}

interface SearchRepository {
    suspend fun searchInBook(bookId: String, query: String): List<SearchResultItem>
    suspend fun indexBookContent(bookId: String, chapters: List<Pair<Int, Pair<String, String>>>) // index: (title, textContent)
}
