package com.epubpro.core.storage.bookbible

import android.content.Context
import androidx.work.*
import com.epubpro.core.database.dao.BookDao
import com.epubpro.core.database.dao.BookBibleDao
import com.epubpro.core.database.entity.BookBibleEditionEntity
import com.epubpro.core.database.entity.BookBibleSnapshotEntity
import com.epubpro.core.database.entity.BookBibleSubmissionEntity
import com.epubpro.core.database.entity.BookBibleTimelineEntity
import com.epubpro.core.storage.network.*
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.BookBibleRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triển khai interface [BookBibleRepository], quản lý điều phối giữa Room Database, WorkManager, PayloadStore và Retrofit API.
 */
@Singleton
class BookBibleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: BookBibleApiService,
    private val onlineNovelApiService: OnlineNovelApiService,
    private val bookDao: BookDao,
    private val bookBibleDao: BookBibleDao,
    private val payloadStore: BookBiblePayloadStore,
    private val gson: Gson
) : BookBibleRepository {

    override suspend fun enqueueChapterSubmission(
        source: BookBibleSource,
        chapterNumber: Int,
        chapterTitle: String,
        totalChapters: Int,
        sourceContent: String,
        bookTitle: String,
        author: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (sourceContent.isBlank()) return@runCatching

            val localSourceKey = source.uniqueKey
            val sourceHash = payloadStore.computeSha256(sourceContent)
            val submissionId = payloadStore.computeSha256("$localSourceKey:$chapterNumber:$sourceHash")

            // Kiểm tra nếu submission đã hoàn thành hoặc đang được xử lý
            val existing = bookBibleDao.getSubmissionById(submissionId)
            if (existing != null && (existing.state == "ACCEPTED" || existing.state == "PROCESSING" || existing.state == "COMPLETED")) {
                return@runCatching
            }

            // Ghi nội dung văn bản nguồn vào file tạm
            val payloadPath = payloadStore.writePayloadAtomically(sourceContent, sourceHash)

            // Lưu bản ghi vào Room DB
            val entity = BookBibleSubmissionEntity(
                id = submissionId,
                localSourceKey = localSourceKey,
                chapterNumber = chapterNumber,
                sourceHash = sourceHash,
                payloadPath = payloadPath,
                state = "PENDING"
            )
            bookBibleDao.insertOrReplaceSubmission(entity)

            // Lập lịch gửi qua WorkManager với ràng buộc kết nối mạng
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workData = workDataOf(
                BookBibleWorker.KEY_SUBMISSION_ID to submissionId,
                BookBibleWorker.KEY_LOCAL_SOURCE_KEY to localSourceKey,
                BookBibleWorker.KEY_CHAPTER_NUMBER to chapterNumber,
                BookBibleWorker.KEY_SOURCE_HASH to sourceHash,
                BookBibleWorker.KEY_BOOK_TITLE to bookTitle,
                BookBibleWorker.KEY_AUTHOR to author,
                BookBibleWorker.KEY_TOTAL_CHAPTERS to totalChapters,
                BookBibleWorker.KEY_CHAPTER_TITLE to chapterTitle
            )

            val workRequest = OneTimeWorkRequestBuilder<BookBibleWorker>()
                .setConstraints(constraints)
                .setInputData(workData)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "bookbible_submit_${localSourceKey}_$chapterNumber",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
            Unit
        }
    }

    override fun observeSnapshot(
        source: BookBibleSource,
        chapterNumber: Int
    ): Flow<BookBibleSnapshot?> {
        return bookBibleDao.observeSnapshotByLocalSource(source.uniqueKey, chapterNumber)
            .map { entity ->
                entity?.let { mapEntityToDomainSnapshot(it) }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun refreshSnapshot(
        source: BookBibleSource,
        chapterNumber: Int
    ): Result<BookBibleSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val edition = ensureEdition(source, chapterNumber)

            val dto = apiService.getSnapshot(
                editionId = edition.backendEditionId,
                chapterNumber = chapterNumber
            )

            val coverageJson = gson.toJson(dto.coverage)
            val payloadJson = gson.toJson(dto.characters ?: emptyList<CharacterProfileDto>())
            val byteSize = (coverageJson.length + payloadJson.length).toLong()

            val snapshotEntity = BookBibleSnapshotEntity(
                id = "${edition.backendEditionId}:$chapterNumber",
                editionId = edition.backendEditionId,
                localSourceKey = source.uniqueKey,
                chapterNumber = chapterNumber,
                canonicalChapter = dto.canonicalChapter ?: dto.requestedChapter,
                status = dto.snapshotStatus?.uppercase() ?: "COMPLETE",
                coverageJson = coverageJson,
                payloadJson = payloadJson,
                revision = dto.snapshotRevision,
                byteSize = byteSize,
                updatedAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            )

            bookBibleDao.insertOrReplaceSnapshot(snapshotEntity)
            checkAndPruneCache()

            mapEntityToDomainSnapshot(snapshotEntity)
        }
    }

    override suspend fun getCharacterTimeline(
        source: BookBibleSource,
        characterId: String,
        chapterNumber: Int
    ): Result<CharacterTimeline> = withContext(Dispatchers.IO) {
        runCatching {
            val edition = ensureEdition(source, chapterNumber)

            // Kiểm tra cache trước
            val cached = bookBibleDao.getTimeline(edition.backendEditionId, chapterNumber, characterId)
            if (cached != null) {
                bookBibleDao.updateTimelineLastAccessed(cached.id)
                val type = object : TypeToken<List<CharacterEventDto>>() {}.type
                val eventDtos: List<CharacterEventDto> = gson.fromJson(cached.payloadJson, type) ?: emptyList()
                return@runCatching CharacterTimeline(
                    characterId = characterId,
                    characterName = "",
                    events = eventDtos.map { mapDtoToTimelineEvent(it) }
                )
            }

            // Gọi API backend nếu chưa có cache
            val eventDtos = apiService.getCharacterTimeline(
                editionId = edition.backendEditionId,
                chapterBoundary = chapterNumber,
                characterId = characterId
            )

            val payloadJson = gson.toJson(eventDtos)
            val timelineEntity = BookBibleTimelineEntity(
                id = "${edition.backendEditionId}:$chapterNumber:$characterId",
                editionId = edition.backendEditionId,
                localSourceKey = source.uniqueKey,
                chapterNumber = chapterNumber,
                characterId = characterId,
                payloadJson = payloadJson,
                byteSize = payloadJson.length.toLong(),
                updatedAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            )
            bookBibleDao.insertOrReplaceTimeline(timelineEntity)
            checkAndPruneCache()

            CharacterTimeline(
                characterId = characterId,
                characterName = "",
                events = eventDtos.map { mapDtoToTimelineEvent(it) }
            )
        }
    }

    override fun observeSubmissionState(
        source: BookBibleSource,
        chapterNumber: Int
    ): Flow<SubmissionState?> {
        return bookBibleDao.observeSubmission(source.uniqueKey, chapterNumber)
            .map { entity ->
                entity?.let {
                    when (it.state) {
                        "PENDING" -> SubmissionState.Pending
                        "SUBMITTING" -> SubmissionState.Submitting
                        "ACCEPTED" -> SubmissionState.Accepted
                        "PROCESSING" -> SubmissionState.Processing
                        "COMPLETED" -> SubmissionState.Completed
                        "RETRYABLE_FAILURE" -> SubmissionState.RetryableFailure(it.errorMessage ?: "Lỗi tạm thời")
                        "PERMANENT_FAILURE" -> SubmissionState.PermanentFailure(it.errorMessage ?: "Lỗi vĩnh viễn")
                        else -> null
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun retrySubmission(
        source: BookBibleSource,
        chapterNumber: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val localSourceKey = source.uniqueKey
            val entity = bookBibleDao.getSubmission(localSourceKey, chapterNumber)
                ?: throw IllegalStateException("Không tìm thấy thông tin submission để thử lại.")

            // Reset trạng thái về PENDING
            bookBibleDao.updateSubmissionState(
                id = entity.id,
                state = "PENDING",
                submissionId = null,
                errorCode = null,
                errorMessage = null
            )

            // Lập lịch lại Worker
            val workRequest = OneTimeWorkRequestBuilder<BookBibleWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setInputData(
                    workDataOf(
                        BookBibleWorker.KEY_SUBMISSION_ID to entity.id,
                        BookBibleWorker.KEY_LOCAL_SOURCE_KEY to entity.localSourceKey,
                        BookBibleWorker.KEY_CHAPTER_NUMBER to entity.chapterNumber,
                        BookBibleWorker.KEY_SOURCE_HASH to entity.sourceHash,
                        BookBibleWorker.KEY_BOOK_TITLE to "",
                        BookBibleWorker.KEY_AUTHOR to "",
                        BookBibleWorker.KEY_TOTAL_CHAPTERS to 0,
                        BookBibleWorker.KEY_CHAPTER_TITLE to ""
                    )
                )
                .build()

            val uniqueWorkName = "bookbible_${localSourceKey}_$chapterNumber"
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Unit
        }
    }

    override suspend fun deleteDataForBook(sourceId: String) = withContext(Dispatchers.IO) {
        val localKey = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, sourceId).uniqueKey
        val onlineKey = BookBibleSource(BookBibleSourceType.ONLINE_NOVEL, sourceId).uniqueKey
        bookBibleDao.deleteByLocalSourceKey(localKey)
        bookBibleDao.deleteByLocalSourceKey(onlineKey)
    }

    /**
     * Đảm bảo thông tin phiên bản sách (Edition) đã được giải quyết và lưu trữ trong Room DB.
     * Nếu chưa có, tự động truy vấn thông tin sách cục bộ hoặc online và gọi API resolve/create edition.
     */
    private suspend fun ensureEdition(
        source: BookBibleSource,
        chapterNumber: Int
    ): BookBibleEditionEntity {
        val localSourceKey = source.uniqueKey
        val cachedEdition = bookBibleDao.getEditionByLocalSourceKey(localSourceKey)
        if (cachedEdition != null) return cachedEdition

        var title = ""
        var author = ""
        var totalChapters = chapterNumber

        when (source.type) {
            BookBibleSourceType.LOCAL_EPUB -> {
                val book = bookDao.getBookById(source.sourceId)
                if (book != null) {
                    title = book.title
                    author = book.author
                    totalChapters = if (book.totalChapters > 0) book.totalChapters else chapterNumber
                }
            }
            BookBibleSourceType.ONLINE_NOVEL -> {
                val novelDetail = runCatching { onlineNovelApiService.getNovelDetail(source.sourceId) }.getOrNull()
                if (novelDetail != null) {
                    title = novelDetail.title
                    author = novelDetail.author
                    totalChapters = if (novelDetail.totalChapters > 0) {
                        novelDetail.totalChapters
                    } else {
                        novelDetail.chapters?.size ?: chapterNumber
                    }
                }
            }
        }

        if (title.isNotBlank()) {
            val bookRes = apiService.resolveBook(
                BookResolutionRequestDto(
                    metadata = BookMetadataDto(
                        title = title,
                        author = author,
                        language = "vi"
                    ),
                    createIfMissing = true
                )
            )
            val bookId = bookRes.bookId
                ?: throw IllegalStateException("Không nhận được book_id từ server.")

            val editionRes = apiService.resolveOrCreateEdition(
                bookId = bookId,
                body = CreateEditionRequestDto(
                    metadata = BookMetadataDto(
                        title = title,
                        author = author,
                        language = "vi"
                    ),
                    chapterCount = totalChapters
                )
            )
            val newEdition = BookBibleEditionEntity(
                localSourceKey = localSourceKey,
                backendBookId = bookId,
                backendEditionId = editionRes.editionId,
                mappingRevision = editionRes.mappingRevision,
                title = title,
                author = author,
                chapterCount = totalChapters
            )
            bookBibleDao.insertEdition(newEdition)
            return newEdition
        } else {
            throw IllegalStateException("Không tìm thấy thông tin sách để kết nối Book Bible.")
        }
    }

    private suspend fun checkAndPruneCache() {
        val totalSize = bookBibleDao.getTotalCacheByteSize() ?: 0L
        if (totalSize > MAX_CACHE_SIZE_BYTES) {
            bookBibleDao.pruneOldestSnapshots(5)
            bookBibleDao.pruneOldestTimelines(5)
        }
    }

    private fun mapEntityToDomainSnapshot(entity: BookBibleSnapshotEntity): BookBibleSnapshot {
        val coverageType = object : TypeToken<CoverageDto>() {}.type
        val coverageDto: CoverageDto? = runCatching { gson.fromJson<CoverageDto>(entity.coverageJson, coverageType) }.getOrNull()

        val characterListType = object : TypeToken<List<CharacterProfileDto>>() {}.type
        val characterDtos: List<CharacterProfileDto> = runCatching {
            gson.fromJson<List<CharacterProfileDto>>(entity.payloadJson, characterListType)
        }.getOrNull() ?: emptyList()

        val characters = characterDtos.map { dto ->
            val charId = dto.characterId ?: dto.id ?: ""
            val name = dto.name
                ?: dto.attributes?.get("name")?.asStringOrJson()
                ?: dto.attributes?.get("vi_name")?.asStringOrJson()
                ?: dto.originalName
                ?: ""
            val origName = dto.originalName ?: dto.attributes?.get("original_name")?.asStringOrJson()
            val realm = dto.cultivationRealm ?: dto.attributes?.get("cultivation_realm")?.asStringOrJson()
            val lastChanged = dto.lastChangedChapter ?: entity.chapterNumber
            val changedInCurr = dto.changedInCurrentChapter || (dto.lastChangedChapter == entity.chapterNumber)

            val relationships = (dto.relationships ?: run {
                val relElem = dto.attributes?.get("relationships")
                if (relElem != null && relElem.isJsonArray) {
                    val type = object : TypeToken<List<CharacterRelationshipDto>>() {}.type
                    runCatching { gson.fromJson<List<CharacterRelationshipDto>>(relElem, type) }.getOrNull() ?: emptyList()
                } else {
                    emptyList()
                }
            }).map { rel ->
                CharacterRelationship(
                    targetName = rel.targetName,
                    relationType = rel.relationType,
                    description = rel.description
                )
            }

            val standardKeys = setOf("name", "vi_name", "original_name", "cultivation_realm", "techniques", "skills", "items", "relationships", "affiliations", "titles", "last_changed_chapter")
            val extraAttributes = mutableListOf<ExtraAttribute>()
            dto.attributes?.forEach { (k, v) ->
                if (k !in standardKeys) {
                    extraAttributes.add(ExtraAttribute(key = k, label = k, value = v.asStringOrJson()))
                }
            }
            dto.extraAttributes?.forEach { (k, v) ->
                if (k !in standardKeys) {
                    extraAttributes.add(ExtraAttribute(key = k, label = k, value = v.asStringOrJson()))
                }
            }

            CharacterProfile(
                id = charId,
                name = name,
                originalName = origName,
                changedInCurrentChapter = changedInCurr,
                lastChangedChapter = lastChanged,
                cultivationRealm = realm,
                techniques = extractStringList(dto.techniques, dto.attributes?.get("techniques")),
                skills = extractStringList(dto.skills, dto.attributes?.get("skills")),
                items = extractStringList(dto.items, dto.attributes?.get("items")),
                relationships = relationships,
                affiliations = extractStringList(dto.affiliations, dto.attributes?.get("affiliations")),
                titles = extractStringList(dto.titles, dto.attributes?.get("titles")),
                extraAttributes = extraAttributes
            )
        }

        val processedRanges = coverageDto?.processedRanges?.mapNotNull {
            if (it.size >= 2) ChapterRange(it[0], it[1]) else null
        } ?: emptyList()

        val missingRanges = coverageDto?.missingRanges?.mapNotNull {
            if (it.size >= 2) ChapterRange(it[0], it[1]) else null
        } ?: emptyList()

        val status = runCatching { SnapshotStatus.valueOf(entity.status) }.getOrDefault(SnapshotStatus.COMPLETE)

        return BookBibleSnapshot(
            bookId = entity.editionId,
            editionId = entity.editionId,
            requestedChapter = entity.chapterNumber,
            canonicalChapter = entity.canonicalChapter,
            status = status,
            coverage = SnapshotCoverage(processedRanges, missingRanges),
            revision = entity.revision,
            updatedAt = entity.updatedAt,
            characters = characters
        )
    }

    private fun extractStringList(field: List<String>?, jsonElem: com.google.gson.JsonElement?): List<String> {
        if (!field.isNullOrEmpty()) return field
        if (jsonElem != null && jsonElem.isJsonArray) {
            val list = mutableListOf<String>()
            val array = jsonElem.asJsonArray
            for (i in 0 until array.size()) {
                val elem = array.get(i)
                if (elem != null && elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                    list.add(elem.asString)
                }
            }
            return list
        }
        return emptyList()
    }

    private fun mapDtoToTimelineEvent(dto: CharacterEventDto): CharacterTimelineEvent {
        val chapterNum = if (dto.canonicalChapter > 0) dto.canonicalChapter else (dto.chapter ?: 1)
        val displayVal = dto.displayValue
            ?: dto.value?.asStringOrJson()
            ?: "${dto.operation} ${dto.category}"

        return CharacterTimelineEvent(
            chapter = chapterNum,
            category = dto.category,
            operation = dto.operation,
            displayValue = displayVal,
            evidence = dto.evidence,
            confidence = dto.confidence
        )
    }

    private fun com.google.gson.JsonElement.asStringOrJson(): String {
        return if (isJsonPrimitive && asJsonPrimitive.isString) {
            asString
        } else {
            toString()
        }
    }

    companion object {
        /** Giới hạn dung lượng mềm của toàn bộ Room cache Book Bible (50 MiB) */
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L
    }
}
