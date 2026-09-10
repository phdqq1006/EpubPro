package com.epubpro.app.intent

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Kiểm thử tính năng thẩm định và bóc tách thông tin file sách từ Intent bên ngoài của [ExternalBookImportHandler].
 */
class ExternalBookImportHandlerTest {

    private val contentResolver = mock(ContentResolver::class.java)

    /**
     * Kiểm tra Intent ACTION_VIEW với Content URI và MIME type EPUB chuẩn được chấp thuận.
     */
    @Test
    fun parseReturnsRequestWhenActionViewWithContentUriAndEpubMime() {
        val intent = mock(Intent::class.java)
        val uri = mock(Uri::class.java)
        val cursor = mock(Cursor::class.java)

        `when`(intent.action).thenReturn(Intent.ACTION_VIEW)
        `when`(intent.data).thenReturn(uri)
        `when`(intent.type).thenReturn("application/epub+zip")
        `when`(uri.scheme).thenReturn("content")
        `when`(uri.lastPathSegment).thenReturn("12345")

        `when`(contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null))
            .thenReturn(cursor)
        `when`(cursor.moveToFirst()).thenReturn(true)
        `when`(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
        `when`(cursor.getString(0)).thenReturn("dac_nhan_tam.epub")

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNotNull(result)
        assertEquals(uri, result?.uri)
        assertEquals("dac_nhan_tam.epub", result?.displayName)
    }

    /**
     * Kiểm tra Intent ACTION_VIEW với File URI có phần mở rộng .epub được chấp thuận.
     */
    @Test
    fun parseReturnsRequestWhenActionViewWithFileUri() {
        val intent = mock(Intent::class.java)
        val uri = mock(Uri::class.java)

        `when`(intent.action).thenReturn(Intent.ACTION_VIEW)
        `when`(intent.data).thenReturn(uri)
        `when`(intent.type).thenReturn(null)
        `when`(uri.scheme).thenReturn("file")
        `when`(uri.lastPathSegment).thenReturn("book_title.epub")

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNotNull(result)
        assertEquals(uri, result?.uri)
        assertEquals("book_title.epub", result?.displayName)
    }

    /**
     * Kiểm tra Intent ACTION_SEND mang extra stream URI với file EPUB được chấp thuận.
     */
    @Test
    fun parseReturnsRequestWhenActionSendWithStreamUri() {
        val intent = mock(Intent::class.java)
        val uri = mock(Uri::class.java)

        `when`(intent.action).thenReturn(Intent.ACTION_SEND)
        @Suppress("DEPRECATION")
        `when`(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).thenReturn(uri)
        `when`(intent.type).thenReturn("application/epub+zip")
        `when`(uri.scheme).thenReturn("content")
        `when`(uri.lastPathSegment).thenReturn("shared_novel.epub")

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNotNull(result)
        assertEquals(uri, result?.uri)
        assertEquals("shared_novel.epub", result?.displayName)
    }

    /**
     * Kiểm tra từ chối các Intent chứa scheme không được phép như http:// hoặc https://.
     */
    @Test
    fun parseReturnsNullWhenSchemeIsHttpOrUnsupported() {
        val intent = mock(Intent::class.java)
        val uri = mock(Uri::class.java)

        `when`(intent.action).thenReturn(Intent.ACTION_VIEW)
        `when`(intent.data).thenReturn(uri)
        `when`(uri.scheme).thenReturn("https")
        `when`(uri.lastPathSegment).thenReturn("book.epub")

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNull(result)
    }

    /**
     * Kiểm tra từ chối Intent không phải hành vi mở hoặc chia sẻ sách như ACTION_MAIN.
     */
    @Test
    fun parseReturnsNullWhenActionIsMainOrUnrelated() {
        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(Intent.ACTION_MAIN)

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNull(result)
    }

    /**
     * Kiểm tra từ chối file có định dạng không được hỗ trợ như PDF hoặc APK.
     */
    @Test
    fun parseReturnsNullWhenExtensionAndMimeAreNotEbook() {
        val intent = mock(Intent::class.java)
        val uri = mock(Uri::class.java)

        `when`(intent.action).thenReturn(Intent.ACTION_VIEW)
        `when`(intent.data).thenReturn(uri)
        `when`(intent.type).thenReturn("application/pdf")
        `when`(uri.scheme).thenReturn("content")
        `when`(uri.lastPathSegment).thenReturn("document.pdf")

        val result = ExternalBookImportHandler.parse(intent, contentResolver)

        assertNull(result)
    }
}
