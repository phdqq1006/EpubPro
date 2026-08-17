package com.epubpro.core.reader.engine

import com.epubpro.core.reader.engine.EpubReadLimits.readBoundedText
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry

class EpubPackageParserTest {

    @Test
    fun `readBoundedText throws IllegalStateException when stream exceeds byte limit`() {
        val largeData = "A".repeat(1000).toByteArray(Charsets.UTF_8)
        val stream = ByteArrayInputStream(largeData)

        val exception = assertThrows(IllegalStateException::class.java) {
            stream.readBoundedText(maxBytes = 500)
        }
        assertTrue(exception.message!!.contains("exceeded maximum allowed byte limit"))
    }

    @Test
    fun `readBoundedText successfully reads content within limit`() {
        val content = "Cuốn sách mẫu của EpubPro"
        val stream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        val result = stream.readBoundedText(maxBytes = 1024)
        assertEquals(content, result)
    }

    @Test
    fun `validateZipEntry throws on entry size exceeding max limit`() {
        val entry = ZipEntry("huge_file.xhtml").apply {
            size = EpubReadLimits.MAX_UNCOMPRESSED_ENTRY_SIZE + 1024
        }
        val exception = assertThrows(IllegalStateException::class.java) {
            EpubReadLimits.validateZipEntry(entry)
        }
        assertTrue(exception.message!!.contains("exceeds limit"))
    }

    @Test
    fun `validateZipEntry throws on suspicious zip bomb compression ratio`() {
        val entry = ZipEntry("bomb.xhtml").apply {
            compressedSize = 100
            size = 20_000 // Ratio 200:1 > threshold 100:1
        }
        val exception = assertThrows(IllegalStateException::class.java) {
            EpubReadLimits.validateZipEntry(entry)
        }
        assertTrue(exception.message!!.contains("compression ratio"))
    }

    @Test
    fun `opf manifest and spine are parsed correctly with jsoup xml parser`() {
        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Đại Chúa Tể</dc:title>
                    <dc:creator>Thiên Tằm Thổ Đậu</dc:creator>
                </metadata>
                <manifest>
                    <item id="chap1" href="text/chapter_001.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chap2" href="text/chapter_002.xhtml#section1" media-type="application/xhtml+xml"/>
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="chap1"/>
                    <itemref idref="chap2"/>
                </spine>
            </package>
        """.trimIndent()

        val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())
        val title = doc.select("dc|title, title").text()
        val author = doc.select("dc|creator, creator").text()
        assertEquals("Đại Chúa Tể", title)
        assertEquals("Thiên Tằm Thổ Đậu", author)

        val manifest = mutableMapOf<String, String>()
        doc.select("manifest > item").forEach {
            manifest[it.attr("id")] = it.attr("href")
        }

        val spine = doc.select("spine > itemref").map { it.attr("idref") }
        assertEquals(listOf("chap1", "chap2"), spine)

        val cleanSpineHrefs = spine.map { id ->
            manifest[id]!!.substringBefore('#')
        }
        assertEquals(listOf("text/chapter_001.xhtml", "text/chapter_002.xhtml"), cleanSpineHrefs)
    }

    @Test
    fun `container xml is parsed correctly to extract rootfile opf full path`() {
        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()

        val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val rootfile = doc.select("rootfile[full-path]").first()
        val fullPath = rootfile?.attr("full-path")
        assertEquals("OEBPS/content.opf", fullPath)
    }

    @Test
    fun `url encoded href paths in opf manifest are decoded properly`() {
        val encodedHref = "text/ch%C6%B0%C6%A1ng_001.xhtml%23section2"
        val cleanHref = encodedHref.substringBefore('#').substringBefore("%23")
        val decoded = java.net.URLDecoder.decode(cleanHref, "UTF-8")
        assertEquals("text/chương_001.xhtml", decoded)
    }
}

