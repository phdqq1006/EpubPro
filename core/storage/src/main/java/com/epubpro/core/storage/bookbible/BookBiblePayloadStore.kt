package com.epubpro.core.storage.bookbible

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình quản lý lưu trữ và xử lý các file payload plain text của chương nguồn trước khi gửi lên backend.
 * Lưu trữ dưới thư mục an toàn `noBackupFilesDir/bookbible_payloads/`.
 */
@Singleton
class BookBiblePayloadStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val payloadDir: File
        get() = File(context.noBackupFilesDir, "bookbible_payloads").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Tính toán mã băm SHA-256 xác định cho chuỗi văn bản chương nguồn.
     *
     * @param content Nội dung văn bản thô.
     * @return Chuỗi hexa 64 ký tự đại diện cho mã SHA-256.
     */
    fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Ghi nội dung văn bản nguồn thành file tạm thời một cách nguyên tử (Atomic write).
     *
     * @param content Nội dung văn bản chương nguồn.
     * @param sourceHash Mã băm SHA-256 của nội dung.
     * @return Đường dẫn tuyệt đối tới file payload vừa được tạo.
     * @throws IllegalArgumentException Nếu dung lượng vượt quá giới hạn 2 MiB / payload.
     * @throws IllegalStateException Nếu tổng dung lượng các payload vượt quá giới hạn mềm 50 MiB.
     */
    fun writePayloadAtomically(content: String, sourceHash: String): String {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size > MAX_PAYLOAD_BYTES) {
            throw IllegalArgumentException("Dung lượng payload (${contentBytes.size} bytes) vượt quá giới hạn cho phép 2 MiB.")
        }

        val currentTotalSize = getTotalPayloadDirSize()
        if (currentTotalSize + contentBytes.size > MAX_GLOBAL_PAYLOAD_DIR_BYTES) {
            throw IllegalStateException("Thư mục payload tạm thời vượt quá giới hạn mềm 50 MiB.")
        }

        val targetFile = File(payloadDir, "$sourceHash.txt")
        if (targetFile.exists()) {
            return targetFile.absolutePath
        }

        val tempFile = File(payloadDir, "${sourceHash}_${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(contentBytes)
                fos.flush()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile.absolutePath
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * Đọc nội dung văn bản từ đường dẫn file payload.
     *
     * @param filePath Đường dẫn file.
     * @return Chuỗi nội dung văn bản hoặc `null` nếu file không tồn tại.
     */
    fun readPayload(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    /**
     * Xóa một file payload cụ thể sau khi đã gửi thành công lên máy chủ backend.
     *
     * @param filePath Đường dẫn file cần xóa.
     */
    fun deletePayload(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        runCatching {
            val file = File(filePath)
            if (file.exists()) file.delete()
        }
    }

    /**
     * Tính tổng dung lượng hiện tại của tất cả các file payload đang lưu trong thư mục.
     */
    fun getTotalPayloadDirSize(): Long {
        return payloadDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    companion object {
        /** Giới hạn dung lượng tối đa cho 1 chương nguồn (2 MiB) */
        const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024L

        /** Giới hạn dung lượng mềm toàn cục của thư mục payload (50 MiB) */
        const val MAX_GLOBAL_PAYLOAD_DIR_BYTES = 50 * 1024 * 1024L
    }
}
