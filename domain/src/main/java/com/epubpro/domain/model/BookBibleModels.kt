package com.epubpro.domain.model

/**
 * Loại nguồn sách hỗ trợ Book Bible.
 */
enum class BookBibleSourceType {
    /** Sách EPUB lưu trữ cục bộ trên thiết bị */
    LOCAL_EPUB,

    /** Truyện online đọc từ máy chủ backend */
    ONLINE_NOVEL
}

/**
 * Định danh nguồn sách trong hệ thống Book Bible.
 *
 * @property type Loại nguồn sách ([BookBibleSourceType]).
 * @property sourceId Mã định danh nguồn (Book ID hoặc Novel ID).
 */
data class BookBibleSource(
    val type: BookBibleSourceType,
    val sourceId: String
) {
    /**
     * Khóa định danh duy nhất của nguồn để lưu trữ trong database.
     */
    val uniqueKey: String get() = "${type.name}:$sourceId"
}

/**
 * Thông tin chương nguồn của sách với chỉ số 1-based phục vụ trích xuất và phân tích Book Bible.
 *
 * @property chapterNumber Thứ tự chương (1-based index).
 * @property chapterTitle Tiêu đề chương.
 * @property totalChapters Tổng số chương của sách/truyện.
 */
data class BookBibleChapter(
    val chapterNumber: Int,
    val chapterTitle: String,
    val totalChapters: Int
)

/**
 * Trạng thái dữ liệu của bản Snapshot Book Bible.
 */
enum class SnapshotStatus {
    /** Dữ liệu phân tích hoàn chỉnh tất cả các chương từ đầu đến chương hiện tại */
    COMPLETE,

    /** Dữ liệu chỉ bao phủ một phần các chương do người dùng nhảy cóc chương */
    PARTIAL,

    /** Backend đang trong tiến trình phân tích dữ liệu */
    PROCESSING,

    /** Chưa có nhân vật nào xuất hiện hoặc chưa phân tích được dữ liệu */
    EMPTY,

    /** Quá trình phân tích hoặc tải snapshot thất bại */
    FAILED
}

/**
 * Đoạn khoảng chương liên tục (inclusive: từ start đến end).
 *
 * @property start Chương bắt đầu (1-based).
 * @property end Chương kết thúc (1-based).
 */
data class ChapterRange(
    val start: Int,
    val end: Int
)

/**
 * Độ bao phủ các chương đã được phân tích và các chương còn thiếu.
 *
 * @property processedRanges Danh sách các khoảng chương đã được AI xử lý.
 * @property missingRanges Danh sách các khoảng chương còn thiếu trước mốc hiện tại.
 */
data class SnapshotCoverage(
    val processedRanges: List<ChapterRange> = emptyList(),
    val missingRanges: List<ChapterRange> = emptyList()
)

/**
 * Mối quan hệ giữa nhân vật với các nhân vật khác trong tác phẩm.
 *
 * @property targetName Tên nhân vật liên quan.
 * @property relationType Loại quan hệ (ví dụ: "Sư phụ", "Đồng môn", "Kẻ thù", "Đạo lữ").
 * @property description Chi tiết thêm về mối quan hệ.
 */
data class CharacterRelationship(
    val targetName: String,
    val relationType: String,
    val description: String? = null
)

/**
 * Thuộc tính mở rộng dạng key-value hiển thị an toàn.
 *
 * @property key Khóa định danh thuộc tính.
 * @property label Nhãn hiển thị tiếng Việt.
 * @property value Giá trị hiển thị tiếng Việt.
 */
data class ExtraAttribute(
    val key: String,
    val label: String,
    val value: String
)

/**
 * Hồ sơ chi tiết của một nhân vật được giới hạn chống spoiler tại chương hiện tại.
 *
 * @property id Mã định danh duy nhất của nhân vật trên backend.
 * @property name Tên nhân vật (đã chuẩn hóa hoặc tên gốc).
 * @property originalName Tên gốc tiếng Trung/Anh nếu có.
 * @property changedInCurrentChapter Cờ đánh dấu nhân vật có sự kiện/tiến triển mới trong chương hiện tại.
 * @property lastChangedChapter Chương gần nhất nhân vật có cập nhật trạng thái/thuộc tính.
 * @property cultivationRealm Cảnh giới tu vi / cấp bậc hiện tại.
 * @property techniques Danh sách công pháp / võ học tu luyện.
 * @property skills Danh sách kỹ năng / năng lực đặc biệt.
 * @property items Danh sách trang bị / pháp bảo / vật phẩm sở hữu.
 * @property relationships Danh sách các mối quan hệ xã hội.
 * @property affiliations Danh sách các bang phái / thế lực / tông môn trực thuộc.
 * @property titles Danh sách các danh hiệu / xưng hiệu / bí danh.
 * @property extraAttributes Danh sách các thuộc tính tùy biến khác.
 */
