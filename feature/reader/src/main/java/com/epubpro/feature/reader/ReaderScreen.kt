package com.epubpro.feature.reader

import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubpro.core.designsystem.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.reader.bridge.ReaderJsBridge
import com.epubpro.core.reader.style.CssInjector
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.MAX_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.MIN_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.PAGE_TURN_SPEED_PRESETS_MS
import com.epubpro.domain.model.ReaderThemeMode
import org.json.JSONObject

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.epubpro.core.reader.tts.TtsService
import com.epubpro.domain.model.ContentFilterPreferences
import com.epubpro.domain.model.TtsPlayerState
import com.epubpro.feature.reader.tts.TtsAudioPlayerScreen
import com.epubpro.feature.reader.tts.TtsMiniPlayerBar
import com.epubpro.feature.reader.tts.TtsSetupBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTocDrawer by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val hostView = LocalView.current
    var ttsService by remember { mutableStateOf<TtsService?>(null) }
    val window = (context as? android.app.Activity)?.window

    DisposableEffect(window, uiState.settings.keepScreenOn) {
        if (window != null) {
            if (uiState.settings.keepScreenOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? TtsService.TtsBinder
                ttsService = binder?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ttsService = null
            }
        }
        val intent = Intent(context, TtsService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(uiState.ttsPlayerState, uiState.isLoading, uiState.displayedChapterHtml) {
        if (uiState.ttsPlayerState == TtsPlayerState.Loading && !uiState.isLoading && uiState.displayedChapterHtml.isNotEmpty()) {
            ttsService?.let { viewModel.startTtsServicePlayback(it) }
        }
    }

    val (readerBgColor, readerBarBgColor, readerContentColor) = when (uiState.settings.themeMode) {
        ReaderThemeMode.LIGHT -> Triple(
            Color(0xFFFFFFFF),
            Color(0xFFF1F5F9),
            Color(0xFF0F172A)
        )
        ReaderThemeMode.DARK -> Triple(
            Color(0xFF0F172A),
            Color(0xFF1E293B),
            Color(0xFFF8FAFC)
        )
        ReaderThemeMode.SEPIA -> Triple(
            Color(0xFFFBF0D9),
            Color(0xFFEFE0C2),
            Color(0xFF3B2F23)
        )
        ReaderThemeMode.PAPER -> Triple(
            Color(0xFFF5F0E8),
            Color(0xFFE5DDD0),
            Color(0xFF2C2825)
        )
        ReaderThemeMode.MIDNIGHT -> Triple(
            Color(0xFF000000),
            Color(0xFF18181B),
            Color(0xFFE2E8F0)
        )
    }
    val isDarkTheme = uiState.settings.themeMode in listOf(ReaderThemeMode.DARK, ReaderThemeMode.MIDNIGHT)

    val currentStatusBarColor = if (uiState.showControls) readerBarBgColor else readerBgColor

    DisposableEffect(currentStatusBarColor, readerBgColor, isDarkTheme) {
        val window = (context as? android.app.Activity)?.window
        val originalStatusBarColor = window?.statusBarColor
        val originalNavBarColor = window?.navigationBarColor
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView) }
        val originalLightStatus = controller?.isAppearanceLightStatusBars ?: true
        val originalLightNav = controller?.isAppearanceLightNavigationBars ?: true

        if (window != null) {
            window.statusBarColor = currentStatusBarColor.toArgb()
            window.navigationBarColor = readerBgColor.toArgb()
            controller?.isAppearanceLightStatusBars = !isDarkTheme
            controller?.isAppearanceLightNavigationBars = !isDarkTheme
        }

        onDispose {
            if (window != null && originalStatusBarColor != null && originalNavBarColor != null) {
                window.statusBarColor = originalStatusBarColor
                window.navigationBarColor = originalNavBarColor
                controller?.isAppearanceLightStatusBars = originalLightStatus
                controller?.isAppearanceLightNavigationBars = originalLightNav
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBgColor)
    ) {
        // Fullscreen Reader Content Layer (Never resizes when controls toggle)
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.loadError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Không thể tải nội dung sách",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.loadError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = viewModel::retryLoad) {
                    Text("Thử lại")
                }
            }
        } else if (uiState.chapters.isNotEmpty()) {
            if (uiState.displayedChapterHtml.isNotEmpty()) {
                val activeChunkIndex = (uiState.ttsPlayerState as? TtsPlayerState.Playing)?.currentChunk?.paragraphIndex
                    ?: (uiState.ttsPlayerState as? TtsPlayerState.Paused)?.currentChunk?.paragraphIndex

                EpubWebView(
                    htmlContent = uiState.displayedChapterHtml,
                    previousChapterHtml = uiState.previousChapterHtml,
                    nextChapterHtml = uiState.nextChapterHtml,
                    initialPage = uiState.initialPageRequest,
                    initialVisibleParagraphIndex = uiState.firstVisibleParagraphIndex,
                    settings = uiState.settings,
                    filterPreferences = uiState.filterPreferences,
                    activeTtsParagraphIndex = activeChunkIndex,
                    onPageTapped = viewModel::toggleControls,
                    onPageChanged = viewModel::updatePageMetrics,
                    onNextChapter = viewModel::nextChapter,
                    onPreviousChapter = viewModel::previousChapter,
                    onTextSelected = { json ->
                        try {
                            val obj = JSONObject(json)
                            val text = obj.optString("selectedText")
                            if (text.isNotBlank()) {
                                viewModel.addHighlight(text)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onCfiChanged = viewModel::updateCfiPosition
                )
            }
        }

        // Floating Top Header Bar Overlay
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = uiState.book?.title ?: stringResource(R.string.reader_loading),
                        maxLines = 1,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openAiBottomSheet) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI thuần Việt",
                            tint = if (
                                uiState.contentVersion == ReaderContentVersion.AI ||
                                uiState.isAiProcessing
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                readerContentColor
                            }
                        )
                    }
                    IconButton(onClick = { viewModel.onTtsIconButtonClicked() }) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = stringResource(R.string.reader_tts),
                            tint = if (uiState.isTtsSpeaking) MaterialTheme.colorScheme.primary else readerContentColor
                        )
                    }
                    IconButton(onClick = { viewModel.addBookmark() }) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = stringResource(R.string.reader_save_bookmark))
                    }
                    IconButton(onClick = { showTocDrawer = true }) {
                        Icon(Icons.Default.List, contentDescription = stringResource(R.string.reader_toc))
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.FormatSize, contentDescription = stringResource(R.string.reader_display_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = readerBarBgColor.copy(alpha = 0.98f),
                    titleContentColor = readerContentColor,
                    actionIconContentColor = readerContentColor,
                    navigationIconContentColor = readerContentColor
                )
            )
        }

        // Floating Bottom Navigation Bar Overlay
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = readerBarBgColor.copy(alpha = 0.98f),
                contentColor = readerContentColor,
                tonalElevation = 8.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = viewModel::previousChapter,
                        enabled = uiState.currentChapterIndex > 0
                    ) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = stringResource(R.string.reader_prev_chapter))
                    }

                    val progressText = if (uiState.settings.isHorizontalPagination) {
                        "Trang ${uiState.currentPageInChapter} / ${uiState.totalPagesInChapter} • Chương ${uiState.currentChapterIndex + 1}"
                    } else {
                        "Chương ${uiState.currentChapterIndex + 1} / ${uiState.chapters.size.coerceAtLeast(1)}"
                    }

                    if (uiState.settings.showStatusBar) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = viewModel::nextChapter,
                        enabled = uiState.currentChapterIndex < uiState.chapters.size - 1
                    ) {
                        Icon(Icons.Default.NavigateNext, contentDescription = stringResource(R.string.reader_next_chapter))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !uiState.showControls && uiState.settings.showStatusBar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            val progressText = if (uiState.settings.isHorizontalPagination) {
                "Trang ${uiState.currentPageInChapter} / ${uiState.totalPagesInChapter}"
            } else {
                "Chương ${uiState.currentChapterIndex + 1} / ${uiState.chapters.size.coerceAtLeast(1)}"
            }
            Text(
                text = progressText,
                color = readerContentColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 10.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Table of Contents Drawer Sheet
        if (showTocDrawer) {
            val tocListState = rememberLazyListState(
                initialFirstVisibleItemIndex = (uiState.currentChapterIndex - 1).coerceAtLeast(0)
            )

            ModalBottomSheet(onDismissRequest = { showTocDrawer = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Mục Lục",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(
                        state = tocListState
                    ) {
                        itemsIndexed(uiState.chapters) { index, ch ->
                            val isCurrent = index == uiState.currentChapterIndex
                            ListItem(
                                headlineContent = {
                                    Text(
                                        ch.title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingContent = {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Default.Book,
                                            contentDescription = stringResource(R.string.reader_reading_now),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    viewModel.onChapterSelected(index)
                                    showTocDrawer = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSettingsSheet) {
            ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
                ReaderSettingsContent(
                    settings = uiState.settings,
                    onSettingsChanged = viewModel::updateSettings
                )
            }
        }

        if (uiState.showAiBottomSheet) {
            AiVietnameseBottomSheet(
                uiState = uiState,
                chapterTitle = uiState.chapters
                    .getOrNull(uiState.currentChapterIndex)
                    ?.title
                    ?: "Chương " + (uiState.currentChapterIndex + 1),
                onDismiss = viewModel::dismissAiBottomSheet,
                onSaveConfiguration = viewModel::saveAiConfiguration,
                onTestConnection = viewModel::testAiConnection,
                onClearApiKey = viewModel::clearAiApiKey,
                onSelectVersion = viewModel::setContentVersion,
                onStartPolish = viewModel::startAiPolish,
                onCancelPolish = viewModel::cancelAiPolish,
                onDeleteChapter = viewModel::deleteCurrentAiChapter,
                onSaveRule = viewModel::saveAiRule,
                onDeleteRule = viewModel::deleteAiRule
            )
        }
        // Floating Mini Player Bar at bottom
        if (!uiState.showTtsPlayerScreen && uiState.ttsPlayerState !is TtsPlayerState.Idle) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (uiState.showControls) 64.dp else 12.dp)
            ) {
                TtsMiniPlayerBar(
                    playerState = uiState.ttsPlayerState,
                    onExpand = viewModel::openTtsPlayerScreen,
                    onPlayPause = { viewModel.toggleTtsPlayback(ttsService) },
                    onClose = { viewModel.stopTtsPlayback(ttsService) }
                )
            }
        }

        // Tts Setup Bottom Sheet
        if (uiState.showTtsSetupBottomSheet) {
            TtsSetupBottomSheet(
                currentSettings = uiState.ttsSettings,
                onGetAvailableVoices = { isAiVoice, language ->
                    ttsService?.getAvailableVoices(isAiVoice, language) ?: emptyList()
                },
                onDismiss = viewModel::dismissTtsSetupBottomSheet,
                onPreviewVoice = { draftSettings ->
                    ttsService?.speakPreview(
                        sampleText = if (draftSettings.language == "en") "Hello, this is the selected EpubPro voice." else "Xin ch\u00e0o, \u0111\u00e2y l\u00e0 gi\u1ecdng \u0111\u1ecdc th\u1eed nghi\u1ec7m c\u1ee7a \u1ee9ng d\u1ee5ng EpubPro.",
                        settings = draftSettings
                    )
                },
                onStartListening = { newSettings ->
                    viewModel.onStartListeningFromSetup(newSettings, ttsService)
                }
            )
        }

        // Fullscreen Tts Audio Player Dialog / Screen
        if (uiState.showTtsPlayerScreen && !uiState.showTtsSetupBottomSheet) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = viewModel::closeTtsPlayerScreen,
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                TtsAudioPlayerScreen(
                    bookTitle = uiState.book?.title ?: "EpubPro Book",
                    author = uiState.book?.author ?: "Tác giả",
                    playerState = uiState.ttsPlayerState,
                    settings = uiState.ttsSettings,
                    selectedSleepTimer = uiState.selectedSleepTimer,
                    onCollapse = viewModel::closeTtsPlayerScreen,
                    onPlayPause = { viewModel.toggleTtsPlayback(ttsService) },
                    onSkipNext = { viewModel.nextTtsChunk(ttsService) },
                    onSkipPrevious = { viewModel.prevTtsChunk(ttsService) },
                    onOpenSetup = { viewModel.openTtsSetupBottomSheet() },
                    onSelectSleepTimer = { option ->
                        viewModel.setSleepTimerOption(option, ttsService)
                    },
                    onSeekToChunk = { index ->
                        viewModel.seekTtsChunk(index, ttsService)
                    }
                )
            }
        }
    }
}

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
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun EpubWebView(
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
    val jsBridge = remember {
        ReaderJsBridge(
            onTextSelectedListener = onTextSelected,
            onCfiChangedListener = onCfiChanged,
            onPageTappedListener = onPageTapped,
            onPageChangedListener = onPageChanged,
            onNextChapterListener = onNextChapter,
            onPreviousChapterListener = onPreviousChapter
        )
    }

    var loadedHtmlKey by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(activeTtsParagraphIndex) {
        if (activeTtsParagraphIndex != null && webViewRef != null) {
            webViewRef?.evaluateJavascript(
                "if (typeof epubproHighlightTtsParagraph === 'function') { epubproHighlightTtsParagraph($activeTtsParagraphIndex); }",
                null
            )
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
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
            webView.isVerticalScrollBarEnabled = settings.showScrollBar && !settings.isHorizontalPagination
            webView.isHorizontalScrollBarEnabled = settings.showScrollBar && settings.isHorizontalPagination
            webView.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false

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
                    settings.enableVolumeKeyNavigation && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN -> 1
                    settings.enableVolumeKeyNavigation && keyCode == KeyEvent.KEYCODE_VOLUME_UP -> -1
                    else -> 0
                }

                if (direction == 0) {
                    false
                } else {
                    val functionName = if (direction > 0) "epubproGoNextPage" else "epubproGoPrevPage"
                    webView.evaluateJavascript("if (typeof $functionName === 'function') { $functionName(); }", null)
                    true
                }
            }

            val runtimeSpeedMs = if (settings.enablePageAnimation) settings.pageTurnSpeedMs else 0
            val runtimeActions = JSONObject.quote(settings.tapZoneActions.joinToString(",") { it.name })
            webView.evaluateJavascript(
                "if (typeof epubproApplyRuntimeSettings === 'function') { epubproApplyRuntimeSettings($runtimeSpeedMs, $runtimeActions); }",
                null
            )

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
                filterPreferences = filterPreferences
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

            val newHtmlKey = "${htmlContent.hashCode()}_${previousChapterHtml?.hashCode()}_${nextChapterHtml?.hashCode()}_${settings.contentReloadKey()}"
            if (loadedHtmlKey != newHtmlKey) {
                loadedHtmlKey = newHtmlKey
                Log.d("EpubPro_HTML", "Loading HTML, length=${preparedHtml.length}, isHorizontal=${settings.isHorizontalPagination}")
                Log.d("EpubPro_HTML", "First 1000 chars: ${preparedHtml.take(1000)}")

                webView.loadDataWithBaseURL("file:///android_asset/", preparedHtml, "text/html", "utf-8", null)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    onSettingsChanged: (ReaderSettings) -> Unit
) {
    var draftPageTurnSpeedMs by remember(settings.pageTurnSpeedMs) { mutableIntStateOf(settings.pageTurnSpeedMs) }
    var draftFontSizeSp by remember(settings.fontSizeSp) { mutableFloatStateOf(settings.fontSizeSp) }
    var draftMarginTopDp by remember(settings.marginTopDp) { mutableIntStateOf(settings.marginTopDp) }
    var draftMarginBottomDp by remember(settings.marginBottomDp) { mutableIntStateOf(settings.marginBottomDp) }
    var draftMarginLeftDp by remember(settings.marginLeftDp) { mutableIntStateOf(settings.marginLeftDp) }
    var draftMarginRightDp by remember(settings.marginRightDp) { mutableIntStateOf(settings.marginRightDp) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Cấu hình đọc sách",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- SECTION 1: Chế độ đọc ---
        SettingSectionHeader(title = "Chế độ đọc", icon = Icons.Default.MenuBook)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_horizontal_scroll), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.reader_horizontal_scroll_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.isHorizontalPagination,
                onCheckedChange = { isHorizontal ->
                    onSettingsChanged(
                        settings.copy(
                            isHorizontalPagination = isHorizontal,
                            readingMode = if (isHorizontal) {
                                com.epubpro.domain.model.ReadingMode.FLIP
                            } else {
                                com.epubpro.domain.model.ReadingMode.SCROLL
                            }
                        )
                    )
                }
            )
        }

        if (settings.isHorizontalPagination) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tốc độ trượt lật trang: $draftPageTurnSpeedMs ms",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = draftPageTurnSpeedMs.toFloat(),
                onValueChange = { draftPageTurnSpeedMs = it.toInt() },
                onValueChangeFinished = {
                    onSettingsChanged(settings.copy(pageTurnSpeedMs = draftPageTurnSpeedMs))
                },
                valueRange = MIN_PAGE_TURN_SPEED_MS.toFloat()..MAX_PAGE_TURN_SPEED_MS.toFloat()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PAGE_TURN_SPEED_PRESETS_MS.zip(listOf("Nhanh", "Vừa", "Chậm")).forEach { (speed, name) ->
                    FilterChip(
                        selected = draftPageTurnSpeedMs == speed,
                        onClick = {
                            draftPageTurnSpeedMs = speed
                            onSettingsChanged(settings.copy(pageTurnSpeedMs = speed))
                        },
                        label = { Text("$name (${speed}ms)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = Color(0xFFE2E8F0)
        )

        // --- SECTION 2: Phông chữ & Kiểu chữ ---
        SettingSectionHeader(title = stringResource(R.string.reader_font_family), icon = Icons.Default.TextFields)
        Spacer(modifier = Modifier.height(10.dp))

        Text(stringResource(R.string.reader_font_family), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val fonts = listOf(
                "serif" to "Có chân",
                "sans-serif" to "Không chân",
                "monospace" to "Đơn cách"
            )
            fonts.forEach { (fontFamily, label) ->
                FontFamilyChip(
                    label = label,
                    isSelected = settings.fontFamily.equals(fontFamily, ignoreCase = true),
                    onClick = { onSettingsChanged(settings.copy(fontFamily = fontFamily)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val formattedFontSize = if (draftFontSizeSp % 1f == 0f) {
            "${draftFontSizeSp.toInt()}"
        } else {
            "%.1f".format(java.util.Locale.US, draftFontSizeSp)
        }
        Text(stringResource(R.string.reader_font_size_format, formattedFontSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Slider(
            value = draftFontSizeSp,
            onValueChange = { draftFontSizeSp = kotlin.math.round(it * 2f) / 2f },
            onValueChangeFinished = { onSettingsChanged(settings.copy(fontSizeSp = draftFontSizeSp)) },
            valueRange = 12f..32f,
            steps = 39
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(stringResource(R.string.reader_line_spacing), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1.2f to "1.2x", 1.5f to "1.5x", 1.8f to "1.8x", 2.0f to "2.0x").forEach { (ratio, label) ->
                FilterChip(
                    selected = settings.lineHeightRatio == ratio,
                    onClick = { onSettingsChanged(settings.copy(lineHeightRatio = ratio)) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = Color(0xFFE2E8F0)
        )

        // --- SECTION 3: Phông nền & Màu sắc ---
        SettingSectionHeader(title = stringResource(R.string.profile_appearance_title), icon = Icons.Default.Palette)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThemeChip("Sáng", Color.White, Color.Black, settings.themeMode == ReaderThemeMode.LIGHT) {
                onSettingsChanged(settings.copy(themeMode = ReaderThemeMode.LIGHT))
            }
            ThemeChip("Tối", Color(0xFF1E293B), Color.White, settings.themeMode == ReaderThemeMode.DARK) {
                onSettingsChanged(settings.copy(themeMode = ReaderThemeMode.DARK))
            }
            ThemeChip("Sepia", Color(0xFFFBF0D9), Color(0xFF4A3B32), settings.themeMode == ReaderThemeMode.SEPIA) {
                onSettingsChanged(settings.copy(themeMode = ReaderThemeMode.SEPIA))
            }
            ThemeChip("Giấy", Color(0xFFF5F0E8), Color(0xFF3C3530), settings.themeMode == ReaderThemeMode.PAPER) {
                onSettingsChanged(settings.copy(themeMode = ReaderThemeMode.PAPER))
            }
            ThemeChip("Đêm", Color(0xFF0F172A), Color(0xFF94A3B8), settings.themeMode == ReaderThemeMode.MIDNIGHT) {
                onSettingsChanged(settings.copy(themeMode = ReaderThemeMode.MIDNIGHT))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.reader_page_margin), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_margin_top_format, draftMarginTopDp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = draftMarginTopDp.toFloat(),
                    onValueChange = { draftMarginTopDp = it.toInt() },
                    onValueChangeFinished = { onSettingsChanged(settings.copy(marginTopDp = draftMarginTopDp)) },
                    valueRange = 0f..64f
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_margin_bottom_format, draftMarginBottomDp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = draftMarginBottomDp.toFloat(),
                    onValueChange = { draftMarginBottomDp = it.toInt() },
                    onValueChangeFinished = { onSettingsChanged(settings.copy(marginBottomDp = draftMarginBottomDp)) },
                    valueRange = 0f..64f
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_margin_left_format, draftMarginLeftDp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = draftMarginLeftDp.toFloat(),
                    onValueChange = { draftMarginLeftDp = it.toInt() },
                    onValueChangeFinished = { onSettingsChanged(settings.copy(marginLeftDp = draftMarginLeftDp)) },
                    valueRange = 0f..64f
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_margin_right_format, draftMarginRightDp), style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = draftMarginRightDp.toFloat(),
                    onValueChange = { draftMarginRightDp = it.toInt() },
                    onValueChangeFinished = { onSettingsChanged(settings.copy(marginRightDp = draftMarginRightDp)) },
                    valueRange = 0f..64f
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RowScope.FontFamilyChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
fun RowScope.ThemeChip(
    label: String,
    bg: Color,
    fg: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
