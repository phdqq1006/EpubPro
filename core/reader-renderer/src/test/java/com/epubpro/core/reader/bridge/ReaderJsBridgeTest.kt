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


    @Test
    fun prefetchCallbacksForwardToListeners() {
        var nextPrefetchCount = 0
        var previousPrefetchCount = 0
        val bridge = ReaderJsBridge(
            onTextSelectedListener = {},
            onCfiChangedListener = {},
            onPageTappedListener = {},
            onNextChapterPrefetchListener = { nextPrefetchCount++ },
            onPreviousChapterPrefetchListener = { previousPrefetchCount++ }
        )

        bridge.onNextChapterPrefetchRequested()
        bridge.onPreviousChapterPrefetchRequested()

        assertEquals(1, nextPrefetchCount)
        assertEquals(1, previousPrefetchCount)
    }

}
