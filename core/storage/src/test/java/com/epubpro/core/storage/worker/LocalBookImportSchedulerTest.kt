package com.epubpro.core.storage.worker

import android.content.Context
import android.net.Uri
import com.epubpro.core.bookconverter.BookFormatSniffer
import com.epubpro.core.storage.EpubStorageManager
import com.epubpro.domain.model.Book
import com.epubpro.domain.model.BookSourceFormat
import com.epubpro.domain.repository.BookRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Kiểm tra import trùng ID trên file EPUB thực trước khi gọi WorkManager. */
class LocalBookImportSchedulerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** Cùng ID dù tên truyện khác vẫn chờ lựa chọn; hủy không làm mất bản cũ. */
    @Test fun sameIdentifierWaitsForChoiceAndDismissOnlyDeletesSource() = runTest {
        val source = epub("incoming.epub", "story-42")
        val old = epub("old.epub", "story-42")
        val unrelated = epub("same-title.epub", "story-99")
        val storage = mock<EpubStorageManager>()
        val sniffer = mock<BookFormatSniffer>()
        val repository = mock<BookRepository>()
        val uri = mock<Uri>()
        whenever(storage.importLocalBookSource(uri, "new-title.epub")).thenReturn(source)
        whenever(sniffer.sniff(source)).thenReturn(BookSourceFormat.EPUB)
        whenever(repository.getAllBooks()).thenReturn(flowOf(listOf(book(old), book(unrelated))))
        val scheduler = LocalBookImportScheduler(mock<Context>(), storage, sniffer, repository)

        assertNull(scheduler.enqueue(uri, "new-title.epub"))
        assertEquals(listOf(old.name), scheduler.pendingImports.value.single().matches.map { it.id })
        assertTrue(source.exists())
        assertTrue(old.exists())
        scheduler.dismiss(source.absolutePath)
        assertTrue(scheduler.pendingImports.value.isEmpty())
        assertFalse(source.exists())
        assertTrue(old.exists())
        assertTrue(unrelated.exists())
        assertNull(scheduler.resolve(source.absolutePath, old.name))
    }

    /**
     * Tạo bản ghi thư viện với tiêu đề giống nhau để tránh test vô tình dựa vào tên.
     *
     * @param file EPUB của bản ghi.
     * @return Truyện có ID nội bộ độc lập với ID xuất bản.
     */
    private fun book(file: File) = Book(file.name, "Same title", "Author", null, file.absolutePath, 1, 1)

    /**
     * Tạo EPUB tối thiểu chứa ID xuất bản để kiểm tra bước phát hiện trùng.
     *
     * @param name Tên file riêng biệt.
     * @param identifier ID xuất bản.
     * @return File EPUB đã ghi.
     */
    private fun epub(name: String, identifier: String): File {
        val file = temporaryFolder.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("content.opf"))
            output.write("""<package unique-identifier="id" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:identifier id="id">$identifier</dc:identifier></metadata></package>""".toByteArray())
            output.closeEntry()
        }
        return file
    }
}
