package com.epubpro.core.reader.engine

import java.io.File
import java.security.MessageDigest

/**
 * Dấu vân tay (fingerprint) định danh duy nhất trạng thái của một tệp EPUB trên bộ nhớ cục bộ.
 *
 * Được sử dụng để xác thực tính hợp lệ của cache cấu trúc chương (structure cache)
 * và snapshot khôi phục chương đọc (resume snapshot). Nếu tệp EPUB bị thay đổi nội dung,
 * sửa đổi ngày giờ hoặc phiên bản bộ phân tích (parser) thay đổi, fingerprint sẽ không khớp
 * và hệ thống sẽ tự động làm mới cache.
 *
 * @property canonicalPath Đường dẫn chuẩn hóa tuyệt đối của tệp sách.
 * @property fileLength Kích thước tệp tính theo byte.
 * @property lastModified Thời điểm chỉnh sửa tệp gần nhất tính theo milliseconds.
 * @property cacheSchemaVersion Phiên bản định dạng lưu trữ của cache.
 * @property headerParserVersion Phiên bản logic trích xuất tiêu đề chương.
 */
data class EpubCacheFingerprint(
    val canonicalPath: String,
    val fileLength: Long,
    val lastModified: Long,
    val cacheSchemaVersion: Int = CURRENT_CACHE_SCHEMA_VERSION,
    val headerParserVersion: Int = CURRENT_HEADER_PARSER_VERSION
) {
    /**
     * Mã định danh SHA-256 an toàn được tạo từ đường dẫn chuẩn hóa của tệp sách,
     * dùng làm tên tệp lưu cache trên đĩa để tránh lỗi đường dẫn không hợp lệ.
     */
    val cacheKey: String by lazy {
        computeSha256(canonicalPath)
    }

    companion object {
        /** Phiên bản cấu trúc schema lưu cache chương. */
        const val CURRENT_CACHE_SCHEMA_VERSION = 1

        /** Phiên bản thuật toán phân tích tiêu đề chương EPUB. */
        const val CURRENT_HEADER_PARSER_VERSION = 1

        /**
         * Tạo đối tượng [EpubCacheFingerprint] từ một [File] EPUB cụ thể.
         *
         * @param file Tệp EPUB cần tạo dấu vân tay.
         * @return Đối tượng [EpubCacheFingerprint] chứa đầy đủ thông tin nhận diện tệp.
         */
        fun fromFile(file: File): EpubCacheFingerprint {
            val canonicalFile = file.canonicalFile
            return EpubCacheFingerprint(
                canonicalPath = canonicalFile.canonicalPath,
                fileLength = canonicalFile.length(),
                lastModified = canonicalFile.lastModified()
            )
        }

        /**
         * Tính toán chuỗi mã băm SHA-256 từ chuỗi đầu vào.
         *
         * @param input Chuỗi văn bản cần băm.
         * @return Chuỗi hexa 64 ký tự biểu diễn mã băm SHA-256.
         */
        fun computeSha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
