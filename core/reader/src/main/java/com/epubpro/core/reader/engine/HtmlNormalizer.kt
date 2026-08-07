package com.epubpro.core.reader.engine

import org.jsoup.Jsoup

object HtmlNormalizer {

    /**
     * Normalizes EPUB HTML content that lacks <p> tags for individual paragraphs
     * (such as books converted via GetTextFromHtml or raw TXT dumps).
     * Automatically wraps text chunks separated by <br> or newlines into proper <p> elements.
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

            val blockTagsSet = setOf("body", "section", "article", "main", "center", "td", "th", "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "blockquote", "div", "figure")

            fun wrapInP(parent: org.jsoup.nodes.Element) {
                val children = parent.childNodes().toList()
                if (children.isEmpty()) return
                
                val currentGroup = mutableListOf<org.jsoup.nodes.Node>()
                
                fun flushGroup() {
                    if (currentGroup.isNotEmpty()) {
                        val shouldWrap = currentGroup.any { node ->
                            node is org.jsoup.nodes.Element || (node is org.jsoup.nodes.TextNode && node.text().isNotBlank())
                        }
                        if (shouldWrap) {
                            val p = org.jsoup.nodes.Element("p")
                            currentGroup.first().before(p)
                            currentGroup.forEach { p.appendChild(it) }
                        }
                        currentGroup.clear()
                    }
                }
                
                for (node in children) {
                    if (node is org.jsoup.nodes.Element) {
                        val tag = node.tagName().lowercase()
                        if (blockTagsSet.contains(tag)) {
                            flushGroup()
                            wrapInP(node)
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
