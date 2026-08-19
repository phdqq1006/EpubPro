package com.epubpro.core.storage.bookbible

import com.epubpro.domain.model.BookFingerprints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình tạo dấu vân tay định danh (Fingerprints) chuẩn xác cho sách theo đặc tả Book Bible Backend.
 *
 * Tính toán mã băm SHA-256 thực tế của nội dung tệp (streamed), cấu trúc danh sách chương (spine structure qua Zip),
 * và các chương mẫu (sampled chapters) mà không phụ thuộc vào đường dẫn tuyệt đối cục bộ trên thiết bị.
 */
@Singleton
class BookBibleFingerprintGenerator @Inject constructor() {

    /**
     * Tạo dấu vân tay [BookFingerprints] hoàn chỉnh từ một tệp EPUB trên bộ nhớ thiết bị.
     *
     * @param file Tệp sách EPUB nguồn.
     * @return Đối tượng [BookFingerprints] gồm file hash, structure hash, sampled chapters hash và edition id.
     */
    suspend fun generateFromEpubFile(file: File): BookFingerprints = withContext(Dispatchers.IO) {
        val fileHash = computeFileSha256(file)

        val entryNames = mutableListOf<String>()
        val sampledHashes = mutableListOf<String>()

        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries().toList().map { it.name }.sorted()
                entryNames.addAll(entries)

                val htmlEntries = entries.filter {
                    it.endsWith(".xhtml", ignoreCase = true) || it.endsWith(".html", ignoreCase = true)
                }

                if (htmlEntries.isNotEmpty()) {
                    val sampleIndices = listOf(
                        0,
                        htmlEntries.size / 2,
                        htmlEntries.lastIndex
                    ).distinct().filter { it in htmlEntries.indices }

                    for (idx in sampleIndices) {
                        val entry = zip.getEntry(htmlEntries[idx])
                        if (entry != null) {
                            zip.getInputStream(entry).use { stream ->
                                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                                if (text.isNotBlank()) {
                                    sampledHashes.add(computeStringSha256(text.trim()))
                                }
                            }
                        }
                    }
                }
            }
        }

        val structureRaw = entryNames.joinToString("\n")
        val structureHash = if (structureRaw.isNotBlank()) {
            computeStringSha256(structureRaw)
        } else {
            fileHash
        }

        val edition = "epub_v1_${structureHash.take(16)}"

        BookFingerprints(
            file = fileHash,
            edition = edition,
            structure = structureHash,
            sampledChapters = sampledHashes
        )
    }

    /**
     * Tạo dấu vân tay [BookFingerprints] từ văn bản nguồn truyện online hoặc fallback.
     *
     * @param novelId Mã truyện online.
     * @param sampleTexts Danh sách nội dung một số chương mẫu đã nạp.
     * @return Đối tượng [BookFingerprints].
     */
    fun generateForOnlineNovel(
        novelId: String,
        sampleTexts: List<String> = emptyList()
    ): BookFingerprints {
        val structureHash = computeStringSha256("online_novel:$novelId")
        val sampledHashes = sampleTexts.map { computeStringSha256(it.trim()) }
        val fileHash = if (sampledHashes.isNotEmpty()) {
            computeStringSha256(sampledHashes.joinToString(":"))
        } else {
            structureHash
        }

        return BookFingerprints(
            file = fileHash,
            edition = "online_v1_${novelId.take(16)}",
            structure = structureHash,
            sampledChapters = sampledHashes
        )
    }

    /**
     * Tính toán mã băm SHA-256 từ luồng đọc byte của tệp (streaming buffer để chống OOM với file dung lượng lớn).
     *
     * @param file Tệp cần tính mã băm.
     * @return Chuỗi hex SHA-256 viết thường.
     */
    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) // 64KB buffer
        FileInputStream(file).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Tính toán mã băm SHA-256 từ một chuỗi văn bản UTF-8.
     *
     * @param input Chuỗi văn bản đầu vào.
     * @return Chuỗi hex SHA-256 viết thường.
     */
    private fun computeStringSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
