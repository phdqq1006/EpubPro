# Android AppWidget & RemoteViews Learnings

> Tổng hợp kiến thức về thiết kế Android Home Screen Widget (TTS Audio 4x1 & Full-screen Text Reader 4x4), an toàn RemoteViews, tối ưu hóa Binder IPC và đồng bộ trạng thái trong dự án EpubPro.
> Cập nhật lần cuối: 2026-08-11

---

## Architecture

### Single Source of Truth cho Widget State Persistence
- **Ngày**: 2026-08-11
- **Chi tiết**: Tái sử dụng và mở rộng `TtsWidgetStateStore` làm nơi lưu trữ projection trạng thái duy nhất cho cả Widget Nghe Audio 4x1 (`TtsAudioWidgetProvider`) và Widget Đọc sách Full màn hình 4x4 (`TtsReadingWidgetProvider`). Giúp tiết kiệm bộ nhớ và tự động đồng bộ 100% khi trạng thái đọc thay đổi.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/TtsWidgetStateStore.kt`

### Service-Backed Background Execution Engine
- **Ngày**: 2026-08-11
- **Chi tiết**: `AppWidgetProvider` đóng vai trò là view controller siêu nhẹ, tuyệt đối không thực hiện đọc file EPUB hoặc I/O đĩa nặng trong `onReceive()`. Tất cả các action (Lật trang, lùi trang, phát/tạm dừng) gửi Intent về `TtsService` qua `getForegroundService()`. `TtsService` xử lý ngầm trong `serviceScope` (Coroutines IO thread) và broadcast `ACTION_STATE_CHANGED` về Widget.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `app/src/main/java/com/epubpro/app/widget/TtsReadingWidgetProvider.kt`

### Backward-Compatible Base64 Codec
- **Ngày**: 2026-08-11
- **Chi tiết**: Thiết kế `TtsWidgetStateCodec` mã hóa Base64 các trường văn bản Unicode (Tiêu đề sách, tên chương, nội dung đoạn). Thuật toán `decode()` linh hoạt xử lý dữ liệu từ 5, 6, 9 cho tới 12 trường mà không bị lỗi `IndexOutOfBoundsException`, giúp ứng dụng nâng cấp mượt mà không crash đối với người dùng cũ.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/TtsWidgetStateStore.kt`

---

## Bugs & Solutions

### Lỗi `InflateException` do dùng thẻ `<View>` trong RemoteViews Layout
- **Ngày**: 2026-08-11
- **Vấn đề**: Thêm Widget ra Launcher báo lỗi "Không thể thêm tiện ích" / "Không thể tải tiện ích".
- **Root cause**: Trong XML layout `tts_reading_widget.xml` có sử dụng các thẻ `<View>` tự do cho đường kẻ 1dp và vùng chạm. Trình inflate RemoteViews của Android Framework cấm tuyệt đối thẻ `<View>` tự do.
- **Fix**: Thay thế các thẻ `<View>` kẻ lề 1dp bằng `<ImageView android:src="@color/..." />` và vùng chạm bằng `<FrameLayout android:background="@android:color/transparent">`.
- **Files liên quan**: `app/src/main/res/layout/tts_reading_widget.xml`

### Lỗi "Không thể thêm tiện ích" do thiếu `initialLayout`
- **Ngày**: 2026-08-11
- **Vấn đề**: Launcher hiển thị "Không thể thêm tiện ích" khi người dùng kéo widget ra màn hình chính.
- **Root cause**: Tệp XML metadata `tts_reading_widget_info.xml` bị thiếu thuộc tính bắt buộc `android:initialLayout`. Launcher không tìm thấy giao diện ban đầu để vẽ preview.
- **Fix**: Bổ sung `android:initialLayout="@layout/tts_reading_widget"` vào tệp metadata provider info.
- **Files liên quan**: `app/src/main/res/xml/tts_reading_widget_info.xml`

### Tràn giới hạn Binder IPC (`TransactionTooLargeException`)
- **Ngày**: 2026-08-11
- **Vấn đề**: Khi nạp ảnh bìa dung lượng lớn hoặc đoạn văn dài hàng chục KB, Widget bị nổ lỗi trên Launcher.
- **Root cause**: Quá tải đường truyền Binder IPC (`Parcel` limit 512KB - 1MB) khi đẩy RemoteViews dữ liệu lớn sang process của System Launcher.
- **Fix**: Downscale ảnh bìa bằng `inJustDecodeBounds` về tối đa `96px` / `144px` trong `decodeScaledCover()`, và giới hạn độ dài chuỗi text `paragraphText.take(800)` trước khi encode Base64 và đẩy sang RemoteViews.
- **Files liên quan**: `app/src/main/java/com/epubpro/app/widget/TtsAudioWidgetProvider.kt`, `app/src/main/java/com/epubpro/app/widget/TtsReadingWidgetProvider.kt`, `core/storage/src/main/java/com/epubpro/core/storage/TtsWidgetStateStore.kt`

