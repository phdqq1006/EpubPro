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
            )
        )
        assertTrue(snapshot.canonicalChapter <= snapshot.requestedChapter)
        assertEquals(SnapshotStatus.COMPLETE, snapshot.status)
        assertEquals(1, snapshot.coverage.processedRanges.size)
        assertTrue(snapshot.coverage.missingRanges.isEmpty())
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
}
