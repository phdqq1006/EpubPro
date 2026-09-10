package com.epubpro.core.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Kiểm tra định danh EPUB độc lập với tên file và thứ tự các identifier trong metadata. */
class EpubPublicationIdentifierTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** Chọn identifier được package tham chiếu, không lấy phần tử đầu tiên. */
    @Test fun referencedIdentifierWinsOverFirstIdentifier() {
        assertEquals("urn:book:42", read("book-id",
            """<dc:identifier id="isbn">other</dc:identifier><dc:identifier id="book-id"> urn:book:42 </dc:identifier>"""))
    }

    /** Thiếu tham chiếu không được suy đoán ID dù có identifier khác. */
    @Test fun missingReferenceDoesNotGuessIdentifier() {
        assertNull(read("", """<dc:identifier id="id">42</dc:identifier>"""))
        assertNull(read("missing", """<dc:identifier id="id">42</dc:identifier>"""))
    }

    /** ID rỗng không được dùng để nhận nhầm các sách thiếu metadata là một truyện. */
    @Test fun blankIdentifierIsAbsent() {
        assertNull(read("id", """<dc:identifier id="id"> </dc:identifier>"""))
    }

    /** Hai file độc lập chỉ được coi là cùng truyện khi giá trị ID trùng chính xác. */
    @Test fun identityIsIndependentOfFilenameAndCaseIsPreserved() {
        val first = read("id", """<dc:identifier id="id">Story-A</dc:identifier>""")
        assertEquals(first, read("id", """<dc:identifier id="id">Story-A</dc:identifier>"""))
        assertEquals("story-a", read("id", """<dc:identifier id="id">story-a</dc:identifier>"""))
    }

    /**
     * Tạo EPUB nhỏ để kiểm tra parser trên dữ liệu ZIP thực.
     *
     * @param reference Tham chiếu unique-identifier trong package.
     * @param identifiers Các identifier thuộc metadata.
     * @return ID parser trích xuất từ EPUB vừa tạo.
     */
    private fun read(reference: String, identifiers: String): String? {
        val file = temporaryFolder.newFile()
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("content.opf"))
            output.write("""<package unique-identifier="$reference" xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata>$identifiers</metadata></package>""".toByteArray())
            output.closeEntry()
        }
        return ZipFile(file).use { EpubPackageStructureParser.readPublicationIdentifier(it) }
    }
}
