# EPUB Reader & Layout Engine

> Tổng hợp kiến thức về hệ thống đọc EPUB, WebView rendering, TTS, tối ưu bộ nhớ RAM, Room FTS5 và cài đặt mặc định đọc / chuyển trang trong dự án.
> Cập nhật lần cuối: 2026-08-17

---

## Architecture

### Bounded Adjacent Chapter Preloading Architecture
- **Ngày**: 2026-08-17
- **Chi tiết**: Với EPUB lớn, chỉ giữ metadata của toàn bộ spine và tối đa ba chuỗi XHTML: chương trước, hiện tại, chương sau. Giới hạn số chương chưa đủ để chống OOM: mọi Zip entry vẫn phải có trần uncompressed bytes, compression ratio và tổng bytes đọc, vì một chapter hoặc Zip bomb có thể chiếm toàn bộ heap. Chương hiện tại phục vụ WebView; preview là dữ liệu bổ trợ, có thể bỏ qua khi vượt budget và không được làm hỏng nội dung chính.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### EPUB WebView Trust Boundary & Multi-Layer Security Architecture
- **Ngày**: 2026-08-17
- **Chi tiết**: Sách EPUB import là dữ liệu không tin cậy. Bảo vệ WebView đa tầng: (1) `EpubHtmlSanitizer` (Jsoup) loại bỏ hoàn toàn các active tags (`script`, `iframe`, `frame`, `object`, `embed`, `form`, `style`), quét sạch mọi event attribute `on*` (`onerror`, `onload`...), và chặn URL nguy hiểm (`javascript:`, `vbscript:`, chỉ cho phép `data:image/`); (2) Khóa cứng WebSettings (`allowFileAccess = false`, `allowContentAccess = false`, `domStorageEnabled = false`); (3) `WebViewClient.shouldOverrideUrlLoading` chặn mọi điều hướng nội bộ và chuyển link ngoài `http/https` sang external Intent; (4) Chỉ gắn `ReaderJsBridge` lên document do app quản lý sau khi đã sanitize.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/EpubHtmlSanitizer.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/webview/EpubProWebView.kt`, `core/reader/src/main/java/com/epubpro/core/reader/bridge/ReaderJsBridge.kt`

### Shared Reader Settings StateFlow
- **Ngày**: 2026-08-05
- **Chi tiết**: `ReaderPreferencesManager` là nguồn sự thật duy nhất cho `ReaderSettings`: sở hữu `MutableStateFlow`, chuẩn hóa trước khi lưu và phát lại giá trị đã normalize. Reader, Reading Defaults và Page Turn Settings chỉ observe cùng `StateFlow`; mọi cập nhật dạng transform phải chạy trên giá trị mới nhất trong manager để tránh hai ViewModel ghi đè lẫn nhau. Constants dùng chung (range/preset tốc độ) đặt ở domain, không hardcode riêng tại từng UI.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `domain/src/main/java/com/epubpro/domain/model/Models.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ReadingDefaultsViewModel.kt`

### Tách Rendering Settings và Runtime Settings
- **Ngày**: 2026-08-05
- **Chi tiết**: Không dùng toàn bộ `ReaderSettings.hashCode()` làm khóa reload WebView. Chỉ font, theme, margin, paragraph layout, reading mode và scrollbar thuộc `contentReloadKey`. Tốc độ/animation, vùng chạm, phím điều hướng, status bar và keep-screen-on cập nhật qua JavaScript/native runtime. Cách tách này giữ nguyên document và loại bỏ reload chương cho thay đổi không ảnh hưởng HTML.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Semantic Reading Position Anchor
- **Ngày**: 2026-08-05
- **Chi tiết**: Số trang không phải vị trí bền vững vì font, margin hoặc theme có thể repaginate chương. Reader lưu index paragraph đầu tiên đang thấy qua JS bridge. Khi nội dung phải render lại, WebView tìm paragraph đó rồi tính lại trang ngang hoặc `scrollIntoView()` trong chế độ cuộn dọc. Chỉ fallback về page index khi chưa có semantic anchor.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Batch Streaming FTS Indexer
- **Ngày**: 2026-07-23
- **Chi tiết**: Xây dựng luồng đánh chỉ mục tìm kiếm toàn văn FTS ngầm qua WorkManager/Coroutine bằng cách đọc từng chương từ file Zip, băm chỉ mục theo batch 5 chương/lần và xả bộ nhớ string ngay lập tức để tránh đè nặng lên Garbage Collector.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `core/database/src/main/java/com/epubpro/core/database/repository/RepositoryImpls.kt`

### Fullscreen Reader Overlay Architecture (Chống xô dịch Layout khi ẩn/hiện Controls)
- **Ngày**: 2026-07-27
- **Chi tiết**: Tách WebView thành lớp nền cố định chiếm 100% toàn màn hình (`fillMaxSize()`). Không dùng `Scaffold(topBar, bottomBar)` vì khi `showControls` đổi, `paddingValues` thu hẹp/giãn ra làm WebView resize, khiến CSS `100vh` recalculate và dồn/xô dịch toàn bộ văn bản. Đặt TopAppBar và BottomBar thành các lớp phủ nổi (`Overlay` với `Alignment.TopCenter`/`Alignment.BottomCenter` trong `BoxScope`) đè lên WebView.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Multi-Column phải đặt trên body, KHÔNG đặt trên html
- **Ngày**: 2026-07-27
- **Chi tiết**: Trong kiến trúc CSS multi-column pagination cho EPUB WebView, `column-width` PHẢI đặt trên `body` (multi-column container). Nếu đặt trên `html`, thì `body` là child duy nhất — nếu `body` có fixed height, nó trở thành khối monolithic KHÔNG THỂ bị fragment qua các cột. Kết quả: chỉ 1 cột hiển thị, text tràn dọc bị `overflow-y: hidden` xén sạch.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Single Native Engine Architecture
- **Ngày**: 2026-08-05
- **Chi tiết**: Runtime chỉ dùng `EpubEngine` + Compose WebView; không hiển thị selector Readium khi chưa có renderer thật. `ReaderEngineType.READIUM` có thể tạm tồn tại để đọc dữ liệu legacy nhưng phải được normalize/ẩn khỏi UI và không được quảng bá như feature hoạt động. Khi bổ sung engine mới, cần phân nhánh renderer thật trước khi expose control.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Real-time Page Turn Drag Transition Engine Architecture (Hiệu ứng lật trang kéo phủ bóng)
- **Ngày**: 2026-08-06
- **Chi tiết**: Trong chế độ cuộn trang ngang CSS Multi-Column, nâng cấp cơ chế lật trang từ `scrollToPage` cố định thành engine theo dõi ngón tay real-time qua `touchstart`, `touchmove`, `touchend`. Khi vuốt ngón tay, `window.scrollTo(startScrollX - deltaX)` dịch chuyển trang sách bám sát theo tay với tần số 60fps, đồng thời một phần tử overlay gradient (`#epubpro-shadow-overlay`) hiển thị ở mép trang bị kéo. Thả tay sẽ kiểm tra ngưỡng kéo (>30% screen width hoặc gia tốc vuốt `velocity > 0.35` với 20% width) để lật trang mượt với cubic-bezier curve hoặc nảy đàn hồi (snap back) về trang cũ.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Dual Overlay Page Turn Engine Architecture (Chống giật và hở nội dung khi vuốt)
- **Ngày**: 2026-08-04
- **Chi tiết**: Sử dụng 3 lớp phủ ảo thuật: Backdrop màu trơn che WebView gốc, Bottom Overlay clone trang đích để làm trang preview, Top Overlay clone trang hiện tại và dịch chuyển. Trình duyệt WebView thật đứng im hoàn toàn dưới Backdrop trong suốt quá trình người dùng kéo chạm (chỉ cuộn programmatic ở sự kiện `touchend`), triệt tiêu tận gốc hiện tượng giằng xé cuộn native (jitter) và ẩn mọi nội dung lỗi ở rìa chương (boundary overflow).
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Valid DIV Layers cho Preview Trang và Biên Chương
- **Ngày**: 2026-08-05
- **Chi tiết**: Overlay chuyển trang phải dùng các `div` độc lập chứa bản sao `body.childNodes`, không clone hoặc lồng thẻ `<body>` vào document hiện tại vì DOM không hợp lệ và WebView có thể reparent/fragment nội dung sai. Ở trang thường, Bottom Overlay lấy trang kề trong chương hiện tại; ở biên, nó parse chương kề bằng `DOMParser`, chọn trang đầu chương sau hoặc trang cuối chương trước. Chỉ commit đổi chapter sau khi animation vượt ngưỡng.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Vertical Scroll Mode Tap Controls Toggle
- **Ngày**: 2026-08-06
- **Chi tiết**: Trong chế độ cuộn dọc (`ReadingMode.SCROLL` / `!window.epubproIsHorizontal`), `touchstart` và `touchend` vẫn phải ghi nhận mốc vị trí chạm để phát hiện hành vi nhấp chạm (`diffX, diffY <= 15px` và `duration <= 300ms`), gọi `handleConfiguredTap()` / `ReaderJsBridge.onPageTapped()` ẩn/hiện TopAppBar và BottomBar. Riêng `touchmove` bỏ qua (return) để WebView thực hiện vuốt cuộn dọc native mượt mà.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Fullscreen System Bars & Reader Theme Background Synchronization
- **Ngày**: 2026-08-06
- **Chi tiết**: Để màn hình đọc sách phủ tràn 100% màu nền của theme (tránh vệt trắng ở đỉnh và đáy): (1) Đặt `Modifier.background(readerBgColor)` lên root `Box` của `ReaderScreen`; (2) Sử dụng `DisposableEffect(currentStatusBarColor, readerBgColor, isDarkTheme)` cập nhật `window.statusBarColor` và `window.navigationBarColor` bằng `readerBgColor.toArgb()`, kết hợp `WindowCompat.getInsetsController().isAppearanceLightStatusBars = !isDarkTheme` để tự động điều chỉnh màu icon hệ thống (tối trên nền Sepia/Light, sáng trên nền Dark/Midnight); (3) Khôi phục màu gốc trong `onDispose`.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Distinct Elevated Reader Bar Palette Architecture
- **Ngày**: 2026-08-06
- **Chi tiết**: Để TopAppBar và BottomBar phân biệt rõ ràng với văn bản khi hiển thị nhưng vẫn giữ thẩm mỹ cao cấp: Định nghĩa cặp màu `(readerBgColor, readerBarBgColor, readerContentColor)` cho từng theme mode (ví dụ Sepia: nền đọc `#FBF0D9`, nền bar `#EFE0C2` đậm hơn, chữ `#3B2F23`). Gắn `shadowElevation = 4.dp` và chuyển `statusBarColor` sang `readerBarBgColor` khi `showControls` bật.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`
---

## Bugs & Solutions

### Lỗi giật lác (Jitter) và hở chữ khi vuốt lật trang ngang do Native Scroll Conflict
- **Ngày**: 2026-08-04
- **Vấn đề**: Khi vuốt lật trang ngang, giao diện bị giật (flicker) liên tục và ở mép trang cuối cùng của chương bị lọt chữ/khoảng trắng lạ ("dính nội dung chương khác"). Cố gắng dùng `window.scrollTo` trong `touchmove` để cập nhật cuộn bị vô hiệu.
- **Root cause**: Trong Android WebView, khi người dùng đang thực hiện cử chỉ vuốt, engine cuộn native của máy vẫn cố gắng can thiệp (kể cả có gọi `e.preventDefault()`). Nếu ép WebView gọi `window.scrollTo` programmatic trong lúc này, nó có thể bị chặn lại hoặc xử lý trễ tạo ra giật lag. Nếu cuộn sát rìa body, các trang tràn cột sẽ vô tình lộ ra.
- **Fix**: Áp dụng mô hình **Dual Overlay**: chặn hoàn toàn cảm ứng native (dùng `touch-action: none` và `preventDefault()`), giấu thẻ `body` thực đi (bằng Backdrop overlay) và chuyển mọi hình ảnh hiển thị trong lúc vuốt vào CSS `transform: translateX` trên thẻ ảo (Top/Bottom Overlay). `window.scrollTo` chỉ được gọi sau khi thả tay (`touchend`) và animation đã xong.
- **Lưu ý Quan Trọng**: Lớp overlay `.epubpro-page-layer` được append vào `html` (không phải con trực tiếp của `body`). Các CSS selector quy định padding lề (`marginLeftDp` / `marginRightDp`) BẮT BUỘC phải nhóm cả `body > *, .epubpro-page-layer > *` và `body > * *, .epubpro-page-layer > * *` để overlay thừa hưởng chính xác 100% lề của `body`, tránh giật/nhảy chữ khi vừa bấm giữ kéo lật trang.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Settings slider reload toàn bộ chương theo từng pixel kéo
- **Ngày**: 2026-08-05
- **Vấn đề**: Kéo font size, margin hoặc tốc độ gây nhiều lần ghi SharedPreferences, gọi `loadDataWithBaseURL()` liên tục và làm Reader giật/đổi vị trí.
- **Root cause**: `onValueChange` persist ngay và khóa HTML chứa `settings.hashCode()`, nên mọi thay đổi runtime đều bị coi là thay đổi document.
- **Fix**: Giữ draft state trong Compose, chỉ commit tại `onValueChangeFinished`; dùng `contentReloadKey()` cho thuộc tính render và `epubproApplyRuntimeSettings()` cho tốc độ/animation/vùng chạm.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ReadingDefaultsScreen.kt`

