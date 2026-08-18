package com.epubpro.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bảng lưu trữ ánh xạ giữa nguồn sách cục bộ (Local EPUB / Online Novel) với Book ID và Edition ID trên backend.
 *
 * @property localSourceKey Khóa định danh duy nhất của nguồn (ví dụ: `LOCAL_EPUB:book_123` hoặc `ONLINE_NOVEL:novel_456`).
 * @property backendBookId Mã định danh sách trên máy chủ backend.
 * @property backendEditionId Mã định danh phiên bản sách trên máy chủ backend.
 * @property mappingRevision Số phiên bản ánh xạ dữ liệu.
 * @property title Tiêu đề sách đã chuẩn hóa.
 * @property author Tên tác giả đã chuẩn hóa.
 * @property chapterCount Tổng số chương của phiên bản sách này.
 * @property updatedAt Thời gian cập nhật bản ghi (mili-giây).
 */
@Entity(tableName = "book_bible_editions")
data class BookBibleEditionEntity(
    @PrimaryKey val localSourceKey: String,
    val backendBookId: String,
    val backendEditionId: String,
    val mappingRevision: Long = 1L,
    val title: String,
    val author: String,
    val chapterCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Bảng lưu trữ trạng thái hàng đợi và tiến trình gửi chương nguồn lên máy chủ backend.
 *
 * @property id Khóa chính được tính từ mã băm xác định của localSourceKey + chapterNumber + sourceHash.
 * @property localSourceKey Khóa định danh nguồn sách.
 * @property chapterNumber Thứ tự chương nguồn (1-based index).
 * @property sourceHash Mã băm SHA-256 của nội dung văn bản chương nguồn.
 * @property payloadPath Đường dẫn file payload plain text tạm thời dưới noBackupFilesDir.
 * @property submissionId Mã submission trả về từ backend sau khi tiếp nhận.
 * @property state Trạng thái gửi hiện tại (PENDING, SUBMITTING, ACCEPTED, PROCESSING, COMPLETED, RETRYABLE_FAILURE, PERMANENT_FAILURE).
 * @property attempts Số lần đã thử gửi qua WorkManager.
 * @property errorCode Mã lỗi HTTP (nếu có).
 * @property errorMessage Thông điệp lỗi chi tiết khi gửi thất bại.
 * @property createdAt Thời điểm tạo yêu cầu gửi.
 * @property updatedAt Thời điểm cập nhật trạng thái gần nhất.
 */
@Entity(
    tableName = "book_bible_submissions",
    indices = [
        Index(value = ["localSourceKey", "chapterNumber", "sourceHash"], unique = true),
        Index(value = ["localSourceKey", "chapterNumber"])
    ]
)
data class BookBibleSubmissionEntity(
    @PrimaryKey val id: String,
    val localSourceKey: String,
    val chapterNumber: Int,
    val sourceHash: String,
    val payloadPath: String?,
    val submissionId: String? = null,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Bảng lưu trữ bộ nhớ đệm (Cache) hồ sơ nhân vật Snapshot của Book Bible theo từng mốc chương.
 *
 * @property id Khóa chính dạng `${editionId}:${chapterNumber}`.
 * @property editionId Mã phiên bản sách trên backend.
 * @property localSourceKey Khóa nguồn sách cục bộ tương ứng.
 * @property chapterNumber Mốc chương yêu cầu (1-based).
 * @property canonicalChapter Mốc chương thực tế backend trả về.
 * @property status Trạng thái snapshot (COMPLETE, PARTIAL, PROCESSING, EMPTY, FAILED).
 * @property coverageJson Chuỗi JSON lưu trữ SnapshotCoverage (processedRanges & missingRanges).
 * @property payloadJson Chuỗi JSON lưu trữ toàn bộ danh sách CharacterProfile.
 * @property revision Số phiên bản sửa đổi dữ liệu từ backend.
 * @property byteSize Kích thước payload JSON tính theo bytes phục vụ giới hạn dung lượng cache.
 * @property updatedAt Thời điểm cập nhật dữ liệu từ backend.
 * @property lastAccessedAt Thời điểm người dùng mở xem gần nhất (phục vụ LRU pruning).
 */
@Entity(
    tableName = "book_bible_snapshots",
    indices = [
        Index(value = ["editionId", "chapterNumber"], unique = true),
        Index(value = ["localSourceKey", "chapterNumber"]),
        Index(value = ["lastAccessedAt"])
    ]
)
data class BookBibleSnapshotEntity(
    @PrimaryKey val id: String,
    val editionId: String,
    val localSourceKey: String,
    val chapterNumber: Int,
    val canonicalChapter: Int,
    val status: String,
    val coverageJson: String,
    val payloadJson: String,
    val revision: Long,
    val byteSize: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Bảng lưu trữ bộ nhớ đệm (Cache) dòng thời gian tiến trình sự kiện của một nhân vật.
 *
 * @property id Khóa chính dạng `${editionId}:${chapterNumber}:${characterId}`.
 * @property editionId Mã phiên bản sách trên backend.
 * @property localSourceKey Khóa nguồn sách cục bộ.
 * @property chapterNumber Mốc chương giới hạn tối đa.
 * @property characterId Mã nhân vật.
 * @property payloadJson Chuỗi JSON lưu trữ danh sách CharacterTimelineEvent.
 * @property byteSize Kích thước dữ liệu JSON.
 * @property updatedAt Thời điểm cập nhật.
 * @property lastAccessedAt Thời điểm truy cập gần nhất.
 */
@Entity(
    tableName = "book_bible_timelines",
    indices = [
        Index(value = ["editionId", "chapterNumber", "characterId"], unique = true),
        Index(value = ["localSourceKey", "chapterNumber", "characterId"]),
        Index(value = ["lastAccessedAt"])
    ]
)
data class BookBibleTimelineEntity(
    @PrimaryKey val id: String,
    val editionId: String,
    val localSourceKey: String,
    val chapterNumber: Int,
    val characterId: String,
    val payloadJson: String,
    val byteSize: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)
