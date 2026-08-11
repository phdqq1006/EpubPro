package com.epubpro.core.reader.tts.bubble

import kotlin.math.roundToInt

enum class TtsBubbleHorizontalEdge {
    LEFT,
    RIGHT
}

data class TtsBubblePosition(
    val edge: TtsBubbleHorizontalEdge = TtsBubbleHorizontalEdge.RIGHT,
    val normalizedY: Float = DEFAULT_NORMALIZED_Y
) {
    companion object {
        const val DEFAULT_NORMALIZED_Y = 0.35f
        val Default = TtsBubblePosition()
    }
}

data class TtsBubbleInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

data class TtsBubbleViewport(
    val widthPx: Int,
    val heightPx: Int,
    val insets: TtsBubbleInsets = TtsBubbleInsets()
)

data class TtsBubbleSize(
    val widthPx: Int,
    val heightPx: Int
)

data class TtsBubbleCoordinates(
    val x: Int,
    val y: Int
)

data class TtsBubblePlacement(
    val coordinates: TtsBubbleCoordinates,
    val position: TtsBubblePosition
)

data class TtsBubbleRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}

object TtsBubblePositionCalculator {
    fun coordinatesFor(
        position: TtsBubblePosition,
        viewport: TtsBubbleViewport,
        bubbleSize: TtsBubbleSize,
        edgeMarginPx: Int = 0
    ): TtsBubbleCoordinates {
        val bounds = movementBounds(viewport, bubbleSize, edgeMarginPx)
        val normalizedY = position.normalizedY
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: TtsBubblePosition.DEFAULT_NORMALIZED_Y
        val y = bounds.top + ((bounds.bottom - bounds.top) * normalizedY).roundToInt()
        val x = when (position.edge) {
            TtsBubbleHorizontalEdge.LEFT -> bounds.left
            TtsBubbleHorizontalEdge.RIGHT -> bounds.right
        }
        return TtsBubbleCoordinates(x = x, y = y)
    }

    fun clampCoordinates(
        coordinates: TtsBubbleCoordinates,
        viewport: TtsBubbleViewport,
        bubbleSize: TtsBubbleSize,
        edgeMarginPx: Int = 0
    ): TtsBubbleCoordinates {
        val bounds = movementBounds(viewport, bubbleSize, edgeMarginPx)
        return TtsBubbleCoordinates(
            x = coordinates.x.coerceIn(bounds.left, bounds.right),
            y = coordinates.y.coerceIn(bounds.top, bounds.bottom)
        )
    }

    fun snap(
        coordinates: TtsBubbleCoordinates,
        viewport: TtsBubbleViewport,
        bubbleSize: TtsBubbleSize,
        edgeMarginPx: Int = 0
    ): TtsBubblePlacement {
        val clamped = clampCoordinates(coordinates, viewport, bubbleSize, edgeMarginPx)
        val bounds = movementBounds(viewport, bubbleSize, edgeMarginPx)
        val bubbleCenterX = clamped.x + bubbleSize.widthPx / 2f
        val viewportCenterX = viewport.widthPx / 2f
        val edge = if (bubbleCenterX <= viewportCenterX) {
            TtsBubbleHorizontalEdge.LEFT
        } else {
            TtsBubbleHorizontalEdge.RIGHT
        }
        val snappedX = if (edge == TtsBubbleHorizontalEdge.LEFT) bounds.left else bounds.right
        val travelY = bounds.bottom - bounds.top
        val normalizedY = if (travelY == 0) {
            0f
        } else {
            (clamped.y - bounds.top).toFloat() / travelY.toFloat()
        }
        val position = TtsBubblePosition(edge = edge, normalizedY = normalizedY.coerceIn(0f, 1f))
        return TtsBubblePlacement(
            coordinates = TtsBubbleCoordinates(x = snappedX, y = clamped.y),
            position = position
        )
    }

    fun isInsideHideZone(
        coordinates: TtsBubbleCoordinates,
        bubbleSize: TtsBubbleSize,
        hideZone: TtsBubbleRect
    ): Boolean {
        val centerX = coordinates.x + bubbleSize.widthPx / 2
        val centerY = coordinates.y + bubbleSize.heightPx / 2
        return hideZone.contains(centerX, centerY)
    }

    private fun movementBounds(
        viewport: TtsBubbleViewport,
        bubbleSize: TtsBubbleSize,
        edgeMarginPx: Int
    ): TtsBubbleRect {
        val margin = edgeMarginPx.coerceAtLeast(0)
        val left = (viewport.insets.left + margin).coerceAtLeast(0)
        val top = (viewport.insets.top + margin).coerceAtLeast(0)
        val right = (viewport.widthPx - viewport.insets.right - margin - bubbleSize.widthPx)
            .coerceAtLeast(left)
        val bottom = (viewport.heightPx - viewport.insets.bottom - margin - bubbleSize.heightPx)
            .coerceAtLeast(top)
        return TtsBubbleRect(left = left, top = top, right = right, bottom = bottom)
    }
}
