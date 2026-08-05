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
import com.epubpro.domain.model.TtsSettings
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

    DisposableEffect(hostView, uiState.settings.keepScreenOn) {
        val previousKeepScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = uiState.settings.keepScreenOn
        onDispose { hostView.keepScreenOn = previousKeepScreenOn }
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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fullscreen Reader Content Layer (Never resizes when controls toggle)
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.chapters.isNotEmpty()) {
            if (uiState.currentChapterHtml.isNotEmpty()) {
                val activeChunkIndex = (uiState.ttsPlayerState as? TtsPlayerState.Playing)?.currentChunk?.paragraphIndex
                    ?: (uiState.ttsPlayerState as? TtsPlayerState.Paused)?.currentChunk?.paragraphIndex

                EpubWebView(
                    htmlContent = uiState.currentChapterHtml,
                    previousChapterHtml = uiState.previousChapterHtml,
                    nextChapterHtml = uiState.nextChapterHtml,
                    initialPage = uiState.initialPageRequest,
                    initialVisibleParagraphIndex = uiState.firstVisibleParagraphIndex,
                    settings = uiState.settings,
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
                    IconButton(onClick = { viewModel.onTtsIconButtonClicked() }) {
                        Icon(
                            imageVector = Icons.Default.Headset,
                            contentDescription = stringResource(R.string.reader_tts),
                            tint = if (uiState.isTtsSpeaking) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
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
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
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
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
            ) {
                val progressText = if (uiState.settings.isHorizontalPagination) {
                    "Trang ${uiState.currentPageInChapter} / ${uiState.totalPagesInChapter}"
                } else {
                    "Chương ${uiState.currentChapterIndex + 1} / ${uiState.chapters.size.coerceAtLeast(1)}"
                }
                Text(
                    text = progressText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
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
                    ttsSettings = uiState.ttsSettings,
                    onSettingsChanged = viewModel::updateSettings,
                    onTtsSettingsChanged = { newTts: TtsSettings ->
                        viewModel.updateTtsSettings(newTts, ttsService)
                    },
                    onOpenTtsSetup = {
                        showSettingsSheet = false
                        viewModel.openTtsSetupBottomSheet()
                    }
                )
            }
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
            val availableVoices = remember(ttsService, uiState.ttsSettings.language) {
                ttsService?.getAvailableVoices(uiState.ttsSettings.language) ?: emptyList()
            }
            TtsSetupBottomSheet(
                currentSettings = uiState.ttsSettings,
                availableVoices = availableVoices,
                onDismiss = viewModel::dismissTtsSetupBottomSheet,
                onPreviewVoice = { draftSettings ->
                    ttsService?.speakPreview(
                        sampleText = "Xin chào, đây là giọng đọc thử nghiệm của ứng dụng EpubPro.",
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
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpubWebView(
    htmlContent: String,
    previousChapterHtml: String?,
    nextChapterHtml: String?,
    initialPage: Int,
    initialVisibleParagraphIndex: Int,
    settings: ReaderSettings,
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
            val css = CssInjector.generateCss(settings)
            val jsScript = CssInjector.generateJsBridgeScript(
                isHorizontalPagination = settings.isHorizontalPagination,
                initialPage = initialPage,
                initialVisibleParagraphIndex = initialVisibleParagraphIndex,
                settings = settings,
                previousChapterHtml = previousPreviewHtml,
                nextChapterHtml = nextPreviewHtml
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
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    ttsSettings: TtsSettings = TtsSettings(),
    onSettingsChanged: (ReaderSettings) -> Unit,
    onTtsSettingsChanged: (TtsSettings) -> Unit = {},
    onOpenTtsSetup: () -> Unit = {}
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

        Text(stringResource(R.string.reader_font_size_format, draftFontSizeSp.toInt()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Slider(
            value = draftFontSizeSp,
            onValueChange = { draftFontSizeSp = it },
            onValueChangeFinished = { onSettingsChanged(settings.copy(fontSizeSp = draftFontSizeSp)) },
            valueRange = 12f..32f,
            steps = 10
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = Color(0xFFE2E8F0)
        )

        // --- SECTION 4: Cấu hình Audio / TTS ---
        SettingSectionHeader(title = stringResource(R.string.profile_audio_settings_title), icon = Icons.Default.VolumeUp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reader_use_ai_voice), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.reader_use_ai_voice_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = ttsSettings.isAiVoice,
                onCheckedChange = { onTtsSettingsChanged(ttsSettings.copy(isAiVoice = it)) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(stringResource(R.string.audio_speech_rate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { sp ->
                FilterChip(
                    selected = ttsSettings.speed == sp,
                    onClick = { onTtsSettingsChanged(ttsSettings.copy(speed = sp)) },
                    label = { Text("${sp}x", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenTtsSetup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.reader_config_voice_detail))
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
