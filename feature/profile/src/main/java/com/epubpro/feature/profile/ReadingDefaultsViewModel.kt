package com.epubpro.feature.profile

import androidx.lifecycle.ViewModel
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.TapZoneAction
import com.epubpro.domain.model.TapZoneLayout
import com.epubpro.domain.model.TextAlignment
import com.epubpro.domain.model.defaultTapZoneActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ReadingDefaultsViewModel @Inject constructor(
    private val preferencesManager: ReaderPreferencesManager
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = preferencesManager.settings

    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        preferencesManager.updateSettings(transform)
    }

    fun resetToDefault() = preferencesManager.saveSettings(ReaderSettings())

    fun applyPreset(preset: ReadingPreset) = update { current ->
        when (preset) {
            ReadingPreset.DEFAULT -> current.copy(
                fontSizeSp = 18f,
                fontFamily = "serif",
                lineHeightRatio = 1.5f,
                marginLeftDp = 16,
                marginRightDp = 16,
                marginTopDp = 16,
                marginBottomDp = 16
            )
            ReadingPreset.COMPACT -> current.copy(
                fontSizeSp = 16f,
                fontFamily = "serif",
                lineHeightRatio = 1.3f,
                marginLeftDp = 12,
                marginRightDp = 12,
                marginTopDp = 12,
                marginBottomDp = 12
            )
            ReadingPreset.COMFORTABLE -> current.copy(
                fontSizeSp = 20f,
                fontFamily = "serif",
                lineHeightRatio = 1.8f,
                marginLeftDp = 24,
                marginRightDp = 24,
                marginTopDp = 24,
                marginBottomDp = 24
            )
            ReadingPreset.READER -> current.copy(
                fontSizeSp = 17f,
                fontFamily = "serif",
                lineHeightRatio = 1.6f,
                marginLeftDp = 20,
                marginRightDp = 20,
                marginTopDp = 20,
                marginBottomDp = 20
            )
        }
    }

    fun setFontSize(sp: Float) = update { it.copy(fontSizeSp = sp) }
    fun setLineHeight(ratio: Float) = update { it.copy(lineHeightRatio = ratio) }
    fun setFontFamily(family: String) = update { it.copy(fontFamily = family) }
    fun setThemeMode(mode: ReaderThemeMode) = update { it.copy(themeMode = mode) }
    fun setMarginHorizontal(dp: Int) = update { it.copy(marginLeftDp = dp, marginRightDp = dp) }
    fun setMarginVertical(dp: Int) = update { it.copy(marginTopDp = dp, marginBottomDp = dp) }
    fun setParagraphSpacing(dp: Int) = update { it.copy(paragraphSpacingDp = dp) }
    fun setFirstLineIndent(dp: Int) = update { it.copy(firstLineIndentDp = dp) }
    fun setTextAlignment(alignment: TextAlignment) = update { it.copy(textAlignment = alignment) }
    fun setReadingMode(mode: ReadingMode) = update {
        val supportedMode = if (mode == ReadingMode.FLIP) ReadingMode.FLIP else ReadingMode.SCROLL
        it.copy(readingMode = supportedMode, isHorizontalPagination = supportedMode == ReadingMode.FLIP)
    }
    fun setShowStatusBar(show: Boolean) = update { it.copy(showStatusBar = show) }
    fun setShowScrollBar(show: Boolean) = update { it.copy(showScrollBar = show) }
    fun setKeepScreenOn(keep: Boolean) = update { it.copy(keepScreenOn = keep) }
    fun setTapZoneLayout(layout: TapZoneLayout) = update {
        it.copy(tapZoneLayout = layout, tapZoneActions = defaultTapZoneActions(layout))
    }
    fun setTapZoneActions(actions: List<TapZoneAction>) = update {
        if (actions.size == 9) it.copy(tapZoneActions = actions) else it
    }
    fun setPageAnimation(enabled: Boolean) = update { it.copy(enablePageAnimation = enabled) }
    fun setPageTurnSpeed(speedMs: Int) = update { it.copy(pageTurnSpeedMs = speedMs) }
    fun setKeyboardNavigation(enabled: Boolean) = update { it.copy(enableKeyboardNavigation = enabled) }
    fun setVolumeKeyNavigation(enabled: Boolean) = update { it.copy(enableVolumeKeyNavigation = enabled) }

    /**
     * Cập nhật mức độ sáng đọc sách mặc định (từ 0.0f đến 1.0f).
     *
     * @param brightness Mức độ sáng người dùng thiết lập trong dải 0.0f đến 1.0f.
     */
    fun setBrightness(brightness: Float) = update { it.copy(brightness = brightness.coerceIn(0.0f, 1.0f)) }
}

enum class ReadingPreset(val label: String) {
    DEFAULT("Mặc định"),
    COMPACT("Gọn"),
    COMFORTABLE("Thoải mái"),
    READER("Đọc sách")
}