### Lỗi `@AndroidEntryPoint` trên `AppWidgetProvider`
- **Ngày**: 2026-08-11
- **Vấn đề**: Widget ngốn bộ nhớ hoặc báo lỗi khi Launcher gọi `onReceive()`.
- **Root cause**: Gắn `@AndroidEntryPoint` lên `AppWidgetProvider` khiến Hilt cố gắng inject dependency bằng reflection ngay cả khi Hilt Application Graph chưa sẵn sàng.
- **Fix**: Bỏ `@AndroidEntryPoint` khỏi `AppWidgetProvider`, khởi tạo `TtsWidgetStateStore(context)` trực tiếp thông qua Application Context.
- **Files liên quan**: `app/src/main/java/com/epubpro/app/widget/TtsReadingWidgetProvider.kt`

### Phần trăm % đọc và thời gian bị đóng băng khi phát TTS
- **Ngày**: 2026-08-11
- **Vấn đề**: Widget chỉ nhận phần trăm 1 lần duy nhất khi bấm Play, sau đó bị đứng yên.
- **Root cause**: Vòng lặp `startNotificationProgressUpdates` trong `TtsService` chỉ update Notification và MediaSession mà không gọi `publishWidgetState()`.
- **Fix**: Thêm bộ đếm 5 giây (`currentTimelinePositionMs / 5000L`) trong loop để trigger `publishWidgetState()` định kỳ khi đang phát (`Playing`).
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

---

## How-To

### Cách tạo một Android AppWidget an toàn cho RemoteViews
- **Ngày**: 2026-08-11
- **Bước thực hiện**:
  1. Khai báo `<receiver>` trong `AndroidManifest.xml` với `exported="true"`, intent filter `APPWIDGET_UPDATE` và `ACTION_STATE_CHANGED`, chỉ định metadata `@xml/...`.
  2. Tạo XML metadata trong `res/xml/` có đầy đủ `minWidth`, `minHeight`, `initialLayout`, `previewLayout`, `resizeMode`.
  3. Xây dựng Layout XML chỉ dùng các class được RemoteViews hỗ trợ (`TextView`, `ImageView`, `ProgressBar`, `ImageButton`, `FrameLayout`, `LinearLayout`). KHÔNG dùng `<View>`.
  4. Đảm bảo dữ liệu đẩy sang RemoteViews nhỏ gọn: downscale bitmap cover (max 144px) và cắt ngắn văn bản (`.take(800)`).
  5. Liên kết sự kiện nút bấm bằng `PendingIntent.getForegroundService()` cho background service hoặc `PendingIntent.getActivity()` để mở App.

---

## Patterns

### Vùng Chạm Nổi (Touch Zones Overlay) trên RemoteViews
- **Ngày**: 2026-08-11
- **Chi tiết**: Xây dựng trải nghiệm "Chạm lật trang" tự nhiên trên Widget bằng cách lồng một `LinearLayout` chứa 2 `FrameLayout` trong suốt (`touch_zone_left` 35%, `touch_zone_right` 65%) đè lên `TextView` nội dung trong một `FrameLayout` gốc. Gán `setOnClickPendingIntent` riêng cho từng `FrameLayout`.
- **Files liên quan**: `app/src/main/res/layout/tts_reading_widget.xml`, `app/src/main/java/com/epubpro/app/widget/TtsReadingWidgetProvider.kt`

### Gộp khối đoạn văn nhiều câu (Multi-sentence Chunking)
- **Ngày**: 2026-08-11
- **Chi tiết**: Thay vì chỉ hiển thị 1 câu thoại ngắn 5-10 từ của bộ đọc TTS giọng nói, sử dụng hàm `buildReadingWidgetText()` để nối các câu thoại/đoạn văn liền kề xung quanh vị trí hiện tại thành một khối văn bản truyện đọc hoàn chỉnh (nhiều dòng, tối đa 800 ký tự) hiển thị vừa vặn trên Widget.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`
