package com.epubpro.core.storage

import java.security.MessageDigest

/**
 * Bản lưu chụp nhanh (snapshot) nội dung chương sách đang đọc dở phục vụ việc mở lại sách tức thì (instant resume).
 *
 * Lưu trữ cả mã HTML đã chuẩn hóa ([normalizedHtml]) và mã HTML đã làm sạch an toàn ([sanitizedHtml])
 * cùng các thông tin xác thực tính toàn vẹn (đường dẫn, kích thước, thời gian sửa đổi, mã băm [sourceHash], phiên bản bộ xử lý).
 *
 * @property bookId Mã định danh duy nhất của cuốn sách trong cơ sở dữ liệu.
 * @property chapterIndex Chỉ số thứ tự chương đang đọc (0-indexed).
 * @property entryName Tên tệp entry của chương trong tệp nén zip EPUB.
 * @property canonicalPath Đường dẫn chuẩn hóa của tệp sách EPUB.
 * @property fileLength Kích thước tệp sách EPUB tính theo byte.
 * @property lastModified Thời điểm chỉnh sửa tệp sách gần nhất (milliseconds).
 * @property cacheSchemaVersion Phiên bản định dạng lưu trữ cache.
 * @property normalizerVersion Phiên bản của bộ chuẩn hóa HTML lúc tạo snapshot.
 * @property sanitizerVersion Phiên bản của bộ làm sạch HTML lúc tạo snapshot.
 * @property sourceHash Mã băm SHA-256 của nội dung HTML thô trước khi làm sạch.
 * @property normalizedHtml Chuỗi mã HTML đã được chuẩn hóa cấu trúc.
 * @property sanitizedHtml Chuỗi mã HTML đã được làm sạch an toàn.
 * @property timestamp Thời điểm tạo snapshot tính bằng epoch milliseconds.
 */
data class ReaderResumeSnapshot(
    val bookId: String,
    val chapterIndex: Int,
    val entryName: String,
    val canonicalPath: String,
    val fileLength: Long,
    val lastModified: Long,
    val cacheSchemaVersion: Int = CURRENT_CACHE_SCHEMA_VERSION,
    val normalizerVersion: Int = CURRENT_NORMALIZER_VERSION,
    val sanitizerVersion: Int = CURRENT_SANITIZER_VERSION,
    val sourceHash: String,
    val normalizedHtml: String,
    val sanitizedHtml: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CURRENT_CACHE_SCHEMA_VERSION = 2
        const val CURRENT_NORMALIZER_VERSION = 1
        const val CURRENT_SANITIZER_VERSION = 1

        /**
         * Tính toán mã băm SHA-256 cho chuỗi nội dung văn bản.
         *
         * @param content Chuỗi văn bản cần băm.
         * @return Chuỗi hexa SHA-256 64 ký tự.
         */
        fun computeContentHash(content: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
