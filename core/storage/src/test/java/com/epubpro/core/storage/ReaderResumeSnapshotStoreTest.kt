package com.epubpro.core.storage

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class ReaderResumeSnapshotStoreTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var store: ReaderResumeSnapshotStore

    @Before
    fun setup() {
        tempDir = File.createTempFile("snapshot_test", "").apply {
            delete()
            mkdirs()
        }
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempDir)
        store = ReaderResumeSnapshotStore(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `loadSnapshot returns null when no snapshot exists`() {
        val result = store.loadSnapshot("book_123", 0, "c1.xhtml", "/path/to/test.epub", 100L, 1000L)
        assertNull(result)
    }

    @Test
    fun `saveSnapshot and loadSnapshot roundtrip success`() {
        val normalized = "<p>Normalized Chapter 1</p>"
        val sanitized = "<p>Normalized Chapter 1</p>"

        val snapshot = ReaderResumeSnapshot(
            bookId = "book_123",
            chapterIndex = 0,
            entryName = "c1.xhtml",
            canonicalPath = "/path/to/test.epub",
            fileLength = 500L,
            lastModified = 2000L,
            sourceHash = ReaderResumeSnapshot.computeContentHash(normalized),
            normalizedHtml = normalized,
            sanitizedHtml = sanitized
        )

        store.saveSnapshot(snapshot)

        val loaded = store.loadSnapshot("book_123", 0, "c1.xhtml", "/path/to/test.epub", 500L, 2000L)
        assertNotNull(loaded)
        assertEquals("book_123", loaded!!.bookId)
        assertEquals(0, loaded.chapterIndex)
        assertEquals("c1.xhtml", loaded.entryName)
        assertEquals(normalized, loaded.normalizedHtml)
        assertEquals(sanitized, loaded.sanitizedHtml)
    }

    @Test
    fun `loadSnapshot returns null if chapterIndex or entryName does not match`() {
        val normalized = "<p>Chap 1</p>"

        val snapshot = ReaderResumeSnapshot(
            bookId = "book_123",
            chapterIndex = 0,
            entryName = "c1.xhtml",
            canonicalPath = "/path/to/test.epub",
            fileLength = 500L,
            lastModified = 2000L,
            sourceHash = ReaderResumeSnapshot.computeContentHash(normalized),
            normalizedHtml = normalized,
            sanitizedHtml = normalized
        )
        store.saveSnapshot(snapshot)

        // Query different chapter index
        val loadedDiffIndex = store.loadSnapshot("book_123", 1, "c1.xhtml", "/path/to/test.epub", 500L, 2000L)
        assertNull(loadedDiffIndex)

        // Query different entryName
        val loadedDiffEntry = store.loadSnapshot("book_123", 0, "c2.xhtml", "/path/to/test.epub", 500L, 2000L)
        assertNull(loadedDiffEntry)
    }

    @Test
    fun `loadSnapshot returns null if file length changed`() {
        val normalized = "<p>Chap 1</p>"

        val snapshot = ReaderResumeSnapshot(
            bookId = "book_123",
            chapterIndex = 0,
            entryName = "c1.xhtml",
            canonicalPath = "/path/to/test.epub",
            fileLength = 500L,
            lastModified = 2000L,
            sourceHash = ReaderResumeSnapshot.computeContentHash(normalized),
            normalizedHtml = normalized,
            sanitizedHtml = normalized
        )
        store.saveSnapshot(snapshot)

        val loaded = store.loadSnapshot("book_123", 0, "c1.xhtml", "/path/to/test.epub", 999L, 2000L)
        assertNull(loaded)
    }

    /**
     * Kiểm tra snapshot bị từ chối khi nội dung sanitized không khớp hash metadata.
     */
    @Test
    fun `loadSnapshot returns null if sanitized content hash mismatches`() {
        val normalized = "<p>Chap 1</p>"
        val sanitized = "<p>Safe Chap 1</p>"
        val snapshot = ReaderResumeSnapshot(
            bookId = "book_123",
            chapterIndex = 0,
            entryName = "c1.xhtml",
            canonicalPath = "/path/to/test.epub",
            fileLength = 500L,
            lastModified = 2000L,
            sourceHash = ReaderResumeSnapshot.computeContentHash(normalized),
            normalizedHtml = normalized,
            sanitizedHtml = sanitized
        )
        store.saveSnapshot(snapshot)

        val bookDir = File(
            tempDir,
            "reader_snapshots/" + ReaderResumeSnapshot.computeContentHash(snapshot.bookId)
        )
        val sanitizedFile = File(
            bookDir,
            "sanitized_" + ReaderResumeSnapshot.computeContentHash(sanitized) + ".html"
        )
        sanitizedFile.writeText("<p>Tampered</p>", Charsets.UTF_8)

        assertNull(
            store.loadSnapshot(
                "book_123",
                0,
                "c1.xhtml",
                "/path/to/test.epub",
                500L,
                2000L
            )
        )
    }

    @Test
    fun `deleteSnapshot cleans up book snapshot directory`() {
        val snapshot = ReaderResumeSnapshot(
            bookId = "book_123",
            chapterIndex = 0,
            entryName = "c1.xhtml",
            canonicalPath = "/path/to/test.epub",
            fileLength = 500L,
            lastModified = 2000L,
            sourceHash = ReaderResumeSnapshot.computeContentHash("<p>Content</p>"),
            normalizedHtml = "<p>Content</p>",
            sanitizedHtml = "<p>Content</p>"
        )
        store.saveSnapshot(snapshot)
        assertNotNull(store.loadSnapshot("book_123", 0, "c1.xhtml", "/path/to/test.epub", 500L, 2000L))

        store.deleteSnapshot("book_123")
        assertNull(store.loadSnapshot("book_123", 0, "c1.xhtml", "/path/to/test.epub", 500L, 2000L))
    }
}
