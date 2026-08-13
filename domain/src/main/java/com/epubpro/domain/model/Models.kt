package com.epubpro.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val filePath: String,
    val addedAt: Long,
    val lastReadAt: Long,
    val totalChapters: Int = 0
)

data class ReadingProgress(
    val bookId: String,
    val currentCfi: String,
    val chapterIndex: Int,
    val pageIndex: Int = 1,
    val progressPercentage: Float,
    val totalChapters: Int = 0
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

const val MIN_PAGE_TURN_SPEED_MS = 100
const val MAX_PAGE_TURN_SPEED_MS = 600
val PAGE_TURN_SPEED_PRESETS_MS = listOf(120, 220, 450)

enum class ReaderThemeMode {
    LIGHT,
    DARK,
    SEPIA,
    PAPER,
    MIDNIGHT
}

/** Chế độ đọc sách */
enum class ReadingMode {
    SCROLL,             // Cuộn dọc
    SCROLL_HORIZONTAL,  // Cuộn ngang
    FLIP,               // Lật trang (horizontal pagination)
    CONTINUOUS          // Cuốn liên tục
}

/** Căn chỉnh văn bản */
enum class TextAlignment {
    LEFT,
    JUSTIFY
}

/** Preset phông chữ */
enum class FontPreset(val fontFamily: String, val displayName: String) {
    SERIF("serif", "Có chân"),
    SANS_SERIF("sans-serif", "Không chân"),
    MONOSPACE("monospace", "Đơn cách")
}
/** Layout vùng chạm lật trang */
enum class TapZoneLayout(val displayName: String) {
    HORIZONTAL("Vùng chạm ngang"),
    VERTICAL("Vùng chạm dọc"),
    BOTTOM_SPLIT("Vùng chạm chia trên dưới")
}

/** Hành động khi chạm vào vùng màn hình */
enum class TapZoneAction(val label: String) {
    PREV_PAGE("Trang trước"),
    NEXT_PAGE("Trang sau"),
    TOGGLE_CONTROLS("Hiện/Ẩn Menu")
}

fun defaultTapZoneActions(layout: TapZoneLayout): List<TapZoneAction> = when (layout) {
    TapZoneLayout.HORIZONTAL -> List(9) { index ->
        when (index % 3) {
            0 -> TapZoneAction.PREV_PAGE
            2 -> TapZoneAction.NEXT_PAGE
            else -> TapZoneAction.TOGGLE_CONTROLS
        }
    }
    TapZoneLayout.VERTICAL -> List(9) { index ->
        when (index / 3) {
            0 -> TapZoneAction.PREV_PAGE
            2 -> TapZoneAction.NEXT_PAGE
            else -> TapZoneAction.TOGGLE_CONTROLS
        }
    }
    TapZoneLayout.BOTTOM_SPLIT -> List(9) { index ->
        when {
            index < 6 -> TapZoneAction.TOGGLE_CONTROLS
            index == 6 -> TapZoneAction.PREV_PAGE
            index == 8 -> TapZoneAction.NEXT_PAGE
            else -> TapZoneAction.TOGGLE_CONTROLS
        }
    }
}
data class ReaderSettings(
    val engineType: ReaderEngineType = ReaderEngineType.WEBVIEW,
    val fontSizeSp: Float = 18f,
    val fontFamily: String = "serif",
    val lineHeightRatio: Float = 1.5f,
    val marginTopDp: Int = 16,
    val marginBottomDp: Int = 16,
    val marginLeftDp: Int = 16,
    val marginRightDp: Int = 16,
    val themeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
    val isHorizontalPagination: Boolean = true,
    // Extended fields for Reading Defaults screen
    val readingMode: ReadingMode = ReadingMode.FLIP,
    val paragraphSpacingDp: Int = 8,
    val firstLineIndentDp: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val showStatusBar: Boolean = true,
    val showScrollBar: Boolean = false,
    val keepScreenOn: Boolean = false,
    // Extended fields for Page Turn Control
    val tapZoneLayout: TapZoneLayout = TapZoneLayout.HORIZONTAL,
    val tapZoneActions: List<TapZoneAction> = defaultTapZoneActions(TapZoneLayout.HORIZONTAL),
    val enablePageAnimation: Boolean = true,
    val enableKeyboardNavigation: Boolean = true,
    val enableVolumeKeyNavigation: Boolean = false,
    val pageTurnSpeedMs: Int = 220
) {
    val marginDp: Int get() = (marginLeftDp + marginRightDp) / 2
}
