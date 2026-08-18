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
}
