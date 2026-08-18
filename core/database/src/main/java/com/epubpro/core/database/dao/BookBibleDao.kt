package com.epubpro.core.database.dao

import androidx.room.*
import com.epubpro.core.database.entity.BookBibleEditionEntity
import com.epubpro.core.database.entity.BookBibleSnapshotEntity
import com.epubpro.core.database.entity.BookBibleSubmissionEntity
import com.epubpro.core.database.entity.BookBibleTimelineEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object quản lý toàn bộ thao tác truy vấn và lưu trữ dữ liệu Book Bible trong Room Database.
 */
@Dao
interface BookBibleDao {

    // --- Edition Mappings ---

    /**
     * Lưu thông tin ánh xạ Edition của sách vào cơ sở dữ liệu.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdition(edition: BookBibleEditionEntity)

    /**
     * Tìm kiếm thông tin ánh xạ Edition theo khóa nguồn sách cục bộ.
     *
     * @param localSourceKey Khóa nguồn sách (ví dụ: `LOCAL_EPUB:book_123`).
     * @return [BookBibleEditionEntity] hoặc `null` nếu chưa có ánh xạ.
     */
    @Query("SELECT * FROM book_bible_editions WHERE localSourceKey = :localSourceKey LIMIT 1")
    suspend fun getEditionByLocalSourceKey(localSourceKey: String): BookBibleEditionEntity?

    // --- Submissions ---

    /**
     * Lưu hoặc cập nhật một bản ghi yêu cầu gửi chương nguồn.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceSubmission(submission: BookBibleSubmissionEntity)

    /**
     * Lấy thông tin bản ghi submission theo ID xác định.
     */
    @Query("SELECT * FROM book_bible_submissions WHERE id = :id LIMIT 1")
    suspend fun getSubmissionById(id: String): BookBibleSubmissionEntity?

    /**
     * Lấy thông tin submission gần nhất của một chương theo nguồn và số chương.
     */
    @Query("SELECT * FROM book_bible_submissions WHERE localSourceKey = :localSourceKey AND chapterNumber = :chapterNumber LIMIT 1")
    suspend fun getSubmission(localSourceKey: String, chapterNumber: Int): BookBibleSubmissionEntity?

    /**
     * Quan sát sự thay đổi trạng thái gửi của một chương cụ thể.
     */
    @Query("SELECT * FROM book_bible_submissions WHERE localSourceKey = :localSourceKey AND chapterNumber = :chapterNumber LIMIT 1")
    fun observeSubmission(localSourceKey: String, chapterNumber: Int): Flow<BookBibleSubmissionEntity?>

