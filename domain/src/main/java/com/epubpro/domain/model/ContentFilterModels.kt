package com.epubpro.domain.model

import java.util.UUID

private val JAVA_ONLY_REGEX_MARKERS = listOf("\\Q", "\\E", "\\R", "\\X", "\\N{", "(?>")
private val JAVA_INLINE_FLAGS_REGEX = Regex("""\(\?[imsudx-]+:?""")
private val POSSESSIVE_QUANTIFIER_REGEX = Regex("""(?:[*+?]|\{\d+(?:,\d*)?\})\+""")

/**
 * Kiểm tra Regex có thể chạy nhất quán trên Kotlin và JavaScript WebView.
 *
 * @param pattern Mẫu Regex cần kiểm tra.
 * @return `true` nếu mẫu hợp lệ và không dùng cú pháp Java Regex không có trong JavaScript.
 */
fun isJavaScriptCompatibleRegex(pattern: String): Boolean {
    if (runCatching { Regex(pattern) }.isFailure) return false
    if (JAVA_ONLY_REGEX_MARKERS.any(pattern::contains)) return false
    if (JAVA_INLINE_FLAGS_REGEX.containsMatchIn(pattern)) return false
    if (POSSESSIVE_QUANTIFIER_REGEX.containsMatchIn(pattern)) return false
    if (pattern.contains("\\p{") || pattern.contains("\\P{")) return false
    return true
}

/**
 * Quy tắc lọc & thay thế từ khóa / mẫu văn bản.
 *
 * @param id Định danh duy nhất của quy tắc.
 * @param pattern Mẫu văn bản hoặc biểu thức Regex cần tìm kiếm.
 * @param replacement Chuỗi văn bản mới thay thế. Nếu để trống ("") tương đương với xóa bỏ.
 * @param isRegex `true` nếu mẫu tìm kiếm là biểu thức chính quy (Regex).
 * @param isEnabled `true` nếu quy tắc đang được kích hoạt sử dụng.
 */
data class ContentFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val replacement: String = "",
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true
)

/**
 * Cấu hình lọc nội dung của ứng dụng
 */
data class ContentFilterPreferences(
    val isFilterEnabled: Boolean = false,
    val rules: List<ContentFilterRule> = emptyList()
)
