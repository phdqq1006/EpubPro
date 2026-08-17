package com.epubpro.core.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
    private val coversDir = File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
    private val aiCacheDir = File(context.filesDir, "ai_chapters").apply { if (!exists()) mkdirs() }

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

    /**
     * Save downloaded EPUB stream directly into internal app storage
     */
    fun importDownloadedEpub(inputStream: InputStream, originalFileName: String?): File {
        val fileId = UUID.randomUUID().toString()
        val extension = originalFileName?.substringAfterLast('.', "epub") ?: "epub"
        val targetFile = File(booksDir, "$fileId.$extension")

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
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

    fun saveAiChapter(bookId: String, chapterIndex: Int, html: String): String {
        val file = aiChapterFile(bookId, chapterIndex)
        writeAtomically(file, html)
        return file.absolutePath
    }

    fun readAiChapter(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        return runCatching {
            val file = File(filePath).canonicalFile
            val root = aiCacheDir.canonicalFile
            require(file.path.startsWith(root.path + File.separator))
            file.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }.getOrNull()
    }

    fun saveAiProgress(bookId: String, chapterIndex: Int, json: String) {
        writeAtomically(aiProgressFile(bookId, chapterIndex), json)
    }

    fun readAiProgress(bookId: String, chapterIndex: Int): String? =
        aiProgressFile(bookId, chapterIndex)
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)

    fun deleteAiProgress(bookId: String, chapterIndex: Int) {
        aiProgressFile(bookId, chapterIndex).delete()
    }

    fun deleteAiChapterCache(bookId: String, chapterIndex: Int) {
        aiChapterFile(bookId, chapterIndex).delete()
        aiProgressFile(bookId, chapterIndex).delete()
    }

    fun deleteAiBookCache(bookId: String) {
        aiBookDirectory(bookId).deleteRecursively()
    }

    private fun aiChapterFile(bookId: String, chapterIndex: Int): File =
        File(aiBookDirectory(bookId), "chapter_$chapterIndex.html")

    private fun aiProgressFile(bookId: String, chapterIndex: Int): File =
        File(aiBookDirectory(bookId), "chapter_$chapterIndex.progress.json")

    private fun aiBookDirectory(bookId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bookId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return File(aiCacheDir, digest).apply { if (!exists()) mkdirs() }
    }

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            error("Không thể cập nhật cache AI")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("Không thể lưu cache AI")
        }
    }
}
