package com.epubpro.feature.profile

import androidx.lifecycle.ViewModel
import com.epubpro.core.storage.ReaderPreferencesManager
import com.epubpro.domain.model.FontPreset
import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.TextAlignment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReadingDefaultsViewModel @Inject constructor(
    private val preferencesManager: ReaderPreferencesManager
) : ViewModel() {

    private val _settings = MutableStateFlow(preferencesManager.getSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private fun update(newSettings: ReaderSettings) {
        _settings.value = newSettings
        preferencesManager.saveSettings(newSettings)
    }

    fun resetToDefault() = update(ReaderSettings())

    fun applyPreset(preset: ReadingPreset) {
        val current = _settings.value
        val new = when (preset) {
            ReadingPreset.DEFAULT -> current.copy(
                fontSizeSp = 18f,
                fontFamily = "Serif",
                lineHeightRatio = 1.5f,
                marginLeftDp = 16,
                marginRightDp = 16,
                marginTopDp = 16,
                marginBottomDp = 16,
            )
            ReadingPreset.COMPACT -> current.copy(
                fontSizeSp = 16f,
                fontFamily = "Serif",
                lineHeightRatio = 1.3f,
                marginLeftDp = 12,
                marginRightDp = 12,
                marginTopDp = 12,
                marginBottomDp = 12,
            )
            ReadingPreset.COMFORTABLE -> current.copy(
                fontSizeSp = 20f,
                fontFamily = "Serif",
                lineHeightRatio = 1.8f,
                marginLeftDp = 24,
                marginRightDp = 24,
                marginTopDp = 24,
                marginBottomDp = 24,
            )
            ReadingPreset.READER -> current.copy(
                fontSizeSp = 17f,
                fontFamily = "Georgia",
                lineHeightRatio = 1.6f,
                marginLeftDp = 20,
                marginRightDp = 20,
                marginTopDp = 20,
                marginBottomDp = 20,
            )
        }
        update(new)
    }

    fun setFontSize(sp: Float) = update(_settings.value.copy(fontSizeSp = sp))
    fun setLineHeight(ratio: Float) = update(_settings.value.copy(lineHeightRatio = ratio))
    fun setFontFamily(family: String) = update(_settings.value.copy(fontFamily = family))
    fun setThemeMode(mode: ReaderThemeMode) = update(_settings.value.copy(themeMode = mode))
    fun setMarginHorizontal(dp: Int) = update(_settings.value.copy(marginLeftDp = dp, marginRightDp = dp))
    fun setMarginVertical(dp: Int) = update(_settings.value.copy(marginTopDp = dp, marginBottomDp = dp))
    fun setParagraphSpacing(dp: Int) = update(_settings.value.copy(paragraphSpacingDp = dp))
    fun setFirstLineIndent(dp: Int) = update(_settings.value.copy(firstLineIndentDp = dp))
    fun setTextAlignment(alignment: TextAlignment) = update(_settings.value.copy(textAlignment = alignment))
    fun setReadingMode(mode: ReadingMode) {
        val isHorizontal = mode == ReadingMode.FLIP || mode == ReadingMode.SCROLL_HORIZONTAL
        update(_settings.value.copy(readingMode = mode, isHorizontalPagination = isHorizontal))
    }
    fun setShowStatusBar(show: Boolean) = update(_settings.value.copy(showStatusBar = show))
    fun setShowScrollBar(show: Boolean) = update(_settings.value.copy(showScrollBar = show))
    fun setKeepScreenOn(keep: Boolean) = update(_settings.value.copy(keepScreenOn = keep))
    fun setTapZoneLayout(layout: com.epubpro.domain.model.TapZoneLayout) = update(_settings.value.copy(tapZoneLayout = layout))
    fun setPageAnimation(enabled: Boolean) = update(_settings.value.copy(enablePageAnimation = enabled))
    fun setKeyboardNavigation(enabled: Boolean) = update(_settings.value.copy(enableKeyboardNavigation = enabled))
    fun setVolumeKeyNavigation(enabled: Boolean) = update(_settings.value.copy(enableVolumeKeyNavigation = enabled))
}

enum class ReadingPreset(val label: String) {
    DEFAULT("Mặc định"),
    COMPACT("Gọn"),
    COMFORTABLE("Thoải mái"),
    READER("R")
}