    /**
     * Cập nhật trạng thái và thông điệp lỗi của bản ghi submission.
     */
    @Query("""
        UPDATE book_bible_submissions 
        SET state = :state, submissionId = :submissionId, errorCode = :errorCode, errorMessage = :errorMessage, updatedAt = :updatedAt 
        WHERE id = :id
    """)
    suspend fun updateSubmissionState(
        id: String,
        state: String,
        submissionId: String?,
        errorCode: Int?,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Tăng số lần thử gửi của một submission.
     */
    @Query("UPDATE book_bible_submissions SET attempts = attempts + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementSubmissionAttempts(id: String, updatedAt: Long = System.currentTimeMillis())

    // --- Snapshots ---

    /**
     * Lưu hoặc thay thế bản snapshot hồ sơ nhân vật trong cache.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceSnapshot(snapshot: BookBibleSnapshotEntity)

    /**
     * Lấy bản snapshot hồ sơ theo Edition ID và số chương.
     */
    @Query("SELECT * FROM book_bible_snapshots WHERE editionId = :editionId AND chapterNumber = :chapterNumber LIMIT 1")
    suspend fun getSnapshot(editionId: String, chapterNumber: Int): BookBibleSnapshotEntity?

    /**
     * Lấy bản snapshot hồ sơ theo localSourceKey và số chương.
     */
    @Query("SELECT * FROM book_bible_snapshots WHERE localSourceKey = :localSourceKey AND chapterNumber = :chapterNumber LIMIT 1")
    suspend fun getSnapshotByLocalSource(localSourceKey: String, chapterNumber: Int): BookBibleSnapshotEntity?

    /**
     * Quan sát luồng phát ra bản snapshot hồ sơ nhân vật từ cache theo localSourceKey và số chương.
     */
    @Query("SELECT * FROM book_bible_snapshots WHERE localSourceKey = :localSourceKey AND chapterNumber = :chapterNumber LIMIT 1")
    fun observeSnapshotByLocalSource(localSourceKey: String, chapterNumber: Int): Flow<BookBibleSnapshotEntity?>

    /**
     * Cập nhật thời điểm truy cập gần nhất của snapshot phục vụ thuật toán LRU pruning.
     */
    @Query("UPDATE book_bible_snapshots SET lastAccessedAt = :lastAccessedAt WHERE id = :id")
    suspend fun updateSnapshotLastAccessed(id: String, lastAccessedAt: Long = System.currentTimeMillis())

    // --- Timelines ---

    /**
     * Lưu hoặc thay thế bản timeline tiến trình của nhân vật vào cache.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceTimeline(timeline: BookBibleTimelineEntity)

    /**
     * Lấy dữ liệu timeline của nhân vật theo Edition ID, số chương và mã nhân vật.
     */
    @Query("SELECT * FROM book_bible_timelines WHERE editionId = :editionId AND chapterNumber = :chapterNumber AND characterId = :characterId LIMIT 1")
    suspend fun getTimeline(editionId: String, chapterNumber: Int, characterId: String): BookBibleTimelineEntity?

    /**
     * Cập nhật thời điểm truy cập gần nhất của timeline phục vụ LRU pruning.
     */
    @Query("UPDATE book_bible_timelines SET lastAccessedAt = :lastAccessedAt WHERE id = :id")
    suspend fun updateTimelineLastAccessed(id: String, lastAccessedAt: Long = System.currentTimeMillis())

    // --- Deletion & Cache Pruning ---

    /**
     * Xóa toàn bộ dữ liệu Book Bible (editions, submissions, snapshots, timelines) liên quan đến một nguồn sách cụ thể.
     */
    @Transaction
    suspend fun deleteByLocalSourceKey(localSourceKey: String) {
        deleteEditionsByLocalSource(localSourceKey)
        deleteSubmissionsByLocalSource(localSourceKey)
        deleteSnapshotsByLocalSource(localSourceKey)
        deleteTimelinesByLocalSource(localSourceKey)
    }

    @Query("DELETE FROM book_bible_editions WHERE localSourceKey = :localSourceKey")
    suspend fun deleteEditionsByLocalSource(localSourceKey: String)

    @Query("DELETE FROM book_bible_submissions WHERE localSourceKey = :localSourceKey")
    suspend fun deleteSubmissionsByLocalSource(localSourceKey: String)

    @Query("DELETE FROM book_bible_snapshots WHERE localSourceKey = :localSourceKey")
    suspend fun deleteSnapshotsByLocalSource(localSourceKey: String)

    @Query("DELETE FROM book_bible_timelines WHERE localSourceKey = :localSourceKey")
    suspend fun deleteTimelinesByLocalSource(localSourceKey: String)

    /**
     * Tính tổng dung lượng (bytes) của tất cả snapshots và timelines trong cache.
     */
    @Query("SELECT (SELECT COALESCE(SUM(byteSize), 0) FROM book_bible_snapshots) + (SELECT COALESCE(SUM(byteSize), 0) FROM book_bible_timelines)")
    suspend fun getTotalCacheByteSize(): Long?

    /**
     * Xóa bớt N bản ghi snapshot cũ nhất theo thời gian truy cập (LRU).
     */
    @Query("DELETE FROM book_bible_snapshots WHERE id IN (SELECT id FROM book_bible_snapshots ORDER BY lastAccessedAt ASC LIMIT :limit)")
    suspend fun pruneOldestSnapshots(limit: Int)

    /**
     * Xóa bớt N bản ghi timeline cũ nhất theo thời gian truy cập (LRU).
     */
    @Query("DELETE FROM book_bible_timelines WHERE id IN (SELECT id FROM book_bible_timelines ORDER BY lastAccessedAt ASC LIMIT :limit)")
    suspend fun pruneOldestTimelines(limit: Int)
}
