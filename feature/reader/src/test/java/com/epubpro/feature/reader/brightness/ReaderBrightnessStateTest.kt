package com.epubpro.feature.reader.brightness

import com.epubpro.domain.model.EXTRA_DIM_THRESHOLD
import com.epubpro.domain.model.calculateBrightnessOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử tính đúng đắn cho các trạng thái và tính toán delta độ sáng của Reader.
 */
class ReaderBrightnessStateTest {

    @Test
    fun verticalDragDeltaIncreasesAndDecreasesCorrectly() {
        val initialBrightness = 0.5f
        val screenHeight = 2000f

        // Vuốt lên 200px (dragAmount = -200f) -> delta = +0.1f -> next = 0.6f
        val dragUpAmount = -200f
        val deltaUp = -dragUpAmount / screenHeight
        val nextUp = (initialBrightness + deltaUp).coerceIn(0.0f, 1.0f)
        assertEquals(0.6f, nextUp, 0.001f)

        // Vuốt xuống 400px (dragAmount = 400f) -> delta = -0.2f -> next = 0.3f
        val dragDownAmount = 400f
        val deltaDown = -dragDownAmount / screenHeight
        val nextDown = (initialBrightness + deltaDown).coerceIn(0.0f, 1.0f)
        assertEquals(0.3f, nextDown, 0.001f)
    }

    @Test
    fun dragDeltaClampsAtBoundaries() {
        val initialBrightness = 0.95f
        val screenHeight = 1000f

        // Vuốt lên quá mức 1.0f -> clamped at 1.0f
        val dragUpAmount = -300f
        val deltaUp = -dragUpAmount / screenHeight
        val nextUp = (initialBrightness + deltaUp).coerceIn(0.0f, 1.0f)
        assertEquals(1.0f, nextUp, 0.001f)

        // Vuốt xuống dưới 0.0f -> clamped at 0.0f
        val lowBrightness = 0.05f
        val dragDownAmount = 300f
        val deltaDown = -dragDownAmount / screenHeight
        val nextDown = (lowBrightness + deltaDown).coerceIn(0.0f, 1.0f)
        assertEquals(0.0f, nextDown, 0.001f)
    }

    @Test
    fun extraDimThresholdTriggersOnlyBelowTwentyPercent() {
        val atThreshold = calculateBrightnessOutput(EXTRA_DIM_THRESHOLD)
        assertEquals(0.0f, atThreshold.extraDimAlpha, 0.001f)

        val justBelow = calculateBrightnessOutput(0.19f)
        assertTrue(justBelow.extraDimAlpha > 0.0f)

        val justAbove = calculateBrightnessOutput(0.21f)
        assertEquals(0.0f, justAbove.extraDimAlpha, 0.001f)
    }
}
