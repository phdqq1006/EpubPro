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
    private val onNextChapterPrefetchListener: () -> Unit = {},
    private val onPreviousChapterPrefetchListener: () -> Unit = {},
    private val onAdjacentChapterCommittedListener: (direction: Int) -> Unit = {},
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

    /**
     * Báo trước rằng người dùng đã commit gesture sang chương kế tiếp để chuẩn bị cache.
     */
    @JavascriptInterface
    fun onNextChapterPrefetchRequested() {
        onNextChapterPrefetchListener()
    }

    /**
     * Báo trước rằng người dùng đã commit gesture về chương trước để chuẩn bị cache.
     */
    @JavascriptInterface
    fun onPreviousChapterPrefetchRequested() {
        onPreviousChapterPrefetchListener()
    }

    /**
     * Báo cho Android rằng nội dung chương kề đã được thay trực tiếp trong DOM hiện tại.
     *
     * @param direction Hướng chuyển chương: 1 là chương kế tiếp, -1 là chương trước đó.
     */
    @JavascriptInterface
    fun onAdjacentChapterCommitted(direction: Int) {
        onAdjacentChapterCommittedListener(direction)
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
