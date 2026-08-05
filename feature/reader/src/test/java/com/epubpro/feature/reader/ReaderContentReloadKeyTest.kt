package com.epubpro.feature.reader

import com.epubpro.domain.model.ReaderEngineType
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.TapZoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderContentReloadKeyTest {
    @Test
    fun runtimeOnlySettingsDoNotReloadChapterHtml() {
        val base = ReaderSettings()
        val expectedKey = base.contentReloadKey()
        val runtimeVariants = listOf(
            base.copy(engineType = ReaderEngineType.READIUM),
            base.copy(pageTurnSpeedMs = 450),
            base.copy(enablePageAnimation = false),
            base.copy(enableKeyboardNavigation = false),
            base.copy(enableVolumeKeyNavigation = true),
            base.copy(showStatusBar = false),
            base.copy(keepScreenOn = true),
            base.copy(tapZoneActions = List(9) { TapZoneAction.NEXT_PAGE })
        )

        runtimeVariants.forEach { settings ->
            assertEquals(expectedKey, settings.contentReloadKey())
        }
    }

    @Test
    fun renderingSettingsReloadChapterHtml() {
        val base = ReaderSettings()
        val expectedKey = base.contentReloadKey()
        val renderingVariants = listOf(
            base.copy(fontSizeSp = 22f),
            base.copy(fontFamily = "sans-serif"),
            base.copy(themeMode = ReaderThemeMode.DARK),
            base.copy(marginLeftDp = 32),
            base.copy(paragraphSpacingDp = 16),
            base.copy(isHorizontalPagination = false),
            base.copy(showScrollBar = true)
        )

        renderingVariants.forEach { settings ->
            assertNotEquals(expectedKey, settings.contentReloadKey())
        }
    }
}