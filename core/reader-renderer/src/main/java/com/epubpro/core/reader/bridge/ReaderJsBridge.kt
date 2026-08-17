package com.epubpro.core.reader.bridge

import android.util.Log
import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val onTextSelectedListener: (selectionJson: String) -> Unit,
    private val onCfiChangedListener: (cfi: String) -> Unit,
    private val onPageTappedListener: () -> Unit,
    private val onPageChangedListener: (currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) -> Unit = { _, _, _ -> },
    private val onNextChapterListener: () -> Unit = {},
    private val onPreviousChapterListener: () -> Unit = {},
    private val onReaderLayoutReadyListener: (loadGeneration: Int) -> Unit = {}
) {
    @JavascriptInterface
    fun onTextSelected(selectionJson: String) {
        onTextSelectedListener(selectionJson)
    }

    @JavascriptInterface
    fun onCfiChanged(cfi: String) {
        onCfiChangedListener(cfi)
    }

    @JavascriptInterface
    fun onPageTapped() {
        onPageTappedListener()
    }

    @JavascriptInterface
    fun onPageChanged(currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) {
        onPageChangedListener(currentPage, totalPages, firstVisibleChunkIndex)
    }

    @JavascriptInterface
    fun onNextChapterRequested() {
        onNextChapterListener()
    }

    @JavascriptInterface
    fun onPreviousChapterRequested() {
        onPreviousChapterListener()
    }

    @JavascriptInterface
    fun onReaderLayoutReady(loadGeneration: Int) {
        onReaderLayoutReadyListener(loadGeneration)
    }

    @JavascriptInterface
    fun onDebugLog(tag: String, message: String) {
        Log.d("EpubPro_JS_$tag", message)
    }
}
