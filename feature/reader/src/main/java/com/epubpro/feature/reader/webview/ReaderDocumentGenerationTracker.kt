package com.epubpro.feature.reader.webview

internal class ReaderDocumentGenerationTracker {
    private var currentLoadGeneration: Int = 0
    private var pendingDestinationGeneration: Int? = null

    fun nextLoadGeneration(): Int {
        currentLoadGeneration += 1
        return currentLoadGeneration
    }

    fun beginChapterTransition(): Int {
        return (currentLoadGeneration + 1).also { expectedGeneration ->
            pendingDestinationGeneration = expectedGeneration
        }
    }

    fun completeIfExpected(loadGeneration: Int): Boolean {
        if (pendingDestinationGeneration != loadGeneration) return false
        pendingDestinationGeneration = null
        return true
    }

    fun cancelIfExpected(loadGeneration: Int): Boolean {
        if (pendingDestinationGeneration != loadGeneration) return false
        pendingDestinationGeneration = null
        return true
    }
}
