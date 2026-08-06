# Content Filter Feature Design Specification

> **Ngày tạo**: 2026-08-06  
> **Trạng thái**: Approved  
> **Tác giả**: Antigravity Assistant & User  

---

## 1. Tóm tắt hiểu biết (Understanding Summary)

- **Mục tiêu**: Xóa/ẩn hoàn toàn các từ khóa, cụm từ hoặc mẫu văn bản (Regex) được cấu hình khỏi chương truyện đang đọc (giao diện đọc WebView) và giọng đọc AI (TTS Engine).
- **Phạm vi**:
  - Reader WebView: Lọc trực tiếp trên DOM Text Node (văn bản tự động nối lại liền mạch).
  - TTS Engine: Lọc chuỗi văn bản sạch trước khi phát âm thanh.
  - Cài đặt tập trung: Quản lý danh sách từ khóa và bật/tắt tính năng tại Settings.
- **Phi phạm vi (Non-goals)**: Không sửa đổi file EPUB ZIP gốc trên đĩa.

---

## 2. Giả định & Ràng buộc (Assumptions & Constraints)

- **Hiệu năng**: Lọc bằng Cached Combined Regex và JS TreeWalker với thời gian xử lý < 16ms/chương.
- **Giới hạn an toàn**: Khuyến nghị số lượng từ khóa/pattern < 500 quy tắc.
- **Lưu trữ**: Dữ liệu cài đặt được lưu trữ bằng `ReaderPreferencesManager` / DataStore.

---

## 3. Nhật ký quyết định (Decision Log)

| Điều đã quyết định | Phương án thay thế đã xem xét | Lý do chọn |
| :--- | :--- | :--- |
| **Cơ chế Dual-Layer Filtering (Native Kotlin + JS TreeWalker)** | 1. Parse HTML bằng Kotlin JSoup trước khi nạp<br>2. Bọc thẻ `<span style="display:none">` | - Tránh tốn RAM/CPU parse HTML lớn trên Kotlin.<br>- An toàn 100% cho cấu trúc HTML & CSS Column pagination.<br>- JS TreeWalker chỉ thao tác trên text node, cực nhẹ. |
| **Hành vi xử lý từ trùng khớp: Ẩn/xóa hoàn toàn & Nối text liền mạch** | 1. Thay bằng `***`<br>2. Làm mờ (Blur) | Đáp ứng đúng nhu cầu của người dùng là loại bỏ hoàn toàn từ bị lọc khỏi mắt và tai người đọc. |
| **Hỗ trợ Regex & Từ thường** | Chỉ hỗ trợ từ đơn giản | Cho phép người dùng lọc linh hoạt các biến thể ký tự, đoạn quảng cáo, watermark phức tạp. |
| **Xử lý đoạn văn rỗng sau lọc** | Để nguyên khung rỗng | Tự động ẩn dòng rỗng trong WebView và tự động skip đoạn rỗng trong TTS để giọng đọc không bị ngắt quãng. |

---

## 4. Thiết kế chi tiết (Final Technical Design)

### 4.1 Data Models (`core/model`)
```kotlin
data class ContentFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true
)

data class ContentFilterPreferences(
    val isFilterEnabled: Boolean = false,
    val rules: List<ContentFilterRule> = emptyList()
)
```

### 4.2 Storage Manager (`core/storage`)
- `ReaderPreferencesManager` quản lý `filterSettingsFlow: StateFlow<ContentFilterPreferences>`.
- Cung cấp các hàm atomic: `addRule()`, `removeRule()`, `toggleRule()`, `toggleFilter()`.

### 4.3 Native Sanitizer (`core/reader` / TTS)
- Class `ContentSanitizer`:
  - Gom các rule active thành Compiled `Regex`.
  - `sanitize(text: String): String` -> thay thế match bằng `""` và chuẩn hóa khoảng trắng thừa `\s+` -> `" "`.

### 4.4 Reader WebView Injector (`core/reader/style/CssInjector.kt`)
- Tiêm script JavaScript `epubproApplyContentFilter(rulesJson)`:
  - Sử dụng `document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT)`.
  - Thay thế text trùng khớp và gọi `document.body.normalize()`.

---

## 5. Kế hoạch kiểm thử (Verification & Test Plan)

1. **Unit Test `ContentSanitizerTest`**:
   - Kiểm tra lọc từ thường (case-insensitive).
   - Kiểm tra lọc bằng Regex.
   - Kiểm tra chuẩn hóa khoảng trắng sau khi xóa từ.
   - Kiểm tra xử lý chuỗi rỗng/rule lỗi cú pháp.
2. **Reader WebView UI Test**:
   - Kiểm tra trang truyện sau khi load không còn chứa các từ bị lọc.
   - Kiểm tra lật trang mượt mà với Dual Overlay Page Turn Engine.
3. **TTS Integration Test**:
   - Kiểm tra TTS skip qua các từ bị lọc và bỏ qua các đoạn văn rỗng.
