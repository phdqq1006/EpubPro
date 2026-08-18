package com.epubpro.core.reader.style

import com.epubpro.domain.model.ReaderSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CssInjectorHorizontalPaginationTest {

    private val asymmetricSettings = ReaderSettings(
        isHorizontalPagination = true,
        marginLeftDp = 24,
        marginRightDp = 12
    )

    @Test
    fun horizontalCssUsesOneViewportPageGeometryWithAsymmetricMargins() {
        val css = CssInjector.generateCss(asymmetricSettings)

        assertTrue(css.contains("padding-left: 24px !important"))
        assertTrue(css.contains("padding-right: 12px !important"))
        assertTrue(css.contains("column-width: calc(100vw - 36px) !important"))
        assertTrue(css.contains("column-gap: 36px !important"))
        assertTrue(css.contains("width: 100vw !important"))
    }

    @Test
    fun horizontalScriptUsesDedicatedScrollExtentInsteadOfExpandingBody() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = asymmetricSettings
        )

        assertTrue(script.contains("scrollExtentElement.id = 'epubpro-scroll-extent'"))
        assertTrue(script.contains("html.appendChild(scrollExtentElement)"))
        assertTrue(script.contains("body.style.removeProperty('min-width')"))
        assertFalse(script.contains("body.style.setProperty('min-width'"))
    }

    @Test
    fun committedPageSettlesAtCanonicalOffsetBeforeOverlayCleanup() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = asymmetricSettings
        )

        assertTrue(script.contains("var targetX = (boundedPage - 1) * pw"))
        assertTrue(script.contains("setHorizontalScrollExtentWidth(scrollExtentWidth + shortfall)"))
        val commitIndex = script.lastIndexOf("settlePageOffset(currentPage, 2")
        val cleanupIndex = script.indexOf("cleanupCoverOverlay()", startIndex = commitIndex)
        val notificationIndex = script.indexOf("notifyPageChangeCompleted()", startIndex = cleanupIndex)

        assertTrue(commitIndex >= 0)
        assertTrue(cleanupIndex > commitIndex)
        assertTrue(notificationIndex > cleanupIndex)
    }

    @Test
    fun metricsAreBlockedUntilHorizontalLayoutIsReady() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = asymmetricSettings
        )

        assertTrue(script.contains("var isLayoutReady = !window.epubproIsHorizontal"))
        assertTrue(
            script.contains(
                "if (!isLayoutReady || isExecutingScroll || isDraggingPage || " +
                    "isCoverOverlayActive || ignoreScrollMetrics) return"
            )
        )
        assertTrue(script.contains("isLayoutReady = true"))
    }

    @Test
    fun layoutReadyCarriesGenerationAfterInitialPageSettles() {
        val script = CssInjector.generateJsBridgeScript(
            isHorizontalPagination = true,
            settings = asymmetricSettings,
            loadGeneration = 42
        )

        assertTrue(
            script.contains(
                "scrollToPage(targetInitPage, false, notifyReaderLayoutReady)"
            )
        )
        assertTrue(script.contains("window.ReaderJsBridge.onReaderLayoutReady(42)"))
    }
}
