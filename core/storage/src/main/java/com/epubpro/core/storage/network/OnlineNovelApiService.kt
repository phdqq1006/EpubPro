package com.epubpro.core.storage.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

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

data class ChapterSummaryDto(
    @SerializedName("chapter_index") val chapterIndex: Int,
    @SerializedName("chapter_title") val chapterTitle: String,
    @SerializedName("status") val status: String? = "raw",
    @SerializedName("word_count") val wordCount: Int = 0,
    @SerializedName("translated_text_preview") val translatedTextPreview: String? = null
)

data class ChapterContentDto(
    @SerializedName("novel_id") val novelId: String,
    @SerializedName("chapter_index") val chapterIndex: Int,
    @SerializedName("version") val version: String,
    @SerializedName("content") val content: String
)

data class TranslateResponseDto(
    @SerializedName("message") val message: String,
    @SerializedName("chapter") val chapter: ChapterSummaryDto? = null
)

interface OnlineNovelApiService {
    @GET("library/novels")
    suspend fun getNovels(): List<NovelSummaryDto>

    @GET("library/novels/{novel_id}")
    suspend fun getNovelDetail(
        @Path("novel_id") novelId: String
    ): NovelDetailDto

    @GET("library/novels/{novel_id}/chapters/{chapter_index}/content")
    suspend fun getChapterContent(
        @Path("novel_id") novelId: String,
        @Path("chapter_index") chapterIndex: Int,
        @Query("version") version: String = "translated"
    ): ChapterContentDto

    @Streaming
    @GET("library/novels/{novel_id}/export/epub")
    suspend fun downloadEpub(
        @Path("novel_id") novelId: String
    ): Response<ResponseBody>

    @POST("library/novels/{novel_id}/chapters/{chapter_index}/translate")
    suspend fun translateChapter(
        @Path("novel_id") novelId: String,
        @Path("chapter_index") chapterIndex: Int,
        @Header("X-API-Key") apiKey: String,
        @Header("X-Provider") provider: String,
        @Header("X-Model") model: String
    ): TranslateResponseDto

    @Multipart
    @POST("library/novels/import-epub")
    suspend fun uploadEpub(
        @Part file: MultipartBody.Part,
        @Part("is_translated") isTranslated: RequestBody
    ): ResponseBody
}
