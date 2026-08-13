package com.epubpro.feature.reader.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.epubpro.core.reader.bridge.ReaderJsBridge
import com.epubpro.core.reader.style.CssInjector
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.ReaderSettings
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val CHAPTER_TRANSITION_TIMEOUT_MS = 2_500L

private enum class ChapterTransitionDirection {
    NEXT,
    PREVIOUS
}

private data class ChapterTransitionCover(
    val token: Long,
    val expectedLoadGeneration: Int,
    val direction: ChapterTransitionDirection,
    val image: ImageBitmap
)

internal fun ReaderSettings.contentReloadKey(): Int = listOf(
    fontSizeSp,
    fontFamily,
    lineHeightRatio,
    marginTopDp,
    marginBottomDp,
    marginLeftDp,
    marginRightDp,
    themeMode,
    isHorizontalPagination,
    paragraphSpacingDp,
    firstLineIndentDp,
    textAlignment,
    showScrollBar
).hashCode()

private fun sanitizeEpubHtml(html: String): String {
    return html
        .replace("""(?i)<meta\s+name=["']viewport["'][^>]*>""".toRegex(), "")
        .replace("""(?i)<link[^>]*rel=["']stylesheet["'][^>]*/>""".toRegex(), "")
        .replace("""(?i)<link[^>]*rel=["']stylesheet["'][^>]*>""".toRegex(), "")
        .replace("""(?is)<style[^>]*>.*?</style>""".toRegex(), "")
        .replace("""(?i)(<body[^>]*?)\s+style\s*=\s*"[^"]*"""".toRegex(), "$1")
        .replace("""(?i)(<body[^>]*?)\s+style\s*=\s*'[^']*'""".toRegex(), "$1")
        .replace("""(?i)(<html[^>]*?)\s+style\s*=\s*"[^"]*"""".toRegex(), "$1")
        .replace("""(?i)(<html[^>]*?)\s+style\s*=\s*'[^']*'""".toRegex(), "$1")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Regression invariant: this must capture composited pixels with PixelCopy.
 * WebView.draw(Canvas) misses hardware-rendered fixed/transform preview layers and
 * previously produced about 270 ms of theme-only frames at chapter boundaries.
 * See docs/reader-chapter-transition-snapshot-design.md.
 */
private fun WebView.captureTransitionFrame(
    callbackHandler: Handler,
    onCaptured: (Bitmap?) -> Unit
) {
    val activity = context.findActivity()
    if (width <= 0 || height <= 0 || !isAttachedToWindow || activity == null) {
        onCaptured(null)
        return
    }

    val locationInWindow = IntArray(2)
    getLocationInWindow(locationInWindow)
    val sourceRect = Rect(
        locationInWindow[0],
        locationInWindow[1],
        locationInWindow[0] + width,
        locationInWindow[1] + height
    )
    val bitmap = runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }.onFailure { error ->
        Log.e("EpubPro_TRANSITION", "Unable to allocate transition bitmap", error)
    }.getOrElse {
        onCaptured(null)
        return
    }

    runCatching {
        PixelCopy.request(
            activity.window,
            sourceRect,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    onCaptured(bitmap)
                } else {
                    bitmap.recycle()
                    Log.e(
                        "EpubPro_TRANSITION",
                        "PixelCopy failed for transition frame, result=${result}"
                    )
                    onCaptured(null)
                }
            },
            callbackHandler
        )
    }.onFailure { error ->
        bitmap.recycle()
        Log.e("EpubPro_TRANSITION", "Unable to capture WebView transition frame", error)
        onCaptured(null)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun EpubProWebView(
    modifier: Modifier = Modifier,
    htmlContent: String,
    previousChapterHtml: String?,
    nextChapterHtml: String?,
    initialPage: Int,
    initialVisibleParagraphIndex: Int,
    settings: ReaderSettings,
    filterPreferences: ContentFilterPreferences = ContentFilterPreferences(),
    activeTtsParagraphIndex: Int? = null,
    onPageTapped: () -> Unit,
    onPageChanged: (currentPage: Int, totalPages: Int, firstVisibleChunkIndex: Int) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onTextSelected: (String) -> Unit,
    onCfiChanged: (String) -> Unit
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val generationTracker = remember { ReaderDocumentGenerationTracker() }
    val currentOnPageTapped by rememberUpdatedState(onPageTapped)
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    val currentOnNextChapter by rememberUpdatedState(onNextChapter)
    val currentOnPreviousChapter by rememberUpdatedState(onPreviousChapter)
    val currentOnTextSelected by rememberUpdatedState(onTextSelected)
    val currentOnCfiChanged by rememberUpdatedState(onCfiChanged)

    var loadedHtmlKey by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var transitionCover by remember { mutableStateOf<ChapterTransitionCover?>(null) }
    var isTransitionCapturePending by remember { mutableStateOf(false) }

    val requestChapterTransition by rememberUpdatedState<(ChapterTransitionDirection) -> Unit> {
        direction ->
        val webView = webViewRef
        if (webView == null) {
            mainHandler.post {
                when (direction) {
                    ChapterTransitionDirection.NEXT -> currentOnNextChapter()
                    ChapterTransitionDirection.PREVIOUS -> currentOnPreviousChapter()
                }
            }
        } else if (transitionCover != null || isTransitionCapturePending) {
            Log.d("EpubPro_TRANSITION", "Ignoring duplicate boundary request")
        } else {
            isTransitionCapturePending = true
            webView.post {
                webView.captureTransitionFrame(mainHandler) { capturedBitmap ->
                    isTransitionCapturePending = false
                    if (webViewRef !== webView) {
                        capturedBitmap?.recycle()
                        return@captureTransitionFrame
                    }

                    if (capturedBitmap == null) {
                        when (direction) {
                            ChapterTransitionDirection.NEXT -> currentOnNextChapter()
                            ChapterTransitionDirection.PREVIOUS -> currentOnPreviousChapter()
                        }
                        return@captureTransitionFrame
                    }

                    val expectedGeneration = generationTracker.beginChapterTransition()
                    transitionCover = ChapterTransitionCover(
                        token = System.nanoTime(),
                        expectedLoadGeneration = expectedGeneration,
                        direction = direction,
                        image = capturedBitmap.asImageBitmap()
                    )
                    Log.d(
                        "EpubPro_TRANSITION",
                        "PixelCopy captured boundary frame, expectedGeneration=${expectedGeneration} direction=${direction}"
                    )
                }
            }
        }
    }

    val jsBridge = remember {
        ReaderJsBridge(
            onTextSelectedListener = { selectionJson ->
                mainHandler.post { currentOnTextSelected(selectionJson) }
            },
            onCfiChangedListener = { cfi ->
                mainHandler.post { currentOnCfiChanged(cfi) }
            },
            onPageTappedListener = {
                mainHandler.post { currentOnPageTapped() }
            },
            onPageChangedListener = { currentPage, totalPages, firstVisibleChunkIndex ->
                mainHandler.post {
                    currentOnPageChanged(currentPage, totalPages, firstVisibleChunkIndex)
                }
            },
            onNextChapterListener = {
                mainHandler.post {
                    requestChapterTransition(ChapterTransitionDirection.NEXT)
                }
            },
            onPreviousChapterListener = {
                mainHandler.post {
                    requestChapterTransition(ChapterTransitionDirection.PREVIOUS)
                }
            },
            onReaderLayoutReadyListener = { loadGeneration ->
                mainHandler.post {
                    val cover = transitionCover
                    if (
                        cover != null &&
                        cover.expectedLoadGeneration == loadGeneration
                    ) {
                        val destinationWebView = webViewRef ?: return@post
                        Log.d(
                            "EpubPro_TRANSITION",
                            "Layout ready, waiting for committed visual generation=${loadGeneration}"
                        )
                        destinationWebView.postVisualStateCallback(
                            loadGeneration.toLong(),
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    if (requestId != loadGeneration.toLong()) return
                                    destinationWebView.postOnAnimation {
                                        if (
                                            webViewRef === destinationWebView &&
                                            transitionCover?.token == cover.token &&
                                            generationTracker.completeIfExpected(loadGeneration)
                                        ) {
                                            Log.d(
                                                "EpubPro_TRANSITION",
                                                "Visual committed, clearing cover generation=${loadGeneration}"
                                            )
                                            transitionCover = null
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )
    }

    LaunchedEffect(transitionCover?.token) {
        val cover = transitionCover ?: return@LaunchedEffect
        withFrameNanos { }
        if (transitionCover?.token != cover.token) return@LaunchedEffect

        when (cover.direction) {
            ChapterTransitionDirection.NEXT -> currentOnNextChapter()
            ChapterTransitionDirection.PREVIOUS -> currentOnPreviousChapter()
        }

        delay(CHAPTER_TRANSITION_TIMEOUT_MS)
        if (
            transitionCover?.token == cover.token &&
            generationTracker.cancelIfExpected(cover.expectedLoadGeneration)
        ) {
            Log.w(
                "EpubPro_TRANSITION",
                "Timed out waiting for generation=${cover.expectedLoadGeneration}"
            )
            transitionCover = null
        }
    }

    LaunchedEffect(activeTtsParagraphIndex) {
        if (activeTtsParagraphIndex != null && webViewRef != null) {
            webViewRef?.evaluateJavascript(
                "if (typeof epubproHighlightTtsParagraph === 'function') { epubproHighlightTtsParagraph($activeTtsParagraphIndex); }",
                null
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            val webView = webViewRef
            webViewRef = null
            isTransitionCapturePending = false
            webView?.apply {
                removeJavascriptInterface("ReaderJsBridge")
                stopLoading()
                destroy()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    this.settings.javaScriptEnabled = true
                    this.settings.domStorageEnabled = true
                    this.settings.useWideViewPort = false
                    this.settings.loadWithOverviewMode = false
                    this.settings.textZoom = 100
                    this.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()

                    addJavascriptInterface(jsBridge, "ReaderJsBridge")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(
                                "if (typeof epubproUpdateMetrics === 'function') { epubproUpdateMetrics(); }",
                                null
                            )
                        }
                    }
                }
            },
            update = { webView ->
                webView.isVerticalScrollBarEnabled =
                    settings.showScrollBar && !settings.isHorizontalPagination
                webView.isHorizontalScrollBarEnabled =
                    settings.showScrollBar && settings.isHorizontalPagination
                webView.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_UP) {
                        return@setOnKeyListener false
                    }

                    val direction = when {
                        settings.enableKeyboardNavigation && keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_PAGE_DOWN,
                            KeyEvent.KEYCODE_SPACE
                        ) -> 1

                        settings.enableKeyboardNavigation && keyCode in listOf(
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_PAGE_UP
                        ) -> -1

                        settings.enableVolumeKeyNavigation &&
                            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> 1

                        settings.enableVolumeKeyNavigation &&
                            keyCode == KeyEvent.KEYCODE_VOLUME_UP -> -1

                        else -> 0
                    }

                    if (direction == 0) {
                        false
                    } else {
                        val functionName =
                            if (direction > 0) "epubproGoNextPage" else "epubproGoPrevPage"
                        webView.evaluateJavascript(
                            "if (typeof $functionName === 'function') { $functionName(); }",
                            null
                        )
                        true
                    }
                }

                val runtimeSpeedMs =
                    if (settings.enablePageAnimation) settings.pageTurnSpeedMs else 0
                val runtimeActions =
                    JSONObject.quote(settings.tapZoneActions.joinToString(",") { it.name })
                webView.evaluateJavascript(
                    "if (typeof epubproApplyRuntimeSettings === 'function') { epubproApplyRuntimeSettings($runtimeSpeedMs, $runtimeActions); }",
                    null
                )

                val newHtmlKey =
                    "${htmlContent.hashCode()}_${previousChapterHtml?.hashCode()}_${nextChapterHtml?.hashCode()}_${settings.contentReloadKey()}"
                if (loadedHtmlKey != newHtmlKey) {
                    loadedHtmlKey = newHtmlKey
                    val loadGeneration = generationTracker.nextLoadGeneration()
                    val cleanHtml = sanitizeEpubHtml(htmlContent)
                    val previousPreviewHtml = previousChapterHtml?.let(::sanitizeEpubHtml)
                    val nextPreviewHtml = nextChapterHtml?.let(::sanitizeEpubHtml)
                    val statusFooterHeightDp = if (settings.showStatusBar) 20 else 0
                    val css = CssInjector.generateCss(settings, statusFooterHeightDp)
                    val jsScript = CssInjector.generateJsBridgeScript(
                        isHorizontalPagination = settings.isHorizontalPagination,
                        initialPage = initialPage,
                        initialVisibleParagraphIndex = initialVisibleParagraphIndex,
                        settings = settings,
                        statusFooterHeightDp = statusFooterHeightDp,
                        previousChapterHtml = previousPreviewHtml,
                        nextChapterHtml = nextPreviewHtml,
                        filterPreferences = filterPreferences,
                        loadGeneration = loadGeneration
                    )
                    val meta = CssInjector.generateMetaAndViewport()
                    val headInjection = """
                        $meta
                        $css
                        <script>
                        $jsScript
                        </script>
                    """.trimIndent()
                    val preparedHtml = when {
                        cleanHtml.contains("</head>", ignoreCase = true) -> {
                            "(?i)</head>".toRegex().replace(cleanHtml) {
                                "$headInjection</head>"
                            }
                        }

                        cleanHtml.contains("<head>", ignoreCase = true) -> {
                            "(?i)<head>".toRegex().replace(cleanHtml) {
                                "<head>$headInjection"
                            }
                        }

                        cleanHtml.contains("<html", ignoreCase = true) -> {
                            "(?i)(<html[^>]*>)".toRegex().replace(cleanHtml) { match ->
                                "${match.value}<head>$headInjection</head>"
                            }
                        }

                        else -> {
                            "<!DOCTYPE html><html><head>$headInjection</head><body>$cleanHtml</body></html>"
                        }
                    }

                    Log.d(
                        "EpubPro_HTML",
                        "Loading HTML, generation=${loadGeneration}, length=${preparedHtml.length}, " +
                            "isHorizontal=${settings.isHorizontalPagination}"
                    )
                    Log.d("EpubPro_HTML", "First 1000 chars: ${preparedHtml.take(1000)}")

                    webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        preparedHtml,
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        transitionCover?.let { cover ->
            Image(
                bitmap = cover.image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cover.token) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes
                                    .forEach { change -> change.consume() }
                            }
                        }
                    }
            )
        }
    }
}
