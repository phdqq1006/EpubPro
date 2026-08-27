# Content Filter & Replacement Feature

> Tổng hợp kiến thức về hệ thống Lọc & Thay thế từ ngữ (Content Filter & Text Replacement) trong Reader WebView và TTS Audio Engine.
> Cập nhật lần cuối: 2026-08-27

---

## Architecture

### Dual-Layer Content Filter & Replacement Architecture (Native Kotlin & JS TreeWalker)
- **Ngày**: 2026-08-27
- **Chi tiết**: Hợp nhất hệ thống Lọc và Thay thế từ ngữ thành một cấu trúc 2 tầng thống nhất, chia sẻ cấu hình `ContentFilterPreferences` (`rules: List<ContentFilterRule>` gồm `pattern`, `replacement`, `isRegex`, `isEnabled`):
  1. **WebView Layer**: Tiêm JavaScript `TreeWalker` trực tiếp trên các Text Node (`NodeFilter.SHOW_TEXT`) trong DOM. Thay thế từ trùng khớp theo `replacement` (hoặc xóa nếu rỗng) bằng function replacer và gọi `document.body.normalize()` để văn bản tự động nối lại liền mạch mà không làm mất DOM tree.
  2. **TTS Engine Layer**: Sử dụng `ContentSanitizer.sanitize(rawText)` làm sạch và thay thế chuỗi văn bản bằng lambda replacer trước khi gửi sang Android Native TTS / Piper TTS.
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `domain/src/main/java/com/epubpro/domain/model/ContentFilterModels.kt`

### Selection-to-Replace Flow với BottomSheet nhập nhanh
- **Ngày**: 2026-08-27
- **Chi tiết**: Action “Thay thế” trong selection toolbar thu thập `window.getSelection()` và mở `ReplaceTextBottomSheet` ngay tại màn đọc. BottomSheet điền sẵn từ gốc, hỗ trợ nhập từ thay thế, bật/tắt Regex kèm validation trực tiếp. Khi lưu, `ReaderPreferencesManager` lưu JSON an toàn và phát `filterPreferences: StateFlow` mới, đồng bộ tức thì cho cả trang sách và giọng đọc TTS mà không cần reload toàn bộ trang.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/webview/ReaderSelectionWebView.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/filter/ReplaceTextBottomSheet.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`

---

## Bugs & Solutions

### Replacement chứa ký tự đặc biệt ($1, $&) gây crash TTS và lỗi hiển thị WebView
- **Ngày**: 2026-08-27
- **Vấn đề**: Khi người dùng thay thế từ ngữ bằng chuỗi chứa ký tự `$` (ví dụ `$100`, `$&`), `Regex.replace` trên JVM ném exception `IllegalArgumentException: No group 1` gây crash TTS. Trên WebView, chuỗi bị diễn giải sai thành capture group token.
- **Root cause**: `String.replace(Regex, replacementString)` mặc định diễn giải ký tự `$` là tham chiếu nhóm capture.
- **Fix**:
  - Trong Kotlin: Dùng lambda replacer `result.replace(regex) { rule.replacement }` để coi kết quả trả về là chuỗi ký tự nguyên bản (literal).
  - Trong JavaScript: Dùng function replacer `n.nodeValue.replace(regex, function() { return rule.replacement; })`.
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Combined Regex chứa Duplicate Named Capture Groups làm WebView bỏ qua filter
- **Ngày**: 2026-08-27
- **Vấn đề**: Khi người dùng thêm 2 quy tắc Regex riêng lẻ nhưng trùng tên capture group (ví dụ `(?<x>a)` và `(?<x>b)`), biểu thức tổng hợp `new RegExp(patterns.join('|'))` ném `SyntaxError: Duplicate capture group name`, rơi vào catch ngoài làm hủy toàn bộ bộ lọc.
- **Root cause**: Ghép chuỗi regex trực tiếp mà không bọc khối catch riêng cho detection regex.
- **Fix**: Bọc `try { detectRegex = new RegExp(...) } catch` riêng. Nếu compile detection regex thất bại, tự động fallback sang duyệt trực tiếp từng `activeRules[r].regex.test(node.nodeValue)`.
- **Files liên quan**: `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### UI Row Squishing Button Layout trong Compose
- **Ngày**: 2026-08-27
- **Vấn đề**: Đặt Checkbox/Switch và Button cùng một `Row` ngang khiến Button bị ép chặt chiều ngang, text nút nhảy thành nhiều dòng dọc ("T \n + hê \n m").
- **Fix**: Tách dòng "Biểu thức chính quy (Regex)" thành một `Row` riêng với `Switch`, và chuyển nút "Thêm quy tắc" thành nút Full-width (`Modifier.fillMaxWidth().height(48.dp)`).
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsScreen.kt`

### Double JSON.parse làm WebView bỏ qua toàn bộ rule đã lưu
- **Ngày**: 2026-08-26
- **Vấn đề**: Rule xuất hiện đúng trong config và `isFilterEnabled` đã bật nhưng nội dung Reader không bị xóa.
- **Root cause**: `quoteForJsArgument(rulesJsonArray.toString())` tạo một JavaScript string literal chứa JSON. Lần `JSON.parse` đầu đã trả về mảng rule; `JSON.parse` lần hai nhận mảng, ép thành `"[object Object]"` rồi ném `SyntaxError`. Exception bị `FILTER_ERROR` bắt nên `TreeWalker` không chạy.
- **Fix**: Chỉ gọi `JSON.parse(filterRulesJson)` một lần. Regression test phải assert có single parse và cấm `JSON.parse(JSON.parse(...))`.
- **Files liên quan**: `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `core/reader-renderer/src/test/java/com/epubpro/core/reader/style/CssInjectorTest.kt`

