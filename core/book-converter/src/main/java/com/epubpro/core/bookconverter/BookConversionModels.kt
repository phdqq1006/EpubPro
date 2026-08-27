package com.epubpro.core.bookconverter

import java.io.File

/** Các giai đoạn chính của quá trình chuyển ebook sang EPUB nội bộ. */
enum class BookConversionStage {
    VALIDATING,
    DECODING,
    PACKAGING,
    VALIDATING_EPUB,
    COMPLETED
}

/** Mã lỗi ổn định để UI và Worker ánh xạ sang chuỗi hiển thị phù hợp. */
enum class BookConversionErrorCode {
    FILE_TOO_LARGE,
    UNSUPPORTED_EXTENSION,
    ENCRYPTED_OR_DRM,
    FIXED_LAYOUT_UNSUPPORTED,
    INVALID_OR_CORRUPTED_FILE,
    OUTPUT_FAILED
}

/** Lỗi miền của bộ chuyển đổi ebook. */
class BookConversionException(
    val code: BookConversionErrorCode,
    cause: Throwable? = null
) : IllegalStateException(code.name, cause)

/**
 * Kết quả chuyển đổi đã được ghi thành một file EPUB hoàn chỉnh.
 *
 * @property epubFile File EPUB nội bộ sau khi chuyển đổi.
 * @property sourceFormat Định dạng file nguồn.
 */
data class BookConversionResult(
    val epubFile: File,
    val sourceFormat: com.epubpro.domain.model.BookSourceFormat
)

/**
 * Trạng thái tiến độ chuyển đổi dùng cho Worker và UI.
 *
 * @property stage Giai đoạn đang chạy.
 * @property progress Phần trăm hoàn thành từ 0 đến 100.
 */
data class BookConversionProgress(
    val stage: BookConversionStage,
    val progress: Int
)
