package com.epubpro.core.storage.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Data Transfer Object biểu diễn thông tin tóm tắt của một bộ truyện từ API.
 */
data class NovelSummaryDto(
    @SerializedName("novel_id") val novelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("author") val author: String,
    @SerializedName("genre") val genre: List<String>? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("total_chapters") val totalChapters: Int = 0,
    @SerializedName("translated_chapters") val translatedChapters: Int = 0,
    @SerializedName("status") val status: String? = "ongoing",
    @SerializedName("updated_at") val updatedAt: String? = null
)

/**
 * Data Transfer Object biểu diễn thông tin chi tiết và danh sách chương của bộ truyện từ API.
 */
data class NovelDetailDto(
    @SerializedName("novel_id") val novelId: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("total_chapters") val totalChapters: Int = 0,
    @SerializedName("translated_chapters") val translatedChapters: Int = 0,
    @SerializedName("chapters") val chapters: List<ChapterSummaryDto>? = null
)

/**
 * Data Transfer Object biểu diễn thông tin một chương trong mục lục.
 */
data class ChapterSummaryDto(
    @SerializedName("chapter_index") val chapterIndex: Int,
    @SerializedName("chapter_title") val chapterTitle: String,
    @SerializedName("status") val status: String? = "raw",
    @SerializedName("word_count") val wordCount: Int = 0,
    @SerializedName("translated_text_preview") val translatedTextPreview: String? = null
)

/**
 * Data Transfer Object biểu diễn nội dung văn bản của một chương.
 */
data class ChapterContentDto(
    @SerializedName("novel_id") val novelId: String,
    @SerializedName("chapter_index") val chapterIndex: Int,
    @SerializedName("version") val version: String,
    @SerializedName("content") val content: String
)

/**
 * Data Transfer Object phản hồi sau khi yêu cầu dịch AI một chương truyện.
 */
data class TranslateResponseDto(
    @SerializedName("message") val message: String,
    @SerializedName("chapter") val chapter: ChapterSummaryDto? = null
)

/**
 * Data Transfer Object trạng thái tiến trình Import truyện (EPUB).
 */
data class ImportJobStatusDto(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("novel_id") val novelId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("status") val status: String,
    @SerializedName("current_step") val currentStep: String?,
    @SerializedName("current_chapter") val currentChapter: Int = 0,
    @SerializedName("total_chapters") val totalChapters: Int = 0,
    @SerializedName("progress_percentage") val progressPercentage: Int = 0,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("completed_at") val completedAt: String?
)

/**
 * Retrofit Service Interface định nghĩa 6 REST API endpoints tương tác với máy chủ backend.
 */
interface OnlineNovelApiService {

    /**
     * Lấy danh sách tất cả các bộ truyện trên server.
     */
    @GET("library/novels")
    suspend fun getNovels(): List<NovelSummaryDto>

    /**
     * Lấy danh sách truyện bằng Base URL ứng viên để kiểm tra kết nối mà không thay đổi cấu hình đã lưu.
     *
     * @param baseUrl Base URL chỉ áp dụng cho request kiểm tra hiện tại.
     * @return Danh sách truyện trả về từ server ứng viên.
     */
    @GET("library/novels")
    suspend fun getNovelsFromBaseUrl(
        @Header(DynamicBaseUrlInterceptor.BASE_URL_OVERRIDE_HEADER) baseUrl: String
    ): List<NovelSummaryDto>

    /**
     * Lấy chi tiết bộ truyện và mục lục các chương theo ID truyện.
     *
     * @param novelId Mã định danh duy nhất của bộ truyện.
     */
    @GET("library/novels/{novel_id}")
    suspend fun getNovelDetail(
        @Path("novel_id") novelId: String
    ): NovelDetailDto

    /**
     * Lấy nội dung chi tiết của một chương truyện.
     *
     * @param novelId Mã định danh duy nhất của bộ truyện.
     * @param chapterIndex Thứ tự chương.
     * @param version Phiên bản (`translated` hoặc `original`).
     */
    @GET("library/novels/{novel_id}/chapters/{chapter_index}/content")
    suspend fun getChapterContent(
        @Path("novel_id") novelId: String,
        @Path("chapter_index") chapterIndex: Int,
        @Query("version") version: String = "translated"
    ): ChapterContentDto

    /**
     * Tải về file nhị phân `.epub` của bộ truyện dưới dạng luồng dữ liệu (Streaming).
     *
     * @param novelId Mã định danh duy nhất của bộ truyện.
     */
    @Streaming
    @GET("library/novels/{novel_id}/export/epub")
    suspend fun downloadEpub(
        @Path("novel_id") novelId: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    /**
     * Yêu cầu AI dịch chương truyện.
     *
     * @param novelId Mã định danh của bộ truyện.
     * @param chapterIndex Thứ tự chương.
     * @param apiKey Khóa API AI.
     * @param provider Nhà cung cấp AI.
     * @param model Tên model AI.
     */
    @POST("library/novels/{novel_id}/chapters/{chapter_index}/translate")
    suspend fun translateChapter(
        @Path("novel_id") novelId: String,
        @Path("chapter_index") chapterIndex: Int,
        @Header("X-API-Key") apiKey: String,
        @Header("X-Provider") provider: String,
        @Header("X-Model") model: String
    ): TranslateResponseDto

    /**
     * Tải lên một file sách bất kỳ từ điện thoại lên server để backend tự xử lý.
     *
     * @param file File multipart body.
     * @param isTranslated Cờ đánh dấu đã dịch.
     * @param novelId (Tùy chọn) Slug truyện.
     * @param autoScanCharacters (Tùy chọn) Tự động scan nhân vật.
     */
    @Multipart
    @POST("library/novels/import-epub")
    suspend fun uploadEpub(
        @Part file: MultipartBody.Part,
        @Part("is_translated") isTranslated: RequestBody,
        @Part("novel_id") novelId: RequestBody? = null,
        @Part("auto_scan_characters") autoScanCharacters: RequestBody? = null
    ): ImportJobStatusDto

    /**
     * Lấy trạng thái của tiến trình upload.
     *
     * @param jobId ID của job nhận được sau khi upload thành công.
     */
    @GET("library/import-jobs/{job_id}")
    suspend fun getImportJobStatus(
        @Path("job_id") jobId: String
    ): ImportJobStatusDto
}
