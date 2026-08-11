package com.epubpro.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsBubblePreferencesCodecTest {
    @Test
    fun roundTripPreservesAllPreferences() {
        val preferences = TtsBubblePreferences(
            enabled = true,
            pendingEnable = true,
            side = TtsBubbleSide.LEFT,
            normalizedY = 0.73f,
            hiddenForCurrentSession = true
        )

        assertEquals(preferences, TtsBubblePreferencesCodec.decode(TtsBubblePreferencesCodec.encode(preferences)))
    }

    @Test
    fun normalizationClampsPositionAndRepairsNonFiniteValues() {
        assertEquals(0f, TtsBubblePreferences(normalizedY = -4f).normalized().normalizedY)
        assertEquals(1f, TtsBubblePreferences(normalizedY = 4f).normalized().normalizedY)
        assertEquals(
            DEFAULT_BUBBLE_NORMALIZED_Y,
            TtsBubblePreferences(normalizedY = Float.NaN).normalized().normalizedY
        )
    }

    @Test
    fun corruptedOrUnknownRecordsAreRejected() {
        assertNull(TtsBubblePreferencesCodec.decode("broken"))
        assertNull(TtsBubblePreferencesCodec.decode("2|1|0|RIGHT|0.5|0"))
        assertNull(TtsBubblePreferencesCodec.decode("1|yes|0|RIGHT|0.5|0"))
        assertNull(TtsBubblePreferencesCodec.decode("1|1|0|CENTER|0.5|0"))
    }
}
