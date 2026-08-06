package com.epubpro.core.reader.style

import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.TextAlignment
import org.json.JSONObject

object CssInjector {

    fun generateMetaAndViewport(): String {
        return """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        """.trimIndent()
    }

    fun generateCss(settings: ReaderSettings): String {
        val (bgColor, textColor) = when (settings.themeMode) {
            ReaderThemeMode.LIGHT -> "#FFFFFF" to "#0F172A"
            ReaderThemeMode.DARK -> "#0F172A" to "#F8FAFC"
            ReaderThemeMode.SEPIA -> "#FBF0D9" to "#4A3B32"
            ReaderThemeMode.PAPER -> "#F5F0E8" to "#3C3530"
            ReaderThemeMode.MIDNIGHT -> "#000000" to "#AAAAAA"
        }

        val fontSizePx = if (settings.fontSizeSp % 1f == 0f) {
            "${settings.fontSizeSp.toInt()}"
        } else {
            "%.1f".format(java.util.Locale.US, settings.fontSizeSp)
        }
        val textAlign = if (settings.textAlignment == TextAlignment.JUSTIFY) "justify" else "left"
        val scrollbarCss = if (settings.showScrollBar) "" else """
            ::-webkit-scrollbar {
                width: 0 !important;
                height: 0 !important;
                display: none !important;
            }
        """.trimIndent()

        val paginationCss = if (settings.isHorizontalPagination) {
            """
            html {
                background-color: $bgColor !important;
                color: $textColor !important;
                width: 100vw !important;
                height: 100vh !important;
                margin: 0 !important;
                padding: 0 !important;
                overflow: hidden !important;
            }
            body {
                background-color: $bgColor !important;
                color: $textColor !important;
                font-family: ${settings.fontFamily}, serif, sans-serif !important;
                font-size: ${fontSizePx}px !important;
                line-height: ${settings.lineHeightRatio} !important;
                margin: 0 !important;
                padding-top: ${settings.marginTopDp}px !important;
                padding-bottom: ${settings.marginBottomDp}px !important;
                padding-left: 0 !important;
                padding-right: 0 !important;
                box-sizing: border-box !important;
                word-wrap: break-word !important;
                overflow-wrap: break-word !important;

                width: 100vw !important;
                height: 100vh !important;

                -webkit-column-width: 100vw !important;
                -moz-column-width: 100vw !important;
                column-width: 100vw !important;

                -webkit-column-gap: 0px !important;
                -moz-column-gap: 0px !important;
                column-gap: 0px !important;

                -webkit-column-fill: auto !important;
                -moz-column-fill: auto !important;
                column-fill: auto !important;

                overflow: visible !important;
            }
            body > *,
            .epubpro-page-layer > * {
                margin-left: 0 !important;
                margin-right: 0 !important;
                padding-left: ${settings.marginLeftDp}px !important;
                padding-right: ${settings.marginRightDp}px !important;
                max-width: 100% !important;
                box-sizing: border-box !important;
            }
            body > * *,
            .epubpro-page-layer > * * {
                margin-left: 0 !important;
                margin-right: 0 !important;
                padding-left: 0 !important;
                padding-right: 0 !important;
            }
            """
        } else {
            """
            html {
                background-color: $bgColor !important;
                color: $textColor !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
                height: auto !important;
            }
            body {
                background-color: $bgColor !important;
                color: $textColor !important;
                font-family: ${settings.fontFamily}, serif, sans-serif !important;
                font-size: ${fontSizePx}px !important;
                line-height: ${settings.lineHeightRatio} !important;
                padding-top: ${settings.marginTopDp}px !important;
                padding-bottom: ${settings.marginBottomDp}px !important;
                padding-left: ${settings.marginLeftDp}px !important;
                padding-right: ${settings.marginRightDp}px !important;
                margin: 0 auto !important;
                box-sizing: border-box !important;
                word-wrap: break-word !important;
                overflow-wrap: break-word !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
                height: auto !important;
                max-width: 100% !important;
            }
            """
        }

        return """
            <style id="epubpro-injected-style">
                p, span, blockquote, li {
                    color: $textColor !important;
                    line-height: ${settings.lineHeightRatio} !important;
                }
                p {
                    margin-top: 0 !important;
                    margin-bottom: ${settings.paragraphSpacingDp}px !important;
                    text-indent: ${settings.firstLineIndentDp}px !important;
                    text-align: $textAlign !important;
                    word-break: break-word !important;
                    overflow-wrap: break-word !important;
                }
                p:empty {
                    display: none !important;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: $textColor !important;
                    line-height: 1.3 !important;
                    margin-top: 0.5em !important;
                    margin-bottom: 0.5em !important;
                }
                img, svg, video, iframe {
                    max-width: 100% !important;
                    max-height: calc(100vh - ${settings.marginTopDp + settings.marginBottomDp + 16}px) !important;
                    object-fit: contain !important;
                    height: auto !important;
                    display: block !important;
                    margin: 0.5em auto !important;
                }
                * {
                    max-width: 100% !important;
                    box-sizing: border-box !important;
                }
                a {
                    color: inherit !important;
                    text-decoration: none !important;
                }
                ::selection {
                    background: rgba(30, 58, 138, 0.3) !important;
                }
                .tts-active-paragraph {
                    background-color: rgba(217, 119, 87, 0.25) !important;
                    border-left: 4px solid #D97757 !important;
                    border-radius: 4px !important;
                    padding-left: 4px !important;
                    transition: background-color 0.3s ease !important;
                }
                $scrollbarCss
                $paginationCss
            </style>
        """.trimIndent()
    }

