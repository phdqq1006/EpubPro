package com.epubpro.core.reader.style

import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.TextAlignment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CssInjectorTest {

    @Test
    fun `generateMetaAndViewport returns valid non-scalable viewport`() {
        val meta = CssInjector.generateMetaAndViewport()
        assertTrue(meta.contains("name=\"viewport\""))
        assertTrue(meta.contains("user-scalable=no"))
    }

    @Test
    fun `generateCss generates accurate theme colors and fractional font sizes`() {
        val sepiaSettings = ReaderSettings(
            themeMode = ReaderThemeMode.SEPIA,
            fontSizeSp = 17.5f,
            textAlignment = TextAlignment.JUSTIFY,
            lineHeightRatio = 1.6f
        )

        val css = CssInjector.generateCss(sepiaSettings)
        assertTrue("Should contain Sepia background", css.contains("#FBF0D9"))
        assertTrue("Should contain Sepia text color", css.contains("#4A3B32"))
        assertTrue("Should contain exact 17.5px font size", css.contains("17.5px"))
        assertTrue("Should contain text-align justify", css.contains("text-align: justify"))
    }

    @Test
    fun `generateCss for horizontal pagination applies strict multi-column math`() {
        val horizontalSettings = ReaderSettings(
            readingMode = ReadingMode.FLIP,
            isHorizontalPagination = true
        )

        val css = CssInjector.generateCss(horizontalSettings)
        assertTrue(css.contains("column-width:"))
        assertTrue(css.contains("column-gap:"))
        assertTrue(css.contains("overflow: hidden !important"))
    }

    @Test
    fun `generateCss for vertical scroll mode applies proper scrollable overflow`() {
        val scrollSettings = ReaderSettings(
            readingMode = ReadingMode.SCROLL,
            isHorizontalPagination = false
        )

        val css = CssInjector.generateCss(scrollSettings)
        assertTrue(css.contains("overflow-y: auto !important"))
        assertTrue(css.contains("overflow-x: hidden !important"))
        assertFalse(css.contains("column-width:"))
    }

    @Test
    fun generateJsBridgeScriptKeepsChapterNavigationEnabledWithoutLoadedPreview() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = ReaderSettings(),
            previousChapterHtml = null,
            nextChapterHtml = null,
            hasPreviousChapter = true,
            hasNextChapter = true
        )

        assertTrue(script.contains("var hasNextChapter = true;"))
        assertTrue(script.contains("var hasPreviousChapter = true;"))
        assertTrue(script.contains("var boundaryTriggered = hasAdjacentChapter && crossedBoundaryThreshold;"))
    }

    @Test
    fun generateJsBridgeScriptBlocksNavigationAtRealChapterBoundary() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = ReaderSettings(),
            hasPreviousChapter = false,
            hasNextChapter = false
        )

        assertTrue(script.contains("var hasNextChapter = false;"))
        assertTrue(script.contains("var hasPreviousChapter = false;"))
    }

    /**
     * Ki?m tra thao t?c ch?m ? c?nh trang cu?i c?ng ?i qua animation boundary
     * thay v? g?i callback native b? qua hi?u ?ng.
     */
    @Test
    fun generateJsBridgeScriptAnimatesTapAtChapterBoundary() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = ReaderSettings(),
            hasNextChapter = true,
            hasPreviousChapter = true
        )

        assertTrue(script.contains("function animateChapterBoundary(direction)"))
        assertTrue(script.contains("animateChapterBoundary(1);"))
        assertTrue(script.contains("animateChapterBoundary(-1);"))
    }

    @Test
    fun generateJsBridgeScriptProducesValidJsBridgeApiDefinitions() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            initialPage = 1,
            settings = ReaderSettings()
        )

        assertTrue(script.contains("window.epubproHighlightTtsParagraph"))
        assertTrue(script.contains("window.epubproApplyContentFilter"))
        assertTrue(script.contains("window.epubproIsHorizontal"))
    }
}
