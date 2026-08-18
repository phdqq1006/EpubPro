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
 * Retrofit Service Interface định nghĩa 6 REST API endpoints tương tác với máy chủ backend.
 */
interface OnlineNovelApiService {

    /**
     * Lấy danh sách tất cả các bộ truyện trên server.
     */
    @GET("library/novels")
    suspend fun getNovels(): List<NovelSummaryDto>

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
        @Path("novel_id") novelId: String
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
     * Tải lên một file `.epub` từ điện thoại lên server.
     *
     * @param file File multipart body.
     * @param isTranslated Cờ đánh dấu đã dịch.
     */
    @Multipart
    @POST("library/novels/import-epub")
    suspend fun uploadEpub(
        @Part file: MultipartBody.Part,
        @Part("is_translated") isTranslated: RequestBody
    ): ResponseBody
}