### Hai màn Settings ghi đè cấu hình của nhau
- **Ngày**: 2026-08-05
- **Vấn đề**: Thay đổi tại Reader hoặc Profile có thể làm mất field vừa cập nhật ở màn còn lại.
- **Root cause**: Mỗi ViewModel giữ snapshot riêng và gọi `saveSettings(settings.copy(...))` trên dữ liệu có thể đã cũ.
- **Fix**: Manager singleton sở hữu StateFlow và API `updateSettings(transform)` chạy trên `_settings.value` mới nhất; UI observe cùng flow. Normalize mode, font, tap-zone và speed trước khi persist/emit.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Đổi cấu hình TTS vô tình bắt đầu phát truyện
- **Ngày**: 2026-08-05
- **Vấn đề**: Bấm giọng AI hoặc tốc độ trong Reader Settings mở player và nạp nội dung phát ngay.
- **Root cause**: Callback chỉnh cấu hình dùng chung `onStartListeningFromSetup()`, vốn có side effect playback.
- **Fix**: Tách `updateTtsSettings()` chỉ persist và cập nhật service; giữ `onStartListeningFromSetup()` riêng cho lệnh nghe rõ ràng. Reader và Audio Settings cùng observe `TtsPreferencesManager.settingsFlow`.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/audio/AudioSettingsViewModel.kt`

### OutOfMemoryError do nạp toàn bộ HTML tất cả các chương vào RAM
- **Ngày**: 2026-08-17
- **Vấn đề**: `java.lang.OutOfMemoryError` khi nạp toàn bộ sách hoặc khi một chapter/Zip entry riêng lẻ có uncompressed size rất lớn.
- **Root cause**: Mô hình cũ giữ mọi chapter; mô hình lazy hiện tại chỉ giữ tối đa ba chapter nhưng vẫn dùng `readText()` không giới hạn, nên chapter cực lớn hoặc Zip bomb vẫn có thể chiếm toàn bộ heap.
- **Fix**: Giữ Lazy Header và batch index; đồng thời áp trần kích thước entry, compression ratio, tổng bytes đọc và budget cho preview. `largeHeap` chỉ là lớp giảm áp lực, không phải biện pháp bảo vệ.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `app/src/main/AndroidManifest.xml`

### Deep Reset Margin/Padding & Width trên các thẻ con EPUB
- **Ngày**: 2026-08-06
- **Chi tiết**: Nhiều file EPUB chứa thẻ wrapper (`div.content`, `section`, `p`) có CSS cứng như `width: 80%`, `margin-left: 30px` hoặc `padding: 20px`. Để cài đặt căn lề trái/phải (`marginLeftDp`, `marginRightDp`) của app hoạt động 100% trên mọi bộ truyện (cả Lật trang ngang lẫn Cuộn dọc): Inject CSS `body > *, body > * * { margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important; padding-right: 0 !important; width: auto !important; max-width: 100% !important; }` để triệt tiêu mọi lề cứng nội bộ, nhường quyền kiểm soát lề duy nhất cho container của app.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Lỗi lệch lề văn bản khi cuộn sang trang mới trong CSS Multi-Column Pagination
- **Ngày**: 2026-07-31
- **Vấn đề**: Văn bản ở trang 2 trở đi bị kéo sát lề trái (0px padding), mất lề bên trái và chừa khoảng trắng lớn ở lề bên phải.
- **Root cause**: Theo CSS Multi-Column Specification, `padding-left`/`padding-right` đặt trên `body` chỉ áp dụng cho lề ngoài cùng của khối body (trang 1). Các cột sau (trang 2, 3...) không được thừa hưởng padding của body mà bị tách bằng `column-gap`.
- **Fix**: 
  1. Đặt `column-width: 100vw !important; column-gap: 0px !important;` trên `body` và cho `padding-left: 0; padding-right: 0`.
  2. Đẩy `padding-left` và `padding-right` với `box-sizing: border-box` vào từng khối nội dung (`div`, `section`, `p`, `h1..h6`, `li`, `blockquote`).
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Lỗi Dialog Z-Order hiển thị đè lên ModalBottomSheet trong Jetpack Compose
- **Ngày**: 2026-07-31
- **Vấn đề**: Khi bấm mở nút Cài đặt giọng đọc từ màn hình trình phát Audio full-screen `Dialog`, bảng `ModalBottomSheet` bị lặn phía sau màn hình dialog.
- **Root cause**: `Dialog` trong Compose tạo một Android Window riêng nằm trên Activity Window (nơi `ModalBottomSheet` dựng view), dẫn đến Z-Order của Window đè bệt bottom sheet.
- **Fix**: Tạm thời ẩn/unmount `Dialog` (ví dụ `if (showTtsPlayerScreen && !showTtsSetupBottomSheet)`) trong thời gian `ModalBottomSheet` mở.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Lỗi lệch lề văn bản ngang do CSS Snap Engine (`scroll-snap-type`)
- **Ngày**: 2026-07-27
- **Vấn đề**: Văn bản ở các trang sau (ví dụ Trang 10/10) bị lệch ngang, lề trái lòi chữ cũ của trang trước, lề phải bị xén chữ.
- **Root cause**: Thêm `scroll-snap-type: x mandatory` vào `html` làm trình duyệt Chromium ép vị trí cuộn `scrollX` hít vào lề thẻ con (`<h1>`, `<p>`) thay vì đúng bội số `100vw`. Ngoài ra các container `div` trong EPUB có `margin-left`/`padding-left` đẩy chữ lệch khỏi khung cột.
- **Fix**: 
  1. Gỡ bỏ `scroll-snap-type` khỏi `html`, trả `html` về `overflow: hidden`. Dùng `window.scrollTo({ left: (page-1)*vw })` để di chuyển chính xác tuyệt đối.
  2. Inject CSS reset `margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important; padding-right: 0 !important;` trên tất cả thẻ khối (`div`, `p`, `section`...).
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Lỗi nhảy từ Chương 1 sang Chương 1007 do Alphabetical String Sorting trên Zip Entry Names
- **Ngày**: 2026-07-27
- **Vấn đề**: Khi ở Chương 1 bấm "Next chapter", app nhảy vọt sang Chương 1007.
- **Root cause**: `extractChapterHeaders` và `indexBookContent` sử dụng `.sortedBy { it.name }` trên các file trong Zip. Phép so sánh chuỗi mặc định xếp `"chap1.xhtml"` < `"chap1007.xhtml"` < `"chap2.xhtml"`.
- **Fix**: 
  1. Đọc file `.opf` của EPUB package, parse `<manifest>` (id -> href) và `<spine>` (`<itemref idref="...">`) để tạo danh sách ZipEntry theo đúng thứ tự đọc do tác giả quy định.
  2. Xây dựng `naturalOrderComparator()` tách số khỏi chuỗi để so sánh số tự nhiên (`chap1` < `chap2` < `chap1007`) làm phương án dự phòng (fallback).
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`

