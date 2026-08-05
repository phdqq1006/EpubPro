package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.epubpro.domain.model.ReaderEngineType
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.TapZoneLayout
import com.epubpro.domain.model.TextAlignment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("reader_settings_prefs", Context.MODE_PRIVATE)

    fun isEngineConfigured(): Boolean {
        return prefs.contains(KEY_ENGINE_TYPE)
    }

    fun getSettings(): ReaderSettings {
        val engineTypeName = prefs.getString(KEY_ENGINE_TYPE, ReaderEngineType.WEBVIEW.name) ?: ReaderEngineType.WEBVIEW.name
        val engineType = try {
            ReaderEngineType.valueOf(engineTypeName)
        } catch (e: Exception) {
            ReaderEngineType.WEBVIEW
        }
        val fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 18f)
        val fontFamily = prefs.getString(KEY_FONT_FAMILY, "Serif") ?: "Serif"
        val lineHeightRatio = prefs.getFloat(KEY_LINE_HEIGHT, 1.5f)
        val legacyMarginDp = prefs.getInt(KEY_MARGIN_DP, 16)
        val marginTopDp = prefs.getInt(KEY_MARGIN_TOP, legacyMarginDp)
        val marginBottomDp = prefs.getInt(KEY_MARGIN_BOTTOM, legacyMarginDp)
        val marginLeftDp = prefs.getInt(KEY_MARGIN_LEFT, legacyMarginDp)
        val marginRightDp = prefs.getInt(KEY_MARGIN_RIGHT, legacyMarginDp)

        val themeModeName = prefs.getString(KEY_THEME_MODE, ReaderThemeMode.LIGHT.name) ?: ReaderThemeMode.LIGHT.name
        val themeMode = try {
            ReaderThemeMode.valueOf(themeModeName)
        } catch (e: Exception) {
            ReaderThemeMode.LIGHT
        }
        val legacyIsHorizontal = prefs.getBoolean(KEY_IS_HORIZONTAL, true)

        // Extended fields
        val readingModeName = prefs.getString(KEY_READING_MODE, ReadingMode.FLIP.name) ?: ReadingMode.FLIP.name
        val readingMode = try {
            ReadingMode.valueOf(readingModeName)
        } catch (e: Exception) {
            ReadingMode.FLIP
        }
        val isHorizontal = if (prefs.contains(KEY_READING_MODE)) {
            readingMode == ReadingMode.FLIP || readingMode == ReadingMode.SCROLL_HORIZONTAL
        } else {
            legacyIsHorizontal
        }

        val textAlignmentName = prefs.getString(KEY_TEXT_ALIGNMENT, TextAlignment.LEFT.name) ?: TextAlignment.LEFT.name
        val textAlignment = try {
            TextAlignment.valueOf(textAlignmentName)
        } catch (e: Exception) {
            TextAlignment.LEFT
        }

        val paragraphSpacingDp = prefs.getInt(KEY_PARAGRAPH_SPACING, 8)
        val firstLineIndentDp = prefs.getInt(KEY_FIRST_LINE_INDENT, 0)
        val showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, true)
        val showScrollBar = prefs.getBoolean(KEY_SHOW_SCROLL_BAR, false)
        val keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)

        val tapZoneLayoutName = prefs.getString(KEY_TAP_ZONE_LAYOUT, TapZoneLayout.HORIZONTAL.name) ?: TapZoneLayout.HORIZONTAL.name
        val tapZoneLayout = try {
            TapZoneLayout.valueOf(tapZoneLayoutName)
        } catch (e: Exception) {
            TapZoneLayout.HORIZONTAL
        }

        val enablePageAnimation = prefs.getBoolean(KEY_ENABLE_PAGE_ANIMATION, true)
        val enableKeyboardNavigation = prefs.getBoolean(KEY_ENABLE_KEYBOARD_NAV, true)
        val enableVolumeKeyNavigation = prefs.getBoolean(KEY_ENABLE_VOLUME_KEY_NAV, false)
        val pageTurnSpeedMs = prefs.getInt(KEY_PAGE_TURN_SPEED_MS, 220)

        return ReaderSettings(
            engineType = engineType,
            fontSizeSp = fontSizeSp,
            fontFamily = fontFamily,
            lineHeightRatio = lineHeightRatio,
            marginTopDp = marginTopDp,
            marginBottomDp = marginBottomDp,
            marginLeftDp = marginLeftDp,
            marginRightDp = marginRightDp,
            themeMode = themeMode,
            isHorizontalPagination = isHorizontal,
            readingMode = readingMode,
            paragraphSpacingDp = paragraphSpacingDp,
            firstLineIndentDp = firstLineIndentDp,
            textAlignment = textAlignment,
            showStatusBar = showStatusBar,
            showScrollBar = showScrollBar,
            keepScreenOn = keepScreenOn,
            tapZoneLayout = tapZoneLayout,
            enablePageAnimation = enablePageAnimation,
            enableKeyboardNavigation = enableKeyboardNavigation,
            enableVolumeKeyNavigation = enableVolumeKeyNavigation,
            pageTurnSpeedMs = pageTurnSpeedMs,
        )
    }

    fun saveSettings(settings: ReaderSettings) {
        prefs.edit()
            .putString(KEY_ENGINE_TYPE, settings.engineType.name)
            .putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
            .putString(KEY_FONT_FAMILY, settings.fontFamily)
            .putFloat(KEY_LINE_HEIGHT, settings.lineHeightRatio)
            .putInt(KEY_MARGIN_TOP, settings.marginTopDp)
            .putInt(KEY_MARGIN_BOTTOM, settings.marginBottomDp)
            .putInt(KEY_MARGIN_LEFT, settings.marginLeftDp)
            .putInt(KEY_MARGIN_RIGHT, settings.marginRightDp)
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_IS_HORIZONTAL, settings.isHorizontalPagination)
            // Extended fields
            .putString(KEY_READING_MODE, settings.readingMode.name)
            .putInt(KEY_PARAGRAPH_SPACING, settings.paragraphSpacingDp)
            .putInt(KEY_FIRST_LINE_INDENT, settings.firstLineIndentDp)
            .putString(KEY_TEXT_ALIGNMENT, settings.textAlignment.name)
            .putBoolean(KEY_SHOW_STATUS_BAR, settings.showStatusBar)
            .putBoolean(KEY_SHOW_SCROLL_BAR, settings.showScrollBar)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putString(KEY_TAP_ZONE_LAYOUT, settings.tapZoneLayout.name)
            .putBoolean(KEY_ENABLE_PAGE_ANIMATION, settings.enablePageAnimation)
            .putBoolean(KEY_ENABLE_KEYBOARD_NAV, settings.enableKeyboardNavigation)
            .putBoolean(KEY_ENABLE_VOLUME_KEY_NAV, settings.enableVolumeKeyNavigation)
            .putInt(KEY_PAGE_TURN_SPEED_MS, settings.pageTurnSpeedMs)
            .apply()
    }

    companion object {
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
        // Extended keys
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_PARAGRAPH_SPACING = "paragraph_spacing_dp"
        private const val KEY_FIRST_LINE_INDENT = "first_line_indent_dp"
        private const val KEY_TEXT_ALIGNMENT = "text_alignment"
        private const val KEY_SHOW_STATUS_BAR = "show_status_bar"
        private const val KEY_SHOW_SCROLL_BAR = "show_scroll_bar"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_TAP_ZONE_LAYOUT = "tap_zone_layout"
        private const val KEY_ENABLE_PAGE_ANIMATION = "enable_page_animation"
        private const val KEY_ENABLE_KEYBOARD_NAV = "enable_keyboard_nav"
        private const val KEY_ENABLE_VOLUME_KEY_NAV = "enable_volume_key_nav"
        private const val KEY_PAGE_TURN_SPEED_MS = "page_turn_speed_ms"
    }
}
