package com.epubpro.core.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }

    /**
     * Copy EPUB from Uri (SAF) into local internal app storage for secure offline access
     */
    fun importEpubFromUri(uri: Uri, originalFileName: String?): File {
        val fileId = UUID.randomUUID().toString()
        val extension = originalFileName?.substringAfterLast('.', "epub") ?: "epub"
        val targetFile = File(booksDir, "$fileId.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open InputStream for URI: $uri")

        return targetFile
    }

    fun saveCoverImage(bookId: String, inputStream: InputStream): String {
        val coverFile = File(coversDir, "$bookId.jpg")
        FileOutputStream(coverFile).use { output ->
            inputStream.copyTo(output)
        }
        return coverFile.absolutePath
    }

    fun getBookFile(filePath: String): File {
        return File(filePath)
    }

    fun deleteBookFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }
}
