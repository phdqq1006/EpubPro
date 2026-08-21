package com.epubpro.core.storage.bookbible

import android.content.Context
import androidx.work.*
import com.epubpro.core.database.dao.BookDao
import com.epubpro.core.database.dao.BookBibleDao
import com.epubpro.core.database.entity.BookBibleEditionEntity
import com.epubpro.core.database.entity.BookBibleSnapshotEntity
import com.epubpro.core.database.entity.BookBibleSubmissionEntity
import com.epubpro.core.database.entity.BookBibleTimelineEntity
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.network.*
import com.epubpro.domain.model.*
import com.epubpro.domain.repository.BookBibleRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Payload chứa siêu dữ liệu mở rộng được lưu trữ an toàn trong trường `coverageJson` của Snapshot.
 */
private data class SnapshotMetadataPayload(
    val coverage: CoverageDto? = null,
    val completeThroughChapter: Int? = null,
    val pendingChapters: List<Int>? = null,
    val projectionStatus: String? = null,
    val bookRevision: Int = 0,
    val projectionRevision: Int = 0,
    val backendBookId: String? = null
)

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
    private val fingerprintGenerator: BookBibleFingerprintGenerator,
    private val storageManager: EpubStorageManager,
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
                entity?.let {
                    bookBibleDao.updateSnapshotLastAccessed(it.id)
                    mapEntityToDomainSnapshot(it)
                }
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Quan sát danh sách tiến trình Book Bible đã được lưu trong Room để màn hình cấp ứng dụng có thể duyệt truyện offline.
     *
     * @return [Flow] phát ra các tóm tắt tiến trình theo lần cập nhật gần nhất.
     */
    override fun observeProgressSummaries(): Flow<List<BookBibleProgressSummary>> {
        return bookBibleDao.observeProgressEntries()
            .map { entries ->
                entries.mapNotNull { entry ->
                    val sourceParts = entry.localSourceKey.split(":", limit = 2)
                    val sourceType = sourceParts.firstOrNull()?.let {
                        runCatching { BookBibleSourceType.valueOf(it) }.getOrNull()
                    } ?: return@mapNotNull null
                    val sourceId = sourceParts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                    val snapshotStatus = entry.snapshotStatus
                        ?.takeIf { entry.latestSnapshotUpdatedAt >= entry.latestSubmissionUpdatedAt }
                        ?.let { runCatching { SnapshotStatus.valueOf(it) }.getOrNull() }
                    val submissionState = when (entry.submissionState) {
                        "PENDING" -> SubmissionState.Pending
                        "SUBMITTING" -> SubmissionState.Submitting
                        "ACCEPTED" -> SubmissionState.Accepted
                        "PROCESSING" -> SubmissionState.Processing
                        "COMPLETED" -> SubmissionState.Completed
                        "RETRYABLE_FAILURE" -> SubmissionState.RetryableFailure("")
                        "PERMANENT_FAILURE" -> SubmissionState.PermanentFailure("")
                        else -> null
                    }

                    BookBibleProgressSummary(
                        source = BookBibleSource(sourceType, sourceId),
                        title = entry.title,
                        author = entry.author,
                        totalChapters = entry.chapterCount,
                        latestChapterNumber = maxOf(
                            entry.latestSnapshotChapter,
                            entry.latestSubmissionChapter
                        ),
                        snapshotStatus = snapshotStatus,
                        submissionState = submissionState,
                        updatedAt = entry.updatedAt
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun refreshSnapshot(
        source: BookBibleSource,
        chapterNumber: Int
    ): Result<BookBibleSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val edition = ensureEdition(source, chapterNumber)

            // Gọi API backend lấy CharacterSnapshotResponse
            val dto = apiService.getSnapshot(
                editionId = edition.backendEditionId,
                chapterNumber = chapterNumber
            )

            val metadataPayload = SnapshotMetadataPayload(
                coverage = dto.coverage,
                completeThroughChapter = dto.completeThroughChapter,
                pendingChapters = dto.pendingChapters,
                projectionStatus = dto.projectionStatus,
                bookRevision = dto.bookRevision,
                projectionRevision = dto.projectionRevision,
                backendBookId = dto.bookId.takeIf { it.isNotBlank() } ?: edition.backendBookId
            )
            val coverageJson = gson.toJson(metadataPayload)
            val allCharDtos = mutableListOf<CharacterProfileDto>()
            dto.mainCharacter?.let { mainDto ->
                allCharDtos.add(mainDto.copy(isMain = true))
            }
            dto.characters?.forEach { c ->
                val charId = c.characterId ?: c.id
                val name = c.name ?: c.viName
                if (allCharDtos.none { (charId != null && (it.characterId == charId || it.id == charId)) || (name != null && (it.name == name || it.viName == name)) }) {
                    allCharDtos.add(c)
                }
            }
            val payloadJson = gson.toJson(allCharDtos)
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

    override suspend fun checkSubmissionStatus(submissionId: String): Result<SubmissionState> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.getSubmissionStatus(submissionId)
            when (response.status?.lowercase(Locale.ROOT)) {
                "completed" -> SubmissionState.Completed
                "processing", "reviewing" -> SubmissionState.Processing
                "queued", "accepted" -> SubmissionState.Accepted
                "failed" -> SubmissionState.PermanentFailure(response.errorMessage ?: "Quá trình phân tích thất bại.")
                else -> SubmissionState.Processing
            }
        }
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

            val uniqueWorkName = "bookbible_submit_${localSourceKey}_$chapterNumber"
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
        var fingerprints = BookFingerprints(file = "", edition = "v1", structure = "")

        when (source.type) {
            BookBibleSourceType.LOCAL_EPUB -> {
                val book = bookDao.getBookById(source.sourceId)
                if (book != null) {
                    title = book.title
                    author = book.author
                    totalChapters = if (book.totalChapters > 0) book.totalChapters else chapterNumber
                    val file = storageManager.getBookFile(book.filePath)
                    if (file.isFile) {
                        fingerprints = fingerprintGenerator.generateFromEpubFile(file)
                    }
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
                    fingerprints = fingerprintGenerator.generateForOnlineNovel(source.sourceId)
                }
            }
        }

        if (title.isNotBlank()) {
            val bookRes = apiService.resolveBook(
                body = BookResolutionRequestDto(
                    metadata = BookMetadataDto(
                        title = title,
                        author = author,
                        language = "vi"
                    ),
                    fingerprints = BookFingerprintsDto(
                        file = fingerprints.file,
                        edition = fingerprints.edition,
                        structure = fingerprints.structure,
                        sampledChapters = fingerprints.sampledChapters
                    ),
                    createIfMissing = true
                )
            )

            val bookId = when {
                !bookRes.bookId.isNullOrBlank() -> bookRes.bookId
                !bookRes.candidates.isNullOrEmpty() -> {
                    // Chọn candidate có điểm khớp cao nhất khi gặp confirmation_required
                    bookRes.candidates.maxByOrNull { it.score }?.bookId
                        ?: bookRes.candidates.first().bookId
                }
                else -> null
            } ?: throw IllegalStateException("Không nhận diện được book_id từ server cho sách $title.")

            val editionRes = apiService.resolveOrCreateEdition(
                bookId = bookId,
                body = CreateEditionRequestDto(
                    metadata = BookMetadataDto(
                        title = title,
                        author = author,
                        language = "vi"
                    ),
                    fingerprints = BookFingerprintsDto(
                        file = fingerprints.file,
                        edition = fingerprints.edition,
                        structure = fingerprints.structure,
                        sampledChapters = fingerprints.sampledChapters
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
        var metadataPayload: SnapshotMetadataPayload? = null
        var coverageDto: CoverageDto? = null

        // Cố gắng deserialize theo SnapshotMetadataPayload trước (mới), nếu không được fallback sang CoverageDto (cũ)
        val metaParsed = runCatching { gson.fromJson(entity.coverageJson, SnapshotMetadataPayload::class.java) }.getOrNull()
        if (metaParsed != null && (metaParsed.coverage != null || metaParsed.completeThroughChapter != null || metaParsed.projectionStatus != null || metaParsed.backendBookId != null)) {
            metadataPayload = metaParsed
            coverageDto = metaParsed.coverage
        } else {
            val coverageType = object : TypeToken<CoverageDto>() {}.type
            coverageDto = runCatching { gson.fromJson<CoverageDto>(entity.coverageJson, coverageType) }.getOrNull()
        }

        val characterListType = object : TypeToken<List<CharacterProfileDto>>() {}.type
        val characterDtos: List<CharacterProfileDto> = runCatching {
            gson.fromJson<List<CharacterProfileDto>>(entity.payloadJson, characterListType)
        }.getOrNull() ?: emptyList()

        val characters = characterDtos.map { dto ->
            val charId = dto.characterId ?: dto.id ?: ""

            // 1. Kiểm tra object attributes["profile"] lồng nhau (theo Techspec mới)
            val profileElem = dto.attributes?.get("profile")
            val profileObj = if (profileElem != null && profileElem.isJsonObject) profileElem.asJsonObject else null

            val name = profileObj?.get("vi_name")?.asStringOrJson()
                ?: profileObj?.get("name")?.asStringOrJson()
                ?: dto.name
                ?: dto.attributes?.get("name")?.asStringOrJson()
                ?: dto.attributes?.get("vi_name")?.asStringOrJson()
                ?: dto.originalName
                ?: ""

            val origName = dto.originalName
                ?: profileObj?.get("original_name")?.asStringOrJson()
                ?: dto.attributes?.get("original_name")?.asStringOrJson()

            val isMain = dto.isMain == true
                || dto.isProtagonistFlag == true
                || profileObj?.get("is_main")?.asBoolean == true
                || profileObj?.get("is_protagonist")?.asBoolean == true
                || dto.attributes?.get("is_main")?.asBoolean == true
                || dto.attributes?.get("is_protagonist")?.asBoolean == true

            val role = profileObj?.get("role")?.asStringOrJson()
                ?: dto.attributes?.get("role")?.asStringOrJson()
                ?: dto.role
                ?: (if (isMain) "Nhân vật chính" else null)

            val voiceNotes = profileObj?.get("voice_notes")?.asStringOrJson()
                ?: dto.attributes?.get("voice_notes")?.asStringOrJson()

            val aliases = extractStringList(
                null,
                profileObj?.get("aliases") ?: dto.attributes?.get("aliases")
            )

            val realm = dto.cultivationRealm
                ?: dto.attributes?.get("cultivation_realm")?.asStringOrJson()

            val lastChanged = profileObj?.get("last_changed_chapter")?.asIntOrNull()
                ?: dto.attributes?.get("last_changed_chapter")?.asIntOrNull()
                ?: dto.lastChangedChapter
                ?: 1

            val changedInCurr = dto.changedInCurrentChapter
                || (dto.lastChangedChapter != null && dto.lastChangedChapter == entity.chapterNumber && entity.chapterNumber > 1)
                || (profileObj?.get("last_changed_chapter")?.asIntOrNull() == entity.chapterNumber && entity.chapterNumber > 1)
                || (dto.attributes?.get("last_changed_chapter")?.asIntOrNull() == entity.chapterNumber && entity.chapterNumber > 1)

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

            val standardKeys = setOf(
                "profile", "name", "vi_name", "original_name", "role", "voice_notes", "aliases",
                "cultivation_realm", "techniques", "skills", "items", "pets", "relationships",
                "affiliations", "titles", "last_changed_chapter",
                "address_terms", "addressTerms", "xưng_hô", "xung_ho", "is_main", "is_protagonist"
            )
            val extraAttributes = mutableListOf<ExtraAttribute>()
            dto.attributes?.forEach { (k, v) ->
                if (k !in standardKeys) {
                    val cleanVal = sanitizeAttributeValue(v)
                    if (cleanVal != null) {
                        extraAttributes.add(ExtraAttribute(key = k, label = formatAttributeLabel(k), value = cleanVal))
                    }
                }
            }
            dto.extraAttributes?.forEach { (k, v) ->
                if (k !in standardKeys) {
                    val cleanVal = sanitizeAttributeValue(v)
                    if (cleanVal != null) {
                        extraAttributes.add(ExtraAttribute(key = k, label = formatAttributeLabel(k), value = cleanVal))
                    }
                }
            }

            val allTitles = (extractStringList(dto.titles, dto.attributes?.get("titles")) + aliases).distinct()
            val pets = extractPetList(dto.pets ?: profileObj?.get("pets") ?: dto.attributes?.get("pets"))
            val addressTerms = extractAddressTerms(
                dto.addressTerms
                    ?: profileObj?.get("address_terms")
                    ?: dto.attributes?.get("address_terms")
                    ?: dto.attributes?.get("addressTerms")
            )

            CharacterProfile(
                id = charId,
                name = name,
                originalName = origName,
                role = role,
                isMain = isMain,
                voiceNotes = voiceNotes,
                aliases = aliases,
                changedInCurrentChapter = changedInCurr,
                lastChangedChapter = lastChanged,
                cultivationRealm = realm,
                techniques = extractStringList(dto.techniques, dto.attributes?.get("techniques")),
                skills = extractStringList(dto.skills, dto.attributes?.get("skills")),
                items = extractStringList(dto.items, dto.attributes?.get("items")),
                pets = pets,
                addressTerms = addressTerms,
                relationships = relationships,
                affiliations = extractStringList(dto.affiliations, dto.attributes?.get("affiliations")),
                titles = allTitles,
                extraAttributes = extraAttributes
            )
        }

        val processedRanges = coverageDto?.processedRanges?.mapNotNull {
            if (it.size >= 2) ChapterRange(it[0], it[1]) else null
        } ?: run {
            val completeThru = metadataPayload?.completeThroughChapter
            if (completeThru != null && completeThru > 0) {
                listOf(ChapterRange(1, completeThru))
            } else {
                emptyList()
            }
        }

        val missingRanges = coverageDto?.missingRanges?.mapNotNull {
            if (it.size >= 2) ChapterRange(it[0], it[1]) else null
        } ?: run {
            metadataPayload?.pendingChapters?.map { ChapterRange(it, it) } ?: emptyList()
        }

        val status = runCatching { SnapshotStatus.valueOf(entity.status) }.getOrDefault(SnapshotStatus.COMPLETE)

        // Sửa lỗi: Lấy đúng backendBookId (không gán nhầm editionId vào bookId)
        val resolvedBookId = metadataPayload?.backendBookId
            ?: entity.editionId

        return BookBibleSnapshot(
            bookId = resolvedBookId,
            editionId = entity.editionId,
            requestedChapter = entity.chapterNumber,
            canonicalChapter = entity.canonicalChapter,
            status = status,
            coverage = SnapshotCoverage(processedRanges, missingRanges),
            revision = entity.revision,
            bookRevision = metadataPayload?.bookRevision ?: 0,
            projectionRevision = metadataPayload?.projectionRevision ?: 0,
            projectionStatus = metadataPayload?.projectionStatus ?: "ready",
            completeThroughChapter = metadataPayload?.completeThroughChapter,
            pendingChapters = metadataPayload?.pendingChapters ?: emptyList(),
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

    private fun extractPetList(jsonElem: com.google.gson.JsonElement?): List<CharacterPet> {
        if (jsonElem == null || !jsonElem.isJsonArray) return emptyList()
        val list = mutableListOf<CharacterPet>()
        val array = jsonElem.asJsonArray
        for (i in 0 until array.size()) {
            val elem = array.get(i) ?: continue
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asStringOrJson() ?: ""
                val species = obj.get("species")?.asStringOrJson()
                val realm = obj.get("realm")?.asStringOrJson()
                val status = obj.get("status")?.asStringOrJson()
                if (name.isNotBlank() || species != null) {
                    list.add(
                        CharacterPet(
                            name = name.ifBlank { species ?: "Linh thú" },
                            species = species,
                            realm = realm,
                            status = status
                        )
                    )
                }
            } else if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                val str = elem.asString
                if (str.isNotBlank()) {
                    list.add(CharacterPet(name = str))
                }
            }
        }
        return list
    }

    private fun extractAddressTerms(jsonElem: com.google.gson.JsonElement?): List<CharacterAddressTerm> {
        if (jsonElem == null || !jsonElem.isJsonArray) return emptyList()
        val rawList = mutableListOf<CharacterAddressTerm>()
        val array = jsonElem.asJsonArray
        for (i in 0 until array.size()) {
            val elem = array.get(i) ?: continue
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val targetName = obj.get("with")?.asStringOrJson()
                    ?: obj.get("counterpart_text")?.asStringOrJson()
                    ?: obj.get("counterpart_original_name")?.asStringOrJson()
                    ?: obj.get("target_name")?.asStringOrJson()
                    ?: ""
                val selfTerm = obj.get("self")?.asStringOrJson()
                    ?: obj.get("self_term")?.asStringOrJson()
                val otherTerm = obj.get("other")?.asStringOrJson()
                    ?: obj.get("other_term")?.asStringOrJson()
                val context = obj.get("context")?.asStringOrJson()

                if (targetName.isNotBlank() || !selfTerm.isNullOrBlank() || !otherTerm.isNullOrBlank()) {
                    rawList.add(
                        CharacterAddressTerm(
                            targetName = targetName.ifBlank { "Đối phương" }.trim(),
                            selfTerm = selfTerm?.trim(),
                            otherTerm = otherTerm?.trim(),
                            context = context?.trim()
                        )
                    )
                }
            } else if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                val str = elem.asString.trim()
                if (str.isNotBlank()) {
                    rawList.add(CharacterAddressTerm(targetName = "Xưng hô", selfTerm = str))
                }
            }
        }

        // Nhóm và gộp thông minh theo đối tượng giao tiếp (loại bỏ lặp trùng lặp cho cùng một người)
        val groups = mutableListOf<MutableList<CharacterAddressTerm>>()
        for (item in rawList) {
            val matchedGroup = groups.firstOrNull { group ->
                group.any { existing -> isSamePerson(existing.targetName, item.targetName) }
            }
            if (matchedGroup != null) {
                matchedGroup.add(item)
            } else {
                groups.add(mutableListOf(item))
            }
        }

        return groups.map { list ->
            // Chọn tên hiển thị ưu tiên tên riêng hơn danh xưng chung (như "lão sư", "tiền bối")
            val primaryTarget = list.map { it.targetName }
                .sortedWith(
                    compareBy<String> { name ->
                        val n = name.lowercase(java.util.Locale.ROOT)
                        if (n.contains("lão sư") || n.contains("thầy") || n.contains("sư phụ") || n.contains("tiền bối")) 1 else 0
                    }.thenByDescending { it.length }
                ).firstOrNull() ?: list.first().targetName

            val allSelfTerms = list.mapNotNull { it.selfTerm }
                .flatMap { it.split("/", ",").map { s -> s.trim() } }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }

            val allOtherTerms = list.mapNotNull { it.otherTerm }
                .flatMap { it.split("/", ",").map { s -> s.trim() } }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }

            val allContexts = list.mapNotNull { it.context }.filter { it.isNotBlank() }.distinct()

            CharacterAddressTerm(
                targetName = primaryTarget,
                selfTerm = allSelfTerms,
                otherTerm = allOtherTerms,
                context = allContexts.firstOrNull(),
                contexts = allContexts
            )
        }
    }

    private val TITLE_WORDS = listOf(
        "lão sư", "lao su", "thầy", "cô", "tiền bối", "sư phụ", "huynh",
        "tỷ tỷ", "tỷ", "muội muội", "muội", "đệ đệ", "đệ", "đại sư", "trưởng lão", "tiểu thư", "công tử"
    )

    private fun isSamePerson(name1: String, name2: String): Boolean {
        val n1 = normalizeNameForMatching(name1)
        val n2 = normalizeNameForMatching(name2)
        if (n1.isEmpty() || n2.isEmpty()) return false
        if (n1 == n2) return true
        if (n1.length >= 3 && n2.length >= 3 && (n1.contains(n2) || n2.contains(n1))) {
            return true
        }
        return false
    }

    private fun normalizeNameForMatching(name: String): String {
        var result = name.lowercase(Locale.ROOT)
        for (title in TITLE_WORDS) {
            result = result.replace(title, " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun sanitizeAttributeValue(elem: com.google.gson.JsonElement?): String? {
        if (elem == null || elem.isJsonNull) return null
        if (elem.isJsonArray) {
            val array = elem.asJsonArray
            if (array.size() == 0) return null
            val items = mutableListOf<String>()
            for (i in 0 until array.size()) {
                val item = array.get(i) ?: continue
                if (item.isJsonNull) continue
                val str = item.asStringOrJson().trim()
                if (str.isNotBlank() && str != "null" && str != "None" && str != "undefined") {
                    items.add(str)
                }
            }
            return if (items.isNotEmpty()) items.joinToString(", ") else null
        }
        val raw = elem.asStringOrJson().trim()
        if (raw.isBlank() || raw == "null" || raw == "[null]" || raw == "None" || raw == "undefined" || raw == "{}" || raw == "[]") {
            return null
        }
        return raw
    }

    private fun formatAttributeLabel(key: String): String {
        val normalized = key.trim().lowercase(java.util.Locale.ROOT).replace("-", "_")
        val translated = ATTRIBUTE_LABEL_TRANSLATIONS[normalized]
            ?: ATTRIBUTE_LABEL_TRANSLATIONS[normalized.replace("_", " ")]
        if (translated != null) return translated

        return key.replace("_", " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
    }

    companion object {
        /** Giới hạn dung lượng mềm của toàn bộ Room cache Book Bible (50 MiB) */
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L

        /** Bảng dịch thuật ngữ các thuộc tính sang tiếng Việt chuẩn xác */
        private val ATTRIBUTE_LABEL_TRANSLATIONS = mapOf(
            "exam_score" to "Điểm sát hạch",
            "exam score" to "Điểm sát hạch",
            "exam_scores" to "Điểm các môn sát hạch",
            "test_score" to "Điểm thi",
            "martial_soul" to "Võ hồn",
            "martial soul" to "Võ hồn",
            "martial_souls" to "Võ hồn",
            "soul_rings" to "Hồn hoàn",
            "soul_ring" to "Hồn hoàn",
            "soul_bones" to "Hồn cốt",
            "soul_bone" to "Hồn cốt",
            "soul_power" to "Hồn lực",
            "soul power" to "Hồn lực",
            "spirit_power" to "Tinh thần lực",
            "bloodline" to "Huyết mạch",
            "bloodline_power" to "Sức mạnh huyết mạch",
            "cultivation" to "Tu vi / Cảnh giới",
            "physique" to "Thể chất",
            "innate_ability" to "Thiên phú",
            "innate ability" to "Thiên phú",
            "domain" to "Lĩnh vực",
            "age" to "Tuổi",
            "gender" to "Giới tính",
            "status" to "Trạng thái",
            "identity" to "Thân phận",
            "personality" to "Tính cách",
            "appearance" to "Ngoại hình",
            "weapon" to "Vũ khí",
            "equipment" to "Trang bị",
            "background" to "Bối cảnh",
            "origin" to "Xuất thân",
            "element" to "Thuộc tính",
            "alignment" to "Trận doanh",
            "grade" to "Phẩm cấp",
            "rank" to "Đẳng cấp",
            "level" to "Cấp bậc",
            "score" to "Điểm số",
            "achievement" to "Thành tựu",
            "assessment" to "Khảo hạch / Đánh giá",
            "location" to "Địa điểm",
            "current_location" to "Vị trí hiện tại",
            "sect" to "Tông môn",
            "family" to "Gia tộc",
            "organization" to "Tổ chức",
            "occupation" to "Nghề nghiệp"
        )
    }

    private fun mapDtoToTimelineEvent(dto: CharacterEventDto): CharacterTimelineEvent {
        val chapterNum = if (dto.canonicalChapter > 0) dto.canonicalChapter else (dto.chapter ?: 1)
        val cleanAttrKey = dto.attributeKey.trim()
        val cleanValueStr = sanitizeAttributeValue(dto.value)

        val displayVal = when {
            !dto.displayValue.isNullOrBlank() && dto.displayValue != "null" -> dto.displayValue

            // Trường hợp thú cưng (Pet)
            dto.category.equals("pet", ignoreCase = true) && dto.value != null && dto.value.isJsonObject -> {
                val obj = dto.value.asJsonObject
                val name = obj.get("name")?.asStringOrJson() ?: ""
                val species = obj.get("species")?.asStringOrJson()
                val realm = obj.get("realm")?.asStringOrJson()
                val opName = when (dto.operation.lowercase(java.util.Locale.ROOT)) {
                    "add" -> "Thu phục"
                    "set", "advance" -> "Tiến hóa / Đột phá"
                    "remove" -> "Rời đi"
                    else -> dto.operation
                }
                val details = listOfNotNull(species, realm).joinToString(" • ")
                if (details.isNotBlank()) "$opName $name ($details)" else "$opName $name"
            }

            // Trường hợp Xưng hô / Quan hệ (Address terms / Relationship)
            (dto.category.equals("relationship", ignoreCase = true) || cleanAttrKey.equals("address_terms", ignoreCase = true))
                    && dto.value != null && dto.value.isJsonObject -> {
                val obj = dto.value.asJsonObject
                val targetName = obj.get("with")?.asStringOrJson()
                    ?: obj.get("counterpart_text")?.asStringOrJson()
                    ?: obj.get("counterpart_original_name")?.asStringOrJson()
                    ?: "đối phương"
                val self = obj.get("self")?.asStringOrJson() ?: obj.get("self_term")?.asStringOrJson()
                val other = obj.get("other")?.asStringOrJson() ?: obj.get("other_term")?.asStringOrJson()
                val terms = listOfNotNull(
                    self?.let { "Tự xưng: $it" },
                    other?.let { "Gọi: $it" }
                ).joinToString(" • ")
                if (terms.isNotBlank()) "Xưng hô với $targetName ($terms)" else "Xưng hô với $targetName"
            }

            // Có giá trị value hợp lệ
            cleanValueStr != null -> {
                if (cleanAttrKey.isNotBlank() && !cleanAttrKey.equals(dto.category, ignoreCase = true) && !cleanAttrKey.equals("value", ignoreCase = true)) {
                    "${formatAttributeLabel(cleanAttrKey)}: $cleanValueStr"
                } else {
                    cleanValueStr
                }
            }

            // Value bị null nhưng có attribute_key (ví dụ: "Hoàng Kim Long Thể", "Kim Long Trảo")
            cleanAttrKey.isNotBlank() && !cleanAttrKey.equals(dto.category, ignoreCase = true) -> {
                when (dto.operation.lowercase(java.util.Locale.ROOT)) {
                    "add" -> "Lĩnh ngộ / Xuất hiện: $cleanAttrKey"
                    "set" -> cleanAttrKey
                    "remove" -> "Mất / Tiêu hao: $cleanAttrKey"
                    else -> cleanAttrKey
                }
            }

            else -> {
                val opName = formatOperationVietnamese(dto.operation)
                val catName = formatCategoryVietnamese(dto.category)
                "$opName $catName"
            }
        }

        return CharacterTimelineEvent(
            chapter = chapterNum,
            category = dto.category,
            operation = dto.operation,
            displayValue = displayVal,
            certainty = dto.certainty ?: "observed",
            evidence = dto.evidence,
            confidence = dto.confidence
        )
    }

    private fun formatOperationVietnamese(op: String): String {
        return when (op.lowercase(java.util.Locale.ROOT)) {
            "add" -> "Lĩnh ngộ / Thêm"
            "set" -> "Cập nhật"
            "advance" -> "Đột phá"
            "remove" -> "Loại bỏ"
            else -> op
        }
    }

    private fun formatCategoryVietnamese(cat: String): String {
        return when (cat.lowercase(Locale.ROOT)) {
            "skill" -> "kỹ năng"
            "technique" -> "công pháp"
            "item" -> "trang bị / pháp bảo"
            "cultivation", "realm" -> "cảnh giới tu vi"
            "relationship" -> "quan hệ nhân vật"
            "pet" -> "linh thú"
            "affiliation" -> "thế lực"
            "title" -> "danh hiệu"
            else -> cat
        }
    }

    private fun com.google.gson.JsonElement.asStringOrJson(): String {
        return if (isJsonPrimitive && asJsonPrimitive.isString) {
            asString
        } else {
            toString()
        }
    }

    private fun com.google.gson.JsonElement.asIntOrNull(): Int? {
        return runCatching {
            if (isJsonPrimitive && asJsonPrimitive.isNumber) {
                asInt
            } else if (isJsonPrimitive && asJsonPrimitive.isString) {
                asString.toIntOrNull()
            } else {
                null
            }
        }.getOrNull()
    }
}
