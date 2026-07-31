package com.epubpro.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.epubpro.domain.model.ReaderEngineType
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
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
        val isHorizontal = prefs.getBoolean(KEY_IS_HORIZONTAL, true)

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
            isHorizontalPagination = isHorizontal
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
    }
}
