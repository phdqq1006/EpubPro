package com.epubpro.core.storage.worker

import com.epubpro.domain.model.Book
import com.epubpro.domain.model.BookSourceFormat

/**
 * Bản sao EPUB chờ lựa chọn dùng chung cho picker và luồng mở từ ứng dụng khác.
 *
 * @property sourcePath Đường dẫn bản sao nguồn.
 * @property originalName Tên file người dùng chọn.
 * @property sourceFormat Định dạng nguồn.
 * @property identifier ID xuất bản được dùng để phát hiện trùng.
 * @property matches Những truyện trong thư viện có cùng ID.
 */
data class PendingLocalBookImport(
    val sourcePath: String,
    val originalName: String?,
    val sourceFormat: BookSourceFormat,
    val identifier: String,
    val matches: List<Book>
)