### Lỗi đè vị trí đọc về Chương 1 / Trang 1 khi vừa mở sách
- **Ngày**: 2026-07-27
- **Vấn đề**: Mở lại sách đã đọc trước đó thì luôn bị đưa về Chương 1 hoặc Trang 1.
- **Root cause**: 
  1. `saveProgress()` trong ViewModel không kiểm tra `state.isLoading`. Khi mở app, state ban đầu có `currentChapterIndex = 0, currentPageInChapter = 1`. Nếu UI/JS phát callback trước khi coroutine `loadBookData` đọc DB xong, `saveProgress()` lập tức ghi đè tiến độ cũ về 0/1.
  2. Trong JS `initLayout()`, hàm `updatePageMetrics()` được gọi khi `scrollX = 0` TRƯỚC KHI `scrollToPage(targetInitPage)` kịp chạy, gửi callback `currentPage = 1` về Android.
- **Fix**:
  1. Thêm guard check `if (state.isLoading || state.chapters.isEmpty()) return@launch` trong `saveProgress()`, `updatePageMetrics()`, và `updateCfiPosition()`.
  2. Trong JS `initLayout()`, chỉ gọi `scrollToPage(targetInitPage, false)` nếu `targetInitPage > 1` mà không phát `updatePageMetrics()` trước.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Body height 32px thay vì 100vh — CSS stylesheet !important bị override trong Android WebView
