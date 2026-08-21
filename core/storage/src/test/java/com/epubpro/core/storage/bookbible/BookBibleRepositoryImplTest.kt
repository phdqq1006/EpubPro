package com.epubpro.core.storage.bookbible

import android.content.Context
import com.epubpro.core.database.dao.BookDao
import com.epubpro.core.database.dao.BookBibleDao
import com.epubpro.core.database.dao.BookBibleProgressEntry
import com.epubpro.core.database.entity.BookBibleEditionEntity
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.core.storage.network.*
import com.epubpro.domain.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class BookBibleRepositoryImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockApiService: BookBibleApiService
    private lateinit var mockOnlineNovelApiService: OnlineNovelApiService
    private lateinit var mockBookDao: BookDao
    private lateinit var mockBookBibleDao: BookBibleDao
    private lateinit var mockPayloadStore: BookBiblePayloadStore
    private lateinit var mockFingerprintGenerator: BookBibleFingerprintGenerator
    private lateinit var mockStorageManager: EpubStorageManager
    private val gson = Gson()

    private lateinit var repository: BookBibleRepositoryImpl

    @Before
    fun setUp() {
        mockContext = mock()
        mockApiService = mock()
        mockOnlineNovelApiService = mock()
        mockBookDao = mock()
        mockBookBibleDao = mock()
        mockPayloadStore = mock()
        mockFingerprintGenerator = mock()
        mockStorageManager = mock()

        repository = BookBibleRepositoryImpl(
            context = mockContext,
            apiService = mockApiService,
            onlineNovelApiService = mockOnlineNovelApiService,
            bookDao = mockBookDao,
            bookBibleDao = mockBookBibleDao,
            payloadStore = mockPayloadStore,
            fingerprintGenerator = mockFingerprintGenerator,
            storageManager = mockStorageManager,
            gson = gson
        )
    }

    @Test
    fun testRefreshSnapshotParsesNestedProfileAndMetadata() = runBlocking {
        val source = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, "book_1")
        val editionEntity = BookBibleEditionEntity(
            localSourceKey = source.uniqueKey,
            backendBookId = "real_backend_book_id",
            backendEditionId = "backend_edition_123",
            title = "Cổ Chân Nhân",
            author = "Cổ Chân Nhân",
            chapterCount = 100
        )
        whenever(mockBookBibleDao.getEditionByLocalSourceKey(source.uniqueKey)).thenReturn(editionEntity)

        val characterJson = """
            {
                "character_id": "c1",
                "original_name": "Tang San",
                "attributes": {
                    "profile": {
                        "vi_name": "Đường Tam",
                        "role": "protagonist",
                        "voice_notes": "Lạnh lùng, điềm tĩnh",
                        "aliases": ["Thiên Thủ Đấu La"]
                    },
                    "cultivation_realm": "Hồn Tôn",
                    "Hoàng Kim Long Thể": [null],
                    "Kim Long Trảo": null,
                    "Hồn Lực": "Cấp ba",
                    "exam_score": "50 điểm (sau 7 quan)",
                    "address_terms": [
                        {
                            "with": "Thẩm Dập",
                            "self": "chúng em/em",
                            "other": "Thẩm lão sư",
                            "context": "học viên với giáo viên hướng dẫn sát hạch"
                        },
                        {
                            "with": "Thẩm lão sư",
                            "self": "chúng em",
                            "other": "Thẩm lão sư",
                            "context": "Hỏi Thẩm Dập về quy tắc thi ăn và xin điểm tuyệt đối"
                        }
                    ]
                },
                "pets": [
                    {
                        "name": "Tiểu Hắc",
                        "species": "U Minh Miêu",
                        "realm": "Bách Niên Hồn Thú",
                        "status": "Khế ước linh hồn"
                    }
                ]
            }
        """.trimIndent()
        val charDto = gson.fromJson(characterJson, CharacterProfileDto::class.java)

        val apiResponse = CharacterSnapshotResponseDto(
            bookId = "real_backend_book_id",
            editionId = "backend_edition_123",
            requestedChapter = 5,
            canonicalChapter = 5,
            bookRevision = 3,
            projectionRevision = 4,
            projectionStatus = "ready",
            snapshotStatus = "complete",
            completeThroughChapter = 5,
            pendingChapters = emptyList(),
            coverage = CoverageDto(
                processedRanges = listOf(listOf(1, 5)),
                missingRanges = emptyList()
            ),
            characters = listOf(charDto)
        )
        whenever(mockApiService.getSnapshot("backend_edition_123", 5)).thenReturn(apiResponse)
        whenever(mockBookBibleDao.getTotalCacheByteSize()).thenReturn(1024L)

        val result = repository.refreshSnapshot(source, 5)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrNull()
        assertNotNull(snapshot)

        // Kiểm tra đúng backendBookId (không gán nhầm editionId vào bookId)
        assertEquals("real_backend_book_id", snapshot!!.bookId)
        assertEquals("backend_edition_123", snapshot.editionId)
        assertEquals(3, snapshot.bookRevision)
        assertEquals(4, snapshot.projectionRevision)
        assertEquals("ready", snapshot.projectionStatus)
        assertEquals(5, snapshot.completeThroughChapter)

        // Kiểm tra parse nested attributes.profile, pets, addressTerms và làm sạch extraAttributes
        assertEquals(1, snapshot.characters.size)
        val character = snapshot.characters[0]
        assertEquals("c1", character.id)
        assertEquals("Đường Tam", character.name)
        assertEquals("Tang San", character.originalName)
        assertEquals("protagonist", character.role)
        assertEquals("Lạnh lùng, điềm tĩnh", character.voiceNotes)
        assertTrue(character.aliases.contains("Thiên Thủ Đấu La"))
        assertEquals("Hồn Tôn", character.cultivationRealm)
        assertEquals(1, character.pets.size)
        assertEquals("Tiểu Hắc", character.pets[0].name)
        assertEquals("U Minh Miêu", character.pets[0].species)
        assertEquals("Bách Niên Hồn Thú", character.pets[0].realm)
        assertEquals("Khế ước linh hồn", character.pets[0].status)

        // Kiểm tra parse và GỘP nhóm chống trùng lặp Xưng hô & Giao tiếp (Address Terms)
        assertEquals(1, character.addressTerms.size)
        assertEquals("Thẩm Dập", character.addressTerms[0].targetName)
        assertEquals("chúng em / em", character.addressTerms[0].selfTerm)
        assertEquals("Thẩm lão sư", character.addressTerms[0].otherTerm)
        assertEquals(2, character.addressTerms[0].contexts.size)
        assertTrue(character.addressTerms[0].contexts.contains("học viên với giáo viên hướng dẫn sát hạch"))
        assertTrue(character.addressTerms[0].contexts.contains("Hỏi Thẩm Dập về quy tắc thi ăn và xin điểm tuyệt đối"))

        // Kiểm tra lọc sạch extraAttributes: không còn [null], null và dịch exam_score sang tiếng Việt "Điểm sát hạch"
        assertEquals(2, character.extraAttributes.size)
        val attrMap = character.extraAttributes.associate { it.label to it.value }
        assertEquals("Cấp ba", attrMap["Hồn Lực"])
        assertEquals("50 điểm (sau 7 quan)", attrMap["Điểm sát hạch"])
    }

    /**
     * Kiểm tra repository chuyển đúng bản ghi tổng hợp từ Room thành tiến trình domain cho màn hình cấp ứng dụng.
     */
    @Test
    fun testObserveProgressSummariesMapsDaoEntry() = runBlocking {
        val entry = BookBibleProgressEntry(
            localSourceKey = "ONLINE_NOVEL:novel_1",
            title = "Truyện thử nghiệm",
            author = "Tác giả",
            chapterCount = 120,
            latestSnapshotChapter = 18,
            latestSubmissionChapter = 20,
            snapshotStatus = "PARTIAL",
            submissionState = "PROCESSING",
            latestSnapshotUpdatedAt = 18L,
            latestSubmissionUpdatedAt = 20L,
            updatedAt = 42L
        )
        whenever(mockBookBibleDao.observeProgressEntries()).thenReturn(flowOf(listOf(entry)))

        val summary = repository.observeProgressSummaries().first().single()

        assertEquals(BookBibleSourceType.ONLINE_NOVEL, summary.source.type)
        assertEquals("novel_1", summary.source.sourceId)
        assertEquals(20, summary.latestChapterNumber)
        assertNull(summary.snapshotStatus)
        assertTrue(summary.submissionState is SubmissionState.Processing)
    }

    @Test
    fun testRefreshSnapshotParsesLegacyFlatAttributesFallback() = runBlocking {
        val source = BookBibleSource(BookBibleSourceType.ONLINE_NOVEL, "novel_1")
        val editionEntity = BookBibleEditionEntity(
            localSourceKey = source.uniqueKey,
            backendBookId = "online_book_id",
            backendEditionId = "online_edition_id",
            title = "Phàm Nhân Tu Tiên",
            author = "Vong Ngữ",
            chapterCount = 200
        )
        whenever(mockBookBibleDao.getEditionByLocalSourceKey(source.uniqueKey)).thenReturn(editionEntity)

        val characterJson = """
            {
                "character_id": "c2",
                "name": "Hàn Lập",
                "originalName": "Han Li",
                "attributes": {
                    "cultivation_realm": "Trúc Cơ kỳ",
                    "role": "main",
                    "voice_notes": "Cẩn trọng, ít nói"
                }
            }
        """.trimIndent()
        val charDto = gson.fromJson(characterJson, CharacterProfileDto::class.java)

        val apiResponse = CharacterSnapshotResponseDto(
            bookId = "online_book_id",
            editionId = "online_edition_id",
            requestedChapter = 10,
            canonicalChapter = 10,
            characters = listOf(charDto)
        )
        whenever(mockApiService.getSnapshot("online_edition_id", 10)).thenReturn(apiResponse)
        whenever(mockBookBibleDao.getTotalCacheByteSize()).thenReturn(1024L)

        val result = repository.refreshSnapshot(source, 10)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrNull()!!
        val character = snapshot.characters[0]
        assertEquals("Hàn Lập", character.name)
        assertEquals("main", character.role)
        assertEquals("Cẩn trọng, ít nói", character.voiceNotes)
        assertEquals("Trúc Cơ kỳ", character.cultivationRealm)
    }

    @Test
    fun testCheckSubmissionStatus() = runBlocking {
        whenever(mockApiService.getSubmissionStatus("sub_completed")).thenReturn(
            ChapterSubmissionResponseDto(status = "completed")
        )
        whenever(mockApiService.getSubmissionStatus("sub_processing")).thenReturn(
            ChapterSubmissionResponseDto(status = "processing")
        )
        whenever(mockApiService.getSubmissionStatus("sub_failed")).thenReturn(
            ChapterSubmissionResponseDto(status = "failed", errorMessage = "Quá tải model LLM")
        )

        val res1 = repository.checkSubmissionStatus("sub_completed")
        assertEquals(SubmissionState.Completed, res1.getOrNull())

        val res2 = repository.checkSubmissionStatus("sub_processing")
        assertEquals(SubmissionState.Processing, res2.getOrNull())

        val res3 = repository.checkSubmissionStatus("sub_failed")
        assertTrue(res3.getOrNull() is SubmissionState.PermanentFailure)
        assertEquals("Quá tải model LLM", (res3.getOrNull() as SubmissionState.PermanentFailure).message)
    }

    @Test
    fun testTimelineEventMappingHandlesNullValueWithAttributeKeyAndAddressTerms() = runBlocking {
        val source = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, "book_1")
        val editionEntity = BookBibleEditionEntity(
            localSourceKey = source.uniqueKey,
            backendBookId = "real_backend_book_id",
            backendEditionId = "backend_edition_123",
            title = "Đấu La Đại Lục 3",
            author = "Đường Gia Tam Thiếu",
            chapterCount = 300
        )
        whenever(mockBookBibleDao.getEditionByLocalSourceKey(source.uniqueKey)).thenReturn(editionEntity)
        whenever(mockBookBibleDao.getTimeline("backend_edition_123", 268, "c1")).thenReturn(null)
        whenever(mockBookBibleDao.getTotalCacheByteSize()).thenReturn(1024L)

        val eventsJson = """
            [
                {
                    "canonical_chapter": 264,
                    "category": "skill",
                    "attribute_key": "Hoàng Kim Long Thể",
                    "operation": "add",
                    "value": null,
                    "evidence": "Vừa nói, Đường Vũ Lân cấp tốc thôi động Hồn Hoàn... Hoàng Kim Long Thể?"
                },
                {
                    "canonical_chapter": 264,
                    "category": "skill",
                    "attribute_key": "Kim Long Trảo",
                    "operation": "add",
                    "value": null,
                    "evidence": "Kim Long Trảo theo đó phơi bày ra."
                },
                {
                    "canonical_chapter": 266,
                    "category": "relationship",
                    "attribute_key": "address_terms",
                    "operation": "add",
                    "value": {
                        "with": "Thẩm Dập",
                        "self": "chúng em/em",
                        "other": "Thẩm lão sư",
                        "context": "học viên với giáo viên hướng dẫn sát hạch"
                    }
                }
            ]
        """.trimIndent()
        val eventDtos = gson.fromJson(eventsJson, Array<CharacterEventDto>::class.java).toList()

        whenever(mockApiService.getCharacterTimeline("backend_edition_123", 268, "c1")).thenReturn(eventDtos)

        val result = repository.getCharacterTimeline(source, "c1", 268)
        assertTrue(result.isSuccess)
        val timeline = result.getOrNull()!!
        assertEquals(3, timeline.events.size)

        // Kiểm tra không bị chữ "null" mà format thành "Lĩnh ngộ / Xuất hiện: Hoàng Kim Long Thể"
        assertEquals("Lĩnh ngộ / Xuất hiện: Hoàng Kim Long Thể", timeline.events[0].displayValue)
        assertEquals("Lĩnh ngộ / Xuất hiện: Kim Long Trảo", timeline.events[1].displayValue)

        // Kiểm tra không bị raw JSON mà format thành "Xưng hô với Thẩm Dập (Tự xưng: chúng em/em • Gọi: Thẩm lão sư)"
        assertEquals("Xưng hô với Thẩm Dập (Tự xưng: chúng em/em • Gọi: Thẩm lão sư)", timeline.events[2].displayValue)
    }
}
