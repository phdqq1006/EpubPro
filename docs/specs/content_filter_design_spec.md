# Text Filter & Replacement Feature Design Specification

> **Ngày tạo**: 2026-08-27
> **Trạng thái**: Approved  
> **Tác giả**: Antigravity Assistant & User  

---

## 1. Tóm tắt hiểu biết (Understanding Summary)

- **Mục tiêu**: Cho phép người dùng tìm kiếm và thay thế một từ, cụm từ hoặc câu khi gặp trong sách bằng một văn bản mới do người dùng chỉ định (hoặc xóa bỏ nếu chuỗi mới để trống). Áp dụng đồng bộ cho cả giao diện đọc (WebView DOM) và giọng đọc AI (TTS Audio Engine).
- **Phạm vi (In-Scope)**:
  - **Selection to Replace**: Khi bôi đen 1 từ hoặc 1 câu trong sách, menu chọn văn bản có action "Thay thế" mở `ReplaceTextBottomSheet` nhập nhanh.
  - **2-Layer Unified Engine**:
    - Reader WebView: Sử dụng JavaScript `TreeWalker` thay thế trực tiếp trên Text Node.
    - TTS Engine: Sử dụng `ContentSanitizer` xử lý chuỗi văn bản thay thế từ/câu trước khi gửi sang Audio Engine.
  - **Màn hình Cài đặt**: Quản lý danh sách quy tắc (xem, thêm, sửa, xóa, bật/tắt) với cả 2 trường `Từ gốc` (Pattern) và `Thay bằng` (Replacement).
- **Phi phạm vi (Non-goals)**:
  - Không chỉnh sửa file EPUB `.epub` gốc trên đĩa.
  - Không đồng bộ cloud (lưu trữ 100% offline trên thiết bị).

---

## 2. Giả định & Ràng buộc (Assumptions & Constraints)

- **Tương thích ngược (Backward Compatibility)**: Toàn bộ rule cũ không có `replacement` sẽ tự động mặc định là `""` (xóa từ) mà không gây lỗi runtime hay mất dữ liệu.
- **Khớp mặc định**: Khớp không phân biệt hoa/thường (Case-insensitive) cho cả từ đơn và cụm từ/câu, kèm tùy chọn nâng cao bật Regex.
- **Hiệu năng**: Quá trình duyệt TreeWalker và Regex Kotlin hoàn thành dưới 16ms/chương, không làm giật khung hình lật trang hay lag âm thanh TTS.
- **An toàn**: Escape chuỗi an toàn khi truyền vào JavaScript (`${'$'}`) và khi biên dịch Regex để chống crash.

---

## 3. Nhật ký quyết định (Decision Log)

| Điều đã quyết định | Các phương án thay thế đã xem xét | Tại sao chọn phương án này |
| :--- | :--- | :--- |
| **Nâng cấp trực tiếp kiến trúc Lọc 2 tầng hiện có (Unified Replace Engine)** | 1. Tách thành hệ thống "Từ điển thay thế" độc lập<br>2. Chỉ thay thế bằng JavaScript trên WebView | Tận dụng 100% hạ tầng DataStore và StateFlow hiện có, đồng bộ tuyệt đối giữa hiển thị mắt đọc và tai nghe TTS, tránh trùng lặp code và tuân thủ YAGNI. |
| **User Flow bôi đen: Mở BottomSheet nhập nhanh ngay tại màn đọc** | 1. Chỉ có ô nhập từ mới<br>2. Chuyển hướng sang màn hình Settings | Tiện lợi tối đa cho người đọc, cho phép xem lại từ gốc, nhập từ mới hoặc xóa, bật/tắt regex và lưu áp dụng tức thì mà không rời trang sách. |
| **Giao diện BottomSheet khớp chính xác mockup người dùng cung cấp** | Giao diện Dialog thông thường | Bo góc mềm mại, typography đồng bộ với theme đọc sách, gồm trường "Từ gốc", "Thay thế bằng", Switch "Regex" và nút Primary Action bo tròn. |
| **Đổi tên Action menu bôi đen thành "Thay thế"** | 1. Giữ tên "Lọc từ"<br>2. Tách thành 2 nút "Lọc từ" & "Thay thế" | Tên "Thay thế" bao hàm cả hành động thay từ mới và xóa từ (khi để trống), giúp thanh Selection Toolbar gọn gàng, không bị tràn icon. |

---

## 4. Thiết kế kỹ thuật chi tiết (Technical Design)

