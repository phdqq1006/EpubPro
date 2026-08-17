# Thiết kế Tính năng Điều chỉnh Độ sáng Độc lập cho Màn hình Đọc sách (Reader Independent Brightness & Extra Dim)

## 1. Tóm tắt hiểu biết (Understanding Summary)

* **Mục tiêu**: Xây dựng tính năng điều chỉnh độ sáng màn hình độc lập trong ứng dụng EpubPro, hoạt động chuyên biệt cho màn hình đọc sách (`ReaderScreen`).
* **Mục đích**: Mang lại trải nghiệm đọc sách thoải mái trong nhiều môi trường ánh sáng, không làm thay đổi độ sáng hệ thống của thiết bị, hỗ trợ giảm độ sáng cực sâu để đọc ban đêm trong phòng tối.
* **Người dùng mục tiêu**: Người dùng đọc sách trên EpubPro, đặc biệt người có thói quen đọc sách ban đêm.
* **Phạm vi (Scope)**:
  * Áp dụng riêng cho `ReaderScreen`.
  * Không can thiệp vào các màn hình Thư viện, Kệ sách, Cài đặt chung.
  * Không yêu cầu quyền hệ thống `android.permission.WRITE_SETTINGS`.
* **Cơ chế Hybrid**:
  * Dải bình thường (`20% – 100%`): Điều chỉnh đèn nền phần cứng qua `Activity.window.attributes.screenBrightness`.
  * Dải siêu tối (`0% – 20%`): Giữ đèn nền ở mức tối thiểu phần cứng (`0.01f`) kết hợp lớp phủ màu đen mờ (`ExtraDimOverlay`) tăng dần độ mờ từ `0%` đến tối đa `75%`.
* **Phương thức tương tác**:
  * Vuốt mép trái màn hình (Left Edge Swipe) kèm HUD hiển thị % độ sáng tự ẩn sau 1.2s.
  * Slider trong bảng Cài đặt đọc sách (`ReaderSettingsContent`) áp dụng cơ chế Draft + Commit-on-Release.

---

## 2. Giả định & Yêu cầu phi chức năng (Assumptions & Non-functional Requirements)

* **Hiệu năng & 60fps**: Việc điều chỉnh độ sáng không làm reload WebView, không thay đổi `contentReloadKey()`, không làm xô lệch vị trí đọc sách.
* **Tối ưu I/O**: Cập nhật giao diện và độ sáng tức thời trong lúc kéo, chỉ commit lưu vào `SharedPreferences` khi kết thúc cử chỉ vuốt hoặc nhả tay khỏi Slider.
* **An toàn Vòng đời (Lifecycle Guard)**:
  * Khi rời khỏi `ReaderScreen` hoặc khi ứng dụng chuyển vào background (`onPause`/`onStop`), tự động khôi phục độ sáng về mặc định hệ thống (`BRIGHTNESS_OVERRIDE_NONE = -1.0f`).
  * Khi quay lại (`onResume`), tự động áp dụng lại độ sáng đọc sách đã lưu.
* **Không xung đột cảm ứng**: Cảm ứng mép trái nằm trong dải hẹp 36dp và chỉ bắt cử chỉ kéo dọc (`detectVerticalDragGestures`), các thao tác chạm chuyển trang (Tap zone) và vuốt ngang (Flip) vẫn hoạt động bình thường.

---

## 3. Nhật ký quyết định (Decision Log)

| Quyết định | Các phương án đã xem xét | Lý do lựa chọn phương án này |
| :--- | :--- | :--- |
| **Phạm vi áp dụng** | A. Chỉ ReaderScreen<br>B. Toàn bộ App<br>C. Có tùy chọn cả hai | Tập trung tối ưu trải nghiệm đọc sách, tránh làm xáo trộn độ sáng khi người dùng duyệt thư viện hay các tác vụ khác. |
| **Cơ chế điều chỉnh độ sáng** | A. Thuần đèn nền phần cứng<br>B. Hybrid (Phần cứng + Extra Dim)<br>C. Thuần lớp phủ màu | Mức sáng tối thiểu của phần cứng nhiều máy vẫn quá sáng khi đọc đêm; cơ chế Hybrid cho phép giảm sáng siêu sâu không gây mỏi mắt. |
| **Phương thức tương tác** | A. Cả Vuốt mép trái + Slider<br>B. Chỉ Slider<br>C. Chỉ Vuốt mép trái | Mang lại sự tiện lợi tối đa: vuốt nhanh ngay trong lúc đọc và có thể tinh chỉnh chính xác trong menu cài đặt. |
| **Quản lý trạng thái** | A. Nhớ mức sáng + Có nút Theo hệ thống<br>B. Luôn dùng độ sáng tùy chỉnh của app<br>C. Nhớ riêng theo từng cuốn sách | Trải nghiệm đồng nhất và đơn giản, người đọc chỉ cần chỉnh một lần cho độ sáng phù hợp với mắt mình. |
| **Kiến trúc kỹ thuật** | A. Compose Window Controller + Compose Dim Layer<br>B. CSS Filter via WebView Bridge | Phương án Compose Window Controller độc lập hoàn toàn với WebView DOM, không gây lag WebView, làm tối được toàn diện cả thanh điều hướng và lề. |

