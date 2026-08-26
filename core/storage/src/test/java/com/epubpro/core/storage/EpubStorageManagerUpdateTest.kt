package com.epubpro.core.storage

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EpubStorageManagerUpdateTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Tạo storage manager sử dụng thư mục tạm để kiểm thử thao tác file.
     *
     * @return Storage manager được cấu hình với context kiểm thử.
     */
    private fun createManager(): EpubStorageManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(temporaryFolder.root)
        return EpubStorageManager(context)
    }

    /**
     * Xác nhận bản EPUB mới thay thế bản cũ chỉ sau khi file tạm đã tải xong.
     */
    @Test
    fun promoteOnlineDownloadReplacesExistingFile() {
        val manager = createManager()
        val completed = temporaryFolder.newFile("completed.epub")
        val temporary = temporaryFolder.newFile("completed.epub.part")
        completed.writeText("old")
        temporary.writeText("new")

        val result = manager.promoteOnlineDownload(
            OnlineDownloadFiles(temporary = temporary, completed = completed)
        )

        assertEquals("new", result.readText())
        assertFalse(temporary.exists())
        assertFalse(File(completed.parent, "completed.epub.previous").exists())
    }

    /**
     * Xác nhận thao tác bắt đầu cập nhật không xóa bản EPUB hiện đang có.
     */
    @Test
    fun clearOnlineDownloadTemporaryKeepsCompletedFile() {
        val manager = createManager()
        val files = manager.getOnlineDownloadFiles("novel-1")
        files.completed.writeText("old")
        files.temporary.writeText("partial")

        manager.clearOnlineDownloadTemporary("novel-1")

        assertTrue(files.completed.isFile)
        assertFalse(files.temporary.exists())
    }
}
