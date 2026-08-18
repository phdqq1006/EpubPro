package com.epubpro.core.storage.network

import retrofit2.http.*

/**
 * Retrofit Service Interface định nghĩa các REST API endpoints cho phân hệ Book Bible trên máy chủ backend.
 */
interface BookBibleApiService {

    /**
     * Định danh cuốn sách dựa trên tiêu đề, tác giả và ngôn ngữ.
     *
     * @param body DTO thông tin sách.
     * @return [BookResolutionResponseDto] chứa mã book_id tương ứng.
     */
    @POST("book-bible/books/resolve")
    suspend fun resolveBook(
        @Body body: BookResolutionRequestDto
    ): BookResolutionResponseDto

    /**
     * Tìm hoặc khởi tạo một phiên bản sách (Edition) trên backend.
     *
     * @param bookId Mã sách trên backend.
     * @param body DTO thông tin phiên bản (tiêu đề, tác giả, tổng số chương).
     * @return [EditionRecordDto] chứa mã edition_id.
     */
    @POST("book-bible/books/{book_id}/editions")
    suspend fun resolveOrCreateEdition(
        @Path("book_id") bookId: String,
        @Body body: CreateEditionRequestDto
    ): EditionRecordDto

    /**
     * Gửi nội dung văn bản nguồn của một chương để máy chủ phân tích trích xuất dữ liệu.
     *
     * @param editionId Mã phiên bản sách.
     * @param chapterNumber Số thứ tự chương (1-based index).
     * @param idempotencyKey Khóa duy nhất chống trùng lặp request (Header X-Idempotency-Key).
     * @param body DTO nội dung chương và mã băm nguồn.
     * @return [ChapterSubmissionResponseDto] chứa kết quả tiếp nhận (HTTP 202).
     */
    @POST("book-bible/editions/{edition_id}/chapters/{local_chapter}/submissions")
    suspend fun submitChapter(
        @Path("edition_id") editionId: String,
        @Path("local_chapter") chapterNumber: Int,
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Body body: ChapterSubmissionRequestDto
    ): ChapterSubmissionResponseDto

    /**
     * Lấy bản Snapshot hồ sơ nhân vật được chặn chống spoiler tại một mốc chương cụ thể.
     *
     * @param editionId Mã phiên bản sách.
     * @param chapterNumber Mốc chương yêu cầu (1-based index).
     * @return [CharacterSnapshotResponseDto] chứa toàn bộ hồ sơ nhân vật và độ bao phủ.
     */
    @GET("book-bible/editions/{edition_id}/chapters/{local_chapter}/snapshot")
    suspend fun getSnapshot(
        @Path("edition_id") editionId: String,
        @Path("local_chapter") chapterNumber: Int
    ): CharacterSnapshotResponseDto

    /**
     * Lấy dòng thời gian tiến trình sự kiện của một nhân vật có chặn spoiler đến mốc chương.
     *
     * @param editionId Mã phiên bản sách.
     * @param chapterBoundary Mốc chương giới hạn tối đa.
     * @param characterId Mã định danh nhân vật.
     * @return Danh sách [CharacterEventDto] các sự kiện của nhân vật.
     */
    @GET("book-bible/editions/{edition_id}/chapters/{local_chapter}/characters/{character_id}/timeline")
    suspend fun getCharacterTimeline(
        @Path("edition_id") editionId: String,
        @Path("local_chapter") chapterBoundary: Int,
        @Path("character_id") characterId: String
    ): List<CharacterEventDto>
}
