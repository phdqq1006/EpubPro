package com.epubpro.core.reader.engine

import android.content.Context
import com.epubpro.domain.model.Book
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class EpubChapterHeader(
    val index: Int,
    val title: String,
    val entryName: String
)

data class EpubChapterContent(
    val index: Int,
    val title: String,
    val htmlContent: String
)

typealias ReadiumEngine = EpubEngine

@Singleton
class EpubEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Parses metadata from EPUB file
     */
    suspend fun parseEpubMetadata(file: File): Book = withContext(Dispatchers.IO) {
        var title = file.nameWithoutExtension
        var author = "Unknown Author"

        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".opf", ignoreCase = true)) {
                        val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        title = extractXmlTag(content, "dc:title") ?: title
                        author = extractXmlTag(content, "dc:creator") ?: author
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val totalChapters = try {
            extractChapterHeaders(file).size
        } catch (e: Exception) {
            0
        }

        Book(
            id = file.name,
            title = title,
            author = author,
            coverPath = null,
            filePath = file.absolutePath,
            addedAt = System.currentTimeMillis(),
            lastReadAt = System.currentTimeMillis(),
            totalChapters = totalChapters
        )
    }

    /**
     * Extracts lightweight chapter headers without loading HTML text into memory
     */
    suspend fun extractChapterHeaders(file: File): List<EpubChapterHeader> = withContext(Dispatchers.IO) {
        val headers = mutableListOf<EpubChapterHeader>()
        try {
            ZipFile(file).use { zip ->
                val orderedEntries = getOrderedHtmlEntries(zip)

                orderedEntries.forEachIndexed { index, entry ->
                    // Read header sample to extract chapter title
                    val title = try {
                        val buffer = ByteArray(8192)
                        val readBytes = zip.getInputStream(entry).use { it.read(buffer, 0, buffer.size) }
                        if (readBytes > 0) {
                            val sample = String(buffer, 0, readBytes, Charsets.UTF_8)
                            extractXmlTag(sample, "title")
                                ?: extractXmlTag(sample, "h1")
                                ?: extractXmlTag(sample, "h2")
                                ?: "Chương ${index + 1}"
                        } else {
                            "Chương ${index + 1}"
                        }
                    } catch (e: Exception) {
                        "Chương ${index + 1}"
                    }

                    headers.add(
                        EpubChapterHeader(
                            index = index,
                            title = title,
                            entryName = entry.name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Không thể đọc cấu trúc EPUB", e)
        }
        headers
    }

    /**
     * Loads single chapter HTML on demand
     */
    suspend fun loadChapterHtml(file: File, entryName: String): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName)
                if (entry != null) {
                    val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    return@withContext HtmlNormalizer.normalize(rawHtml)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Không thể tải nội dung chương", e)
        }
        throw IllegalStateException("Không tìm thấy nội dung chương: $entryName")
    }

    /**
     * Streams chapters one by one into FTS index without keeping them in memory
     */
    suspend fun indexBookContent(file: File, bookId: String, searchRepository: SearchRepository) = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip ->
                val orderedEntries = getOrderedHtmlEntries(zip)

                val indexedChapters = mutableListOf<Pair<Int, Pair<String, String>>>()

                orderedEntries.forEachIndexed { index, entry ->
                    val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val plainText = stripHtmlTags(rawHtml)
                    val title = extractXmlTag(rawHtml, "title")
                        ?: extractXmlTag(rawHtml, "h1")
                        ?: "Chương ${index + 1}"
                    indexedChapters.add(index to (title to plainText))

                    // Batch index every 5 chapters to keep memory usage low
                    if (indexedChapters.size >= 5) {
                        searchRepository.indexBookContent(bookId, indexedChapters.toList())
                        indexedChapters.clear()
                    }
                }

                if (indexedChapters.isNotEmpty()) {
                    searchRepository.indexBookContent(bookId, indexedChapters)
                    indexedChapters.clear()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOrderedHtmlEntries(zip: ZipFile): List<java.util.zip.ZipEntry> {
        val allEntries = zip.entries().toList()
        val entryMap = allEntries.associateBy { it.name }

        // 1. Try OPF spine parsing for exact author-intended chapter sequence
        val opfEntry = allEntries.find { it.name.endsWith(".opf", ignoreCase = true) }
        if (opfEntry != null) {
            try {
                val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val opfDir = opfEntry.name.substringBeforeLast('/', "")
                val dirPrefix = if (opfDir.isNotEmpty()) "$opfDir/" else ""

                val manifestMap = mutableMapOf<String, String>()
                val itemRegex = "(?i)<item\\s+[^>]*id=[\"']([^\"']+)[\"'][^>]*href=[\"']([^\"']+)[\"']".toRegex()
                val itemRegexAlt = "(?i)<item\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*id=[\"']([^\"']+)[\"']".toRegex()

                itemRegex.findAll(opfContent).forEach { match ->
                    manifestMap[match.groupValues[1]] = match.groupValues[2]
                }
                itemRegexAlt.findAll(opfContent).forEach { match ->
                    manifestMap[match.groupValues[2]] = match.groupValues[1]
                }

                val spineIds = mutableListOf<String>()
                val itemrefRegex = "(?i)<itemref\\s+[^>]*idref=[\"']([^\"']+)[\"']".toRegex()
                itemrefRegex.findAll(opfContent).forEach { match ->
                    spineIds.add(match.groupValues[1])
                }

                val orderedEntries = mutableListOf<java.util.zip.ZipEntry>()
                for (id in spineIds) {
                    val href = manifestMap[id] ?: continue
                    val decodedHref = java.net.URLDecoder.decode(href, "UTF-8")
                    val fullPath = if (dirPrefix.isNotEmpty() && !decodedHref.startsWith("/")) "$dirPrefix$decodedHref" else decodedHref

                    val entry = entryMap[fullPath]
                        ?: entryMap[decodedHref]
                        ?: allEntries.find { it.name.endsWith(decodedHref, ignoreCase = true) }

                    if (entry != null && isHtmlEntry(entry.name)) {
                        orderedEntries.add(entry)
                    }
                }

                if (orderedEntries.isNotEmpty()) {
                    return orderedEntries
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Fallback: Natural numeric sort comparator
        val htmlEntries = allEntries.filter { isHtmlEntry(it.name) }
        val comparator = naturalOrderComparator()
        return htmlEntries.sortedWith(Comparator { e1, e2 -> comparator.compare(e1.name, e2.name) })
    }

    private fun isHtmlEntry(name: String): Boolean {
        return name.endsWith(".xhtml", ignoreCase = true) ||
               name.endsWith(".html", ignoreCase = true) ||
               name.endsWith(".htm", ignoreCase = true)
    }

    private fun naturalOrderComparator(): Comparator<String> {
        val regex = Regex("(\\d+)|(\\D+)")
        return Comparator { s1, s2 ->
            val m1 = regex.findAll(s1).map { it.value }.toList()
            val m2 = regex.findAll(s2).map { it.value }.toList()
            for (i in 0 until minOf(m1.size, m2.size)) {
                val token1 = m1[i]
                val token2 = m2[i]
                val num1 = token1.toIntOrNull()
                val num2 = token2.toIntOrNull()
                if (num1 != null && num2 != null) {
                    if (num1 != num2) return@Comparator num1.compareTo(num2)
                } else {
                    val res = token1.compareTo(token2, ignoreCase = true)
                    if (res != 0) return@Comparator res
                }
            }
            m1.size.compareTo(m2.size)
        }
    }

    private fun extractXmlTag(xml: String, tagName: String): String? {
        val regex = "(?i)<$tagName[^>]*>(.*?)</$tagName>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return regex.find(xml)?.groupValues?.get(1)?.let { stripHtmlTags(it) }?.takeIf { it.isNotBlank() }
    }

    private fun stripHtmlTags(html: String): String {
        return html.replace("<[^>]*>".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
