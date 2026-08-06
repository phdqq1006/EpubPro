package com.epubpro.domain.model

import java.util.UUID

/**
 * Quy tắc lọc từ khóa / mẫu văn bản
 */
data class ContentFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
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
