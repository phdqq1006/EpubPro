# Content Filter Feature

> Tổng hợp kiến thức về hệ thống Lọc nội dung nhạy cảm / từ khóa nhạy cảm trong Reader WebView và TTS Engine.
> Cập nhật lần cuối: 2026-08-06

---

## Architecture

### Dual-Layer Content Filtering Architecture (Native Kotlin & JS TreeWalker)
- **Ngày**: 2026-08-06
- **Chi tiết**: Tách việc lọc từ khóa thành 2 lớp xử lý độc lập nhưng dùng chung nguồn cấu hình `ContentFilterPreferences` từ `ReaderPreferencesManager`:
  1. **WebView Layer**: Tiêm JavaScript `TreeWalker` trực tiếp trên các Text Node (`NodeFilter.SHOW_TEXT`) trong DOM. Xóa từ trùng khớp và gọi `document.body.normalize()` để văn bản tự động nối lại liền mạch mà không re-parse lại cây HTML.
  2. **TTS Engine Layer**: Sử dụng `ContentSanitizer.sanitize(rawText)` làm sạch chuỗi văn bản trước khi gửi sang Android Native TTS / Piper TTS. Tự động skip đoạn văn nếu toàn bộ từ trong đoạn đều bị lọc.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

---

## Bugs & Solutions

### Kotlin Raw String Template Interpretation Error với Regex Special Characters
- **Vấn đề**: Trình biên dịch Kotlin báo lỗi `e: Expecting an expression` tại dòng JavaScript injection trong `CssInjector.kt`.
- **Root cause**: Trong chuỗi Kotlin Raw String (`""" ... """`), đoạn mã JS `replace(/[.*+?^${}()|[\]\\]/g, '\\$&')` chứa ký tự `${}` và `$` bị Kotlin nhầm là String Template Interpolation `${...}`.
- **Fix**: Escape tất cả các ký tự `$` thành `${'$'}` (ví dụ: `replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\${'$'}&')`) để ngăn Kotlin cố gắng evaluate biểu thức.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Invalid Individual Regex Rule Breaking Entire Combined Regex Filter
- **Vấn đề**: Khi một quy tắc Regex do người dùng nhập bị lỗi cú pháp (ví dụ thiếu dấu `]`), toàn bộ bộ lọc `ContentSanitizer` bị trả về `null` và không từ nào bị lọc.
- **Root cause**: Gom tất cả các rule pattern thành chuỗi `(?:rule1)|(?:rule2)` mà không kiểm tra tính hợp lệ của từng `rule.pattern` trước khi compile.
- **Fix**: Trong `mapNotNull` của `ContentSanitizer`, validate từng Regex rule cá nhân qua `runCatching { Regex(rule.pattern) }`. Nếu rule đó hợp lệ mới gom vào combined pattern, bỏ qua rule bị lỗi cú pháp một cách êm đẹp.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`

### UI Row Squishing Button Layout trong Compose
- **Vấn đề**: Nút bấm "Thêm" nằm chung hàng ngang `Row` với dòng chữ dài bị ép kích thước ngang, khiến chữ bên trong nút bị rớt dòng thành 3 dòng đứng và bóp méo hình dạng.
- **Fix**: Chuyển nút bấm thành nút icon `IconButton` nằm trực tiếp ở góc phải (`trailingIcon`) của `OutlinedTextField`, vừa gọn gàng 0px thừa vừa không thể bị squish.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsScreen.kt`

---

## How-To

### Cách thêm một quy tắc Lọc từ khóa mới và đồng bộ xuống WebView & TTS
- **Bước thực hiện**:
  1. Người dùng nhập pattern trong `ContentFilterSettingsScreen` (tùy chọn từ khóa thường hoặc Regex).
  2. `ContentFilterSettingsViewModel` kiểm tra `validatePattern` rồi gọi `ReaderPreferencesManager.updateFilterPreferences`.
  3. `ReaderPreferencesManager` persist danh sách quy tắc dưới dạng JSON string vào `SharedPreferences` và phát tín hiệu qua `filterPreferences: StateFlow<ContentFilterPreferences>`.
  4. `ReaderViewModel` và `TtsService` cùng observe flow này để tự động làm sạch giao diện và giọng đọc runtime.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/filter/ContentFilterSettingsViewModel.kt`

---

## Patterns

### Compiled Combined Regex Pattern
- **Chi tiết**: Để tối ưu hiệu năng lọc hàng nghìn từ trên văn bản, gom toàn bộ $N$ quy tắc lọc đang bật thành một biểu thức duy nhất `(?:pattern1|pattern2|...)` và compile một lần duy nhất.
- **Ví dụ code**:
  ```kotlin
  val combinedPattern = activeRules.joinToString("|") { "(?:${it.pattern})" }
  val regex = Regex(combinedPattern, RegexOption.IGNORE_CASE)
  ```
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/filter/ContentSanitizer.kt`
