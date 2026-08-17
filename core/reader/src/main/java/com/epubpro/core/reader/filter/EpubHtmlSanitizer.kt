package com.epubpro.core.reader.filter

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Bộ làm sạch HTML cho nội dung sách EPUB.
 *
 * Loại bỏ triệt để các mã độc, active script, iframe, form, event handlers (on*),
 * và URL nguy hiểm (javascript:, vbscript:) trước khi nạp vào Android WebView.
 */
object EpubHtmlSanitizer {

    private val BLOCKED_TAGS = setOf(
        "script",
        "iframe",
        "frame",
        "frameset",
        "object",
        "embed",
        "applet",
        "form",
        "input",
        "button",
        "select",
        "textarea",
        "style"
    )

    private val URL_ATTRIBUTES = listOf("href", "src", "action", "data", "formaction", "xlink:href")

    /**
     * Làm sạch mã HTML/XHTML của chương sách.
     *
     * @param html Chuỗi HTML gốc cần xử lý.
     * @return Chuỗi HTML đã được làm sạch an toàn.
     */
    fun sanitize(html: String): String {
        if (html.isBlank()) return ""

        val doc = Jsoup.parse(html)
        doc.outputSettings().apply {
            syntax(Document.OutputSettings.Syntax.html)
            charset(Charsets.UTF_8)
            prettyPrint(false)
        }

        // 1. Loại bỏ các thẻ độc hại / active tags
        for (tag in BLOCKED_TAGS) {
            doc.select(tag).remove()
        }

        // 2. Loại bỏ stylesheet bên ngoài và meta độc hại
        doc.select("link[rel*=stylesheet], link[rel*=icon]").remove()
        doc.select("meta[http-equiv=refresh]").remove()
        doc.select("meta[name=viewport]").remove()

        // 3. Quét và làm sạch tất cả elements
        val allElements = doc.allElements
        for (element in allElements) {
            sanitizeElement(element)
        }

        return doc.outerHtml()
    }

    private fun sanitizeElement(element: Element) {
        val attributesToRemove = mutableListOf<String>()

        for (attribute in element.attributes()) {
            val attrName = attribute.key.lowercase(Locale.ROOT)
            val attrValue = attribute.value.trim()

            // 3.1. Loại bỏ các event attributes (onclick, onerror, onload, onmouseover, ...)
            if (attrName.startsWith("on")) {
                attributesToRemove.add(attribute.key)
                continue
            }

            // 3.2. Loại bỏ inline style trên html/body để nhường quyền kiểm soát cho CssInjector
            if (element.normalName() in listOf("html", "body") && attrName == "style") {
                attributesToRemove.add(attribute.key)
                continue
            }

            // 3.3. Kiểm tra các thuộc tính URL
            if (attrName in URL_ATTRIBUTES) {
                if (isDangerousUrl(attrValue)) {
                    attributesToRemove.add(attribute.key)
                }
            }
        }

        for (attrKey in attributesToRemove) {
            element.removeAttr(attrKey)
        }
    }

    private fun isDangerousUrl(url: String): Boolean {
        if (url.isBlank()) return false

        // Bỏ qua các ký tự khoảng trắng hoặc điều khiển đặc biệt
        val normalized = url.replace("""[\s\u0000-\u001F]""".toRegex(), "").lowercase(Locale.ROOT)

        // Chặn scheme javascript: và vbscript:
        if (normalized.startsWith("javascript:") || normalized.startsWith("vbscript:")) {
            return true
        }

        // Với data: URI, chỉ cho phép ảnh an toàn
        if (normalized.startsWith("data:")) {
            return !normalized.startsWith("data:image/")
        }

        return false
    }
}
