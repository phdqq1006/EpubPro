package com.epubpro.core.reader.filter

import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule

object ContentSanitizer {

    /**
     * Biên dịch danh sách quy tắc lọc thành một đối tượng [Regex] duy nhất.
     * Trả về null nếu tính năng bị tắt hoặc không có quy tắc nào hợp lệ.
     */
    fun compileCombinedRegex(preferences: ContentFilterPreferences): Regex? {
        if (!preferences.isFilterEnabled || preferences.rules.isEmpty()) return null

        val activeRules = preferences.rules.filter { it.isEnabled && it.pattern.isNotBlank() }
        if (activeRules.isEmpty()) return null

        val patternParts = activeRules.mapNotNull { rule ->
            if (rule.isRegex) {
                runCatching {
                    Regex(rule.pattern)
                    "(?:${rule.pattern})"
                }.getOrNull()
            } else {
                "(?:${Regex.escape(rule.pattern)})"
            }
        }

        if (patternParts.isEmpty()) return null

        val combinedPattern = patternParts.joinToString("|")
        return runCatching {
            Regex(combinedPattern, RegexOption.IGNORE_CASE)
        }.getOrNull()
    }

    /**
     * Làm sạch văn bản: Xóa các từ trùng khớp và chuẩn hóa khoảng trắng thừa.
     */
    fun sanitize(text: String, preferences: ContentFilterPreferences): String {
        if (text.isEmpty() || !preferences.isFilterEnabled) return text

        val regex = compileCombinedRegex(preferences) ?: return text
        val cleaned = text.replace(regex, "")
        
        // Chuẩn hóa khoảng trắng thừa (ví dụ: "anh  em" -> "anh em")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }
}
