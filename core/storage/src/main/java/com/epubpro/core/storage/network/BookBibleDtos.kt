package com.epubpro.core.storage.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * DTO siêu dữ liệu sách (Metadata).
 */
data class BookMetadataDto(
    @SerializedName("title") val title: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("language") val language: String = "vi",
    @SerializedName("publisher") val publisher: String = "",
    @SerializedName("identifier") val identifier: String? = null
)

/**
 * DTO yêu cầu định danh/tìm kiếm sách trên backend.
 */
data class BookResolutionRequestDto(
    @SerializedName("metadata") val metadata: BookMetadataDto,
    @SerializedName("create_if_missing") val createIfMissing: Boolean = true,
    @SerializedName("book_id") val bookId: String? = null
)

/**
 * DTO ứng viên sách trùng khớp.
 */
data class BookMatchCandidateDto(
    @SerializedName("book_id") val bookId: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String? = "",
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("reasons") val reasons: List<String>? = null
)

/**
 * DTO phản hồi kết quả định danh sách từ backend.
 */
data class BookResolutionResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("book_id") val bookId: String? = null,
    @SerializedName("candidates") val candidates: List<BookMatchCandidateDto>? = null
)

/**
 * DTO yêu cầu tạo hoặc khớp phiên bản sách (Edition).
 */
data class CreateEditionRequestDto(
    @SerializedName("metadata") val metadata: BookMetadataDto,
    @SerializedName("chapter_count") val chapterCount: Int? = null
)

/**
 * DTO phản hồi thông tin phiên bản sách (Edition).
 */
data class EditionRecordDto(
    @SerializedName("edition_id") val editionId: String,
    @SerializedName("book_id") val bookId: String,
    @SerializedName("metadata") val metadata: BookMetadataDto? = null,
    @SerializedName("chapter_count") val chapterCount: Int? = null,
    @SerializedName("mapping_revision") val mappingRevision: Long = 1L,
    @SerializedName("created_at") val createdAt: String? = null
)

/**
 * DTO yêu cầu gửi nội dung chương nguồn để phân tích.
 */
data class ChapterSubmissionRequestDto(
    @SerializedName("local_chapter_index") val localChapterIndex: Int,
    @SerializedName("input_type") val inputType: String = "chapter_text",
    @SerializedName("content") val content: String? = null,
    @SerializedName("content_fingerprint") val contentFingerprint: String? = null,
    @SerializedName("chapter_id") val chapterId: String? = null,
    @SerializedName("source_label") val sourceLabel: String? = null
)

/**
 * DTO phản hồi sau khi gửi chương nguồn thành công (HTTP 202 SubmissionStatusResponse).
 */
data class ChapterSubmissionResponseDto(
    @SerializedName("submission_id") val submissionId: String,
    @SerializedName("idempotency_key") val idempotencyKey: String? = null,
    @SerializedName("book_id") val bookId: String? = null,
    @SerializedName("edition_id") val editionId: String? = null,
    @SerializedName("local_chapter_index") val localChapterIndex: Int = 1,
    @SerializedName("canonical_chapter_start") val canonicalChapterStart: Int? = null,
    @SerializedName("canonical_chapter_end") val canonicalChapterEnd: Int? = null,
    @SerializedName("input_type") val inputType: String? = "chapter_text",
    @SerializedName("content_fingerprint") val contentFingerprint: String? = null,
    @SerializedName("source_group_id") val sourceGroupId: String? = null,
    @SerializedName("status") val status: String? = "queued",
    @SerializedName("error_code") val errorCode: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("event_ids") val eventIds: List<String>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null
)

/**
 * DTO độ bao phủ các khoảng chương đã phân tích và còn thiếu.
 */
data class CoverageDto(
    @SerializedName("processed_ranges") val processedRanges: List<List<Int>>? = null,
    @SerializedName("missing_ranges") val missingRanges: List<List<Int>>? = null
)

/**
 * DTO thông tin quan hệ giữa các nhân vật.
 */
data class CharacterRelationshipDto(
    @SerializedName("target_name") val targetName: String = "",
    @SerializedName("relation_type") val relationType: String = "",
    @SerializedName("description") val description: String? = null
)

/**
 * DTO hồ sơ của một nhân vật trong Snapshot (theo CharacterSnapshot của OpenAPI).
 */
data class CharacterProfileDto(
    @SerializedName("character_id") val characterId: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("last_changed_chapter") val lastChangedChapter: Int? = null,
    @SerializedName("attributes") val attributes: Map<String, JsonElement>? = null,
    @SerializedName("changed_in_current_chapter") val changedInCurrentChapter: Boolean = false,
    @SerializedName("cultivation_realm") val cultivationRealm: String? = null,
    @SerializedName("techniques") val techniques: List<String>? = null,
    @SerializedName("skills") val skills: List<String>? = null,
    @SerializedName("items") val items: List<String>? = null,
    @SerializedName("relationships") val relationships: List<CharacterRelationshipDto>? = null,
    @SerializedName("affiliations") val affiliations: List<String>? = null,
    @SerializedName("titles") val titles: List<String>? = null,
    @SerializedName("extra_attributes") val extraAttributes: Map<String, JsonElement>? = null
)

/**
 * DTO phản hồi Snapshot hồ sơ Book Bible tại một mốc chương (theo CharacterSnapshotResponse của OpenAPI).
 */
data class CharacterSnapshotResponseDto(
    @SerializedName("book_id") val bookId: String,
    @SerializedName("edition_id") val editionId: String,
    @SerializedName("requested_chapter") val requestedChapter: Int,
    @SerializedName("canonical_chapter") val canonicalChapter: Int? = null,
    @SerializedName("book_revision") val bookRevision: Int = 0,
    @SerializedName("projection_revision") val projectionRevision: Int = 0,
    @SerializedName("projection_status") val projectionStatus: String? = "ready",
    @SerializedName("snapshot_status") val snapshotStatus: String? = "complete",
    @SerializedName("complete_through_chapter") val completeThroughChapter: Int? = null,
    @SerializedName("pending_chapters") val pendingChapters: List<Int>? = null,
    @SerializedName("snapshot_revision") val snapshotRevision: Long = 1L,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("coverage") val coverage: CoverageDto? = null,
    @SerializedName("characters") val characters: List<CharacterProfileDto>? = null
)

/**
 * DTO sự kiện diễn tiến của một nhân vật (theo CharacterEvent của OpenAPI).
 */
data class CharacterEventDto(
    @SerializedName("event_id") val eventId: String? = null,
    @SerializedName("book_id") val bookId: String? = null,
    @SerializedName("character_id") val characterId: String? = null,
    @SerializedName("character_original_name") val characterOriginalName: String? = null,
    @SerializedName("canonical_chapter") val canonicalChapter: Int = 1,
    @SerializedName("chapter") val chapter: Int? = null,
    @SerializedName("category") val category: String = "",
    @SerializedName("attribute_key") val attributeKey: String = "",
    @SerializedName("operation") val operation: String = "set",
    @SerializedName("value") val value: JsonElement? = null,
    @SerializedName("display_value") val displayValue: String? = null,
    @SerializedName("certainty") val certainty: String? = "observed",
    @SerializedName("status") val status: String? = "pending",
    @SerializedName("evidence") val evidence: String? = null,
    @SerializedName("confidence") val confidence: Double? = null,
    @SerializedName("source_group_id") val sourceGroupId: String? = null,
    @SerializedName("source_submission_id") val sourceSubmissionId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)