---

## How-To

### Cách thêm/sửa một quy tắc lọc & thay thế và đồng bộ xuống WebView & TTS
- **Ngày**: 2026-08-27
- **Bước thực hiện**:
  1. Nhận `pattern`, `replacement`, `isRegex` từ `ContentFilterSettingsScreen` hoặc `ReplaceTextBottomSheet`.
  2. Validate cú pháp Regex trên UI (`runCatching { Regex(pattern) }`), hiển thị lỗi inline và chặn lưu nếu không hợp lệ.
  3. Gọi `ReaderPreferencesManager.updateFilterPreferences` (hoặc `withEnabledReplaceRule`) để lưu JSON và cập nhật `filterPreferences: StateFlow`.
  4. `ReaderViewModel` và `EpubProWebView` tự động cập nhật script lọc qua `CssInjector`; `TtsService` đồng thời nhận cấu hình mới qua `ContentSanitizer`.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsViewModel.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/filter/ReplaceTextBottomSheet.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`

---

## Patterns

### Literal Text Replacement Pattern trong Kotlin & JS
- **Ngày**: 2026-08-27
- **Chi tiết**: Khi thay thế chuỗi regex mà replacement có thể chứa ký tự đặc biệt do người dùng nhập (`$`, `\`), luôn dùng transform callback:
  ```kotlin
  // Kotlin
  text.replace(regex) { rule.replacement }
  ```
  ```javascript
  // JavaScript
  nodeValue.replace(regex, function() { return rule.replacement; });
  ```
- **Files liên quan**: `core/playback/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader-renderer/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Client-side Regex Validation Pattern trong Material3 BottomSheet & TextField
- **Ngày**: 2026-08-27
- **Chi tiết**: Tính toán `errorMessage` reactive bằng `remember(pattern, isRegex)`:
  ```kotlin
  val errorMessage = remember(pattern, isRegex) {
      if (pattern.isBlank()) null
      else if (isRegex) {
          runCatching { Regex(pattern); null }.getOrElse { "Cú pháp Regex không hợp lệ" }
      } else null
  }
  val isInputValid = pattern.isNotBlank() && errorMessage == null
  ```
  Gán `isError = errorMessage != null`, hiển thị `supportingText` và gán `enabled = isInputValid` cho action Button.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/filter/ReplaceTextBottomSheet.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsScreen.kt`
