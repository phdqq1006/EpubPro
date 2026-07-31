package com.epubpro.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastReadAt: Long
)

data class ReadingProgress(
    val bookId: String,
    val currentCfi: String,
    val chapterIndex: Int,
    val pageIndex: Int = 1,
    val progressPercentage: Float
)

data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cfi: String,
    val createdAt: Long
)

data class Highlight(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val startCfi: String,
    val endCfi: String,
    val selectedText: String,
    val colorHex: String,
    val note: String?,
    val createdAt: Long
)

data class SearchResultItem(
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val cfi: String? = null
)

enum class ReaderEngineType {
    WEBVIEW,
    READIUM
}

enum class ReaderThemeMode {
    LIGHT,
    DARK,
    SEPIA,
    OLED,
    MIDNIGHT
}

data class ReaderSettings(
    val engineType: ReaderEngineType = ReaderEngineType.WEBVIEW,
    val fontSizeSp: Float = 18f,
    val fontFamily: String = "Serif",
    val lineHeightRatio: Float = 1.5f,
    val marginTopDp: Int = 16,
    val marginBottomDp: Int = 16,
    val marginLeftDp: Int = 16,
    val marginRightDp: Int = 16,
    val themeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
    val isHorizontalPagination: Boolean = false
) {
    val marginDp: Int get() = (marginLeftDp + marginRightDp) / 2
}
