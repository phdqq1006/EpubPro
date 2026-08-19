package com.epubpro.core.storage.network

import retrofit2.http.*

/**
 * Retrofit Service Interface định nghĩa các REST API endpoints cho phân hệ Book Bible trên máy chủ backend.
 */
interface BookBibleApiService {

    /**
     * Định danh cuốn sách dựa trên tiêu đề, tác giả, ngôn ngữ và fingerprints cấu trúc tệp.
     *
     * @param clientKey Khóa xác thực Trusted Client (Header X-Book-Bible-Client-Key).
     * @param body DTO thông tin sách và dấu vân tay.
     * @return [BookResolutionResponseDto] chứa mã book_id tương ứng hoặc danh sách candidates.
     */
    @POST("book-bible/books/resolve")
    suspend fun resolveBook(
        @Header("X-Book-Bible-Client-Key") clientKey: String? = null,
        @Body body: BookResolutionRequestDto
    ): BookResolutionResponseDto

    /**
     * Tìm hoặc khởi tạo một phiên bản sách (Edition) trên backend.
     *
     * @param bookId Mã sách trên backend.
     * @param clientKey Khóa xác thực Trusted Client (Header X-Book-Bible-Client-Key).
     * @param body DTO thông tin phiên bản (tiêu đề, tác giả, tổng số chương, fingerprints).
     * @return [EditionRecordDto] chứa mã edition_id.
     */
    @POST("book-bible/books/{book_id}/editions")
    suspend fun resolveOrCreateEdition(
        @Path("book_id") bookId: String,
        @Header("X-Book-Bible-Client-Key") clientKey: String? = null,
        @Body body: CreateEditionRequestDto
    ): EditionRecordDto

    /**
     * Cấu hình ánh xạ số chương local của ấn bản sang số chương canonical của tác phẩm.
     *
     * @param editionId Mã phiên bản sách.
     * @param chapterNumber Số thứ tự chương local.
     * @param clientKey Khóa xác thực Trusted Client.
     * @param body DTO cấu hình mapping.
     */
    @POST("book-bible/editions/{edition_id}/chapters/{local_chapter}/mapping")
    suspend fun createChapterMapping(
        @Path("edition_id") editionId: String,
        @Path("local_chapter") chapterNumber: Int,
        @Header("X-Book-Bible-Client-Key") clientKey: String? = null,
        @Body body: ChapterMappingRequestDto
    )

    /**
     * Gửi nội dung văn bản nguồn của một chương để máy chủ phân tích trích xuất dữ liệu.
     *
     * @param editionId Mã phiên bản sách.
     * @param chapterNumber Số thứ tự chương (1-based index).
     * @param idempotencyKey Khóa duy nhất chống trùng lặp request (Header X-Idempotency-Key).
     * @param clientKey Khóa xác thực Trusted Client (Header X-Book-Bible-Client-Key).
     * @param apiKey Khóa LLM API cá nhân (Header X-Api-Key).
     * @param model Tên model LLM trích xuất (Header X-Model).
     * @param body DTO nội dung chương và mã băm nguồn.
     * @return [ChapterSubmissionResponseDto] chứa kết quả tiếp nhận (HTTP 202).
     */
    @POST("book-bible/editions/{edition_id}/chapters/{local_chapter}/submissions")
    suspend fun submitChapter(
        @Path("edition_id") editionId: String,
        @Path("local_chapter") chapterNumber: Int,
        @Header("X-Idempotency-Key") idempotencyKey: String,
        @Header("X-Book-Bible-Client-Key") clientKey: String? = null,
        @Header("X-Api-Key") apiKey: String? = null,
        @Header("X-Model") model: String? = null,
        @Body body: ChapterSubmissionRequestDto
    ): ChapterSubmissionResponseDto

    /**
     * Tra cứu tiến độ xử lý và trạng thái chi tiết của submission phân tích chương.
     *
     * @param submissionId Mã định danh submission.
     * @return [ChapterSubmissionResponseDto] chứa trạng thái hiện tại (queued, processing, reviewing, completed, failed).
     */
    @GET("book-bible/submissions/{submission_id}")
    suspend fun getSubmissionStatus(
        @Path("submission_id") submissionId: String
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
