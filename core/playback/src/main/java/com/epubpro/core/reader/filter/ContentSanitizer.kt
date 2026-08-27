package com.epubpro.core.reader.filter

import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule

/**
 * Đối tượng xử lý làm sạch và thay thế từ ngữ trong văn bản cho TTS Engine.
 */
object ContentSanitizer {

    /**
     * Biên dịch danh sách quy tắc lọc thành một đối tượng [Regex] duy nhất để phát hiện nhanh.
     * Trả về null nếu tính năng bị tắt hoặc không có quy tắc nào hợp lệ.
     *
     * @param preferences Cấu hình lọc và thay thế.
     * @return Biểu thức [Regex] tổng hợp các quy tắc đang bật.
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
     * Xử lý làm sạch và thay thế từ ngữ trong văn bản theo cấu hình:
     * Thay thế các từ trùng khớp theo [ContentFilterRule.replacement] và chuẩn hóa khoảng trắng thừa.
     *
     * @param text Văn bản gốc cần xử lý.
     * @param preferences Cấu hình lọc và thay thế nội dung.
     * @return Văn bản sau khi đã áp dụng các quy tắc thay thế.
     */
    fun sanitize(text: String, preferences: ContentFilterPreferences): String {
        if (text.isEmpty() || !preferences.isFilterEnabled || preferences.rules.isEmpty()) return text

        val activeRules = preferences.rules.filter { it.isEnabled && it.pattern.isNotBlank() }
        if (activeRules.isEmpty()) return text

        var result = text
        for (rule in activeRules) {
            val regex = if (rule.isRegex) {
                runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE) }.getOrNull()
            } else {
                runCatching { Regex(Regex.escape(rule.pattern), RegexOption.IGNORE_CASE) }.getOrNull()
            }
            if (regex != null) {
                result = result.replace(regex) { rule.replacement }
            }
        }

        // Chuẩn hóa khoảng trắng thừa (ví dụ: "anh  em" -> "anh em")
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