data class CharacterProfile(
    val id: String,
    val name: String,
    val originalName: String? = null,
    val changedInCurrentChapter: Boolean = false,
    val lastChangedChapter: Int = 1,
    val cultivationRealm: String? = null,
    val techniques: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val relationships: List<CharacterRelationship> = emptyList(),
    val affiliations: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val extraAttributes: List<ExtraAttribute> = emptyList()
)

/**
 * Bản Snapshot Book Bible đại diện cho toàn bộ hồ sơ nhân vật tại một mốc chương xác định.
 *
 * @property bookId Mã backend của cuốn sách.
 * @property editionId Mã backend của phiên bản sách (edition).
 * @property requestedChapter Mốc chương người dùng yêu cầu xem (1-based).
 * @property canonicalChapter Mốc chương chuẩn mà backend trả về (không bao giờ vượt quá requestedChapter).
 * @property status Trạng thái của snapshot ([SnapshotStatus]).
 * @property coverage Độ bao phủ của các chương đã phân tích ([SnapshotCoverage]).
 * @property revision Số phiên bản sửa đổi dữ liệu (dùng để kiểm tra cache mới nhất).
 * @property updatedAt Thời điểm cập nhật cuối cùng tính theo mili-giây.
 * @property characters Danh sách toàn bộ hồ sơ nhân vật xuất hiện đến chương này.
 */
data class BookBibleSnapshot(
    val bookId: String,
    val editionId: String,
    val requestedChapter: Int,
    val canonicalChapter: Int,
    val status: SnapshotStatus,
    val coverage: SnapshotCoverage = SnapshotCoverage(),
    val revision: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
    val characters: List<CharacterProfile> = emptyList()
)

/**
 * Một sự kiện diễn tiến của nhân vật trong dòng thời gian.
 *
 * @property chapter Thứ tự chương diễn ra sự kiện.
 * @property category Phân loại sự kiện (ví dụ: "Cảnh giới", "Trang bị", "Quan hệ", "Thế lực").
 * @property operation Loại tác động (ví dụ: "Đột phá", "Nhận được", "Thay đổi", "Mất đi").
 * @property displayValue Chuỗi hiển thị nội dung sự kiện bằng tiếng Việt.
 * @property evidence Đoạn văn bản trích dẫn làm chứng cứ trong nguyên tác (nếu có).
 * @property confidence Độ tin cậy của mô hình AI trích xuất (chỉ ghi log, không hiển thị trên UI).
 */
data class CharacterTimelineEvent(
    val chapter: Int,
    val category: String,
    val operation: String,
    val displayValue: String,
    val evidence: String? = null,
    val confidence: Double? = null
)

/**
 * Dòng thời gian tiến trình của một nhân vật cụ thể đến mốc chương hiện tại.
 *
 * @property characterId Mã nhân vật.
 * @property characterName Tên nhân vật.
 * @property events Danh sách các sự kiện được sắp xếp theo trình tự chương.
 */
data class CharacterTimeline(
    val characterId: String,
    val characterName: String,
    val events: List<CharacterTimelineEvent> = emptyList()
)

/**
 * Trạng thái gửi nội dung chương lên máy chủ backend để phân tích.
 */
sealed class SubmissionState {
    /** Đang chờ mạng để gửi */
    object Pending : SubmissionState()

    /** Đang trong tiến trình truyền dữ liệu lên backend */
    object Submitting : SubmissionState()

    /** Backend đã tiếp nhận yêu cầu phân tích thành công (HTTP 200/202/409) */
    object Accepted : SubmissionState()

    /** Backend đang phân tích AI */
    object Processing : SubmissionState()

    /** Backend đã phân tích xong hoàn tất */
    object Completed : SubmissionState()

    /** Lỗi mạng hoặc server tạm thời có thể tự động thử lại (HTTP 408, 429, 5xx, mất mạng) */
    data class RetryableFailure(val message: String) : SubmissionState()

    /** Lỗi vĩnh viễn không thể retry (HTTP 400, 413 Payload Too Large, lỗi dữ liệu nguồn) */
    data class PermanentFailure(val message: String) : SubmissionState()
}