- **Ngày**: 2026-07-27
- **Vấn đề**: Body chỉ cao 32px (= 2×padding), mỗi cột column chứa 0px nội dung → tạo ra 160+ trang trống. Debug log: `BODY: h=32 height=32px colWidth=352px scrollW=62192`.
- **Root cause**: CSS `body { height: 100vh !important; }` trong `<style>` tag KHÔNG được áp dụng bởi Android WebView. Computed height = 32px (chỉ padding).
- **Fix**: Dùng JavaScript `element.style.setProperty('height', vh + 'px', 'important')` — inline `!important` via JS = priority CAO NHẤT trong CSS cascade, override tất cả.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### EPUB HTML có `<html xmlns=...>` không match regex `<html>`
- **Ngày**: 2026-07-27
- **Vấn đề**: Head injection thất bại vì regex match `<html>` (exact) nhưng EPUB dùng `<html xmlns="http://www.w3.org/1999/xhtml" ...>`.
- **Fix**: Đổi thành `cleanHtml.contains("<html", ignoreCase = true)` để detect, và regex `(?i)(<html[^>]*>)` để replace.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Inline JavaScript hỏng do Regex Replacement làm mất dấu escape
- **Ngày**: 2026-08-05
- **Vấn đề**: Sau khi nhúng XHTML chương trước/sau vào bridge, WebView không khởi tạo phân trang và logcat báo `Uncaught SyntaxError: Unexpected number` tại dòng khai báo HTML preview.
- **Root cause**: `JSONObject.quote()` tạo JSON đúng với `\"`, `\n`, `\u003C`, nhưng `Regex.replace(input, replacementString)` tiếp tục diễn giải dấu `\` theo cú pháp replacement và loại bỏ chúng. Ví dụ `version=\"1.0\"` biến thành `version="1.0"` bên trong chuỗi JavaScript.
- **Fix**: Dùng overload callback `regex.replace(input) { literalValue }` khi chèn toàn bộ head/script. Escape `<`, `>` và `&` sau `JSONObject.quote()` để chuỗi XHTML không thể đóng inline `<script>`.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Nội dung chương bị dính khi clone BODY làm overlay
- **Ngày**: 2026-08-05
- **Vấn đề**: Khi giữ và kéo ngang, mép trang lộ các cột chữ không thuộc trang preview mong muốn và có thể trông như dính nội dung chương khác.
- **Root cause**: Clone toàn bộ `<body>` rồi gắn bản clone vào document tạo nested BODY không hợp lệ; đồng thời layer có `overflow: visible` nên các cột ngoài viewport có thể lộ ra ở biên.
- **Fix**: Tạo wrapper fixed `overflow: hidden`, dựng nội dung trong `div.epubpro-page-layer`, loại script/ID trùng và dịch đúng offset trang. Dùng Backdrop, Bottom Overlay và Top Overlay với z-index tách biệt.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Lỗ hổng XSS & Active Content Injection từ tệp sách EPUB
- **Ngày**: 2026-08-17
- **Vấn đề**: Tệp EPUB chứa `<script>`, `<iframe>`, `<object>`, `<form>` hoặc event handlers (`onerror`, `onload`, `onclick`) có thể thực thi mã JavaScript tùy ý trong WebView hoặc bypass Javascript Interface `ReaderJsBridge`.
- **Root cause**: Cơ chế sanitize trước đây chỉ dùng Regex đơn giản loại bỏ một số thẻ stylesheet/style cơ bản, không loại bỏ script, iframe hay attribute `on*`.
- **Fix**: Tạo `EpubHtmlSanitizer` dùng Jsoup bóc tách và loại bỏ hoàn toàn các active tags (`script`, `iframe`, `object`, `embed`, `form`, `style`), quét sạch mọi attribute bắt đầu bằng `on*`, chỉ cho phép `data:image/` và chuyển link `javascript:` thành vô hại. Cấu hình WebSettings `allowFileAccess = false`, `allowContentAccess = false`, `domStorageEnabled = false` và chặn URL lạ trong `shouldOverrideUrlLoading`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/EpubHtmlSanitizer.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/webview/EpubProWebView.kt`, `core/reader/src/test/java/com/epubpro/core/reader/filter/EpubHtmlSanitizerTest.kt`

### Piper TTS mắc kẹt ở Preparing khi initialize lỗi
- **Ngày**: 2026-08-17
- **Vấn đề**: Model thiếu/hỏng hoặc native init lỗi có thể để player và foreground notification đứng ở `Preparing`.
- **Root cause**: `speak()` lưu callback hiện tại nhưng gọi `initialize(onErrorCallback ?: {})`; service chưa initialize Piper nên lỗi bị gửi vào lambda rỗng và `pendingSpeech` bị xóa.
- **Fix**: Truyền callback của `PendingSpeech` vào lần initialize hoặc initialize Piper qua lifecycle của service; test success, missing model, init exception và cancellation.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/PiperTtsEngineWrapper.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

### Widget mất bước khi move xuyên biên chapter
- **Ngày**: 2026-08-17
- **Vấn đề**: Nhiều thao tác Next/Previous được queue đúng nhưng kết quả dừng sai paragraph khi vượt chapter.
- **Root cause**: Projection đặt target về `0` hoặc paragraph cuối khi đổi chapter, làm mất phần overflow/underflow còn lại.
- **Fix**: Carry số bước dư qua từng chapter; test nhiều bước theo cả hai chiều, chapter rỗng và giới hạn đầu/cuối sách.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsReadingWidgetMoveQueue.kt`

