package com.epubpro.core.reader.engine

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility factory to construct in-memory/temp-file valid and edge-case EPUB archives for testing.
 */
object EpubTestArchiveFactory {

    /**
     * Creates a standard EPUB archive with META-INF/container.xml and OPF manifest/spine.
     */
    fun createStandardEpub(
        targetFile: File,
        title: String = "Test Book",
        author: String = "Test Author",
        chapters: List<Pair<String, String>> = listOf(
            "Chapter 1" to "<p>Content of Chapter 1</p>",
            "Chapter 2" to "<p>Content of Chapter 2</p>"
        )
    ): File {
        ZipOutputStream(FileOutputStream(targetFile)).use { zip ->
            // 1. mimetype (uncompressed)
            val mimetypeEntry = ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = 20; crc = 0x2CAB616F }
            zip.putNextEntry(mimetypeEntry)
            zip.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zip.closeEntry()

            // 2. META-INF/container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent()
            zip.write(containerXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 3. OEBPS/content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val manifestItems = chapters.mapIndexed { idx, (chapTitle, _) ->
                """<item id="chap_${idx + 1}" href="text/chapter_${idx + 1}.xhtml" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n        ")

            val spineItems = chapters.mapIndexed { idx, _ ->
                """<itemref idref="chap_${idx + 1}"/>"""
            }.joinToString("\n        ")

            val opfContent = """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>$title</dc:title>
                        <dc:creator>$author</dc:creator>
                    </metadata>
                    <manifest>
                        $manifestItems
                    </manifest>
                    <spine>
                        $spineItems
                    </spine>
                </package>
            """.trimIndent()
            zip.write(opfContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 4. Chapter files
            chapters.forEachIndexed { idx, (chapTitle, htmlBody) ->
                zip.putNextEntry(ZipEntry("OEBPS/text/chapter_${idx + 1}.xhtml"))
                val xhtml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <!DOCTYPE html>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>$chapTitle</title>
                    </head>
                    <body>
                        <h1>$chapTitle</h1>
                        $htmlBody
                    </body>
                    </html>
                """.trimIndent()
                zip.write(xhtml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return targetFile
    }

    /**
     * Creates an EPUB archive missing META-INF/container.xml to test fallback mechanisms.
     */
    fun createLegacyEpubWithoutContainerXml(
        targetFile: File,
        title: String = "Legacy Book",
        chapters: List<Pair<String, String>> = listOf(
            "chap10.xhtml" to "<p>Chapter 10</p>",
            "chap2.xhtml" to "<p>Chapter 2</p>",
            "chap1.xhtml" to "<p>Chapter 1</p>"
        )
    ): File {
        ZipOutputStream(FileOutputStream(targetFile)).use { zip ->
            chapters.forEach { (entryName, htmlBody) ->
                zip.putNextEntry(ZipEntry(entryName))
                val xhtml = """
                    <html>
                    <head><title>$entryName</title></head>
                    <body>$htmlBody</body>
                    </html>
                """.trimIndent()
                zip.write(xhtml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return targetFile
    }
}
