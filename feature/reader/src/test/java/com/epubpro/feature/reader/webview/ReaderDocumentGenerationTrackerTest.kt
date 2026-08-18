package com.epubpro.feature.reader.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDocumentGenerationTrackerTest {

    @Test
    fun staleDocumentCannotCompletePendingTransition() {
        val tracker = ReaderDocumentGenerationTracker()
        val firstLoad = tracker.nextLoadGeneration()
        val destination = tracker.beginChapterTransition()

        assertFalse(tracker.completeIfExpected(firstLoad))
        assertTrue(tracker.completeIfExpected(destination))
    }

    @Test
    fun timeoutOnlyCancelsMatchingTransition() {
        val tracker = ReaderDocumentGenerationTracker()
        tracker.nextLoadGeneration()
        val destination = tracker.beginChapterTransition()

        assertFalse(tracker.cancelIfExpected(destination + 1))
        assertTrue(tracker.cancelIfExpected(destination))
        assertFalse(tracker.completeIfExpected(destination))
    }

    @Test
    fun everyLoadedDocumentGetsMonotonicGeneration() {
        val tracker = ReaderDocumentGenerationTracker()

        assertTrue(tracker.nextLoadGeneration() < tracker.nextLoadGeneration())
    }
}
