package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.epubpro.domain.model.ReaderEngineType
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.MAX_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.MIN_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.TapZoneAction
import com.epubpro.domain.model.TapZoneLayout
import com.epubpro.domain.model.TextAlignment
import com.epubpro.domain.model.defaultTapZoneActions
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ContentFilterRule
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun normalizePageTurnSpeed(speedMs: Int): Int =
    speedMs.coerceIn(MIN_PAGE_TURN_SPEED_MS, MAX_PAGE_TURN_SPEED_MS)

internal fun resolveReadingMode(storedName: String?, legacyHorizontal: Boolean): ReadingMode {
    val parsed = storedName?.let { runCatching { ReadingMode.valueOf(it) }.getOrNull() }
        ?: return if (legacyHorizontal) ReadingMode.FLIP else ReadingMode.SCROLL
    return when (parsed) {
        ReadingMode.SCROLL_HORIZONTAL -> ReadingMode.FLIP
        ReadingMode.CONTINUOUS -> ReadingMode.SCROLL
        else -> parsed
    }
}

@Singleton
class ReaderPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reader_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _filterPreferences = MutableStateFlow(readFilterPreferences())
    val filterPreferences: StateFlow<ContentFilterPreferences> = _filterPreferences.asStateFlow()

    fun isEngineConfigured(): Boolean = prefs.contains(KEY_ENGINE_TYPE)

    fun getSettings(): ReaderSettings = _settings.value

    @Synchronized
    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        saveSettings(transform(_settings.value))
    }

    @Synchronized
    fun saveSettings(settings: ReaderSettings) {
        val readingMode = resolveReadingMode(settings.readingMode.name, settings.isHorizontalPagination)
        val tapZoneActions = settings.tapZoneActions
            .takeIf { it.size == TAP_ZONE_COUNT }
            ?: defaultTapZoneActions(settings.tapZoneLayout)
        val normalized = settings.copy(
            fontFamily = normalizeFontFamily(settings.fontFamily),
            readingMode = readingMode,
            isHorizontalPagination = readingMode == ReadingMode.FLIP,
            tapZoneActions = tapZoneActions,
            pageTurnSpeedMs = normalizePageTurnSpeed(settings.pageTurnSpeedMs),
            brightness = settings.brightness.coerceIn(0.0f, 1.0f)
        )

        prefs.edit()
            .putString(KEY_ENGINE_TYPE, normalized.engineType.name)
            .putFloat(KEY_FONT_SIZE, normalized.fontSizeSp)
            .putString(KEY_FONT_FAMILY, normalized.fontFamily)
            .putFloat(KEY_LINE_HEIGHT, normalized.lineHeightRatio)
            .putInt(KEY_MARGIN_TOP, normalized.marginTopDp)
            .putInt(KEY_MARGIN_BOTTOM, normalized.marginBottomDp)
            .putInt(KEY_MARGIN_LEFT, normalized.marginLeftDp)
            .putInt(KEY_MARGIN_RIGHT, normalized.marginRightDp)
            .putString(KEY_THEME_MODE, normalized.themeMode.name)
            .putBoolean(KEY_IS_HORIZONTAL, normalized.isHorizontalPagination)
            .putString(KEY_READING_MODE, normalized.readingMode.name)
            .putInt(KEY_PARAGRAPH_SPACING, normalized.paragraphSpacingDp)
            .putInt(KEY_FIRST_LINE_INDENT, normalized.firstLineIndentDp)
            .putString(KEY_TEXT_ALIGNMENT, normalized.textAlignment.name)
            .putBoolean(KEY_SHOW_STATUS_BAR, normalized.showStatusBar)
            .putBoolean(KEY_SHOW_SCROLL_BAR, normalized.showScrollBar)
            .putBoolean(KEY_KEEP_SCREEN_ON, normalized.keepScreenOn)
            .putString(KEY_TAP_ZONE_LAYOUT, normalized.tapZoneLayout.name)
            .putString(KEY_TAP_ZONE_ACTIONS, normalized.tapZoneActions.joinToString(",") { it.name })
            .putBoolean(KEY_ENABLE_PAGE_ANIMATION, normalized.enablePageAnimation)
            .putBoolean(KEY_ENABLE_KEYBOARD_NAV, normalized.enableKeyboardNavigation)
            .putBoolean(KEY_ENABLE_VOLUME_KEY_NAV, normalized.enableVolumeKeyNavigation)
            .putInt(KEY_PAGE_TURN_SPEED_MS, normalized.pageTurnSpeedMs)
            .putFloat(KEY_READER_BRIGHTNESS, normalized.brightness)
            .putBoolean(KEY_AUTO_RESUME_LAST_BOOK, normalized.autoResumeLastBookOnStartup)
            .apply()
        _settings.value = normalized
    }

    /**
     * Bật hoặc tắt chế độ tự động mở cuốn sách vừa đọc gần nhất khi khởi động ứng dụng.
     *
     * @param enabled true để mở thẳng vào cuốn sách vừa đọc, false để mở Kệ sách mặc định.
     */
    fun setAutoResumeLastBookOnStartup(enabled: Boolean) {
        updateSettings { it.copy(autoResumeLastBookOnStartup = enabled) }
    }

    private fun readSettings(): ReaderSettings {
        val engineType = enumPreference(KEY_ENGINE_TYPE, ReaderEngineType.WEBVIEW)
        val themeMode = enumPreference(KEY_THEME_MODE, ReaderThemeMode.LIGHT)
        val legacyMarginDp = prefs.getInt(KEY_MARGIN_DP, 16)
        val legacyIsHorizontal = prefs.getBoolean(KEY_IS_HORIZONTAL, true)
        val readingMode = resolveReadingMode(prefs.getString(KEY_READING_MODE, null), legacyIsHorizontal)
        val tapZoneLayout = enumPreference(KEY_TAP_ZONE_LAYOUT, TapZoneLayout.HORIZONTAL)
        val tapZoneActions = prefs.getString(KEY_TAP_ZONE_ACTIONS, null)
            ?.split(',')
            ?.mapNotNull { runCatching { TapZoneAction.valueOf(it) }.getOrNull() }
            ?.takeIf { it.size == TAP_ZONE_COUNT }
            ?: defaultTapZoneActions(tapZoneLayout)

        return ReaderSettings(
            engineType = engineType,
            fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 18f),
            fontFamily = normalizeFontFamily(prefs.getString(KEY_FONT_FAMILY, "serif") ?: "serif"),
            lineHeightRatio = prefs.getFloat(KEY_LINE_HEIGHT, 1.5f),
            marginTopDp = prefs.getInt(KEY_MARGIN_TOP, legacyMarginDp),
            marginBottomDp = prefs.getInt(KEY_MARGIN_BOTTOM, legacyMarginDp),
            marginLeftDp = prefs.getInt(KEY_MARGIN_LEFT, legacyMarginDp),
            marginRightDp = prefs.getInt(KEY_MARGIN_RIGHT, legacyMarginDp),
            themeMode = themeMode,
            isHorizontalPagination = readingMode == ReadingMode.FLIP,
            readingMode = readingMode,
            paragraphSpacingDp = prefs.getInt(KEY_PARAGRAPH_SPACING, 8),
            firstLineIndentDp = prefs.getInt(KEY_FIRST_LINE_INDENT, 0),
            textAlignment = enumPreference(KEY_TEXT_ALIGNMENT, TextAlignment.LEFT),
            showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, true),
            showScrollBar = prefs.getBoolean(KEY_SHOW_SCROLL_BAR, false),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
            tapZoneLayout = tapZoneLayout,
            tapZoneActions = tapZoneActions,
            enablePageAnimation = prefs.getBoolean(KEY_ENABLE_PAGE_ANIMATION, true),
            enableKeyboardNavigation = prefs.getBoolean(KEY_ENABLE_KEYBOARD_NAV, true),
            enableVolumeKeyNavigation = prefs.getBoolean(KEY_ENABLE_VOLUME_KEY_NAV, false),
            pageTurnSpeedMs = normalizePageTurnSpeed(prefs.getInt(KEY_PAGE_TURN_SPEED_MS, 220)),
            brightness = prefs.getFloat(KEY_READER_BRIGHTNESS, 0.5f).coerceIn(0.0f, 1.0f),
            autoResumeLastBookOnStartup = prefs.getBoolean(KEY_AUTO_RESUME_LAST_BOOK, false)
        )
    }

    fun getFilterPreferences(): ContentFilterPreferences = _filterPreferences.value

    @Synchronized
    fun updateFilterPreferences(transform: (ContentFilterPreferences) -> ContentFilterPreferences) {
        saveFilterPreferences(transform(_filterPreferences.value))
    }

    @Synchronized
    fun saveFilterPreferences(filterPreferences: ContentFilterPreferences) {
        val rulesArray = JSONArray()
        filterPreferences.rules.forEach { rule ->
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("pattern", rule.pattern)
                put("replacement", rule.replacement)
                put("isRegex", rule.isRegex)
                put("isEnabled", rule.isEnabled)
            }
            rulesArray.put(obj)
        }

        prefs.edit()
            .putBoolean(KEY_FILTER_ENABLED, filterPreferences.isFilterEnabled)
            .putString(KEY_FILTER_RULES, rulesArray.toString())
            .apply()

        _filterPreferences.value = filterPreferences
    }

    /**
     * Thêm mới hoặc cập nhật một quy tắc lọc/thay thế từ ngữ và tự động kích hoạt tính năng.
     *
     * @param pattern Chuỗi từ khóa hoặc mẫu Regex cần tìm kiếm.
     * @param replacement Chuỗi văn bản mới thay thế (để trống nếu muốn xóa).
     * @param isRegex `true` nếu mẫu tìm kiếm là biểu thức Regex.
     */
    @Synchronized
    fun addOrUpdateFilterRule(
        pattern: String,
        replacement: String = "",
        isRegex: Boolean = false
    ) {
        val normalizedPattern = pattern.trim()
        if (normalizedPattern.isEmpty()) return

        updateFilterPreferences { current ->
            val existing = current.rules.firstOrNull { rule ->
                rule.isRegex == isRegex && rule.pattern.equals(normalizedPattern, ignoreCase = true)
            }

            val updatedRules = if (existing != null) {
                current.rules.map { rule ->
                    if (rule.id == existing.id) {
                        rule.copy(
                            replacement = replacement,
                            isRegex = isRegex,
                            isEnabled = true
                        )
                    } else rule
                }
            } else {
                current.rules + ContentFilterRule(
                    pattern = normalizedPattern,
                    replacement = replacement,
                    isRegex = isRegex,
                    isEnabled = true
                )
            }

            current.copy(
                isFilterEnabled = true,
                rules = updatedRules
            )
        }
    }

    private fun readFilterPreferences(): ContentFilterPreferences {
        val isEnabled = prefs.getBoolean(KEY_FILTER_ENABLED, false)
        val rulesJsonStr = prefs.getString(KEY_FILTER_RULES, "[]") ?: "[]"
        val rulesList = mutableListOf<ContentFilterRule>()

        runCatching {
            val jsonArray = JSONArray(rulesJsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                rulesList.add(
                    ContentFilterRule(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        pattern = obj.optString("pattern"),
                        replacement = obj.optString("replacement", ""),
                        isRegex = obj.optBoolean("isRegex", false),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        }

        return ContentFilterPreferences(
            isFilterEnabled = isEnabled,
            rules = rulesList
        )
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T {
        val name = prefs.getString(key, fallback.name) ?: fallback.name
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }

    private fun normalizeFontFamily(fontFamily: String): String = when (fontFamily.lowercase()) {
        "sans-serif", "sans serif" -> "sans-serif"
        "monospace" -> "monospace"
        else -> "serif"
    }

    companion object {
        private const val TAP_ZONE_COUNT = 9
        private const val KEY_ENGINE_TYPE = "engine_type"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LINE_HEIGHT = "line_height_ratio"
        private const val KEY_MARGIN_DP = "margin_dp"
        private const val KEY_MARGIN_TOP = "margin_top_dp"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom_dp"
        private const val KEY_MARGIN_LEFT = "margin_left_dp"
        private const val KEY_MARGIN_RIGHT = "margin_right_dp"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_IS_HORIZONTAL = "is_horizontal_pagination"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_PARAGRAPH_SPACING = "paragraph_spacing_dp"
        private const val KEY_FIRST_LINE_INDENT = "first_line_indent_dp"
        private const val KEY_TEXT_ALIGNMENT = "text_alignment"
        private const val KEY_SHOW_STATUS_BAR = "show_status_bar"
        private const val KEY_SHOW_SCROLL_BAR = "show_scroll_bar"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_TAP_ZONE_LAYOUT = "tap_zone_layout"
        private const val KEY_TAP_ZONE_ACTIONS = "tap_zone_actions"
        private const val KEY_ENABLE_PAGE_ANIMATION = "enable_page_animation"
        private const val KEY_ENABLE_KEYBOARD_NAV = "enable_keyboard_nav"
        private const val KEY_ENABLE_VOLUME_KEY_NAV = "enable_volume_key_nav"
        private const val KEY_PAGE_TURN_SPEED_MS = "page_turn_speed_ms"
        private const val KEY_READER_BRIGHTNESS = "reader_brightness"
        private const val KEY_AUTO_RESUME_LAST_BOOK = "auto_resume_last_book_on_startup"
        private const val KEY_FILTER_ENABLED = "content_filter_enabled"
        private const val KEY_FILTER_RULES = "content_filter_rules"
    }
}