---

## 4. Thiết kế Chi tiết (Detailed Architecture & Components)

### 4.1. Domain & Data Layer
* **`ReaderSettings`** (`domain/src/main/java/com/epubpro/domain/model/Models.kt`):
  ```kotlin
  data class ReaderSettings(
      ...
      val brightness: Float = 0.5f // Dải giá trị từ 0.0f đến 1.0f
  )
  ```
* **`ReaderPreferencesManager`** (`core/storage/src/main/java/com/epubpro/core/storage/ReaderPreferencesManager.kt`):
  * Thêm key `KEY_READER_BRIGHTNESS = "reader_brightness"`.
  * Hàm `normalize()` ràng buộc `brightness.coerceIn(0.0f, 1.0f)`.

### 4.2. Pure Function tính toán độ sáng: `calculateBrightnessOutput`
```kotlin
data class BrightnessOutput(
    val hardwareBrightness: Float,
    val extraDimAlpha: Float
)

const val EXTRA_DIM_THRESHOLD = 0.2f
const val MAX_EXTRA_DIM_ALPHA = 0.75f
const val MIN_HARDWARE_BRIGHTNESS = 0.01f

fun calculateBrightnessOutput(brightness: Float): BrightnessOutput {
    val clamped = brightness.coerceIn(0.0f, 1.0f)
    return if (clamped >= EXTRA_DIM_THRESHOLD) {
        val fraction = (clamped - EXTRA_DIM_THRESHOLD) / (1.0f - EXTRA_DIM_THRESHOLD)
        val hw = MIN_HARDWARE_BRIGHTNESS + fraction * (1.0f - MIN_HARDWARE_BRIGHTNESS)
        BrightnessOutput(hardwareBrightness = hw, extraDimAlpha = 0.0f)
    } else {
        val dimFraction = 1.0f - (clamped / EXTRA_DIM_THRESHOLD)
        BrightnessOutput(
            hardwareBrightness = MIN_HARDWARE_BRIGHTNESS,
            extraDimAlpha = dimFraction * MAX_EXTRA_DIM_ALPHA
        )
    }
}
```

### 4.3. UI Layer & Compose Components (`feature/reader`)
1. **`BrightnessWindowEffect`**:
   * Quản lý `window.attributes.screenBrightness`.
   * Gắn `DisposableEffect` để reset về `-1.0f` khi thoát `ReaderScreen`.
   * Lắng nghe Lifecycle (`ON_PAUSE` reset về `-1.0f`, `ON_RESUME` apply lại `hardwareBrightness`).
2. **`BrightnessEdgeSensor`**:
   * Dải Box trong suốt sát mép trái (`width = 36.dp`, `fillMaxHeight()`).
   * Sử dụng `pointerInput` với `detectVerticalDragGestures` để bắt cử chỉ kéo lên/xuống và cập nhật state draft.
   * `onDragEnd`: gọi `onBrightnessChanged(newBrightness)` để commit vào ViewModel.
3. **`BrightnessHud`**:
   * Card nổi giữa màn hình hiển thị icon động (Mặt trời / Mặt trăng) + phần trăm (ví dụ: `☀️ 54%` hoặc `🌙 10% • Siêu tối`).
   * Tự động ẩn bằng `AnimatedVisibility(visible, fadeOut(tween(400)))` sau 1.2s.
4. **`ExtraDimOverlay`**:
   * `Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = extraDimAlpha)))`.
   * Không bắt touch event để không cản trở thao tác đọc sách.
5. **`ReaderSettingsContent`**:
   * Bổ sung Slider điều chỉnh độ sáng từ 0% đến 100% kèm icon minh họa.
   * Áp dụng draft state trong lúc kéo, commit khi `onValueChangeFinished`.

---

## 5. Kế hoạch Kiểm thử (Testing Plan)

1. **Unit Test**:
   * `calculateBrightnessOutputTest`: Kiểm thử tính đúng đắn của hàm ánh xạ tại các mốc `0.0f`, `0.1f`, `0.2f`, `0.6f`, `1.0f`.
   * `ReaderContentReloadKeyTest`: Kiểm thử hồi quy đảm bảo việc thay đổi `brightness` không làm thay đổi `contentReloadKey()`.
   * `ReaderPreferencesMigrationTest`: Đảm bảo khi đọc dữ liệu cũ không có trường `brightness`, giá trị mặc định `0.5f` được khởi tạo chuẩn xác.
2. **Manual Test**:
   * Vuốt dọc mép trái màn hình: Kiểm tra độ mượt, hiển thị HUD và độ sáng thay đổi ngay lập tức.
   * Kéo Slider trong menu Cài đặt đọc sách: Kiểm tra độ sáng cập nhật real-time và lưu lại khi thả tay.
   * Nhấn Home ra màn hình chính: Độ sáng máy quay lại bình thường; Mở lại app: Độ sáng đọc sách được khôi phục.
