# Localization & String Resources

> Tổng hợp kiến thức về refactor hardcoded strings và quản lý tài nguyên chuỗi đa ngôn ngữ trong dự án EpubPro.
> Cập nhật lần cuối: 2026-08-03

---

## Architecture

### Centralized String Resources trong Multi-Module Android
- **Ngày**: 2026-08-03
- **Chi tiết**: Đặt tất cả các chuỗi UI dùng chung tại `core/designsystem/src/main/res/values/strings.xml`. Vì tất cả các feature module (`feature:library`, `feature:reader`, `feature:bookmark`, `feature:profile`, `feature:search`, `app`) đều phụ thuộc `:core:designsystem`, việc này giúp dùng chung tài nguyên chuỗi mà không bị lặp file `strings.xml`.
- **Navigation Enum**: `TopLevelDestination` lưu tài nguyên dạng `@StringRes val iconTextId: Int` và `@StringRes val titleTextId: Int` thay vì `String` hardcode.
- **Files liên quan**: `core/designsystem/src/main/res/values/strings.xml`, `app/src/main/java/com/epubpro/app/navigation/TopLevelDestination.kt`

---

## Bugs & Solutions

### Smart Cast Fail cho Property từ Module khác
- **Ngày**: 2026-08-03
- **Vấn đề**: `Smart cast to 'String' is impossible, because 'hl.note' is a public API property declared in different module`.
- **Root cause**: Trình biên dịch Kotlin không thể guarantee property nullable của object thuộc module khác (`domain`) không bị thay đổi giữa thời điểm check non-null và thời điểm sử dụng.
- **Fix**: Gán property vào biến cục bộ `val note = hl.note` trước khi check `!note.isNullOrBlank()` và truyền vào `stringResource(R.string.xxx, note)`.
- **Files liên quan**: `feature/bookmark/src/main/java/com/epubpro/feature/bookmark/BookmarkScreen.kt`

### Xung đột R import giữa Feature Module và Core Designsystem
- **Ngày**: 2026-08-03
- **Vấn đề**: `Unresolved reference: R` khi Activity/View trong feature module cần dùng cả Layout ID của feature module và String Resource của core designsystem.
- **Root cause**: Import `com.epubpro.core.designsystem.R` đè lên `com.epubpro.feature.reader.R`.
- **Fix**: Import `com.epubpro.core.designsystem.R` cho strings, và gọi FQCN `com.epubpro.feature.reader.R.id.xxx` / `com.epubpro.feature.reader.R.layout.xxx` cho layout và view IDs.
- **Files liên quan**: `feature/reader/src/main/java/com/epubpro/feature/reader/readium/ReadiumReaderActivity.kt`

---

## How-To

### Quy trình Refactor Hardcoded Strings sang Resource Files
- **Ngày**: 2026-08-03
- **Bước thực hiện**:
  1. Khai báo `<string name="key_name">Gía trị</string>` trong `core/designsystem/src/main/res/values/strings.xml`.
  2. Import `androidx.compose.ui.res.stringResource` và `com.epubpro.core.designsystem.R`.
  3. Với Jetpack Compose: Thay `"Text"` bằng `stringResource(R.string.key_name)`. Với string có tham số: `stringResource(R.string.key_format, param1)`.
  4. Với Non-Compose (Activity/Service/Manager): Dùng `context.getString(R.string.key_name)`.
- **Files liên quan**: `core/designsystem/src/main/res/values/strings.xml`

---

## Patterns

### Local Smart Cast Pattern
- **Ngày**: 2026-08-03
- **Chi tiết**: Pattern an toàn khi làm việc với nullable model properties từ module khác trong Compose UI.
- **Ví dụ code**:
  ```kotlin
  val note = hl.note
  if (!note.isNullOrBlank()) {
      Text(stringResource(R.string.highlight_note_format, note))
  }
  ```
- **Files liên quan**: `feature/bookmark/src/main/java/com/epubpro/feature/bookmark/BookmarkScreen.kt`
