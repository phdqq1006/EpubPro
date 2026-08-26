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

    /**
     * Trả về cặp file tạm và file hoàn tất dành riêng cho một truyện online.
     *
     * @param novelId Định danh ổn định của truyện online.
     * @return Cặp đường dẫn deterministic để WorkManager có thể tiếp tục sau retry hoặc process death.
     */
    fun getOnlineDownloadFiles(novelId: String): OnlineDownloadFiles {
        val key = stableFileKey(novelId)
        return OnlineDownloadFiles(
            temporary = File(booksDir, "online_$key.epub.part"),
            completed = File(booksDir, "online_$key.epub")
        )
    }

    /**
     * Ghi nối tiếp dữ liệu EPUB vào file tạm với giới hạn dung lượng và callback tiến độ.
     *
     * @param inputStream Luồng dữ liệu HTTP cần ghi.
     * @param targetFile File tạm đích.
     * @param append True nếu tiếp tục ghi sau số byte đã có.
     * @param initialBytes Số byte đã có trước khi ghi.
     * @param totalBytes Tổng số byte dự kiến, có thể null nếu server không cung cấp.
     * @param onProgress Callback nhận số byte đã ghi và tổng byte dự kiến.
     * @return Tổng số byte hiện có trong file sau khi ghi.
     * @throws IllegalStateException Nếu file vượt quá giới hạn tải tối đa.
     */
    suspend fun appendOnlineDownload(
        inputStream: InputStream,
        targetFile: File,
        append: Boolean,
        initialBytes: Long,
        totalBytes: Long?,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Long {
        targetFile.parentFile?.mkdirs()
        var downloadedBytes = if (append) initialBytes else 0L
        inputStream.use { input ->
            FileOutputStream(targetFile, append).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    downloadedBytes += read
                    check(downloadedBytes <= MAX_ONLINE_DOWNLOAD_BYTES) {
                        "File EPUB tải xuống vượt quá kích thước tối đa cho phép"
                    }
                    output.write(buffer, 0, read)
                    onProgress(downloadedBytes, totalBytes)
                }
                output.fd.sync()
            }
        }
        return downloadedBytes
    }

    /**
     * Đổi file tạm thành file EPUB hoàn tất bằng rename cùng thư mục đích.
     *
     * @param files Cặp file tạm và file hoàn tất của truyện.
     * @return File EPUB hoàn tất.
     * @throws IllegalStateException Nếu không thể đổi tên file atomically.
     */
    fun promoteOnlineDownload(files: OnlineDownloadFiles): File {
        check(files.temporary.isFile) { "Không tìm thấy file tải tạm" }

        if (files.completed.isFile) {
            val previousFile = File(files.completed.parentFile, files.completed.name + ".previous")
            previousFile.delete()
            check(files.completed.renameTo(previousFile)) {
                "Không thể chuẩn bị file EPUB cũ để cập nhật"
            }
            if (!files.temporary.renameTo(files.completed)) {
                previousFile.renameTo(files.completed)
                error("Không thể hoàn tất file EPUB tải xuống")
            }
            previousFile.delete()
        } else {
            check(files.temporary.renameTo(files.completed)) {
                "Không thể hoàn tất file EPUB tải xuống"
            }
        }

        check(files.completed.isFile) { "Không tìm thấy file EPUB đã hoàn tất" }
        return files.completed
    }

    /**
     * Xóa riêng file tạm trước khi bắt đầu một lần cập nhật EPUB mới.
     *
     * File EPUB đang có trong thư viện được giữ nguyên cho tới khi file mới tải xong.
     *
     * @param novelId Định danh ổn định của truyện online.
     */
    fun clearOnlineDownloadTemporary(novelId: String) {
        getOnlineDownloadFiles(novelId).temporary.delete()
    }

    /**
     * Xóa dữ liệu tải tạm và file hoàn tất của một truyện online.
     *
     * @param novelId Định danh ổn định của truyện online.
     */
    fun deleteOnlineDownloadFiles(novelId: String) {
        val files = getOnlineDownloadFiles(novelId)
        files.temporary.delete()
        files.completed.delete()
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

    private fun stableFileKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(32)

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

    companion object {
        /** Giới hạn kích thước một file EPUB tải online để tránh làm đầy internal storage. */
        const val MAX_ONLINE_DOWNLOAD_BYTES: Long = 500L * 1024 * 1024
    }
}

/**
 * Các đường dẫn bền vững cho một tác vụ tải EPUB online.
 *
 * @property temporary File đang được ghi nối tiếp.
 * @property completed File đã tải xong và có thể đưa vào thư viện.
 */
data class OnlineDownloadFiles(
    val temporary: File,
    val completed: File
)
