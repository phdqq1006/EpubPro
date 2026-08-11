package com.epubpro.core.reader.tts.bubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsBubblePositionCalculatorTest {
    private val viewport = TtsBubbleViewport(
        widthPx = 1_080,
        heightPx = 1_920,
        insets = TtsBubbleInsets(top = 100, bottom = 100)
    )
    private val bubbleSize = TtsBubbleSize(widthPx = 64, heightPx = 64)
    private val margin = 16

    @Test
    fun `stored normalized position resolves against safe vertical travel`() {
        val coordinates = TtsBubblePositionCalculator.coordinatesFor(
            position = TtsBubblePosition(
                edge = TtsBubbleHorizontalEdge.RIGHT,
                normalizedY = 0.5f
            ),
            viewport = viewport,
            bubbleSize = bubbleSize,
            edgeMarginPx = margin
        )

        assertEquals(1_000, coordinates.x)
        assertEquals(928, coordinates.y)
    }

    @Test
    fun `out of range normalized position is clamped`() {
        val above = TtsBubblePositionCalculator.coordinatesFor(
            TtsBubblePosition(TtsBubbleHorizontalEdge.LEFT, -10f),
            viewport,
            bubbleSize,
            margin
        )
        val below = TtsBubblePositionCalculator.coordinatesFor(
            TtsBubblePosition(TtsBubbleHorizontalEdge.LEFT, 10f),
            viewport,
            bubbleSize,
            margin
        )

        assertEquals(TtsBubbleCoordinates(x = 16, y = 116), above)
        assertEquals(TtsBubbleCoordinates(x = 16, y = 1_740), below)
    }

    @Test
    fun `snap selects nearest physical edge and keeps clamped y`() {
        val left = TtsBubblePositionCalculator.snap(
            coordinates = TtsBubbleCoordinates(x = 120, y = 600),
            viewport = viewport,
            bubbleSize = bubbleSize,
            edgeMarginPx = margin
        )
        val right = TtsBubblePositionCalculator.snap(
            coordinates = TtsBubbleCoordinates(x = 850, y = 600),
            viewport = viewport,
            bubbleSize = bubbleSize,
            edgeMarginPx = margin
        )

        assertEquals(TtsBubbleHorizontalEdge.LEFT, left.position.edge)
        assertEquals(16, left.coordinates.x)
        assertEquals(TtsBubbleHorizontalEdge.RIGHT, right.position.edge)
        assertEquals(1_000, right.coordinates.x)
        assertEquals(600, left.coordinates.y)
        assertEquals(600, right.coordinates.y)
        assertEquals(left.position.normalizedY, right.position.normalizedY, 0.0001f)
    }

    @Test
    fun `drag coordinates stay inside insets and margins`() {
        val clamped = TtsBubblePositionCalculator.clampCoordinates(
            coordinates = TtsBubbleCoordinates(x = 9_999, y = -500),
            viewport = viewport,
            bubbleSize = bubbleSize,
            edgeMarginPx = margin
        )

        assertEquals(TtsBubbleCoordinates(x = 1_000, y = 116), clamped)
    }

    @Test
    fun `small viewport produces stable single placement`() {
        val tinyViewport = TtsBubbleViewport(
            widthPx = 40,
            heightPx = 40,
            insets = TtsBubbleInsets(left = 8, top = 9, right = 8, bottom = 9)
        )

        val coordinates = TtsBubblePositionCalculator.coordinatesFor(
            position = TtsBubblePosition(TtsBubbleHorizontalEdge.RIGHT, Float.NaN),
            viewport = tinyViewport,
            bubbleSize = bubbleSize,
            edgeMarginPx = margin
        )

        assertEquals(TtsBubbleCoordinates(x = 24, y = 25), coordinates)
    }

    @Test
    fun `hide zone checks bubble center rather than top left`() {
        val hideZone = TtsBubbleRect(left = 450, top = 1_700, right = 630, bottom = 1_880)

        assertTrue(
            TtsBubblePositionCalculator.isInsideHideZone(
                coordinates = TtsBubbleCoordinates(x = 440, y = 1_690),
                bubbleSize = bubbleSize,
                hideZone = hideZone
            )
        )
        assertFalse(
            TtsBubblePositionCalculator.isInsideHideZone(
                coordinates = TtsBubbleCoordinates(x = 300, y = 1_500),
                bubbleSize = bubbleSize,
                hideZone = hideZone
            )
        )
    }
}
