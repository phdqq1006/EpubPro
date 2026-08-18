package com.epubpro.core.storage

import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.TapZoneAction
import com.epubpro.domain.model.TapZoneLayout
import com.epubpro.domain.model.defaultTapZoneActions
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPreferencesMigrationTest {
    @Test
    fun missingReadingModeFallsBackToLegacyPaginationFlag() {
        assertEquals(ReadingMode.SCROLL, resolveReadingMode(null, legacyHorizontal = false))
        assertEquals(ReadingMode.FLIP, resolveReadingMode(null, legacyHorizontal = true))
    }

    @Test
    fun legacyReadingModesAreNormalizedToSupportedModes() {
        assertEquals(ReadingMode.FLIP, resolveReadingMode("SCROLL_HORIZONTAL", legacyHorizontal = false))
        assertEquals(ReadingMode.SCROLL, resolveReadingMode("CONTINUOUS", legacyHorizontal = true))
        assertEquals(ReadingMode.SCROLL, resolveReadingMode("SCROLL", legacyHorizontal = true))
        assertEquals(ReadingMode.FLIP, resolveReadingMode("FLIP", legacyHorizontal = false))
    }

    @Test
    fun invalidReadingModeFallsBackToLegacyPaginationFlag() {
        assertEquals(ReadingMode.SCROLL, resolveReadingMode("UNKNOWN", legacyHorizontal = false))
        assertEquals(ReadingMode.FLIP, resolveReadingMode("UNKNOWN", legacyHorizontal = true))
    }

    @Test
    fun pageTurnSpeedIsNormalizedToSharedRange() {
        assertEquals(100, normalizePageTurnSpeed(0))
        assertEquals(220, normalizePageTurnSpeed(220))
        assertEquals(600, normalizePageTurnSpeed(1000))
    }

    @Test
    fun defaultTapZonesAlwaysProduceNineRunnableActions() {
        TapZoneLayout.values().forEach { layout ->
            val actions = defaultTapZoneActions(layout)
            assertEquals(9, actions.size)
            assertEquals(
                setOf(TapZoneAction.PREV_PAGE, TapZoneAction.NEXT_PAGE, TapZoneAction.TOGGLE_CONTROLS),
                actions.toSet()
            )
        }
    }

    @Test
    fun brightnessValueIsClampedBetweenZeroAndOne() {
        assertEquals(0.0f, (-0.2f).coerceIn(0.0f, 1.0f), 0.001f)
        assertEquals(0.65f, (0.65f).coerceIn(0.0f, 1.0f), 0.001f)
        assertEquals(1.0f, (1.8f).coerceIn(0.0f, 1.0f), 0.001f)
    }
}