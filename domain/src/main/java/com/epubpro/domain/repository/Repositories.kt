package com.epubpro.domain.repository

import com.epubpro.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getBookById(id: String): Book?
    suspend fun insertBook(book: Book)
    suspend fun deleteBook(id: String)
    suspend fun updateLastRead(id: String, timestamp: Long)

    /**
     * Cập nhật đường dẫn cover nếu sách vẫn giữ đúng file nguồn và chưa có cover.
     *
     * @param id Định danh sách.
     * @param filePath Đường dẫn file EPUB tại thời điểm quét.
     * @param coverPath Đường dẫn cover đã trích xuất.
     * @return true nếu bản ghi vẫn hợp lệ và đã được cập nhật.
     */
    suspend fun updateCoverPathIfMissing(id: String, filePath: String, coverPath: String): Boolean

    fun getReadingProgress(bookId: String): Flow<ReadingProgress?>
    fun getAllReadingProgress(): Flow<List<ReadingProgress>>
    suspend fun saveReadingProgress(progress: ReadingProgress)

    /**
     * Lấy cuốn sách có thời điểm đọc gần nhất ([Book.lastReadAt] > 0).
     *
     * @return Đối tượng [Book] đọc gần nhất hoặc null nếu chưa có cuốn sách nào được đọc.
     */
    suspend fun getLatestReadBook(): Book?

    /**
     * Lấy trực tiếp tiến độ đọc của một cuốn sách.
     *
     * @param bookId Định danh cuốn sách.
     * @return Bản ghi [ReadingProgress] hoặc null nếu chưa lưu tiến độ.
     */
    suspend fun getReadingProgressDirect(bookId: String): ReadingProgress?
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
    suspend fun clearIndexForBook(bookId: String)
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