### Global RegExp làm content filter bỏ sót text node
- **Ngày**: 2026-08-17
- **Vấn đề**: Một số text node chứa nội dung cần lọc vẫn hiển thị trong WebView.
- **Root cause**: Regex có cờ `g` được dùng `.test()` liên tiếp; `lastIndex` từ node trước làm node sau bắt đầu kiểm tra sai offset.
- **Fix**: Reset `lastIndex = 0` trước mỗi `test`, hoặc dùng regex không-global để detect và regex global riêng để replace.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### HtmlNormalizer bọc block container vào paragraph
- **Ngày**: 2026-08-17
- **Vấn đề**: Chapter ít thẻ `<p>` chứa list/table/pre có thể bị normalize thành DOM không hợp lệ và lệch layout/TTS index.
- **Root cause**: Danh sách block tag thủ công thiếu `ul`, `ol`, `table`, `pre`, `dl` và các container hợp lệ khác, nên chúng bị gom vào `<p>`.
- **Fix**: Dựa vào block semantics của Jsoup hoặc duy trì danh sách block đầy đủ; thêm fixture test cho list, table, nested section và mixed content.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/HtmlNormalizer.kt`

### Rò rỉ nội dung chương sách trên Android Logcat
- **Ngày**: 2026-08-17
- **Vấn đề**: Reader ghi 1000 ký tự HTML của từng chương vào Logcat, có thể làm lộ nội dung riêng tư hoặc có bản quyền.
- **Root cause**: Lệnh debug log `Log.d("EpubPro_HTML", "First 1000 chars: ${preparedHtml.take(1000)}")` còn sót lại trong production path.
- **Fix**: Xóa bỏ lệnh log nội dung chương, chỉ giữ lại log kích thước, generation token và cờ phân trang.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/webview/EpubProWebView.kt`
---

