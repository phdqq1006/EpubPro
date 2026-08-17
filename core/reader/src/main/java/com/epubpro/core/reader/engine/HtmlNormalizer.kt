package com.epubpro.core.reader.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlNormalizer {

    private val BLOCK_TAGS = setOf(
        "address", "article", "aside", "blockquote", "body", "center", "dd", "details",
        "dialog", "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer",
        "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hgroup", "hr", "li",
        "main", "menu", "nav", "ol", "p", "pre", "section", "summary", "table", "tbody",
        "td", "tfoot", "th", "thead", "tr", "ul"
    )

    private val RECURSIVE_CONTAINERS = setOf(
        "body", "section", "article", "main", "div", "blockquote", "li", "td", "th", "center", "aside", "figure"
    )

    /**
     * Normalizes EPUB HTML content that lacks <p> tags for individual paragraphs
     * (such as books converted via GetTextFromHtml or raw TXT dumps).
     * Automatically wraps text chunks separated by <br> or newlines into proper <p> elements
     * without breaking block container semantics (lists, tables, pre, headers).
     */
    fun normalize(htmlContent: String): String {
        if (htmlContent.isBlank()) return htmlContent

        try {
            var processedHtml = htmlContent
            if (!processedHtml.contains("<br", ignoreCase = true)) {
                processedHtml = processedHtml.replace(Regex("(\\r?\\n)+"), "<br/>")
            }

            val doc = Jsoup.parse(processedHtml)
            val body = doc.body() ?: return htmlContent

            val pTags = body.select("p")
            // If the document already has 3 or more <p> tags, assume it's properly structured
            if (pTags.size >= 3) {
                return htmlContent
            }

            fun isBlock(element: Element): Boolean {
                val tag = element.tagName().lowercase()
                return tag in BLOCK_TAGS || element.tag().isBlock
            }

            fun wrapInP(parent: Element) {
                val children = parent.childNodes().toList()
                if (children.isEmpty()) return

                val currentGroup = mutableListOf<Node>()

                fun flushGroup() {
                    if (currentGroup.isNotEmpty()) {
                        val shouldWrap = currentGroup.any { node ->
                            node is Element || (node is TextNode && node.text().isNotBlank())
                        }
                        if (shouldWrap) {
                            val p = Element("p")
                            currentGroup.first().before(p)
                            currentGroup.forEach { p.appendChild(it) }
                        }
                        currentGroup.clear()
                    }
                }

                for (node in children) {
                    if (node is Element) {
                        val tag = node.tagName().lowercase()
                        if (isBlock(node)) {
                            flushGroup()
                            if (tag in RECURSIVE_CONTAINERS) {
                                wrapInP(node)
                            }
                        } else if (tag == "br") {
                            flushGroup()
                            node.remove()
                        } else {
                            currentGroup.add(node)
                        }
                    } else {
                        currentGroup.add(node)
                    }
                }
                flushGroup()
            }

            wrapInP(body)

            return doc.outerHtml()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return htmlContent
    }
}
