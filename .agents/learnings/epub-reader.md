# EPUB Reader & Layout Engine

> Tổng hợp kiến thức về hệ thống đọc EPUB, WebView rendering, tối ưu bộ nhớ RAM và Room FTS5 trong dự án.
> Cập nhật lần cuối: 2026-07-31

---

## Architecture

### On-Demand Lazy Chapter Loading Architecture
- **Ngày**: 2026-07-23
- **Chi tiết**: Đối với các file EPUB dung lượng lớn (hàng ngàn chương), không được nạp toàn bộ mã XHTML của tất cả các chương vào RAM cùng một lúc. Tách thành `EpubChapterHeader` (chỉ chứa tiêu đề và đường dẫn entry, tốn <0.01MB RAM) và nạp `loadChapterHtml()` duy nhất chương đang xem vào WebView.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/ReadiumEngine.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Batch Streaming FTS Indexer
- **Ngày**: 2026-07-23
- **Chi tiết**: Xây dựng luồng đánh chỉ mục tìm kiếm toàn văn FTS ngầm qua WorkManager/Coroutine bằng cách đọc từng chương từ file Zip, băm chỉ mục theo batch 5 chương/lần và xả bộ nhớ string ngay lập tức để tránh đè nặng lên Garbage Collector.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/ReadiumEngine.kt`, `core/database/src/main/java/com/epubpro/core/database/repository/RepositoryImpls.kt`

### Fullscreen Reader Overlay Architecture (Chống xô dịch Layout khi ẩn/hiện Controls)
- **Ngày**: 2026-07-27
- **Chi tiết**: Tách WebView thành lớp nền cố định chiếm 100% toàn màn hình (`fillMaxSize()`). Không dùng `Scaffold(topBar, bottomBar)` vì khi `showControls` đổi, `paddingValues` thu hẹp/giãn ra làm WebView resize, khiến CSS `100vh` recalculate và dồn/xô dịch toàn bộ văn bản. Đặt TopAppBar và BottomBar thành các lớp phủ nổi (`Overlay` với `Alignment.TopCenter`/`Alignment.BottomCenter` trong `BoxScope`) đè lên WebView.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Multi-Column phải đặt trên body, KHÔNG đặt trên html
- **Ngày**: 2026-07-27
- **Chi tiết**: Trong kiến trúc CSS multi-column pagination cho EPUB WebView, `column-width` PHẢI đặt trên `body` (multi-column container). Nếu đặt trên `html`, thì `body` là child duy nhất — nếu `body` có fixed height, nó trở thành khối monolithic KHÔNG THỂ bị fragment qua các cột. Kết quả: chỉ 1 cột hiển thị, text tràn dọc bị `overflow-y: hidden` xén sạch.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Multi-Engine Architecture cho EPUB Reader
- **Ngày**: 2026-07-28
- **Chi tiết**: Hỗ trợ 2 Engine song song: EpubPro Custom Engine (tốc độ cao, tối ưu RAM, TTS) và Readium SDK 3.0.0 (chuẩn hóa toàn cầu). Sử dụng `ReaderSelectionBottomSheet` ở tầng Thư viện làm lớp định tuyến: mở thẳng Compose cho Custom hoặc `Intent` kích hoạt `ReadiumReaderActivity` cho Readium. Tách biệt hoàn toàn luồng hoạt động.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/LibraryScreen.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/readium/ReadiumReaderActivity.kt`

---

## Bugs & Solutions

### OutOfMemoryError do nạp toàn bộ HTML tất cả các chương vào RAM
- **Ngày**: 2026-07-23
- **Vấn đề**: `java.lang.OutOfMemoryError` khi nạp sách EPUB lớn (1500+ chương).
- **Root cause**: `extractChapters()` đọc toàn bộ `readText()` của tất cả các entry XHTML trong file EPUB Zip và giữ đồng thời trong một List duy nhất trong bộ nhớ Heap 256MB.
- **Fix**: Chuyển sang mô hình Lazy Header `extractChapterHeaders()` + nạp `loadChapterHtml()` theo yêu cầu + phân batch 5 chương khi index FTS + bật `android:largeHeap="true"`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/ReadiumEngine.kt`, `app/src/main/AndroidManifest.xml`

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
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/ReadiumEngine.kt`

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

### Xung đột Android SDK 36 khi nâng cấp Readium Kotlin Toolkit 3.3.x
- **Ngày**: 2026-07-28
- **Vấn đề**: Biên dịch lỗi toàn bộ dự án khi nâng cấp Readium lên bản `3.3.0`.
- **Root cause**: Phiên bản 3.3.0 phụ thuộc `core-ktx 1.18.0` và ép hệ thống phải dùng Android SDK API 36, AGP 8.9.1 cùng `desugar_jdk_libs 2.1.5`, xung đột trực tiếp với cấu hình API 34 & AGP 8.3.2 hiện tại.
- **Fix**: Rollback về phiên bản Stable `3.0.0` (tương thích tối đa với API 34). Tránh việc phải cấu hình lại toàn bộ hệ thống Gradle mà vẫn dùng tốt `EpubNavigatorFragment`.
- **Files liên quan**: `gradle/libs.versions.toml`

---

## How-To