    fun generateJsBridgeScript(
        isHorizontalPagination: Boolean,
        initialPage: Int = 1,
        initialVisibleParagraphIndex: Int = 0,
        settings: ReaderSettings = ReaderSettings(),
        previousChapterHtml: String? = null,
        nextChapterHtml: String? = null
    ): String {
        val (bgColor, _) = when (settings.themeMode) {
            ReaderThemeMode.LIGHT -> "#FFFFFF" to "#0F172A"
            ReaderThemeMode.DARK -> "#0F172A" to "#F8FAFC"
            ReaderThemeMode.SEPIA -> "#FBF0D9" to "#4A3B32"
            ReaderThemeMode.PAPER -> "#F5F0E8" to "#3C3530"
            ReaderThemeMode.MIDNIGHT -> "#000000" to "#AAAAAA"
        }
        fun quoteForInlineScript(value: String): String = JSONObject.quote(value)
            .replace("<", "\\u003C")
            .replace(">", "\\u003E")
            .replace("&", "\\u0026")

        val previousChapterHtmlJson = quoteForInlineScript(previousChapterHtml.orEmpty())
        val nextChapterHtmlJson = quoteForInlineScript(nextChapterHtml.orEmpty())
        val tapZoneActionsJson = JSONObject.quote(settings.tapZoneActions.joinToString(",") { it.name })

        return """
            (function() {
                window.epubproIsHorizontal = $isHorizontalPagination;
                var targetInitPage = $initialPage;
                var targetInitParagraph = $initialVisibleParagraphIndex;
                var marginTop = ${settings.marginTopDp};
                var marginBottom = ${settings.marginBottomDp};
                var marginLeft = ${settings.marginLeftDp};
                var marginRight = ${settings.marginRightDp};
                var transitionSpeedMs = ${if (settings.enablePageAnimation) settings.pageTurnSpeedMs else 0};
                var currentPage = targetInitPage;
                var totalPages = 1;
                var startTouchX = 0;
                var startTouchY = 0;
                var startTouchTime = 0;
                var isExecutingScroll = false;

                function dbg(tag, msg) {
                    try {
                        if (window.ReaderJsBridge && window.ReaderJsBridge.onDebugLog) {
                            window.ReaderJsBridge.onDebugLog(tag, msg);
                        }
                    } catch(e) {}
                }

                function forceBodyDimensions() {
                    if (!window.epubproIsHorizontal) return;
                    var html = document.documentElement;
                    var body = document.body;
                    if (!html || !body) return;

                    var vh = window.innerHeight;
                    var vw = window.innerWidth;

                    // Force html dimensions via inline !important
                    html.style.setProperty('height', vh + 'px', 'important');
                    html.style.setProperty('width', vw + 'px', 'important');
                    html.style.setProperty('margin', '0', 'important');
                    html.style.setProperty('padding', '0', 'important');
                    html.style.setProperty('overflow', 'hidden', 'important');

                    // Force body dimensions via inline !important
                    body.style.setProperty('height', vh + 'px', 'important');
                    body.style.setProperty('width', vw + 'px', 'important');
                    body.style.setProperty('margin', '0', 'important');
                    body.style.setProperty('padding-top', marginTop + 'px', 'important');
                    body.style.setProperty('padding-bottom', marginBottom + 'px', 'important');
                    body.style.setProperty('padding-left', '0px', 'important');
                    body.style.setProperty('padding-right', '0px', 'important');
                    body.style.setProperty('box-sizing', 'border-box', 'important');
                    body.style.setProperty('column-width', vw + 'px', 'important');
                    body.style.setProperty('-webkit-column-width', vw + 'px', 'important');
                    body.style.setProperty('column-gap', '0px', 'important');
                    body.style.setProperty('-webkit-column-gap', '0px', 'important');
                    body.style.setProperty('column-fill', 'auto', 'important');
                    body.style.setProperty('-webkit-column-fill', 'auto', 'important');
                    body.style.setProperty('overflow', 'visible', 'important');
                    body.style.setProperty('word-wrap', 'break-word', 'important');
                    body.style.setProperty('overflow-wrap', 'break-word', 'important');

                    dbg('FORCE', 'vh=' + vh + ' vw=' + vw + ' bodyH=' + body.clientHeight);
                }

                function dumpLayout() {
                    try {
                        var html = document.documentElement;
                        var body = document.body;
                        if (!html || !body) { dbg('DUMP', 'html or body is null'); return; }

                        var htmlCS = window.getComputedStyle(html);
                        var bodyCS = window.getComputedStyle(body);

                        dbg('HTML', 'w=' + html.clientWidth + ' h=' + html.clientHeight
                            + ' scrollW=' + html.scrollWidth + ' scrollH=' + html.scrollHeight
                            + ' overflow=' + htmlCS.overflow);

                        dbg('BODY', 'w=' + body.clientWidth + ' h=' + body.clientHeight
                            + ' scrollW=' + body.scrollWidth + ' scrollH=' + body.scrollHeight
                            + ' colWidth=' + bodyCS.columnWidth + ' colGap=' + bodyCS.columnGap
                            + ' height=' + bodyCS.height + ' overflow=' + bodyCS.overflow
                            + ' bodyStyleAttr=' + (body.getAttribute('style') || 'none'));

                        dbg('WINDOW', 'innerW=' + window.innerWidth + ' innerH=' + window.innerHeight
                            + ' scrollX=' + window.scrollX);
                    } catch(e) {
                        dbg('ERROR', 'dumpLayout: ' + e.message);
                    }
                }

                function getFirstVisibleParagraphIndex() {
                    try {
                        var paragraphs = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote');
                        var screenWidth = window.innerWidth || document.documentElement.clientWidth || 1;
                        var screenHeight = window.innerHeight || document.documentElement.clientHeight || 1;

                        for (var i = 0; i < paragraphs.length; i++) {
                            var rect = paragraphs[i].getBoundingClientRect();
                            if (window.epubproIsHorizontal) {
                                if (rect.right > 10 && rect.left < screenWidth - 10 && rect.bottom > 0 && rect.top < screenHeight) {
                                    return i;
                                }
                            } else {
                                if (rect.bottom > 0 && rect.top < screenHeight) {
                                    return i;
                                }
                            }
                        }
                    } catch(e) {}
                    return 0;
                }

                var ignoreScrollMetrics = false;
                var ignoreScrollTimeout = null;

                function suppressScrollMetrics(durationMs) {
                    ignoreScrollMetrics = true;
                    if (ignoreScrollTimeout) clearTimeout(ignoreScrollTimeout);
                    ignoreScrollTimeout = setTimeout(function() {
                        ignoreScrollMetrics = false;
                    }, durationMs || 250);
                }

                function measureTotalPages() {
                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var sw = document.body ? document.body.scrollWidth : pw;
                    totalPages = Math.max(1, Math.ceil((sw - 1) / pw));
                    return pw;
                }

                function updatePageMetrics() {
                    if (!window.epubproIsHorizontal) {
                        if (window.ReaderJsBridge && window.ReaderJsBridge.onPageChanged) {
                            window.ReaderJsBridge.onPageChanged(1, 1, getFirstVisibleParagraphIndex());
                        }
                        return;
                    }
                    var pw = measureTotalPages();

                    if (isExecutingScroll || isDraggingPage || isCoverOverlayActive || ignoreScrollMetrics) return;
                    var sl = window.scrollX || window.pageXOffset || 0;
                    currentPage = Math.min(totalPages, Math.max(1, Math.round(sl / pw) + 1));

                    dbg('METRICS', 'pw=' + pw + ' totalPages=' + totalPages + ' currentPage=' + currentPage);

                    var visibleIndex = getFirstVisibleParagraphIndex();

                    if (window.ReaderJsBridge && window.ReaderJsBridge.onPageChanged) {
                        window.ReaderJsBridge.onPageChanged(currentPage, totalPages, visibleIndex);
                    }
                }

                function notifyPageChangeCompleted() {
                    if (isDraggingPage || isCoverOverlayActive) return;
                    if (window.ReaderJsBridge && window.ReaderJsBridge.onPageChanged) {
                        var visibleIndex = getFirstVisibleParagraphIndex();
                        window.ReaderJsBridge.onPageChanged(currentPage, totalPages, visibleIndex);
                    }
                }

                function scrollToPage(page, animate) {
                    if (!window.epubproIsHorizontal) return;
                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var targetX = (page - 1) * pw;
                    isExecutingScroll = true;
                    currentPage = page;

                    if (animate !== false && transitionSpeedMs > 0) {
                        var startX = window.scrollX || window.pageXOffset;
                        var distance = targetX - startX;
                        var duration = transitionSpeedMs;
                        var startTime = null;

                        function step(timestamp) {
                            if (!startTime) startTime = timestamp;
                            var progress = timestamp - startTime;
                            var percent = Math.min(progress / duration, 1);
                            var easeOut = 1 - Math.pow(1 - percent, 3);
                            window.scrollTo(startX + distance * easeOut, 0);

                            if (progress < duration) {
                                window.requestAnimationFrame(step);
                            } else {
                                window.scrollTo(targetX, 0);
                                isExecutingScroll = false;
                                suppressScrollMetrics(100);
                                notifyPageChangeCompleted();
                            }
                        }
                        window.requestAnimationFrame(step);
                    } else {
                        window.scrollTo(targetX, 0);
                        setTimeout(function() {
                            isExecutingScroll = false;
                            suppressScrollMetrics(100);
                            notifyPageChangeCompleted();
                        }, 50);
                    }
                }

                window.epubproGoNextPage = function() {
                    if (!window.epubproIsHorizontal) {
                        var root = document.scrollingElement || document.documentElement;
                        var atEnd = root.scrollTop + window.innerHeight >= root.scrollHeight - 2;
                        if (atEnd && window.ReaderJsBridge && window.ReaderJsBridge.onNextChapterRequested) {
                            window.ReaderJsBridge.onNextChapterRequested();
                        } else {
                            window.scrollBy({ top: window.innerHeight * 0.85, behavior: transitionSpeedMs > 0 ? 'smooth' : 'auto' });
                        }
                        return;
                    }
                    if (currentPage < totalPages) {
                        scrollToPage(currentPage + 1, true);
                    } else if (window.ReaderJsBridge && window.ReaderJsBridge.onNextChapterRequested) {
                        window.ReaderJsBridge.onNextChapterRequested();
                    }
                };

                window.epubproGoPrevPage = function() {
                    if (!window.epubproIsHorizontal) {
                        var root = document.scrollingElement || document.documentElement;
                        if (root.scrollTop <= 2 && window.ReaderJsBridge && window.ReaderJsBridge.onPreviousChapterRequested) {
                            window.ReaderJsBridge.onPreviousChapterRequested();
                        } else {
                            window.scrollBy({ top: -window.innerHeight * 0.85, behavior: transitionSpeedMs > 0 ? 'smooth' : 'auto' });
                        }
                        return;
                    }
                    if (currentPage > 1) {
                        scrollToPage(currentPage - 1, true);
                    } else if (window.ReaderJsBridge && window.ReaderJsBridge.onPreviousChapterRequested) {
                        window.ReaderJsBridge.onPreviousChapterRequested();
                    }
                };

                window.epubproUpdateMetrics = updatePageMetrics;

                var scrollTimeout = null;
                window.addEventListener('scroll', function() {
                    if (ignoreScrollMetrics || isDraggingPage || isCoverOverlayActive) return;
                    if (scrollTimeout) clearTimeout(scrollTimeout);
                    scrollTimeout = setTimeout(updatePageMetrics, 80);
                }, { passive: true });

                window.addEventListener('resize', function() {
                    forceBodyDimensions();
                    setTimeout(function() {
                        updatePageMetrics();
                    }, 100);
                }, { passive: true });

                function initLayout() {
                    dbg('INIT', 'initLayout called, readyState=' + document.readyState + ', targetInitPage=' + targetInitPage);
                    forceBodyDimensions();
                    dumpLayout();
                    window.requestAnimationFrame(function() {
                        window.requestAnimationFrame(function() {
                            measureTotalPages();
                            var paragraphs = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote');
                            var anchor = paragraphs[Math.min(paragraphs.length - 1, Math.max(0, targetInitParagraph))];
                            if (anchor && targetInitParagraph > 0) {
                                if (window.epubproIsHorizontal) {
                                    var pw = window.innerWidth || 1;
                                    var anchorX = anchor.getBoundingClientRect().left + (window.scrollX || 0);
                                    targetInitPage = Math.floor(anchorX / pw) + 1;
                                } else {
                                    anchor.scrollIntoView({ block: 'start', behavior: 'auto' });
                                }
                            }
                            targetInitPage = Math.min(totalPages, Math.max(1, targetInitPage));
                            if (window.epubproIsHorizontal) {
                                scrollToPage(targetInitPage, false);
                            } else {
                                updatePageMetrics();
                            }
                        });
                    });
                }

                if (document.readyState === 'complete') {
                    setTimeout(initLayout, 100);
                } else {
                    window.addEventListener('load', function() {
                        setTimeout(initLayout, 100);
                    });
                }
                setTimeout(initLayout, 500);

                var startTouchX = 0;
                var startTouchY = 0;
                var startTouchTime = 0;
                var startScrollX = 0;
                var currentDeltaX = 0;
                var isDraggingPage = false;
                var isHorizontalDragConfirmed = false;
                var activeTopOverlay = null;
                var activeBottomOverlay = null;
                var isCoverOverlayActive = false;
                var dragDirection = 0;
                var gestureToken = 0;
                var themeBgColor = '$bgColor';
                var previousChapterHtml = $previousChapterHtmlJson;
                var nextChapterHtml = $nextChapterHtmlJson;
                var tapZoneActions = $tapZoneActionsJson.split(',');

                window.epubproApplyRuntimeSettings = function(speedMs, actionsCsv) {
                    transitionSpeedMs = Math.max(0, Number(speedMs) || 0);
                    if (typeof actionsCsv === 'string') {
                        var parsedActions = actionsCsv.split(',');
                        if (parsedActions.length === 9) tapZoneActions = parsedActions;
                    }
                };

                function handleConfiguredTap(x, y) {
                    var width = window.innerWidth || 1;
                    var height = window.innerHeight || 1;
                    var column = Math.min(2, Math.max(0, Math.floor(x / (width / 3))));
                    var row = Math.min(2, Math.max(0, Math.floor(y / (height / 3))));
                    var zoneIndex = row * 3 + column;
                    var action = tapZoneActions[zoneIndex] || 'TOGGLE_CONTROLS';
                    if (action === 'PREV_PAGE') {
                        window.epubproGoPrevPage();
                    } else if (action === 'NEXT_PAGE') {
                        window.epubproGoNextPage();
                    } else if (window.ReaderJsBridge && window.ReaderJsBridge.onPageTapped) {
                        window.ReaderJsBridge.onPageTapped();
                    }
                }

                function cleanupCoverOverlay() {
                    // Restore body scroll ability
                    var body = document.body;
                    if (body) body.style.removeProperty('touch-action');
                    var html = document.documentElement;
                    if (html) html.style.removeProperty('touch-action');
                    if (activeTopOverlay && activeTopOverlay.parentNode) {
                        activeTopOverlay.parentNode.removeChild(activeTopOverlay);
                    }
                    if (activeBottomOverlay && activeBottomOverlay.parentNode) {
                        activeBottomOverlay.parentNode.removeChild(activeBottomOverlay);
                    }
                    if (window.activeBackdropOverlay && window.activeBackdropOverlay.parentNode) {
                        window.activeBackdropOverlay.parentNode.removeChild(window.activeBackdropOverlay);
                    }
                    activeTopOverlay = null;
                    activeBottomOverlay = null;
                    window.activeBackdropOverlay = null;
                    isCoverOverlayActive = false;
                    dragDirection = 0;
                    isDraggingPage = false;
                    isHorizontalDragConfirmed = false;
                    currentDeltaX = 0;
                    suppressScrollMetrics(300);
                }

                function configurePageLayer(layer, pw, vh, bg) {
                    var bodyStyle = window.getComputedStyle(document.body);
                    layer.className = 'epubpro-page-layer';
                    layer.setAttribute('aria-hidden', 'true');
                    layer.style.setProperty('position', 'absolute', 'important');
                    layer.style.setProperty('top', '0px', 'important');
                    layer.style.setProperty('left', '0px', 'important');
                    layer.style.setProperty('display', 'block', 'important');
                    layer.style.setProperty('width', pw + 'px', 'important');
                    layer.style.setProperty('min-width', pw + 'px', 'important');
                    layer.style.setProperty('max-width', 'none', 'important');
                    layer.style.setProperty('height', vh + 'px', 'important');
                    layer.style.setProperty('margin', '0px', 'important');
                    layer.style.setProperty('padding-top', marginTop + 'px', 'important');
                    layer.style.setProperty('padding-bottom', marginBottom + 'px', 'important');
                    layer.style.setProperty('padding-left', '0px', 'important');
                    layer.style.setProperty('padding-right', '0px', 'important');
                    layer.style.setProperty('box-sizing', 'border-box', 'important');
                    layer.style.setProperty('column-width', pw + 'px', 'important');
                    layer.style.setProperty('-webkit-column-width', pw + 'px', 'important');
                    layer.style.setProperty('column-gap', '0px', 'important');
                    layer.style.setProperty('-webkit-column-gap', '0px', 'important');
                    layer.style.setProperty('column-fill', 'auto', 'important');
                    layer.style.setProperty('-webkit-column-fill', 'auto', 'important');
                    layer.style.setProperty('overflow', 'visible', 'important');
                    layer.style.setProperty('pointer-events', 'none', 'important');
                    layer.style.setProperty('touch-action', 'none', 'important');
                    layer.style.setProperty('background-color', bg, 'important');
                    layer.style.setProperty('color', bodyStyle.color, 'important');
                    layer.style.setProperty('font-family', bodyStyle.fontFamily, 'important');
                    layer.style.setProperty('font-size', bodyStyle.fontSize, 'important');
                    layer.style.setProperty('line-height', bodyStyle.lineHeight, 'important');
                }

                function sanitizePageLayer(layer) {
                    var removable = layer.querySelectorAll(
                        'script, #epubpro-top-overlay, #epubpro-bottom-overlay, #epubpro-backdrop-overlay'
                    );
                    for (var i = 0; i < removable.length; i++) {
                        if (removable[i].parentNode) removable[i].parentNode.removeChild(removable[i]);
                    }

                    var elementsWithIds = layer.querySelectorAll('[id]');
                    for (var j = 0; j < elementsWithIds.length; j++) {
                        elementsWithIds[j].removeAttribute('id');
                    }
                }

                function createPageLayerFromNodes(nodes, pw, vh, bg) {
                    var layer = document.createElement('div');
                    for (var i = 0; i < nodes.length; i++) {
                        layer.appendChild(nodes[i].cloneNode(true));
                    }
                    sanitizePageLayer(layer);
                    configurePageLayer(layer, pw, vh, bg);
                    return layer;
                }

                function createCurrentChapterLayer(body, pw, vh, bg) {
                    return createPageLayerFromNodes(body.childNodes, pw, vh, bg);
                }

                function createAdjacentChapterLayer(html, pw, vh, bg) {
                    if (!html || !html.trim()) return null;
                    try {
                        var parsed = new DOMParser().parseFromString(html, 'text/html');
                        if (!parsed.body) return null;
                        return createPageLayerFromNodes(parsed.body.childNodes, pw, vh, bg);
                    } catch (error) {
                        dbg('PREVIEW_ERROR', error.message || 'Unable to parse adjacent chapter');
                        return null;
                    }
                }

                function mountPageOverlay(layer, pageOffset, pw, vh, bg, zIndex, shadowStyle, overlayId) {
                    if (!layer) return null;

                    var wrapper = document.createElement('div');
                    wrapper.style.cssText = 'position:absolute;top:0;left:0;width:' + pw + 'px;height:' + vh + 'px;pointer-events:none;background-color:' + bg + ';';
                    wrapper.appendChild(layer);

                    var overlay = document.createElement('div');
                    overlay.id = overlayId;
                    overlay.style.cssText = 'position:fixed;top:0;left:0;width:' + pw + 'px;height:' + vh + 'px;z-index:' + zIndex + ';overflow:hidden;pointer-events:none;background-color:' + bg + ';will-change:transform;';
                    if (shadowStyle) overlay.style.boxShadow = shadowStyle;
                    overlay.appendChild(wrapper);
                    document.documentElement.appendChild(overlay);

                    var contentWidth = Math.max(pw, layer.scrollWidth);
                    var pageCount = Math.max(1, Math.ceil((contentWidth - 1) / pw));
                    var resolvedOffset = pageOffset === 'last' ? (pageCount - 1) * pw : pageOffset;
                    resolvedOffset = Math.min((pageCount - 1) * pw, Math.max(0, resolvedOffset || 0));
                    wrapper.style.width = contentWidth + 'px';
                    wrapper.style.transform = 'translateX(-' + resolvedOffset + 'px)';

                    return overlay;
                }

                function initCoverOverlay(direction) {
                    if (isCoverOverlayActive) return;

                    isCoverOverlayActive = true;
                    dragDirection = direction;
                    suppressScrollMetrics(10000);

                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var vh = window.innerHeight || document.documentElement.clientHeight || 1;
                    var body = document.body;
                    if (!body) {
                        isCoverOverlayActive = false;
                        return;
                    }

                    var bg = themeBgColor || '#FFFFFF';
                    var shadowStyle = direction < 0
                        ? '14px 0 36px rgba(0,0,0,0.45)'
                        : '-14px 0 36px rgba(0,0,0,0.45)';

                    var backdropOverlay = document.createElement('div');
                    backdropOverlay.id = 'epubpro-backdrop-overlay';
                    backdropOverlay.style.cssText = 'position:fixed;top:0;left:0;width:' + pw + 'px;height:' + vh + 'px;z-index:19997;pointer-events:none;background-color:' + bg + ';';
                    document.documentElement.appendChild(backdropOverlay);
                    window.activeBackdropOverlay = backdropOverlay;

                    var targetScrollX = startScrollX + (direction < 0 ? pw : -pw);
                    var isNextBoundary = direction < 0 && currentPage >= totalPages;
                    var isPreviousBoundary = direction > 0 && currentPage <= 1;
                    var targetLayer = null;
                    var targetOffset = targetScrollX;

                    if (isNextBoundary) {
                        targetLayer = createAdjacentChapterLayer(nextChapterHtml, pw, vh, bg);
                        targetOffset = 0;
                    } else if (isPreviousBoundary) {
                        targetLayer = createAdjacentChapterLayer(previousChapterHtml, pw, vh, bg);
                        targetOffset = 'last';
                    } else {
                        targetLayer = createCurrentChapterLayer(body, pw, vh, bg);
                    }

                    activeBottomOverlay = mountPageOverlay(
                        targetLayer,
                        targetOffset,
                        pw,
                        vh,
                        bg,
                        19998,
                        '',
                        'epubpro-bottom-overlay'
                    );

                    var currentLayer = createCurrentChapterLayer(body, pw, vh, bg);
                    activeTopOverlay = mountPageOverlay(
                        currentLayer,
                        startScrollX,
                        pw,
                        vh,
                        bg,
                        19999,
                        shadowStyle,
                        'epubpro-top-overlay'
                    );

                    body.style.setProperty('touch-action', 'none', 'important');
                    document.documentElement.style.setProperty('touch-action', 'none', 'important');
                }
                document.addEventListener('touchstart', function(e) {
                    if (!window.epubproIsHorizontal) return;
                    if (e.touches && e.touches.length === 1) {
                        gestureToken += 1;
                        cleanupCoverOverlay();
                        startTouchX = e.touches[0].clientX;
                        startTouchY = e.touches[0].clientY;
                        startTouchTime = Date.now();
                        startScrollX = window.scrollX || window.pageXOffset || 0;
                        currentDeltaX = 0;
                        isDraggingPage = true;
                        isHorizontalDragConfirmed = false;
                    }
                }, { passive: true });

                document.addEventListener('touchmove', function(e) {
                    if (!window.epubproIsHorizontal || !isDraggingPage || !e.touches || e.touches.length === 0) return;

                    // MUST preventDefault IMMEDIATELY before WebView commits scroll gesture
                    e.preventDefault();

                    var touchX = e.touches[0].clientX;
                    var touchY = e.touches[0].clientY;
                    var deltaX = touchX - startTouchX;
                    var deltaY = touchY - startTouchY;

                    if (!isHorizontalDragConfirmed) {
                        if (Math.abs(deltaX) > 8 && Math.abs(deltaX) > Math.abs(deltaY) * 1.2) {
                            isHorizontalDragConfirmed = true;
                        } else if (Math.abs(deltaY) > 8) {
                            isDraggingPage = false;
                            return;
                        }
                    }

                    if (isHorizontalDragConfirmed) {
                        currentDeltaX = deltaX;
                        var pw = window.innerWidth || document.documentElement.clientWidth || 1;

                        if (!isCoverOverlayActive) {
                            if (deltaX < 0) {
                                initCoverOverlay(-1);
                            } else if (deltaX > 0) {
                                initCoverOverlay(1);
                            }
                        }

                        if (isCoverOverlayActive && activeTopOverlay) {
                            var clampedX = deltaX;
                            var hasNextPreview = !!(nextChapterHtml && nextChapterHtml.trim());
                            var hasPreviousPreview = !!(previousChapterHtml && previousChapterHtml.trim());
                            var isBlockedNextBoundary = dragDirection < 0 && currentPage === totalPages && !hasNextPreview;
                            var isBlockedPreviousBoundary = dragDirection > 0 && currentPage === 1 && !hasPreviousPreview;

                            if (isBlockedNextBoundary) {
                                clampedX = Math.max(-pw * 0.3, deltaX * 0.3);
                            } else if (isBlockedPreviousBoundary) {
                                clampedX = Math.min(pw * 0.3, deltaX * 0.3);
                            } else {
                                if (dragDirection < 0) clampedX = Math.min(0, Math.max(-pw, deltaX));
                                if (dragDirection > 0) clampedX = Math.max(0, Math.min(pw, deltaX));
                            }
                            activeTopOverlay.style.transform = 'translateX(' + clampedX + 'px)';
                        }
                    }
                }, { passive: false });

                document.addEventListener('touchend', function(e) {
                    if (!window.epubproIsHorizontal || !isDraggingPage) return;
                    isDraggingPage = false;

                    if (!isHorizontalDragConfirmed) {
                        cleanupCoverOverlay();
                        if (!e.changedTouches || e.changedTouches.length === 0) return;
                        var endX = e.changedTouches[0].clientX;
                        var endY = e.changedTouches[0].clientY;
                        var diffX = endX - startTouchX;
                        var diffY = endY - startTouchY;
                        var duration = Date.now() - startTouchTime;

                        if (Math.abs(diffX) <= 15 && Math.abs(diffY) <= 15 && duration <= 300) {
                            handleConfiguredTap(endX, endY);
                        }
                        return;
                    }

                    var duration = Math.max(1, Date.now() - startTouchTime);
                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var velocity = Math.abs(currentDeltaX) / duration;
                    var threshold = pw * 0.50; // Yêu cầu kéo qua 50% màn hình mới chuyển trang
                    var completingGestureToken = gestureToken;
                    var isNextBoundary = dragDirection < 0 && currentPage >= totalPages;
                    var isPreviousBoundary = dragDirection > 0 && currentPage <= 1;

                    if (isNextBoundary || isPreviousBoundary) {
                        var hasAdjacentPreview = isNextBoundary
                            ? !!(nextChapterHtml && nextChapterHtml.trim())
                            : !!(previousChapterHtml && previousChapterHtml.trim());
                        var crossedBoundaryThreshold = isNextBoundary
                            ? (currentDeltaX <= -threshold || (currentDeltaX < -pw * 0.35 && velocity > 0.45))
                            : (currentDeltaX >= threshold || (currentDeltaX > pw * 0.35 && velocity > 0.45));
                        var boundaryTriggered = hasAdjacentPreview && crossedBoundaryThreshold;
                        var boundaryOverlay = activeTopOverlay;
                        var boundaryDone = false;

                        function finishBoundaryGesture() {
                            if (boundaryDone || completingGestureToken !== gestureToken) return;
                            boundaryDone = true;

                            if (!boundaryTriggered) {
                                cleanupCoverOverlay();
                                return;
                            }

                            var chapterRequested = false;
                            if (isNextBoundary && window.ReaderJsBridge && window.ReaderJsBridge.onNextChapterRequested) {
                                chapterRequested = true;
                                window.ReaderJsBridge.onNextChapterRequested();
                            } else if (isPreviousBoundary && window.ReaderJsBridge && window.ReaderJsBridge.onPreviousChapterRequested) {
                                chapterRequested = true;
                                window.ReaderJsBridge.onPreviousChapterRequested();
                            }

                            if (!chapterRequested) {
                                cleanupCoverOverlay();
                                return;
                            }

                            setTimeout(function() {
                                if (completingGestureToken === gestureToken) cleanupCoverOverlay();
                            }, 1800);
                        }

                        if (boundaryOverlay) {
                            boundaryOverlay.addEventListener('transitionend', function onBoundaryTransitionEnd(ev) {
                                if (ev.propertyName !== 'transform') return;
                                boundaryOverlay.removeEventListener('transitionend', onBoundaryTransitionEnd);
                                finishBoundaryGesture();
                            });
                            boundaryOverlay.style.transition = 'transform ' + (transitionSpeedMs / 1000).toFixed(2) + 's cubic-bezier(0.2, 0.8, 0.2, 1)';
                            boundaryOverlay.style.transform = boundaryTriggered
                                ? (isNextBoundary ? 'translateX(-' + pw + 'px)' : 'translateX(' + pw + 'px)')
                                : 'translateX(0px)';
                        }
                        setTimeout(finishBoundaryGesture, transitionSpeedMs + 100);
                        return;
                    }

                    if (isCoverOverlayActive && activeTopOverlay) {
                        var speedSec = (transitionSpeedMs / 1000).toFixed(2);
                        if (dragDirection < 0) { // Dragging left to go next
                            if (currentDeltaX <= -threshold || (currentDeltaX < -pw * 0.35 && velocity > 0.45)) {
                                // COMMIT: slide overlay out, scroll native body, then cleanup
                                var commitOverlay = activeTopOverlay;
                                var commitDone = false;
                                function doCommitLeft() {
                                    if (commitDone || completingGestureToken !== gestureToken) return;
                                    commitDone = true;
                                    currentPage = Math.min(totalPages, currentPage + 1);
                                    window.scrollTo((currentPage - 1) * pw, 0);
                                    requestAnimationFrame(function() {
                                        requestAnimationFrame(function() {
                                            cleanupCoverOverlay();
                                            notifyPageChangeCompleted();
                                        });
                                    });
                                }
                                activeTopOverlay.addEventListener('transitionend', function onTE(ev) {
                                    if (ev.propertyName !== 'transform') return;
                                    commitOverlay.removeEventListener('transitionend', onTE);
                                    doCommitLeft();
                                });
                                setTimeout(doCommitLeft, transitionSpeedMs + 100);
                                activeTopOverlay.style.transition = 'transform ' + speedSec + 's cubic-bezier(0.2, 0.8, 0.2, 1)';
                                activeTopOverlay.style.transform = 'translateX(-' + pw + 'px)';
                            } else {
                                // SNAP-BACK: body never moved, slide overlay back then cleanup
                                var snapOverlay = activeTopOverlay;
                                var snapDone = false;
                                function doSnapLeft() {
                                    if (snapDone || completingGestureToken !== gestureToken) return;
                                    snapDone = true;
                                    window.scrollTo(startScrollX, 0);
                                    dbg('SNAP_BACK', 'left: done');
                                    cleanupCoverOverlay();
                                }
                                activeTopOverlay.addEventListener('transitionend', function onTE2(ev) {
                                    if (ev.propertyName !== 'transform') return;
                                    snapOverlay.removeEventListener('transitionend', onTE2);
                                    doSnapLeft();
                                });
                                setTimeout(doSnapLeft, transitionSpeedMs + 100);
                                activeTopOverlay.style.transition = 'transform ' + speedSec + 's cubic-bezier(0.2, 0.8, 0.2, 1)';
                                activeTopOverlay.style.transform = 'translateX(0px)';
                                dbg('SNAP_BACK', 'left: anim start');
                            }
                        } else if (dragDirection > 0) { // Dragging right to go prev
                            if (currentDeltaX >= threshold || (currentDeltaX > pw * 0.35 && velocity > 0.45)) {
                                // COMMIT: slide overlay out, scroll native body, then cleanup
                                var commitOverlay = activeTopOverlay;
                                var commitDone = false;
                                function doCommitRight() {
                                    if (commitDone || completingGestureToken !== gestureToken) return;
                                    commitDone = true;
                                    currentPage = Math.max(1, currentPage - 1);
                                    window.scrollTo((currentPage - 1) * pw, 0);
                                    requestAnimationFrame(function() {
                                        requestAnimationFrame(function() {
                                            cleanupCoverOverlay();
                                            notifyPageChangeCompleted();
                                        });
                                    });
                                }
                                activeTopOverlay.addEventListener('transitionend', function onTE3(ev) {
                                    if (ev.propertyName !== 'transform') return;
                                    commitOverlay.removeEventListener('transitionend', onTE3);
                                    doCommitRight();
                                });
                                setTimeout(doCommitRight, transitionSpeedMs + 100);
                                activeTopOverlay.style.transition = 'transform ' + speedSec + 's cubic-bezier(0.2, 0.8, 0.2, 1)';
                                activeTopOverlay.style.transform = 'translateX(' + pw + 'px)';
                            } else {
                                // SNAP-BACK: body never moved, slide overlay back then cleanup
                                var snapOverlay = activeTopOverlay;
                                var snapDone = false;
                                function doSnapRight() {
                                    if (snapDone || completingGestureToken !== gestureToken) return;
                                    snapDone = true;
                                    window.scrollTo(startScrollX, 0);
                                    dbg('SNAP_BACK', 'right: done');
                                    cleanupCoverOverlay();
                                }
                                activeTopOverlay.addEventListener('transitionend', function onTE4(ev) {
                                    if (ev.propertyName !== 'transform') return;
                                    snapOverlay.removeEventListener('transitionend', onTE4);
                                    doSnapRight();
                                });
                                setTimeout(doSnapRight, transitionSpeedMs + 100);
                                activeTopOverlay.style.transition = 'transform ' + speedSec + 's cubic-bezier(0.2, 0.8, 0.2, 1)';
                                activeTopOverlay.style.transform = 'translateX(0px)';
                                dbg('SNAP_BACK', 'right: anim start');
                            }
                        }
                    } else {
                        cleanupCoverOverlay();
                    }
                }, { passive: true });

                document.addEventListener('touchcancel', function() {
                    if (!window.epubproIsHorizontal) return;
                    gestureToken += 1;
                    window.scrollTo(startScrollX, 0);
                    cleanupCoverOverlay();
                }, { passive: true });

                window.epubproHighlightTtsParagraph = function(index) {
                    try {
                        var oldElements = document.querySelectorAll('.tts-active-paragraph');
                        for (var i = 0; i < oldElements.length; i++) {
                            oldElements[i].classList.remove('tts-active-paragraph');
                        }
                        var paragraphs = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote');
                        if (index >= 0 && index < paragraphs.length) {
                            var target = paragraphs[index];
                            target.classList.add('tts-active-paragraph');
                            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        }
                    } catch(e) {}
                };

                document.addEventListener('selectionchange', function() {
                    var selection = window.getSelection();
                    if (selection && selection.toString().trim().length > 0) {
                        var text = selection.toString();
                        var data = JSON.stringify({
                            selectedText: text,
                            startCfi: "epubcfi(/6/2!/4/2/1:0)",
                            endCfi: "epubcfi(/6/2!/4/2/1:" + text.length + ")"
                        });
                        if (window.ReaderJsBridge && window.ReaderJsBridge.onTextSelected) {
                            window.ReaderJsBridge.onTextSelected(data);
                        }
                    }
                });
            })();
        """.trimIndent()
    }
}
