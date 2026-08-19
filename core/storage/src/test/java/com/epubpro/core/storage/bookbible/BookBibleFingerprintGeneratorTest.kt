package com.epubpro.core.storage.bookbible

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookBibleFingerprintGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var generator: BookBibleFingerprintGenerator

    @Before
    fun setUp() {
        generator = BookBibleFingerprintGenerator()
    }

    @Test
    fun testGenerateForOnlineNovel() {
        val novelId = "novel_12345"
        val sampleTexts = listOf("Chương 1 nội dung khởi đầu", "Chương 2 nội dung tiếp theo")

        val fingerprints = generator.generateForOnlineNovel(novelId, sampleTexts)

        assertNotNull(fingerprints.file)
        assertTrue(fingerprints.file.isNotBlank())
        assertNotNull(fingerprints.edition)
        assertTrue(fingerprints.edition.startsWith("online_v1_"))
        assertNotNull(fingerprints.structure)
        assertEquals(2, fingerprints.sampledChapters.size)
    }

    @Test
    fun testGenerateFromEpubFileStreamsSha256() = runBlocking {
        val testFile = tempFolder.newFile("sample.epub")
        ZipOutputStream(FileOutputStream(testFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write("<html><body>Chương 1 nội dung</body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/ch2.xhtml"))
            zos.write("<html><body>Chương 2 nội dung</body></html>".toByteArray())
            zos.closeEntry()
        }

        val fingerprints = generator.generateFromEpubFile(testFile)

        assertNotNull(fingerprints.file)
        assertEquals(64, fingerprints.file.length) // SHA-256 hex is 64 characters
        assertNotNull(fingerprints.structure)
        assertEquals(64, fingerprints.structure.length)
        assertTrue(fingerprints.edition.startsWith("epub_v1_"))
        assertEquals(2, fingerprints.sampledChapters.size)
    }
}