### Cách lưu & khôi phục cấu hình đọc sách bằng ReaderPreferencesManager
- **Ngày**: 2026-07-27
- **Bước thực hiện**:
  1. Tạo `@Singleton` class `ReaderPreferencesManager` bọc `SharedPreferences`.
  2. Lưu/đọc bộ tham số `ReaderSettings`: themeMode, fontSizeSp, fontFamily, marginTopDp, marginBottomDp, marginLeftDp, marginRightDp, isHorizontalPagination.
  3. Inject `ReaderPreferencesManager` vào `ReaderViewModel`. Khởi tạo StateFlow `_uiState` mặc định bằng `preferencesManager.getSettings()`.
  4. Trong `updateSettings(newSettings)`, gọi `preferencesManager.saveSettings(newSettings)` để persist tức thì.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Cách parse OPF Spine thứ tự chương EPUB & Natural Order Fallback
- **Ngày**: 2026-07-27
- **Bước thực hiện**:
  1. Đọc entry `.opf` trong Zip file.
  2. Parse `<manifest>` lấy bảng map `id -> href` dùng regex `(?i)<item\s+[^>]*id=["']([^"']+)["'][^>]*href=["']([^"']+)["']`.
  3. Parse `<spine>` lấy danh sách `idref` từ `<itemref idref="...">`.
  4. Lần lượt lấy ZipEntry tương ứng theo danh sách `idref`.
  5. Nếu không parse được OPF, fallback dùng Natural Order Comparator (`chap1` < `chap2` < `chap1007`) để sắp xếp `htmlEntries`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/ReadiumEngine.kt`

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

### Cách khởi tạo Readium PublicationOpener (Readium 3.0.0)
- **Ngày**: 2026-07-28
- **Bước thực hiện**:
  1. API `Streamer` cũ đã bị loại bỏ. Khởi tạo `HttpClient` và `AssetRetriever(contentResolver)`.
  2. Tạo `PublicationOpener(publicationParser = EpubParser())` (dùng `EpubParser()` thay vì `DefaultPublicationParser` để tránh lỗi cấu hình `pdfFactory`).
  3. Lấy Asset an toàn qua `assetRetriever.retrieve(url).getOrNull()`.
  4. Mở Publication, lấy factory `EpubNavigatorFactory(pub)`. Gọi `fragmentFactory.instantiate` và nạp vào FragmentContainer của Activity.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/readium/ReadiumReaderActivity.kt`

---

## Patterns

### Strict Multi-Column CSS Math Pattern (100vw Column & Block-Level Margin Padding)
- **Ngày**: 2026-07-31
- **Chi tiết**: Trong EPUB WebView cuộn ngang, luôn set `column-width: 100vw; column-gap: 0px;` trên multi-column container (`body`) và đẩy `padding-left`/`padding-right` vào các phần tử thẻ con dạng khối. Cách này giữ cho khoảng cách phép tính `scrollToPage((page - 1) * 100vw)` khớp 100% tuyệt đối mà không bị lệch lề ở bất kỳ trang nào.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Reader Engine Selection Persistence Pattern
- **Ngày**: 2026-07-31
- **Chi tiết**: Lưu cấu hình Reader Engine (`WEBVIEW`/`READIUM`) vào `ReaderPreferencesManager`. Trong `LibraryScreen`, nếu `isEngineConfigured()` trả về true, mở trực tiếp sách bằng engine đã chọn mà không hiển thị lại `ReaderSelectionBottomSheet`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/library/src/main/java/com/epubpro/feature/library/LibraryScreen.kt`

### 4-Directional Margin Geometry Pattern cho EPUB Multi-Column
- **Ngày**: 2026-07-27
- **Chi tiết**: Khi hỗ trợ căn lề 4 hướng (trên, dưới, trái, phải) độc lập trong CSS multi-column pagination:
  - `padding-top: marginTop; padding-bottom: marginBottom; padding-left: marginLeft; padding-right: marginRight;` trên `body`.
  - `column-width = calc(100vw - ${marginLeft + marginRight}px)`
  - `column-gap = ${marginLeft + marginRight}px`
  - Đảm bảo tổng chu kỳ cột `colWidth + colGap = 100vw`, giúp mỗi trang cuộn ngang bằng đúng 1 viewport mà không bị lệch hay đè viền lề.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `domain/src/main/java/com/epubpro/domain/model/Models.kt`

### ViewModel Persistence Guard Pattern (Chống đè dữ liệu khi loading)
- **Ngày**: 2026-07-27
- **Chi tiết**: Trong ViewModel hỗ trợ lưu tiến trình (save progress, save settings), luôn kiểm tra `if (state.isLoading || state.isInitializing) return@launch` trong mọi hàm save. Việc này ngăn các callback từ UI/JS phát tín hiệu sớm đè dữ liệu rỗng/mặc định lên DB trước khi dữ liệu thật được load xong.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### forceBodyDimensions pattern — Bulletproof CSS override qua JS
- **Ngày**: 2026-07-27
- **Chi tiết**: Khi CSS stylesheet `!important` bị override bất ngờ trong Android WebView (do EPUB inline styles, WebView bugs, hoặc CSS cascade conflicts), dùng JS `element.style.setProperty(prop, value, 'important')` để set inline `!important` — đây là priority CAO NHẤT trong CSS cascade, không gì override được. Gọi trong `initLayout()` và `resize` event.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Safe Result Decoding Pattern (Xử lý Try/Result từ SDK ngoài)
- **Ngày**: 2026-07-28
- **Chi tiết**: Khi SDK bên thứ 3 (như Readium) trả về các kiểu `Result` hoặc `Try` tùy chỉnh, TUYỆT ĐỐI không gọi `getOrThrow()`. Luôn bọc trong khối `try-catch`, gọi `getOrNull()`, và dùng toán tử Elvis `?: return` để thoát hàm an toàn, ngăn crash ngầm do Exception cấu trúc.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/readium/ReadiumReaderActivity.kt`