## How-To

### Cách lưu & khôi phục cấu hình đọc sách bằng ReaderPreferencesManager
- **Ngày**: 2026-08-05
- **Bước thực hiện**:
  1. Để `@Singleton ReaderPreferencesManager` đọc SharedPreferences một lần và expose `StateFlow<ReaderSettings>`.
  2. Trong `saveSettings`, normalize mode/font/tap-zone/speed, persist rồi emit chính giá trị normalized.
  3. Với cập nhật từng field, dùng `updateSettings { current -> current.copy(...) }` để luôn dựa trên state mới nhất.
  4. Reader/Profile collect cùng flow; không tạo MutableStateFlow settings riêng trong từng ViewModel.
  5. Viết migration test cho key cũ, enum cũ và giá trị ngoài range.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `core/storage/src/test/java/com/epubpro/core/storage/ReaderPreferencesMigrationTest.kt`

### Cách thêm một Reader setting đồng bộ end-to-end
- **Ngày**: 2026-08-05
- **Bước thực hiện**:
  1. Thêm field/default và constants liên quan vào `ReaderSettings`/domain.
  2. Thêm key đọc, normalize, lưu và migration fallback trong `ReaderPreferencesManager`.
  3. Phân loại field là rendering hay runtime; cập nhật `contentReloadKey()` hoặc bridge runtime tương ứng.
  4. Dùng cùng StateFlow và cùng constants cho Reader Settings lẫn Profile Settings.
  5. Slider giữ draft và commit khi thả; command/chip có thể persist ngay một lần.
  6. Thêm unit test reload-key/migration, build, lint, cài ADB và smoke-test đồng bộ hai chiều.
- **Files liên quan**: `domain/src/main/java/com/epubpro/domain/model/Models.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/PageTurnControlScreen.kt`

