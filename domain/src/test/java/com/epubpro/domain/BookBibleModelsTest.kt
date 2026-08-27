package com.epubpro.domain

import com.epubpro.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BookBibleModelsTest {

    @Test
    fun testBookBibleSourceUniqueKey() {
        val localSource = BookBibleSource(BookBibleSourceType.LOCAL_EPUB, "book_123")
        assertEquals("LOCAL_EPUB:book_123", localSource.uniqueKey)

        val onlineSource = BookBibleSource(BookBibleSourceType.ONLINE_NOVEL, "novel_456")
        assertEquals("ONLINE_NOVEL:novel_456", onlineSource.uniqueKey)
    }

    @Test
    fun testSnapshotCanonicalChapterDoesNotExceedRequested() {
        val snapshot = BookBibleSnapshot(
            bookId = "book_1",
            editionId = "edition_1",
            requestedChapter = 5,
            canonicalChapter = 5,
            status = SnapshotStatus.COMPLETE,
            coverage = SnapshotCoverage(
                processedRanges = listOf(ChapterRange(1, 5)),
                missingRanges = emptyList()
            ),
            bookRevision = 2,
            projectionRevision = 3,
            projectionStatus = "ready",
            completeThroughChapter = 5,
            pendingChapters = emptyList()
        )
        assertTrue(snapshot.canonicalChapter <= snapshot.requestedChapter)
        assertEquals(SnapshotStatus.COMPLETE, snapshot.status)
        assertEquals(1, snapshot.coverage.processedRanges.size)
        assertTrue(snapshot.coverage.missingRanges.isEmpty())
        assertEquals(2, snapshot.bookRevision)
        assertEquals(3, snapshot.projectionRevision)
        assertEquals("ready", snapshot.projectionStatus)
        assertEquals(5, snapshot.completeThroughChapter)
    }

    @Test
    fun testCharacterProfileWithNewFields() {
        val character = CharacterProfile(
            id = "char_1",
            name = "Bạch Ngưng Băng",
            originalName = "Bai Ning Bing",
            role = "protagonist",
            voiceNotes = "Lạnh lùng, điềm tĩnh, sát phạt quyết đoán",
            aliases = listOf("Bạch Ma Đầu", "Bắc Minh Băng Phách Thể"),
            cultivationRealm = "Tam chuyển sơ kỳ",
            titles = listOf("Bạch Ma Đầu"),
            pets = listOf(
                CharacterPet(
                    name = "Băng Nhãn Lang",
                    species = "Lang Thú",
                    realm = "Vạn Thú Vương",
                    status = "Khế ước linh hồn"
                )
            )
        )
        assertEquals("char_1", character.id)
        assertEquals("Bạch Ngưng Băng", character.name)
        assertEquals("protagonist", character.role)
        assertEquals("Lạnh lùng, điềm tĩnh, sát phạt quyết đoán", character.voiceNotes)
        assertEquals(2, character.aliases.size)
        assertTrue(character.aliases.contains("Bắc Minh Băng Phách Thể"))
        assertEquals(1, character.pets.size)
        assertEquals("Băng Nhãn Lang", character.pets[0].name)
        assertEquals("Vạn Thú Vương", character.pets[0].realm)
    }

    @Test
    fun testCharacterTimelineEventWithCertainty() {
        val event = CharacterTimelineEvent(
            chapter = 12,
            category = "realm",
            operation = "advance",
            displayValue = "Đột phá Tam chuyển",
            certainty = "observed",
            evidence = "Bạch Ngưng Băng thành công phá tan bích chướng"
        )
        assertEquals(12, event.chapter)
        assertEquals("observed", event.certainty)
        assertEquals("advance", event.operation)
    }

    @Test
    fun testBookFingerprintsModel() {
        val fingerprints = BookFingerprints(
            file = "abc123sha",
            edition = "epub_v1_xyz",
            structure = "struct123sha",
            sampledChapters = listOf("hash1", "hash2")
        )
        assertEquals("abc123sha", fingerprints.file)
        assertEquals("epub_v1_xyz", fingerprints.edition)
        assertEquals(2, fingerprints.sampledChapters.size)
    }

    @Test
    fun testPartialSnapshotCoverage() {
        val coverage = SnapshotCoverage(
            processedRanges = listOf(ChapterRange(1, 2), ChapterRange(5, 5)),
            missingRanges = listOf(ChapterRange(3, 4))
        )
        assertEquals(2, coverage.processedRanges.size)
        assertEquals(1, coverage.missingRanges.size)
        assertEquals(3, coverage.missingRanges[0].start)
        assertEquals(4, coverage.missingRanges[0].end)
    }

    @Test
    fun testIsProtagonistAndIsAntagonistCaseInsensitive() {
        val main1 = CharacterProfile(id = "1", name = "Tiêu Viêm", isMain = true)
        assertTrue(main1.isProtagonist)

        val main2 = CharacterProfile(id = "2", name = "Đường Tam", role = "NHÂN VẬT CHÍNH")
        assertTrue(main2.isProtagonist)

        val main3 = CharacterProfile(id = "3", name = "Hàn Lập", role = "Nam chính")
        assertTrue(main3.isProtagonist)

        val main4 = CharacterProfile(id = "4", name = "Vân Hi", role = "Nữ chủ")
        assertTrue(main4.isProtagonist)

        val main5 = CharacterProfile(id = "5", name = "Đỗ Phong", role = "Main character")
        assertTrue(main5.isProtagonist)

        val main6 = CharacterProfile(id = "6", name = "Trần Dạ", role = "主角 (Nam chính của truyện)")
        assertTrue(main6.isProtagonist)

        val antagonist1 = CharacterProfile(id = "7", name = "Hồn Diệt Sinh", role = "PHẢN DIỆN")
        assertTrue(antagonist1.isAntagonist)
        assertFalse(antagonist1.isProtagonist)

        val antagonist2 = CharacterProfile(id = "8", name = "Bàng Chấn", role = "Phản phái / Kẻ thù")
        assertTrue(antagonist2.isAntagonist)

        val sideChar = CharacterProfile(id = "9", name = "Hải Ba Đông", role = "Bạn hữu")
        assertFalse(sideChar.isProtagonist)
        assertFalse(sideChar.isAntagonist)
    }

    @Test
    fun testCharacterAddressTermsModel() {
        val term = CharacterAddressTerm(
            targetName = "Thẩm Dập",
            selfTerm = "em / chúng em",
            otherTerm = "Thẩm lão sư",
            context = "Học viên với giáo viên",
            contexts = listOf("Học viên với giáo viên", "Khi ở học viện")
        )
        assertEquals("Thẩm Dập", term.targetName)
        assertEquals("em / chúng em", term.selfTerm)
        assertEquals("Thẩm lão sư", term.otherTerm)
        assertEquals(2, term.contexts.size)
    }
}
