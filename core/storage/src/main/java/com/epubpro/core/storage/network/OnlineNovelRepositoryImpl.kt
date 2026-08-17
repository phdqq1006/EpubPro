package com.epubpro.core.storage.network

import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.ServerPreferencesManager
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.OnlineNovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineNovelRepositoryImpl @Inject constructor(
    private val apiService: OnlineNovelApiService,
    private val storageManager: EpubStorageManager,
    private val serverPreferencesManager: ServerPreferencesManager
) : OnlineNovelRepository {

    override suspend fun getNovels(): Result<List<OnlineNovelSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getNovels().map { dto ->
                OnlineNovelSummary(
                    novelId = dto.novelId,
                    title = dto.title,
                    originalTitle = dto.originalTitle,
                    author = dto.author,
                    genres = dto.genre ?: emptyList(),
                    coverUrl = dto.coverUrl,
                    totalChapters = dto.totalChapters,
                    translatedChapters = dto.translatedChapters,
                    status = dto.status ?: "ongoing",
                    updatedAt = dto.updatedAt
                )
            }
        }
    }

    override suspend fun getNovelDetail(novelId: String): Result<OnlineNovelDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getNovelDetail(novelId)
            OnlineNovelDetail(
                novelId = dto.novelId,
                title = dto.title,
                author = dto.author,
                description = dto.description,
                coverUrl = dto.coverUrl,
                totalChapters = dto.totalChapters,
                translatedChapters = dto.translatedChapters,
                chapters = dto.chapters?.map { chapterDto ->
                    OnlineChapterSummary(
                        chapterIndex = chapterDto.chapterIndex,
                        chapterTitle = chapterDto.chapterTitle,
                        status = chapterDto.status ?: "raw",
                        wordCount = chapterDto.wordCount,
                        translatedTextPreview = chapterDto.translatedTextPreview
                    )
                } ?: emptyList()
            )
        }
    }

    override suspend fun getChapterContent(
        novelId: String,
        chapterIndex: Int,
        version: String
    ): Result<OnlineChapterContent> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getChapterContent(
                novelId = novelId,
                chapterIndex = chapterIndex,
                version = version
            )
            OnlineChapterContent(
                novelId = dto.novelId,
                chapterIndex = dto.chapterIndex,
                version = dto.version,
                content = dto.content
            )
        }
    }

    override fun downloadEpub(novelId: String, saveFileName: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))
        try {
            val response = apiService.downloadEpub(novelId)
            if (!response.isSuccessful || response.body() == null) {
                emit(DownloadState.Error("Tải sách thất bại (HTTP ${response.code()}): ${response.message()}"))
                return@flow
            }

            val body = response.body()!!
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()

            // Stream to temporary file with progress tracking
            val targetFile = storageManager.importDownloadedEpub(inputStream, saveFileName)
            
            emit(DownloadState.Downloading(100))
            emit(DownloadState.Success(targetFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Lỗi khi tải file EPUB từ server"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun translateChapter(
        novelId: String,
        chapterIndex: Int,
        apiKey: String,
        provider: String,
        model: String
    ): Result<TranslateChapterResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.translateChapter(
                novelId = novelId,
                chapterIndex = chapterIndex,
                apiKey = apiKey,
                provider = provider,
                model = model
            )
            TranslateChapterResult(
                message = dto.message,
                chapter = dto.chapter?.let { chapterDto ->
                    OnlineChapterSummary(
                        chapterIndex = chapterDto.chapterIndex,
                        chapterTitle = chapterDto.chapterTitle,
                        status = chapterDto.status ?: "completed",
                        wordCount = chapterDto.wordCount,
                        translatedTextPreview = chapterDto.translatedTextPreview
                    )
                }
            )
        }
    }

    override suspend fun uploadEpub(
        filePath: String,
        isTranslated: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File không tồn tại: $filePath")
            }

            val mediaType = "application/epub+zip".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val isTranslatedBody = isTranslated.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val responseBody = apiService.uploadEpub(filePart, isTranslatedBody)
            responseBody.string().ifBlank { "Upload thành công" }
        }
    }

    override fun getBaseUrl(): Flow<String> = serverPreferencesManager.baseUrlFlow

    override suspend fun setBaseUrl(url: String) {
        withContext(Dispatchers.IO) {
            serverPreferencesManager.saveBaseUrl(url)
        }
    }

    override suspend fun testServerConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getNovels()
            true
        }
    }
}