### Cách parse OPF Spine thứ tự chương EPUB & Natural Order Fallback
- **Ngày**: 2026-07-27
- **Bước thực hiện**:
  1. Đọc entry `.opf` trong Zip file.
  2. Parse `<manifest>` lấy bảng map `id -> href` dùng regex `(?i)<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["']`.
  3. Parse `<spine>` lấy danh sách `idref` từ `<itemref idref="...">`.
  4. Lần lượt lấy ZipEntry tương ứng theo danh sách `idref`.
  5. Nếu không parse được OPF, fallback dùng Natural Order Comparator (`chap1` < `chap2` < `chap1007`) để sắp xếp `htmlEntries`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`

### Cách sanitize và inject XHTML vào Reader WebView an toàn
- **Ngày**: 2026-08-17
- **Bước thực hiện**:
  1. Đưa chuỗi XHTML/HTML qua `EpubHtmlSanitizer.sanitize()`.
  2. Jsoup loại bỏ triệt để blacklist tags (`script`, `iframe`, `object`, `embed`, `form`, `style`, `meta-refresh`).
  3. Quét mọi element xóa event handler `on*` và làm sạch các URL `javascript:`, `vbscript:`, chỉ giữ `data:image/` cho ảnh an toàn.
  4. Tạo head/CSS/script nội bộ từ dữ liệu đã quote; dùng regex replacement lambda để giữ literal escape.
  5. Inject trước `</head>`, sau tag mở `<html...>`, hoặc bọc document fallback khi thiếu cấu trúc.
  6. Cấu hình WebView tắt `allowFileAccess`, `allowContentAccess`, `domStorageEnabled` và chặn external navigation trong `shouldOverrideUrlLoading`.
  7. Test script/event handler, external URL, malformed XHTML, generation và pagination regression.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/EpubHtmlSanitizer.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/webview/EpubProWebView.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Cách chẩn đoán lỗi JavaScript trong EPUB WebView bằng Logcat
- **Ngày**: 2026-08-17
- **Bước thực hiện**:
  1. Lọc đúng PID ứng dụng và các tag `chromium`, `EpubPro_HTML`, `EpubPro_VM`.
  2. Tìm thông báo console có số dòng, ví dụ `INFO:CONSOLE:371`.
  3. Chỉ dump đoạn HTML đã redact trong debug build; production chỉ log generation, length và hash.
  4. So sánh chuỗi trước/sau injection, sửa root cause rồi gỡ log tạm.
  5. Build/cài lại, mở đúng sách và xác nhận log có callback page metrics nhưng không còn console syntax error.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`
---

## Patterns

### Canvas Dotted Tap Zone Diagram Pattern
- **Ngày**: 2026-08-03
- **Chi tiết**: Sử dụng Jetpack Compose `Canvas` với `PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)` để vẽ các đường đứt đoạn phân chia vùng chạm lật trang (Ngang 3 cột, Dọc 3 hàng, Chia trên dưới) kết hợp icon điều hướng SVG giúp hiển thị trực quan các chế độ điều khiển.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/PageTurnControlScreen.kt`

### Interactive 3x3 Tap Zone Pattern
- **Ngày**: 2026-08-05
- **Chi tiết**: Dùng `ModalBottomSheet` và `mutableStateMapOf<Int, TapZoneAction>()` cho ma trận 9 vùng. Chỉ expose hành động đã có runtime implementation (`PREV_PAGE`, `NEXT_PAGE`, `TOGGLE_CONTROLS`). Khi đổi preset layout, tạo lại đủ 9 action bằng `defaultTapZoneActions(layout)`; khi lưu custom map, validate đúng 9 phần tử trước khi persist.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/PageTurnControlScreen.kt`, `domain/src/main/java/com/epubpro/domain/model/Models.kt`

