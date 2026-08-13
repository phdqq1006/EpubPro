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
    fun getAllReadingProgress(): Flow<List<ReadingProgress>>
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
interface AiRuleRepository {
    fun observeRulesForBook(bookId: String): Flow<List<AiRule>>
    suspend fun getRulesForBook(bookId: String): List<AiRule>
    suspend fun upsertRule(rule: AiRule)
    suspend fun deleteRule(ruleId: String)
}

interface AiChapterRepository {
    suspend fun getChapterCache(bookId: String, chapterIndex: Int): AiChapterCache?
    suspend fun upsertChapterCache(cache: AiChapterCache)
    suspend fun deleteChapterCache(bookId: String, chapterIndex: Int)
    suspend fun deleteBookCaches(bookId: String)
}
