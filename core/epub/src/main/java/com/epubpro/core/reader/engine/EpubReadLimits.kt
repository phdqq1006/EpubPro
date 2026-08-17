package com.epubpro.core.reader.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry

/**
 * Ngưỡng giới hạn tài nguyên và các tiện ích đọc dữ liệu có giới hạn nhằm bảo vệ ứng dụng
 * khỏi lỗi tràn bộ nhớ Heap (OutOfMemoryError) và các cuộc tấn công nén dữ liệu độc hại (Zip Bomb).
 */
object EpubReadLimits {

    /** Dung lượng tệp EPUB tối đa cho phép import (500 MB). */
    const val MAX_EPUB_FILE_SIZE: Long = 500L * 1024 * 1024

    /** Dung lượng giải nén tối đa cho mỗi entry tệp XHTML/OPF (10 MB). */
    const val MAX_UNCOMPRESSED_ENTRY_SIZE: Long = 10L * 1024 * 1024

    /** Ngân sách tổng số bytes giải nén tối đa cho các tác vụ xử lý hàng loạt (50 MB). */
    const val MAX_TOTAL_EXTRACTED_BYTES: Long = 50L * 1024 * 1024

    /** Tỷ lệ nén tối đa an toàn (100:1). Tỷ lệ lớn hơn bị nghi ngờ là tệp Zip Bomb. */
    const val MAX_COMPRESSION_RATIO: Long = 100L

    /**
     * Kiểm tra tính hợp lệ và an toàn của tệp [entry] trong tệp nén ZIP trước khi tiến hành đọc.
     *
     * @param entry Đối tượng [ZipEntry] cần kiểm tra metadata.
     * @throws IllegalStateException Ném ra exception nếu kích thước tệp vượt quá [MAX_UNCOMPRESSED_ENTRY_SIZE]
     * hoặc tỷ lệ nén vượt quá [MAX_COMPRESSION_RATIO].
     */
    fun validateZipEntry(entry: ZipEntry) {
        if (entry.size > MAX_UNCOMPRESSED_ENTRY_SIZE) {
            throw IllegalStateException("EPUB entry '${entry.name}' uncompressed size (${entry.size} bytes) exceeds limit ($MAX_UNCOMPRESSED_ENTRY_SIZE bytes)")
        }
        if (entry.compressedSize > 0 && entry.size > 0) {
            val ratio = entry.size / entry.compressedSize
            if (ratio > MAX_COMPRESSION_RATIO) {
                throw IllegalStateException("EPUB entry '${entry.name}' compression ratio ($ratio:1) exceeds safety threshold ($MAX_COMPRESSION_RATIO:1)")
            }
        }
    }

    /**
     * Đọc nội dung từ [InputStream] thành chuỗi văn bản với giới hạn kích thước bytes tối đa.
     *
     * @param maxBytes Giới hạn dung lượng bytes tối đa được phép đọc (mặc định [MAX_UNCOMPRESSED_ENTRY_SIZE]).
     * @param charset Bảng mã ký tự sử dụng để chuyển đổi chuỗi (mặc định [Charsets.UTF_8]).
     * @return Chuỗi văn bản đọc được từ luồng dữ liệu.
     * @throws IllegalStateException Ném ra exception nếu tổng số bytes đọc được từ luồng vượt quá [maxBytes].
     */
    fun InputStream.readBoundedText(
        maxBytes: Long = MAX_UNCOMPRESSED_ENTRY_SIZE,
        charset: Charset = Charsets.UTF_8
    ): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0L

        use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxBytes) {
                    throw IllegalStateException("Stream exceeded maximum allowed byte limit of $maxBytes bytes")
                }
                out.write(buffer, 0, bytesRead)
            }
        }
        return out.toString(charset.name())
    }
}

