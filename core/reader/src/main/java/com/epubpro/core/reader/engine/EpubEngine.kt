package com.epubpro.core.reader.engine

import android.content.Context
import com.epubpro.core.reader.engine.EpubReadLimits.readBoundedText
import com.epubpro.domain.model.Book
import com.epubpro.domain.repository.SearchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
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
     * Parses metadata from EPUB file with memory safety boundaries.
     */
    suspend fun parseEpubMetadata(file: File): Book = withContext(Dispatchers.IO) {
        if (file.length() > EpubReadLimits.MAX_EPUB_FILE_SIZE) {
            throw IllegalStateException("File EPUB vượt quá kích thước tối đa cho phép (${EpubReadLimits.MAX_EPUB_FILE_SIZE / (1024 * 1024)}MB)")
        }

        var title = file.nameWithoutExtension
        var author = "Unknown Author"

        try {
            ZipFile(file).use { zip ->
                val allEntries = zip.entries().toList()
                val opfEntry = findOpfEntry(zip, allEntries)
                if (opfEntry != null) {
                    EpubReadLimits.validateZipEntry(opfEntry)
                    val content = zip.getInputStream(opfEntry).use { it.readBoundedText() }
                    val opfDoc = Jsoup.parse(content, "", Parser.xmlParser())
                    val dcTitle = opfDoc.select("dc|title, title").text().trim()
                    val dcCreator = opfDoc.select("dc|creator, creator").text().trim()
                    if (dcTitle.isNotBlank()) title = dcTitle
                    if (dcCreator.isNotBlank()) author = dcCreator
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
     * Extracts lightweight chapter headers without loading full HTML text into memory.
     */
    suspend fun extractChapterHeaders(file: File): List<EpubChapterHeader> = withContext(Dispatchers.IO) {
        val headers = mutableListOf<EpubChapterHeader>()
        try {
            ZipFile(file).use { zip ->
                val orderedEntries = getOrderedHtmlEntries(zip)

                orderedEntries.forEachIndexed { index, entry ->
                    // Read header sample to extract chapter title
                    val rawTitle = try {
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

                    val title = sanitizeChapterTitle(rawTitle)
                        .ifBlank { "Chương ${index + 1}" }

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
     * Loads single chapter HTML on demand with bounded stream reading.
     */
    suspend fun loadChapterHtml(file: File, entryName: String): String = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName)
                if (entry != null) {
                    EpubReadLimits.validateZipEntry(entry)
                    val rawHtml = zip.getInputStream(entry).use { it.readBoundedText() }
                    return@withContext HtmlNormalizer.normalize(rawHtml)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Không thể tải nội dung chương: $entryName", e)
        }
        throw IllegalStateException("Không tìm thấy nội dung chương: $entryName")
    }

    /**
     * Streams chapters one by one into FTS index without keeping them in memory.
     */
    suspend fun indexBookContent(file: File, bookId: String, searchRepository: SearchRepository) = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip ->
                val orderedEntries = getOrderedHtmlEntries(zip)

                val indexedChapters = mutableListOf<Pair<Int, Pair<String, String>>>()

                orderedEntries.forEachIndexed { index, entry ->
                    try {
                        EpubReadLimits.validateZipEntry(entry)
                        val rawHtml = zip.getInputStream(entry).use { it.readBoundedText() }
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
                    } catch (e: Exception) {
                        // Skip corrupted/invalid entry in indexer
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

    private fun findOpfEntry(zip: ZipFile, allEntries: List<ZipEntry>): ZipEntry? {
        val entryMap = allEntries.associateBy { it.name }

        // 1. Try reading META-INF/container.xml (EPUB specification)
        val containerEntry = entryMap["META-INF/container.xml"]
            ?: allEntries.find { it.name.equals("META-INF/container.xml", ignoreCase = true) }

        if (containerEntry != null) {
            try {
                EpubReadLimits.validateZipEntry(containerEntry)
                val containerXml = zip.getInputStream(containerEntry).use { it.readBoundedText() }
                val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
                val rootfile = doc.select("rootfile").firstOrNull()
                val fullPath = rootfile?.attr("full-path")?.trim()
                if (!fullPath.isNullOrBlank()) {
                    val decodedPath = URLDecoder.decode(fullPath, "UTF-8")
                    val opf = entryMap[decodedPath]
                        ?: entryMap[fullPath]
                        ?: allEntries.find { it.name.equals(decodedPath, ignoreCase = true) || it.name.equals(fullPath, ignoreCase = true) }
                    if (opf != null) return opf
                }
            } catch (e: Exception) {
                // Fallback to scanning first .opf
            }
        }

        // 2. Fallback: Search for first .opf file in the archive
        return allEntries.find { it.name.endsWith(".opf", ignoreCase = true) }
    }

    private fun getOrderedHtmlEntries(zip: ZipFile): List<ZipEntry> {
        val allEntries = zip.entries().toList()
        val entryMap = allEntries.associateBy { it.name }

        // 1. Try OPF spine parsing for exact author-intended chapter sequence
        val opfEntry = findOpfEntry(zip, allEntries)
        if (opfEntry != null) {
            try {
                EpubReadLimits.validateZipEntry(opfEntry)
                val opfContent = zip.getInputStream(opfEntry).use { it.readBoundedText() }
                val opfDir = opfEntry.name.substringBeforeLast('/', "")
                val dirPrefix = if (opfDir.isNotEmpty()) "$opfDir/" else ""

                val opfDoc = Jsoup.parse(opfContent, "", Parser.xmlParser())
                val manifestMap = mutableMapOf<String, String>()
                opfDoc.select("manifest > item").forEach { item ->
                    val id = item.attr("id").trim()
                    val href = item.attr("href").trim()
                    if (id.isNotEmpty() && href.isNotEmpty()) {
                        manifestMap[id] = href
                    }
                }

                val spineIds = mutableListOf<String>()
                opfDoc.select("spine > itemref").forEach { itemref ->
                    val idref = itemref.attr("idref").trim()
                    if (idref.isNotEmpty()) {
                        spineIds.add(idref)
                    }
                }

                val orderedEntries = mutableListOf<ZipEntry>()
                for (id in spineIds) {
                    val href = manifestMap[id] ?: continue
                    val cleanHref = href.substringBefore('#') // Strip fragment identifier if present
                    val decodedHref = URLDecoder.decode(cleanHref, "UTF-8")
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

    private fun sanitizeChapterTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) return rawTitle

        var title = rawTitle.trim()

        // 1. Remove software watermarks / author generator tags
        title = title.replace("(?i)\\s*[-|–—]\\s*(Created|Written|Converted|Generated)(\\s+with|\\s+by).*$".toRegex(), "")
        title = title.replace("(?i)\\s*(Created|Written|Converted|Generated)(\\s+with|\\s+by).*$".toRegex(), "")
        title = title.replace("(?i)\\s*[-|–—]\\s*(truyenfull|metruyenchu|wikidich|tangthuvien|vbooks|mkbyme).*$".toRegex(), "")

        // 2. Remove appended Book Title / Series Title in parentheses or suffix after dash
        val parts = title.split(" - ")
        if (parts.size >= 2) {
            val lastPart = parts.last().trim()
            if (lastPart.contains("(") && lastPart.contains(")")) {
                title = parts.dropLast(1).joinToString(" - ").trim()
            }
        }

        return title.trim().removeSuffix("-").removeSuffix("|").trim()
    }
}
