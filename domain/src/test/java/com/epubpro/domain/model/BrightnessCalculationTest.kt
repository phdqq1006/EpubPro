package com.epubpro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử đơn vị cho thuật toán ánh xạ độ sáng hybrid [calculateBrightnessOutput].
 */
class BrightnessCalculationTest {

    @Test
    fun `zero brightness produces minimum hardware and maximum extra dim alpha`() {
        val output = calculateBrightnessOutput(0.0f)
        assertEquals(0.01f, output.hardwareBrightness, 0.001f)
        assertEquals(0.75f, output.extraDimAlpha, 0.001f)
    }

    @Test
    fun `halfway extra dim brightness scales alpha linearly`() {
        val output = calculateBrightnessOutput(0.1f)
        assertEquals(0.01f, output.hardwareBrightness, 0.001f)
        assertEquals(0.375f, output.extraDimAlpha, 0.001f)
    }

    @Test
    fun `extra dim threshold boundary has minimum hardware and zero extra dim alpha`() {
        val output = calculateBrightnessOutput(0.2f)
        assertEquals(0.01f, output.hardwareBrightness, 0.001f)
        assertEquals(0.0f, output.extraDimAlpha, 0.001f)
    }

    @Test
    fun `standard brightness scales hardware linearly with no extra dim alpha`() {
        val output = calculateBrightnessOutput(0.6f)
        // fraction = (0.6 - 0.2) / 0.8 = 0.5
        // hw = 0.01 + 0.5 * 0.99 = 0.505
        assertEquals(0.505f, output.hardwareBrightness, 0.001f)
        assertEquals(0.0f, output.extraDimAlpha, 0.001f)
    }

    @Test
    fun `maximum brightness produces maximum hardware and zero extra dim alpha`() {
        val output = calculateBrightnessOutput(1.0f)
        assertEquals(1.0f, output.hardwareBrightness, 0.001f)
        assertEquals(0.0f, output.extraDimAlpha, 0.001f)
    }

    @Test
    fun `out of bounds brightness is clamped safely`() {
        val underflow = calculateBrightnessOutput(-0.5f)
        assertEquals(0.01f, underflow.hardwareBrightness, 0.001f)
        assertEquals(0.75f, underflow.extraDimAlpha, 0.001f)

        val overflow = calculateBrightnessOutput(1.5f)
        assertEquals(1.0f, overflow.hardwareBrightness, 0.001f)
        assertEquals(0.0f, overflow.extraDimAlpha, 0.001f)
    }
}
