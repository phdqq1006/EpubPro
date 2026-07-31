package com.epubpro.core.reader.style

import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode

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
            ReaderThemeMode.OLED -> "#000000" to "#FFFFFF"
            ReaderThemeMode.MIDNIGHT -> "#0F172A" to "#94A3B8"
        }

        val fontSizePx = settings.fontSizeSp.toInt()
        val marginPx = settings.marginDp

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
            div, section, article, main, header, footer, aside, nav, figure, blockquote, ul, ol, li, form, fieldset, p, h1, h2, h3, h4, h5, h6 {
                margin-left: 0 !important;
                margin-right: 0 !important;
                padding-left: ${settings.marginLeftDp}px !important;
                padding-right: ${settings.marginRightDp}px !important;
                max-width: 100% !important;
                box-sizing: border-box !important;
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
                    margin-top: 0.4em !important;
                    margin-bottom: 0.4em !important;
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
                $paginationCss
            </style>
        """.trimIndent()
    }

    fun generateJsBridgeScript(isHorizontalPagination: Boolean, initialPage: Int = 1, settings: ReaderSettings = ReaderSettings()): String {
        return """
            (function() {
                window.epubproIsHorizontal = $isHorizontalPagination;
                var targetInitPage = $initialPage;
                var marginTop = ${settings.marginTopDp};
                var marginBottom = ${settings.marginBottomDp};
                var marginLeft = ${settings.marginLeftDp};
                var marginRight = ${settings.marginRightDp};
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

                function updatePageMetrics() {
                    if (!window.epubproIsHorizontal) return;
                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var sw = document.body ? document.body.scrollWidth : pw;
                    
                    totalPages = Math.max(1, Math.round(sw / pw));
                    
                    if (isExecutingScroll) return;
                    var sl = window.scrollX || window.pageXOffset || 0;
                    currentPage = Math.min(totalPages, Math.max(1, Math.round(sl / pw) + 1));
                    
                    dbg('METRICS', 'pw=' + pw + ' sw=' + sw + ' totalPages=' + totalPages + ' currentPage=' + currentPage);
                    
                    var visibleIndex = getFirstVisibleParagraphIndex();

                    if (window.ReaderJsBridge && window.ReaderJsBridge.onPageChanged) {
                        window.ReaderJsBridge.onPageChanged(currentPage, totalPages, visibleIndex);
                    }
                }

                function scrollToPage(page, animate) {
                    if (!window.epubproIsHorizontal) return;
                    var pw = window.innerWidth || document.documentElement.clientWidth || 1;
                    var targetX = (page - 1) * pw;
                    isExecutingScroll = true;
                    currentPage = page;

                    var visibleIndex = getFirstVisibleParagraphIndex();
                    if (window.ReaderJsBridge && window.ReaderJsBridge.onPageChanged) {
                        window.ReaderJsBridge.onPageChanged(currentPage, totalPages, visibleIndex);
                    }

                    if (animate !== false) {
                        var startX = window.scrollX || window.pageXOffset;
                        var distance = targetX - startX;
                        var duration = 180; // ms (fast and snappy)
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
                                updatePageMetrics();
                            }
                        }
                        window.requestAnimationFrame(step);
                    } else {
                        window.scrollTo(targetX, 0);
                        setTimeout(function() {
                            isExecutingScroll = false;
                            updatePageMetrics();
                        }, 50);
                    }
                }

                window.epubproGoNextPage = function() {
                    updatePageMetrics();
                    if (currentPage < totalPages) {
                        scrollToPage(currentPage + 1, true);
                    } else {
                        if (window.ReaderJsBridge && window.ReaderJsBridge.onNextChapterRequested) {
                            window.ReaderJsBridge.onNextChapterRequested();
                        }
                    }
                };

                window.epubproGoPrevPage = function() {
                    updatePageMetrics();
                    if (currentPage > 1) {
                        scrollToPage(currentPage - 1, true);
                    } else {
                        if (window.ReaderJsBridge && window.ReaderJsBridge.onPreviousChapterRequested) {
                            window.ReaderJsBridge.onPreviousChapterRequested();
                        }
                    }
                };

                window.epubproUpdateMetrics = updatePageMetrics;

                var scrollTimeout = null;
                window.addEventListener('scroll', function() {
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
                    if (targetInitPage > 1) {
                        scrollToPage(targetInitPage, false);
                    } else {
                        updatePageMetrics();
                    }
                }

                if (document.readyState === 'complete') {
                    setTimeout(initLayout, 100);
                } else {
                    window.addEventListener('load', function() {
                        setTimeout(initLayout, 100);
                    });
                }
                setTimeout(initLayout, 500);

                document.addEventListener('touchstart', function(e) {
                    if (e.touches && e.touches.length > 0) {
                        startTouchX = e.touches[0].clientX;
                        startTouchY = e.touches[0].clientY;
                        startTouchTime = Date.now();
                    }
                }, { passive: true });

                document.addEventListener('touchend', function(e) {
                    if (!e.changedTouches || e.changedTouches.length === 0) return;
                    var endX = e.changedTouches[0].clientX;
                    var endY = e.changedTouches[0].clientY;
                    var diffX = endX - startTouchX;
                    var diffY = endY - startTouchY;
                    var duration = Date.now() - startTouchTime;

                    if (Math.abs(diffX) > 35 && Math.abs(diffX) > Math.abs(diffY)) {
                        if (diffX < 0) {
                            window.epubproGoNextPage();
                        } else {
                            window.epubproGoPrevPage();
                        }
                        return;
                    }

                    if (Math.abs(diffX) <= 15 && Math.abs(diffY) <= 15 && duration <= 300) {
                        var screenWidth = window.innerWidth;
                        if (window.epubproIsHorizontal) {
                            if (endX < screenWidth * 0.30) {
                                window.epubproGoPrevPage();
                            } else if (endX > screenWidth * 0.70) {
                                window.epubproGoNextPage();
                            } else {
                                if (window.ReaderJsBridge && window.ReaderJsBridge.onPageTapped) {
                                    window.ReaderJsBridge.onPageTapped();
                                }
                            }
                        } else {
                            if (window.ReaderJsBridge && window.ReaderJsBridge.onPageTapped) {
                                window.ReaderJsBridge.onPageTapped();
                            }
                        }
                    }
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
