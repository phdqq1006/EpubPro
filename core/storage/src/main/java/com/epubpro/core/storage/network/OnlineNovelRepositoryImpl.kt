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

/**
 * Triển khai interface [OnlineNovelRepository], xử lý giao tiếp mạng qua Retrofit, bóc tách DTO sang Domain Model
 * và lưu trữ trực tiếp file EPUB vào bộ nhớ ứng dụng.
 */
@Singleton
class OnlineNovelRepositoryImpl @Inject constructor(
    private val apiService: OnlineNovelApiService,
    private val storageManager: EpubStorageManager,
    private val serverPreferencesManager: ServerPreferencesManager
) : OnlineNovelRepository {

    /**
     * Lấy danh sách tóm tắt tất cả các bộ truyện online từ server.
     */
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

    /**
     * Lấy chi tiết bộ truyện và danh sách chương từ server.
     */
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

    /**
     * Lấy nội dung chi tiết của một chương truyện từ server để đọc trực tuyến.
     */
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

    /**
     * Tải file EPUB nhị phân dưới dạng luồng dữ liệu (Stream) và ghi thẳng vào internal storage.
     */
    override fun downloadEpub(novelId: String, saveFileName: String): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))
        try {
            val response = apiService.downloadEpub(novelId)
            if (!response.isSuccessful || response.body() == null) {
                emit(DownloadState.Error("Tải sách thất bại (HTTP ${response.code()}): ${response.message()}"))
                return@flow
            }

            val body = response.body()!!
            val inputStream: InputStream = body.byteStream()

            // Ghi luồng dữ liệu vào file tạm trên thiết bị
            val targetFile = storageManager.importDownloadedEpub(inputStream, saveFileName)
            
            emit(DownloadState.Downloading(100))
            emit(DownloadState.Success(targetFile.absolutePath))
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Lỗi khi tải file EPUB từ server"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Yêu cầu AI dịch một chương truyện cụ thể.
     */
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

    /**
     * Upload trực tiếp file EPUB từ điện thoại lên server backend.
     */
    override suspend fun uploadEpub(
        filePath: String,
        isTranslated: Boolean,
        novelId: String?,
        autoScanCharacters: Boolean
    ): Result<ImportJobStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("File không tồn tại: $filePath")
            }

            val mediaType = "application/epub+zip".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val isTranslatedBody = isTranslated.toString().toRequestBody("text/plain".toMediaTypeOrNull())
            
            val novelIdBody = novelId?.toRequestBody("text/plain".toMediaTypeOrNull())
            val autoScanBody = autoScanCharacters.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val dto = apiService.uploadEpub(filePart, isTranslatedBody, novelIdBody, autoScanBody)
            mapToImportJobStatus(dto)
        }
    }

    /**
     * Lấy trạng thái của tiến trình upload.
     */
    override suspend fun getImportJobStatus(jobId: String): Result<ImportJobStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val dto = apiService.getImportJobStatus(jobId)
            mapToImportJobStatus(dto)
        }
    }

    private fun mapToImportJobStatus(dto: ImportJobStatusDto): ImportJobStatus {
        return ImportJobStatus(
            jobId = dto.jobId,
            novelId = dto.novelId,
            title = dto.title,
            status = dto.status,
            currentStep = dto.currentStep,
            currentChapter = dto.currentChapter,
            totalChapters = dto.totalChapters,
            progressPercentage = dto.progressPercentage,
            errorMessage = dto.errorMessage,
            createdAt = dto.createdAt,
            completedAt = dto.completedAt
        )
    }

    /**
     * Lấy luồng phát ra địa chỉ Base URL hiện tại của backend.
     */
    override fun getBaseUrl(): Flow<String> = serverPreferencesManager.baseUrlFlow

    /**
     * Cập nhật địa chỉ Base URL mới cho backend.
     */
    override suspend fun setBaseUrl(url: String) {
        withContext(Dispatchers.IO) {
            serverPreferencesManager.saveBaseUrl(url)
        }
    }

    /**
     * Kiểm tra ping kết nối tới server.
     */
    override suspend fun testServerConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            apiService.getNovels()
            true
        }
    }
}