### Strict Multi-Column CSS Math Pattern (100vw Column & Block-Level Margin Padding)
- **Ngày**: 2026-07-31
- **Chi tiết**: Trong EPUB WebView cuộn ngang, luôn set `column-width: 100vw; column-gap: 0px;` trên multi-column container (`body`) và đẩy `padding-left`/`padding-right` vào các phần tử thẻ con dạng khối. Cách này giữ cho khoảng cách phép tính `scrollToPage((page - 1) * 100vw)` khớp 100% tuyệt đối mà không bị lệch lề ở bất kỳ trang nào.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### 4-Directional Margin Geometry cho EPUB Multi-Column
- **Ngày**: 2026-08-05
- **Chi tiết**: Với chu kỳ trang cố định `100vw`, giữ `column-width: 100vw` và `column-gap: 0` trên `body`. Margin trên/dưới đặt ở body; margin trái/phải áp vào từng block content bằng padding + `box-sizing: border-box`. Không đưa tổng margin vào column-gap vì sẽ phá công thức `scrollX = (page - 1) * viewportWidth`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### ViewModel Persistence Guard Pattern (Chống đè dữ liệu khi loading)
- **Ngày**: 2026-07-27
- **Chi tiết**: Trong ViewModel hỗ trợ lưu tiến trình (save progress, save settings), luôn kiểm tra `if (state.isLoading || state.isInitializing) return@launch` trong mọi hàm save. Việc này ngăn các callback từ UI/JS phát tín hiệu sớm đè dữ liệu rỗng/mặc định lên DB trước khi dữ liệu thật được load xong.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### forceBodyDimensions pattern — Bulletproof CSS override qua JS
- **Ngày**: 2026-07-27
- **Chi tiết**: Khi CSS stylesheet `!important` bị override bất ngờ trong Android WebView (do EPUB inline styles, WebView bugs, hoặc CSS cascade conflicts), dùng JS `element.style.setProperty(prop, value, 'important')` để set inline `!important` — đây là priority CAO NHẤT trong CSS cascade, không gì override được. Gọi trong `initLayout()` và `resize` event.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Draft Slider + Commit-on-Release Pattern
- **Ngày**: 2026-08-05
- **Chi tiết**: Slider cấu hình giữ `remember(value)` draft để label/thumb phản hồi tức thì, nhưng chỉ gọi ViewModel/persist tại `onValueChangeFinished`. Chip/toggle là thao tác rời rạc nên cập nhật ngay. Pattern này giảm ghi storage, tránh recomposition dây chuyền và ngăn WebView reload theo từng pixel kéo.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ReadingDefaultsScreen.kt`

### Content Reload Key Regression Test Pattern
- **Ngày**: 2026-08-05
- **Chi tiết**: Duy trì pure function tạo reload key chỉ từ rendering fields. Unit test phải chứng minh runtime-only variants giữ nguyên key và rendering variants đổi key. Test này ngăn việc thêm field mới vào `ReaderSettings` rồi vô tình đưa toàn bộ data class hash trở lại khóa WebView.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `feature/reader/src/test/java/com/epubpro/feature/reader/ReaderContentReloadKeyTest.kt`

### Literal Regex Replacement Pattern cho HTML/JavaScript
- **Ngày**: 2026-08-05
- **Chi tiết**: Khi replacement chứa JavaScript, JSON, đường dẫn hoặc dữ liệu có `$` và `\`, không dùng overload nhận replacement string. Dùng transform lambda để kết quả được chèn như literal và không bị regex engine diễn giải lần hai.
- **Ví dụ code**:
  ```kotlin
  headEndRegex.replace(html) {
      "$headInjection</head>"
  }
  ```
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Dual Overlay Layer CSS Padding Inheritance Pattern
- **Ngày**: 2026-08-06
- **Chi tiết**: Các lớp overlay động (`.epubpro-page-layer`) của Dual Overlay Page Turn Engine được append trực tiếp vào `html` (không phải con trực tiếp của `body`). Khi viết CSS selector quy định margin/padding lề, BẮT BUỘC phải dùng cặp selector đồng thời `body > *, .epubpro-page-layer > *` và reset tầng sâu `body > * *, .epubpro-page-layer > * *` để nội dung lớp overlay thừa hưởng chính xác 100% lề của `body`, tránh giật/nhảy vị trí chữ khi bắt đầu gesture kéo lật trang.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Fractional Font Size Precision Pattern (0.5 sp Stepping)
- **Ngày**: 2026-08-06
- **Chi tiết**: Để hỗ trợ cỡ chữ lẻ (0.5 sp bước nhảy như 17.5, 18.5 sp): (1) String resource dùng format `%1$s sp` thay vì `%1$d sp`; (2) `CssInjector` format chuỗi `fontSizePx` giữ nguyên thập phân `%.1f.format(Locale.US, fontSizeSp)` không gọi `.toInt()`; (3) Slider Compose đặt `steps = 39` cho dải `12f..32f` và làm tròn `round(it * 2f) / 2f` khi kéo slide.
- **Files liên quan**: `core/designsystem/src/main/res/values/strings.xml`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ReadingDefaultsScreen.kt`

### Hardened WebSettings & Out-of-App Intent Navigation Pattern
- **Ngày**: 2026-08-17
- **Chi tiết**: Đối với WebView nạp dữ liệu untrusted (sách tải về), luôn tắt `allowFileAccess`, `allowContentAccess`, `domStorageEnabled`. Trong `shouldOverrideUrlLoading`, chặn toàn bộ điều hướng nội bộ để bảo vệ state/DOM/Bridge của Reader; nếu là link web `http/https`, mở an toàn qua trình duyệt hệ thống với `Intent.ACTION_VIEW` và cờ `FLAG_ACTIVITY_NEW_TASK` bọc trong `runCatching`.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/webview/EpubProWebView.kt`

### Cross-Boundary Movement Carry Pattern
- **Ngày**: 2026-08-17
- **Chi tiết**: Khi queue nhiều bước Next/Previous, chuyển chapter phải giữ phần overflow/underflow. Với chiều tiến, trừ số item của chapter hiện tại rồi tiếp tục; với chiều lùi, cộng số item của chapter trước. Không reset thẳng về đầu/cuối chapter vì sẽ làm mất thao tác đã được queue.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

### Pending Engine Callback Ownership Pattern
- **Ngày**: 2026-08-17
- **Chi tiết**: TTS engine lazy-init phải gắn callback success/error với chính `PendingSpeech` đang chờ. Không fallback sang callback lưu từ lần initialize trước vì có thể null, stale hoặc thuộc request khác. Stop/cancel phải xóa pending request; callback native muộn phải được generation/state guard trước khi đổi playback state.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/PiperTtsEngineWrapper.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/AndroidNativeTtsEngine.kt`

### Stateful Global RegExp Reset Pattern
- **Ngày**: 2026-08-17
- **Chi tiết**: JavaScript RegExp có cờ `g` hoặc `y` thay đổi `lastIndex` khi gọi `test()`/`exec()`. Khi tái sử dụng trên nhiều text node, reset `lastIndex = 0` trước mỗi input hoặc dùng regex non-global để detect và regex global riêng để replace. Pattern này đặc biệt quan trọng với DOM TreeWalker và content filter.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`
