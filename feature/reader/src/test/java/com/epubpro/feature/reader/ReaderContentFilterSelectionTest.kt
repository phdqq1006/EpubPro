package com.epubpro.feature.reader

import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentFilterSelectionTest {

    /** Kiểm tra selection mới tạo rule literal đã bật và tự động bật content filter. */
    @Test
    fun selectedText_addsEnabledLiteralRuleAndEnablesFilter() {
        val original = ContentFilterPreferences()

        val updated = original.withEnabledLiteralRule("  quảng cáo  ")

        assertTrue(updated.isFilterEnabled)
        assertEquals(1, updated.rules.size)
        assertEquals("quảng cáo", updated.rules.single().pattern)
        assertFalse(updated.rules.single().isRegex)
        assertTrue(updated.rules.single().isEnabled)
    }

    /** Kiểm tra rule literal trùng đang tắt được bật lại mà không tạo bản ghi mới. */
    @Test
    fun existingDisabledLiteralRule_isReenabledWithoutCreatingDuplicate() {
        val existingRule = ContentFilterRule(
            id = "existing-rule",
            pattern = "Quảng Cáo",
            isEnabled = false
        )
        val original = ContentFilterPreferences(
            isFilterEnabled = false,
            rules = listOf(existingRule)
        )

        val updated = original.withEnabledLiteralRule("quảng cáo")

        assertTrue(updated.isFilterEnabled)
        assertEquals(1, updated.rules.size)
        assertEquals("existing-rule", updated.rules.single().id)
        assertTrue(updated.rules.single().isEnabled)
    }

    /** Kiểm tra selection chỉ có khoảng trắng không làm thay đổi cấu hình hiện tại. */
    @Test
    fun blankSelectedText_keepsPreferencesUnchanged() {
        val original = ContentFilterPreferences(
            isFilterEnabled = false,
            rules = listOf(ContentFilterRule(pattern = "đã có"))
        )

        val updated = original.withEnabledLiteralRule("   ")

        assertSame(original, updated)
    }
}
