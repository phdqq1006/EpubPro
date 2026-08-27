package com.epubpro.core.reader.filter

import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentSanitizerTest {

    @Test
    fun sanitize_disabledFilter_returnsOriginalText() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = false,
            rules = listOf(ContentFilterRule(pattern = "xấu"))
        )
        val input = "Đây là một từ xấu trong đoạn văn."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals(input, result)
    }

    @Test
    fun sanitize_plainWordsCaseInsensitive_removesWordsAndNormalizesSpace() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "Xấu"),
                ContentFilterRule(pattern = "quảng cáo")
            )
        )
        val input = "Đoạn văn có từ XẤU và chứa Quảng Cáo của trang web."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Đoạn văn có từ và chứa của trang web.", result)
    }

    @Test
    fun sanitize_regexRule_removesMatchingPattern() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "www\\.[a-z0-9]+\\.com", isRegex = true)
            )
        )
        val input = "Mời ghé thăm www.abc123.com để biết thêm chi tiết."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Mời ghé thăm để biết thêm chi tiết.", result)
    }

    @Test
    fun sanitize_invalidRegexRule_skipsInvalidRuleGracefully() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "[a-z", isRegex = true), // Lỗi cú pháp Regex (thiếu ])
                ContentFilterRule(pattern = "bài viết")
            )
        )
        val input = "Đây là bài viết hay."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Đây là hay.", result)
    }

    @Test
    fun sanitize_textReplacement_replacesWithNewText() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "tu sĩ", replacement = "pháp sư"),
                ContentFilterRule(pattern = "câu này rất dài và phức tạp", replacement = "câu ngắn")
            )
        )
        val input = "Vị Tu Sĩ nói rằng câu này rất dài và phức tạp trong sách."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Vị pháp sư nói rằng câu ngắn trong sách.", result)
    }

    @Test
    fun sanitize_mixedReplacementAndDeletion_appliesAllRulesCorrectly() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "quảng cáo", replacement = ""), // Xóa
                ContentFilterRule(pattern = "ba", replacement = "bố") // Thay thế
            )
        )
        val input = "Đây là quảng cáo của ba tôi."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Đây là của bố tôi.", result)
    }

    @Test
    fun sanitize_replacementWithSpecialTokens_treatsAsLiteral() {
        val prefs = ContentFilterPreferences(
            isFilterEnabled = true,
            rules = listOf(
                ContentFilterRule(pattern = "giá tiền", replacement = "$100 USD"),
                ContentFilterRule(pattern = "kí hiệu", replacement = "$& hoặc $1")
            )
        )
        val input = "Sách có giá tiền và kí hiệu đặc biệt."
        val result = ContentSanitizer.sanitize(input, prefs)
        assertEquals("Sách có $100 USD và $& hoặc $1 đặc biệt.", result)
    }
}
