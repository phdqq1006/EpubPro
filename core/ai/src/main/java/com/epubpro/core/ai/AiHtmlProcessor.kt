package com.epubpro.core.ai

import com.epubpro.domain.model.AiRule
import com.epubpro.domain.model.AiRuleAction
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class AiTextBlock(
    val id: String,
    val html: String,
    val plainText: String
)

class AiHtmlProcessor private constructor(
    private val originalHtml: String,
    val blocks: List<AiTextBlock>
) {
    fun render(replacements: Map<String, String>, rules: List<AiRule>): String {
        val document = Jsoup.parse(originalHtml)
        val elements = selectableElements(document)
        require(elements.size == blocks.size) { "Cấu trúc chương đã thay đổi" }

        blocks.forEachIndexed { index, block ->
            val replacement = replacements[block.id] ?: error("Thiếu đoạn ${block.id}")
            validateReplacement(block, replacement, rules)
            elements[index].html(replacement)
        }
        document.outputSettings().prettyPrint(false)
        return document.outerHtml()
    }

    fun validateBatch(
        requestedBlocks: List<AiTextBlock>,
        replacements: Map<String, String>,
        rules: List<AiRule>
    ) {
        require(replacements.keys == requestedBlocks.map { it.id }.toSet()) {
            "AI trả về thiếu hoặc thừa đoạn"
        }
        requestedBlocks.forEach { block ->
            validateReplacement(block, replacements.getValue(block.id), rules)
        }
    }

    private fun validateReplacement(
        block: AiTextBlock,
        replacementHtml: String,
        rules: List<AiRule>
    ) {
        require(elementSignature(block.html) == elementSignature(replacementHtml)) {
            "AI đã thay đổi cấu trúc HTML ở ${block.id}"
        }

        val outputText = Jsoup.parseBodyFragment(replacementHtml).text().trim()
        require(outputText.isNotBlank()) { "AI trả về đoạn trống ở ${block.id}" }
        val ratio = outputText.length.toDouble() / block.plainText.length.coerceAtLeast(1)
        require(ratio in MIN_LENGTH_RATIO..MAX_LENGTH_RATIO) {
            "Độ dài đoạn ${block.id} thay đổi bất thường"
        }

        rules.forEach { rule ->
            val sourceCount = countWholeTerm(block.plainText, rule.source, rule.caseSensitive)
            if (sourceCount == 0) return@forEach

            val expected = when (rule.action) {
                AiRuleAction.KEEP -> rule.source
                AiRuleAction.REPLACE -> rule.replacement.orEmpty()
            }
            require(
                expected.isNotBlank() &&
                    countWholeTerm(outputText, expected, rule.caseSensitive) >= sourceCount
            ) {
                "AI vi phạm quy tắc “${rule.source}”"
            }
        }
    }

    private fun elementSignature(fragment: String): List<String> {
        val body = Jsoup.parseBodyFragment(fragment).body()
        return buildList {
            body.childNodes().filterIsInstance<Element>().forEach { appendElementSignature(it, this) }
        }
    }

    private fun appendElementSignature(element: Element, target: MutableList<String>) {
        val attributes = element.attributes().asList()
            .sortedBy { it.key }
            .joinToString("|") { "${it.key}=${it.value}" }
        target += "<${element.tagName()}|$attributes>"
        element.children().forEach { appendElementSignature(it, target) }
        target += "</${element.tagName()}>"
    }

    companion object {
        private const val SELECTOR = "p, h1, h2, h3, h4, h5, h6, li, blockquote"
        private const val MIN_LENGTH_RATIO = 0.35
        private const val MAX_LENGTH_RATIO = 2.75

        fun parse(html: String): AiHtmlProcessor {
            val document = Jsoup.parse(html)
            val blocks = selectableElements(document).mapIndexed { index, element ->
                AiTextBlock(
                    id = "block-$index",
                    html = element.html(),
                    plainText = element.text().trim()
                )
            }
            return AiHtmlProcessor(html, blocks)
        }

        private fun selectableElements(root: Element): List<Element> =
            root.select(SELECTOR)
                .filter { element ->
                    element.text().trim().length > 1 &&
                        element.select(SELECTOR).all { it === element }
                }

        private fun countWholeTerm(text: String, term: String, caseSensitive: Boolean): Int {
            if (term.isBlank()) return 0
            val pattern = "(?<![\\p{L}\\p{N}_])${Regex.escape(term)}(?![\\p{L}\\p{N}_])"
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            return Regex(pattern, options).findAll(text).count()
        }
    }
}

object AiTextChunker {
    fun chunk(
        blocks: List<AiTextBlock>,
        maxCharacters: Int = 12_000,
        maxBlocks: Int = 30
    ): List<List<AiTextBlock>> {
        require(maxCharacters > 0)
        require(maxBlocks > 0)
        if (blocks.isEmpty()) return emptyList()

        val result = mutableListOf<List<AiTextBlock>>()
        var current = mutableListOf<AiTextBlock>()
        var currentCharacters = 0

        blocks.forEach { block ->
            val blockSize = block.html.length
            val wouldOverflow = current.isNotEmpty() &&
                (currentCharacters + blockSize > maxCharacters || current.size >= maxBlocks)
            if (wouldOverflow) {
                result += current
                current = mutableListOf()
                currentCharacters = 0
            }
            current += block
            currentCharacters += blockSize
        }

        if (current.isNotEmpty()) result += current
        return result
    }
}