### 4.1 Data Models (`domain` module)
```kotlin
data class ContentFilterRule(
    val id: String = UUID.randomUUID().toString(),
    val pattern: String,
    val replacement: String = "", // Chuỗi mới thay thế. Nếu rỗng ("") = xóa bỏ
    val isRegex: Boolean = false,
    val isEnabled: Boolean = true
)

data class ContentFilterPreferences(
    val isFilterEnabled: Boolean = false,
    val rules: List<ContentFilterRule> = emptyList()
)
```

### 4.2 Storage & StateFlow (`core/storage` module)
- `ReaderPreferencesManager` quản lý `filterPreferences: StateFlow<ContentFilterPreferences>`.
- Xử lý JSON serialization/deserialization an toàn với trường `replacement`.
- Hàm tiện ích: `addOrUpdateFilterRule(pattern: String, replacement: String, isRegex: Boolean)` tự động kích hoạt `isFilterEnabled = true` khi thêm rule.

### 4.3 Tầng WebView DOM (`core/reader-renderer` module)
- File `CssInjector.kt`:
  - Serialize `rules` thành JSON array chứa `pattern`, `replacement`, `isRegex`, `isEnabled`.
  - Trong `window.epubproApplyContentFilter()`:
    ```javascript
    var activeRules = [];
    // Chuẩn bị danh sách regex và replacement
    for (var i = 0; i < rules.length; i++) {
        var r = rules[i];
        if (!r.isEnabled || !r.pattern) continue;
        var regex = r.isRegex
            ? new RegExp(r.pattern, 'gi')
            : new RegExp(r.pattern.replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\${'$'}&'), 'gi');
        activeRules.push({ regex: regex, replacement: r.replacement || '' });
    }
    // Duyệt và thay thế trên Text Nodes
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    var node;
    var nodesToProcess = [];
    while (node = walker.nextNode()) {
        nodesToProcess.push(node);
    }
    for (var j = 0; j < nodesToProcess.length; j++) {
        var n = nodesToProcess[j];
        for (var k = 0; k < activeRules.length; k++) {
            activeRules[k].regex.lastIndex = 0;
            n.nodeValue = n.nodeValue.replace(activeRules[k].regex, function() {
                return activeRules[k].replacement;
            });
        }
    }
    document.body.normalize();
    ```

### 4.4 Tầng TTS Audio Engine (`core/playback` module)
- File `ContentSanitizer.kt`:
  - Hàm `sanitize(text: String, preferences: ContentFilterPreferences): String`:
    - Lặp qua các rule đang bật:
      - Nếu Regex: `result = result.replace(Regex(rule.pattern, RegexOption.IGNORE_CASE)) { rule.replacement }`
      - Nếu Literal: `result = result.replace(Regex(Regex.escape(rule.pattern), RegexOption.IGNORE_CASE)) { rule.replacement }`
    - Chuẩn hóa khoảng trắng thừa: `result.replace(Regex("\\s+"), " ").trim()`.

### 4.5 Tầng Giao diện UI (`feature/reader` & `feature/profile`)
- **`ReaderSelectionWebView.kt`**: Bọc `ActionMode.Callback2`, gắn menu item `R.string.reader_action_replace` ("Thay thế").
- **`ReplaceTextBottomSheet.kt`**: Composable BottomSheet với giao diện chuẩn mockup (Từ gốc, Thay thế bằng, Switch Regex, Nút Lưu).
- **`ContentFilterSettingsScreen.kt`**: Cập nhật danh sách hiển thị dạng `"Từ gốc" -> "Từ mới"` kèm tag `[Regex]`, hỗ trợ sửa/xóa/bật/tắt.

---

## 5. Kế hoạch kiểm thử (Verification Plan)

1. **Unit Tests**:
   - `ContentSanitizerTest`: Test thay thế từ đơn, cụm từ, câu, thay bằng rỗng, regex, case-insensitive.
   - `CssInjectorTest`: Test single JSON parse và chuỗi JS injection chứa trường replacement.
   - `ReaderPreferencesManagerTest`: Test migration JSON cũ và thêm/sửa rule.
2. **Manual / UI Verification**:
   - Mở sách EPUB, bôi đen từ/câu, chọn "Thay thế", nhập từ mới -> Kiểm tra màn hình đổi sang từ mới tức thì.
   - Bật TTS đọc chương truyện -> Kiểm tra giọng đọc phát âm theo từ mới thay thế.
   - Vào Settings -> Kiểm tra danh sách hiển thị đúng quy tắc vừa thêm.
