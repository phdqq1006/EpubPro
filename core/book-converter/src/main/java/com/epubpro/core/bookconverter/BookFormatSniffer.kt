package com.epubpro.core.bookconverter

import com.epubpro.domain.model.BookSourceFormat
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nhận diện các định dạng ebook được phép chuyển đổi cục bộ.
 *
 * Tên file chỉ là gợi ý; libmobi tiếp tục kiểm tra header và cấu trúc thực tế.
 */
@Singleton
class BookFormatSniffer @Inject constructor() {
    /**
     * Xác định định dạng nguồn từ phần mở rộng hoặc header Palm Database.
     *
     * @param file File nguồn cần nhận diện.
     * @return Định dạng được hỗ trợ hoặc null nếu không thuộc phạm vi converter.
     */
    fun sniff(file: File): BookSourceFormat? = when (file.extension.lowercase()) {
        "epub" -> BookSourceFormat.EPUB
        "prc" -> BookSourceFormat.PRC
        "mobi" -> BookSourceFormat.MOBI
        "azw3" -> BookSourceFormat.AZW3
        else -> detectPalmDatabaseFormat(file)
    }

    /**
     * Kiểm tra nhanh file có header Palm Database ebook hợp lý hay không.
     *
     * @param file File nguồn.
     * @return true khi header có thể là MOBI hoặc PalmDOC/PRC.
     */
    fun hasPalmDatabaseHeader(file: File): Boolean {
        return detectPalmDatabaseFormat(file) != null
    }

    /**
     * Nhận diện định dạng Palm Database dựa trên cặp trường type/creator trong header.
     *
     * @param file File nguồn cần kiểm tra.
     * @return Định dạng tương ứng hoặc null nếu header không phải PDB ebook hỗ trợ.
     */
    private fun detectPalmDatabaseFormat(file: File): BookSourceFormat? {
        if (!file.isFile || file.length() < 78L) return null
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(78)
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read <= 0) return@use null
                    offset += read
                }
                when {
                    header.copyOfRange(60, 68).contentEquals("BOOKMOBI".toByteArray(Charsets.US_ASCII)) ->
                        BookSourceFormat.MOBI
                    header.copyOfRange(60, 68).contentEquals("TEXtREAd".toByteArray(Charsets.US_ASCII)) ->
                        BookSourceFormat.PRC
                    else -> null
                }
            }
        }.getOrNull()
    }
}
