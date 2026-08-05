# EPUB Reader & Layout Engine

> Tổng hợp kiến thức về hệ thống đọc EPUB, WebView rendering, tối ưu bộ nhớ RAM, Room FTS5 và cài đặt mặc định đọc / chuyển trang trong dự án.
> Cập nhật lần cuối: 2026-08-05

---

## Architecture

### Bounded Adjacent Chapter Preloading Architecture
- **Ngày**: 2026-08-05
- **Chi tiết**: Với EPUB lớn, chỉ giữ metadata của toàn bộ spine và tối đa ba chuỗi XHTML: chương trước, hiện tại, chương sau. Chương hiện tại phục vụ WebView; hai chương kề chỉ phục vụ preview ở biên. Không preload xa hơn để tránh quay lại lỗi giữ hàng nghìn chương trong RAM. Việc đọc Zip nên tuần tự trên thiết bị; preview là dữ liệu bổ trợ và không được làm hỏng nội dung chương chính.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`
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

### Single Native Engine Architecture (Chuyển đổi từ Readium SDK sang EpubEngine duy nhất)
- **Ngày**: 2026-08-03
- **Chi tiết**: Gỡ bỏ hoàn toàn bộ thư viện Readium SDK (`org.readium.r2:*`), `ReadiumReaderActivity` và `ReaderSelectionBottomSheet`. Đơn giản hóa kiến trúc bằng cách dùng duy nhất `EpubEngine` tự phát triển dựa trên Compose + Custom WebView, giúp tối ưu dung lượng app, mở sách tức thì và hỗ trợ tô sáng âm thanh TTS offline đồng bộ.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`, `feature/library/src/main/java/com/epubpro/feature/library/LibraryScreen.kt`

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
---

## Bugs & Solutions

### Lỗi giật lác (Jitter) và hở chữ khi vuốt lật trang ngang do Native Scroll Conflict
- **Ngày**: 2026-08-04
- **Vấn đề**: Khi vuốt lật trang ngang, giao diện bị giật (flicker) liên tục và ở mép trang cuối cùng của chương bị lọt chữ/khoảng trắng lạ ("dính nội dung chương khác"). Cố gắng dùng `window.scrollTo` trong `touchmove` để cập nhật cuộn bị vô hiệu.
- **Root cause**: Trong Android WebView, khi người dùng đang thực hiện cử chỉ vuốt, engine cuộn native của máy vẫn cố gắng can thiệp (kể cả có gọi `e.preventDefault()`). Nếu ép WebView gọi `window.scrollTo` programmatic trong lúc này, nó có thể bị chặn lại hoặc xử lý trễ tạo ra giật lag. Nếu cuộn sát rìa body, các trang tràn cột sẽ vô tình lộ ra.
- **Fix**: Áp dụng mô hình **Dual Overlay**: chặn hoàn toàn cảm ứng native (dùng `touch-action: none` và `preventDefault()`), giấu thẻ `body` thực đi (bằng Backdrop overlay) và chuyển mọi hình ảnh hiển thị trong lúc vuốt vào CSS `transform: translateX` trên thẻ ảo (Top/Bottom Overlay). `window.scrollTo` chỉ được gọi sau khi thả tay (`touchend`) và animation đã xong.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

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
- **Ngày**: 2026-07-27
- **Bước thực hiện**:
  1. Tạo `@Singleton` class `ReaderPreferencesManager` bọc `SharedPreferences`.
  2. Lưu/đọc bộ tham số `ReaderSettings`: themeMode, fontSizeSp, fontFamily, marginTopDp, marginBottomDp, marginLeftDp, marginRightDp, isHorizontalPagination.
  3. Inject `ReaderPreferencesManager` vào `ReaderViewModel`. Khởi tạo StateFlow `_uiState` mặc định bằng `preferencesManager.getSettings()`.
  4. Trong `updateSettings(newSettings)`, gọi `preferencesManager.saveSettings(newSettings)` để persist tức thì.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Cách mở rộng ReaderSettings và persist thuộc tính đọc mới
- **Ngày**: 2026-08-03
- **Bước thực hiện**:
  1. Mở rộng `ReaderSettings` data class (bổ sung fields + default values).
  2. Định nghĩa các enum hỗ trợ (`ReadingMode`, `TextAlignment`, `TapZoneLayout`, `TapZoneAction`).
  3. Cập nhật `getSettings()` và `saveSettings()` trong `ReaderPreferencesManager` để đọc/lưu `SharedPreferences`.
  4. Bổ sung helper functions trong `ReadingDefaultsViewModel` để emit StateFlow + persist tự động.
- **Files liên quan**: `domain/src/main/java/com/epubpro/domain/model/Models.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ReadingDefaultsViewModel.kt`

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

### Interactive 3x3 Grid Customization Bottom Sheet Pattern
- **Ngày**: 2026-08-03
- **Chi tiết**: Sử dụng `ModalBottomSheet` kết hợp `remember { mutableStateMapOf<Int, TapZoneAction>() }` biểu diễn ma trận 9 vùng chạm màn hình, cho phép chạm từng ô và gán động các hành động như Lật trang, Bật menu, Bookmark hay TTS.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/PageTurnControlScreen.kt`

### Strict Multi-Column CSS Math Pattern (100vw Column & Block-Level Margin Padding)
- **Ngày**: 2026-07-31
- **Chi tiết**: Trong EPUB WebView cuộn ngang, luôn set `column-width: 100vw; column-gap: 0px;` trên multi-column container (`body`) và đẩy `padding-left`/`padding-right` vào các phần tử thẻ con dạng khối. Cách này giữ cho khoảng cách phép tính `scrollToPage((page - 1) * 100vw)` khớp 100% tuyệt đối mà không bị lệch lề ở bất kỳ trang nào.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

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
