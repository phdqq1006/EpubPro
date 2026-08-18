package com.epubpro.core.storage.bookbible

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class BookBiblePayloadStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var payloadStore: BookBiblePayloadStore
    private lateinit var noBackupDir: File

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        noBackupDir = tempFolder.newFolder("no_backup")
        `when`(context.noBackupFilesDir).thenReturn(noBackupDir)
        payloadStore = BookBiblePayloadStore(context)
    }

    @Test
    fun testComputeSha256IsDeterministic() {
        val content = "Chương 1: Khởi đầu tu tiên"
        val hash1 = payloadStore.computeSha256(content)
        val hash2 = payloadStore.computeSha256(content)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }

    @Test
    fun testWriteAndReadPayloadAtomically() {
        val content = "Nội dung chương 1 ngắn gọn."
        val hash = payloadStore.computeSha256(content)

        val path = payloadStore.writePayloadAtomically(content, hash)
        assertTrue(File(path).exists())

        val readBack = payloadStore.readPayload(path)
        assertEquals(content, readBack)

        payloadStore.deletePayload(path)
        assertFalse(File(path).exists())
    }
}
