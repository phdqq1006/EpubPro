package com.epubpro.core.database.dao

import androidx.room.*
import com.epubpro.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    @Query("UPDATE books SET lastReadAt = :timestamp WHERE id = :id")
    suspend fun updateLastRead(id: String, timestamp: Long)

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    fun getReadingProgress(bookId: String): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingProgress(progress: ReadingProgressEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getHighlightsForBook(bookId: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: String)
}

data class FtsSearchResult(
    val bookId: String,
    val chapterIndex: String,
    val chapterTitle: String,
    val textContent: String,
    val snippet: String
)

@Dao
interface SearchDao {
    @Query("""
        SELECT bookId, chapterIndex, chapterTitle, textContent, snippet(book_search_fts, '<b>', '</b>', '...', -1, 30) as snippet 
        FROM book_search_fts 
        WHERE book_search_fts MATCH :query AND bookId = :bookId
    """)
    suspend fun searchInBook(bookId: String, query: String): List<FtsSearchResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchIndex(indices: List<BookSearchEntity>)

    @Query("DELETE FROM book_search_fts WHERE bookId = :bookId")
    suspend fun clearIndexForBook(bookId: String)
}
@Dao
interface AiRuleDao {
    @Query("""
        SELECT * FROM ai_rules
        WHERE scope = 'GLOBAL' OR (scope = 'BOOK' AND bookId = :bookId)
        ORDER BY scope ASC, source COLLATE NOCASE
    """)
    fun observeRulesForBook(bookId: String): Flow<List<AiRuleEntity>>

    @Query("""
        SELECT * FROM ai_rules
        WHERE scope = 'GLOBAL' OR (scope = 'BOOK' AND bookId = :bookId)
        ORDER BY scope ASC, source COLLATE NOCASE
    """)
    suspend fun getRulesForBook(bookId: String): List<AiRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: AiRuleEntity)

    @Query("DELETE FROM ai_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)
}

@Dao
interface AiChapterDao {
    @Query("SELECT * FROM ai_chapter_cache WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun getChapterCache(bookId: String, chapterIndex: Int): AiChapterCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterCache(cache: AiChapterCacheEntity)

    @Query("DELETE FROM ai_chapter_cache WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun deleteChapterCache(bookId: String, chapterIndex: Int)

    @Query("DELETE FROM ai_chapter_cache WHERE bookId = :bookId")
    suspend fun deleteBookCaches(bookId: String)
}
