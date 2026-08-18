package com.epubpro.core.reader.engine

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

class EpubStructureCacheTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var cache: EpubStructureCache

    @Before
    fun setup() {
        tempDir = File.createTempFile("epub_cache_test", "").apply {
            delete()
            mkdirs()
        }
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempDir)
        cache = EpubStructureCache(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `readHeaders returns null on cache miss`() {
        val dummyFile = File(tempDir, "test.epub").apply { writeText("dummy") }
        val fingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        val result = cache.readHeaders(fingerprint)
        assertNull(result)
    }

    @Test
    fun `saveHeaders and readHeaders roundtrip success`() {
        val dummyFile = File(tempDir, "test.epub").apply { writeText("dummy content") }
        val fingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        val headers = listOf(
            EpubChapterHeader(0, "Chương 1: Mở đầu", "OEBPS/c1.xhtml"),
            EpubChapterHeader(1, "Chương 2: Diễn biến", "OEBPS/c2.xhtml")
        )

        cache.saveHeaders(fingerprint, headers)

        val read = cache.readHeaders(fingerprint)
        assertNotNull(read)
        assertEquals(2, read!!.size)
        assertEquals("Chương 1: Mở đầu", read[0].title)
        assertEquals("OEBPS/c1.xhtml", read[0].entryName)
        assertEquals(0, read[0].index)
        assertEquals("Chương 2: Diễn biến", read[1].title)
    }

    @Test
    fun `readHeaders returns null when fingerprint length changes`() {
        val dummyFile = File(tempDir, "test.epub").apply { writeText("dummy") }
        val initialFingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        val headers = listOf(
            EpubChapterHeader(0, "Chương 1", "c1.xhtml")
        )
        cache.saveHeaders(initialFingerprint, headers)

        // Modify file size
        dummyFile.writeText("longer dummy content")
        val modifiedFingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        val read = cache.readHeaders(modifiedFingerprint)
        assertNull(read)
    }

    @Test
    fun `deleteCache removes cached headers file`() {
        val dummyFile = File(tempDir, "test.epub").apply { writeText("dummy") }
        val fingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        cache.saveHeaders(fingerprint, listOf(EpubChapterHeader(0, "Chương 1", "c1.xhtml")))
        assertNotNull(cache.readHeaders(fingerprint))

        cache.deleteCache(dummyFile.canonicalPath)
        assertNull(cache.readHeaders(fingerprint))
    }

    @Test
    fun `corrupted JSON cache file falls back safely to null without crashing`() {
        val dummyFile = File(tempDir, "test.epub").apply { writeText("dummy") }
        val fingerprint = EpubCacheFingerprint.fromFile(dummyFile)

        val cacheFile = File(File(tempDir, "epub_structure_cache"), "${fingerprint.cacheKey}.json")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("{ invalid json ...")

        val result = cache.readHeaders(fingerprint)
        assertNull(result)
    }
}
