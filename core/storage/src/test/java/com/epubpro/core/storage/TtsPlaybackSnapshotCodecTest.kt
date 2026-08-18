package com.epubpro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsPlaybackSnapshotCodecTest {
    @Test
    fun roundTripPreservesSnapshotIncludingUnicodeBookId() {
        val snapshot = TtsPlaybackSnapshot(
            bookId = "Ä‘áº¥u-la-Ä‘áº¡i-lá»¥c/3",
            chapterIndex = 12,
            paragraphIndex = 34,
            sentenceIndex = 2,
            preferAiContent = true,
            timelinePositionMs = 98_765L
        )

        assertEquals(snapshot, TtsPlaybackSnapshotCodec.decode(TtsPlaybackSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun negativeCursorValuesAreClampedBeforePersistence() {
        val snapshot = TtsPlaybackSnapshot(
            bookId = "book-id",
            chapterIndex = -1,
            paragraphIndex = -2,
            sentenceIndex = -3,
            preferAiContent = false,
            timelinePositionMs = -4L
        )

        assertEquals(
            snapshot.copy(
                chapterIndex = 0,
                paragraphIndex = 0,
                sentenceIndex = 0,
                timelinePositionMs = 0L
            ),
            TtsPlaybackSnapshotCodec.decode(TtsPlaybackSnapshotCodec.encode(snapshot))
        )
    }

    @Test
    fun invalidRecordsAreRejected() {
        assertNull(
            TtsPlaybackSnapshot(
                bookId = "   ",
                chapterIndex = 0,
                paragraphIndex = 0,
                sentenceIndex = 0,
                preferAiContent = false,
                timelinePositionMs = 0L
            ).normalizedOrNull()
        )
        assertNull(TtsPlaybackSnapshotCodec.decode("broken"))
        assertNull(TtsPlaybackSnapshotCodec.decode("2\nYm9vaw\n0\n0\n0\n0\n0"))
        assertNull(TtsPlaybackSnapshotCodec.decode("1\nnot*base64\n0\n0\n0\n0\n0"))
    }
}
