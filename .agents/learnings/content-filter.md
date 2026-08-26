# Content Filter Feature

> Tổng hợp kiến thức về hệ thống Lọc nội dung nhạy cảm / từ khóa nhạy cảm trong Reader WebView và TTS Engine.
> Cập nhật lần cuối: 2026-08-26

---

## Architecture

### Dual-Layer Content Filtering Architecture (Native Kotlin & JS TreeWalker)
- **Ngày**: 2026-08-06
- **Chi tiết**: Tách việc lọc từ khóa thành 2 lớp xử lý độc lập nhưng dùng chung nguồn cấu hình `ContentFilterPreferences` từ `ReaderPreferencesManager`:
  1. **WebView Layer**: Tiêm JavaScript `TreeWalker` trực tiếp trên các Text Node (`NodeFilter.SHOW_TEXT`) trong DOM. Xóa từ trùng khớp và gọi `document.body.normalize()` để văn bản tự động nối lại liền mạch mà không re-parse lại cây HTML.
  2. **TTS Engine Layer**: Sử dụng `ContentSanitizer.sanitize(rawText)` làm sạch chuỗi văn bản trước khi gửi sang Android Native TTS / Piper TTS. Tự động skip đoạn văn nếu toàn bộ từ trong đoạn đều bị lọc.
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `core/playback/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

### Selection-to-Filter dùng chung nguồn cấu hình runtime
- **Ngày**: 2026-08-26
- **Chi tiết**: Action “Lọc từ” trong native selection toolbar chỉ thu thập `window.getSelection()` và chuyển text về `ReaderViewModel`. ViewModel chuẩn hóa text, bật global filter, tái kích hoạt rule literal trùng hoặc thêm rule mới qua `ReaderPreferencesManager`. `filterPreferences: StateFlow` tiếp tục là nguồn sự thật duy nhất; thay đổi hash làm Reader WebView reload với script lọc mới, đồng thời TTS nhận cùng cấu hình. Không lưu state rule trong WebView hoặc Compose.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/webview/ReaderSelectionWebView.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`

---

## Bugs & Solutions

### Kotlin Raw String Template Interpretation Error với Regex Special Characters
- **Ngày**: 2026-08-06
- **Vấn đề**: Trình biên dịch Kotlin báo lỗi `e: Expecting an expression` tại dòng JavaScript injection trong `CssInjector.kt`.
- **Root cause**: Trong chuỗi Kotlin Raw String (`""" ... """`), đoạn mã JS `replace(/[.*+?^${}()|[\]\\]/g, '\\$&')` chứa ký tự `${}` và `$` bị Kotlin nhầm là String Template Interpolation `${...}`.
- **Fix**: Escape tất cả ký tự `$` thành `${'$'}` để ngăn Kotlin cố evaluate biểu thức JavaScript.
- **Files liên quan**: `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Invalid Individual Regex Rule Breaking Entire Combined Regex Filter
- **Ngày**: 2026-08-06
- **Vấn đề**: Một quy tắc Regex sai cú pháp làm toàn bộ `ContentSanitizer` trả về `null` và không rule nào được áp dụng.
- **Root cause**: Gom mọi `rule.pattern` vào combined pattern mà không validate từng regex trước khi compile.
- **Fix**: Validate riêng từng Regex rule bằng `runCatching { Regex(rule.pattern) }`, chỉ đưa rule hợp lệ vào combined pattern và bỏ qua rule lỗi.
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`

### UI Row Squishing Button Layout trong Compose
- **Ngày**: 2026-08-06
- **Vấn đề**: Nút “Thêm” nằm cùng `Row` với input dài bị ép ngang, khiến label rớt thành nhiều dòng.
- **Fix**: Dùng `IconButton` làm `trailingIcon` của `OutlinedTextField` để action có kích thước ổn định.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsScreen.kt`

### Double JSON.parse làm WebView bỏ qua toàn bộ rule đã lưu
- **Ngày**: 2026-08-26
- **Vấn đề**: Rule xuất hiện đúng trong config và `isFilterEnabled` đã bật nhưng nội dung Reader không bị xóa.
- **Root cause**: `quoteForJsArgument(rulesJsonArray.toString())` tạo một JavaScript string literal chứa JSON. Lần `JSON.parse` đầu đã trả về mảng rule; `JSON.parse` lần hai nhận mảng, ép thành `"[object Object]"` rồi ném `SyntaxError`. Exception bị `FILTER_ERROR` bắt nên `TreeWalker` không chạy.
- **Fix**: Chỉ gọi `JSON.parse(filterRulesJson)` một lần. Regression test phải assert có single parse và cấm `JSON.parse(JSON.parse(...))`.
- **Files liên quan**: `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `core/reader-renderer/src/test/java/com/epubpro/core/reader/style/CssInjectorTest.kt`

---

## How-To

### Cách thêm một rule lọc và đồng bộ xuống WebView & TTS
- **Ngày**: 2026-08-26
- **Bước thực hiện**:
  1. Nhận pattern từ `ContentFilterSettingsScreen` hoặc selection toolbar của `ReaderSelectionWebView`.
  2. Chuẩn hóa input; với selection, tạo rule literal, tránh duplicate và bật lại rule trùng đang tắt.
  3. Gọi `ReaderPreferencesManager.updateFilterPreferences` để persist JSON và phát `filterPreferences` mới.
  4. `ReaderViewModel` cập nhật `ReaderUiState`; `EpubProWebView` đổi reload key và inject rules bằng `CssInjector`.
  5. JavaScript parse rules đúng một lần rồi chạy `TreeWalker`; `TtsService` dùng cùng preferences qua `ContentSanitizer`.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsViewModel.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

---

## Patterns

### Compiled Combined Regex Pattern
- **Ngày**: 2026-08-06
- **Chi tiết**: Để lọc nhiều rule hiệu quả, gom các rule đang bật thành một biểu thức `(?:pattern1)|(?:pattern2)|...` và compile một lần với chế độ ignore-case. Rule literal phải được escape; rule Regex phải được validate riêng trước khi ghép.
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Mở rộng WebView selection toolbar bằng ActionMode.Callback2
- **Ngày**: 2026-08-26
- **Chi tiết**: Khi thêm action nội bộ vào toolbar chọn text, subclass `WebView` và bọc callback gốc bằng `ActionMode.Callback2`. Luôn delegate lifecycle/action hệ thống, thêm menu item sau `onCreateActionMode`/`onPrepareActionMode`, và chuyển tiếp `onGetContentRect` để floating toolbar giữ đúng vị trí. Chỉ đọc selection khi action được bấm; giải mã kết quả `evaluateJavascript` như JSON string và đóng `ActionMode` sau khi lấy text.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/webview/ReaderSelectionWebView.kt`
