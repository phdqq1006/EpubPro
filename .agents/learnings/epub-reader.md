# EPUB Reader & Layout Engine

> Tổng hợp kiến thức về hệ thống đọc EPUB, WebView rendering, tối ưu bộ nhớ RAM, Room FTS5 và cài đặt mặc định đọc / chuyển trang trong dự án.
> Cập nhật lần cuối: 2026-08-06

---

## Architecture

### Bounded Adjacent Chapter Preloading Architecture
- **Ngày**: 2026-08-05
- **Chi tiết**: Với EPUB lớn, chỉ giữ metadata của toàn bộ spine và tối đa ba chuỗi XHTML: chương trước, hiện tại, chương sau. Chương hiện tại phục vụ WebView; hai chương kề chỉ phục vụ preview ở biên. Không preload xa hơn để tránh quay lại lỗi giữ hàng nghìn chương trong RAM. Việc đọc Zip nên tuần tự trên thiết bị; preview là dữ liệu bổ trợ và không được làm hỏng nội dung chương chính.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`
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
- **Ngày**: 2026-08-03
- **Chi tiết**: Trong chế độ cuộn trang ngang CSS Multi-Column, nâng cấp cơ chế lật trang từ `scrollToPage` cố định thành engine theo dõi ngón tay real-time qua `touchstart`, `touchmove`, `touchend`. Khi vuốt ngón tay, `window.scrollTo(startScrollX - deltaX)` dịch chuyển trang sách bám sát theo tay với tần số 60fps, đồng thời một phần tử overlay gradient (`#epubpro-shadow-overlay`) hiển thị ở mép trang bị kéo. Thả tay sẽ kiểm tra ngưỡng kéo (>22% screen width) hoặc gia tốc vuốt để lật trang mượt với cubic-bezier curve hoặc nảy đàn hồi (snap back) về trang cũ.
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
- **Chi tiết**: Để màn hình đọc sách phủ tràn 100% màu nền của theme (tránh vệt trắng ở đỉnh và đáy): (1) Đặt `Modifier.background(readerBgColor)` lên root `Box` của `ReaderScreen`; (2) Sử dụng `DisposableEffect(readerBgColor, isDarkTheme)` cập nhật `window.statusBarColor` và `window.navigationBarColor` bằng `readerBgColor.toArgb()`, kết hợp `WindowCompat.getInsetsController().isAppearanceLightStatusBars = !isDarkTheme` để tự động điều chỉnh màu icon hệ thống (tối trên nền Sepia/Light, sáng trên nền Dark/Midnight); (3) Khôi phục màu gốc trong `onDispose`.
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
- **Ngày**: 2026-07-23
- **Vấn đề**: `java.lang.OutOfMemoryError` khi nạp sách EPUB lớn (1500+ chương).
- **Root cause**: `extractChapters()` đọc toàn bộ `readText()` của tất cả các entry XHTML trong file EPUB Zip và giữ đồng thời trong một List duy nhất trong bộ nhớ Heap 256MB.
- **Fix**: Chuyển sang mô hình Lazy Header `extractChapterHeaders()` + nạp `loadChapterHtml()` theo yêu cầu + phân batch 5 chương khi index FTS + bật `android:largeHeap="true"`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `app/src/main/AndroidManifest.xml`

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

### Cách Inject CSS và Viewport Meta vào file XHTML an toàn
- **Ngày**: 2026-07-27
- **Bước thực hiện**:
  1. Strip viewport meta cũ: `(?i)<meta\s+name=["']viewport["'][^>]*>`.
  2. Strip external stylesheets: `(?i)<link[^>]*rel=["']stylesheet["'][^>]*>`.
  3. Strip tất cả `<style>` blocks: `(?is)<style[^>]*>.*.*?/style>`.
  4. Strip inline styles khỏi body/html: `(?i)(<body[^>]*?)\s+style\s*=\s*"[^"]*"`.
  5. Detect `</head>` bằng `contains("</head>")` → inject trước `</head>`.
  6. Detect `<html` (có attributes) → inject `<head>...</head>` sau tag mở.
  7. Fallback: bọc toàn bộ trong `<!DOCTYPE html><html><head>...</head><body>...</body></html>`.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Cách chẩn đoán lỗi JavaScript trong EPUB WebView bằng Logcat
- **Ngày**: 2026-08-05
- **Bước thực hiện**:
  1. Lọc đúng PID ứng dụng và các tag `chromium`, `EpubPro_HTML`, `EpubPro_VM`.
  2. Tìm thông báo console có số dòng, ví dụ `INFO:CONSOLE:371`.
  3. Tạm log `preparedHtml.lineSequence()` quanh số dòng đó để xem HTML cuối cùng trước khi nạp WebView.
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

