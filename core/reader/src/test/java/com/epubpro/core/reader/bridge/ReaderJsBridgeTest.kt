package com.epubpro.core.reader.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderJsBridgeTest {

    @Test
    fun layoutReadyForwardsLoadGeneration() {
        var receivedGeneration = -1
        val bridge = ReaderJsBridge(
            onTextSelectedListener = {},
            onCfiChangedListener = {},
            onPageTappedListener = {},
            onReaderLayoutReadyListener = { receivedGeneration = it }
        )

        bridge.onReaderLayoutReady(17)

        assertEquals(17, receivedGeneration)
    }
}
