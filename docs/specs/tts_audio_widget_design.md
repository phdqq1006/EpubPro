# Thiết kế widget điều khiển TTS 4x1

## Tóm tắt hiểu biết

- Xây dựng một Android home-screen widget kích thước mặc định 4x1.
- Widget là giao diện điều khiển độc lập, không phụ thuộc công tắc hoặc quyền overlay của bubble.
- Bubble, notification, full player và widget cùng gửi lệnh tới một `TtsService` duy nhất.
- Widget hiển thị tên sách, trạng thái playback, tiến trình và các nút media.
- Chạm vùng nền/tiêu đề widget không thực hiện hành động; chỉ các nút có hành động.
- Khi process app không còn chạy, Play trên widget phải khởi động lại service, restore snapshot và phát tiếp.
- Widget không tự xin quyền, không tạo overlay và không tạo service thứ hai.

## Giả định

- Dùng `RemoteViews` + `AppWidgetProvider`, tương thích `minSdk 26`.
- Nút chính gồm Play/Pause; Previous/Next được hiển thị khi kích thước launcher cho phép.
- Không có snapshot thì Play không tự chọn sách; widget mở Library trong app để người dùng chọn nội dung.
- Force-stop trong Android Settings có thể chặn PendingIntent cho tới khi người dùng mở app lại.
- Widget không lưu nội dung sách; chỉ lưu projection tối thiểu để render trạng thái.

## Nhật ký quyết định

| Quyết định | Phương án thay thế | Lý do |
|---|---|---|
| `RemoteViews` + `AppWidgetProvider` | Jetpack Glance | Ít dependency, phù hợp `minSdk 26`, dễ dùng với PendingIntent |
| Widget độc lập với bubble | Widget chỉ mở bubble expanded | Widget vẫn hoạt động khi bubble tắt hoặc mất quyền overlay |
| Chạm nền không làm gì | Mở app hoặc mở bubble | Tránh hành động ngoài ý muốn; nút media là affordance rõ ràng |
| Dùng `TtsService` làm state owner | Service widget riêng | Tránh race giữa playback, notification, bubble và widget |
| `PendingIntent.getForegroundService()` cho Play | `getService()` | Hỗ trợ khởi động playback an toàn khi app process đã bị kill |

## Kiến trúc

### Thành phần

- `TtsAudioWidgetProvider`: nhận lifecycle/update callback và render `RemoteViews`.
- `tts_audio_widget.xml`: layout 4x1, màu lấy theo design system.
- `tts_audio_widget_info.xml`: metadata kích thước và resize.
- `TtsWidgetStateStore`: lưu projection tối thiểu cho lần render sau process recreation.
- `TtsService`: nhận action widget và cập nhật state/notification/bubble/widget đồng bộ.

### Action

- `ACTION_WIDGET_PLAY_PAUSE`
- `ACTION_WIDGET_PREVIOUS`
- `ACTION_WIDGET_NEXT`

Mỗi action là explicit PendingIntent tới `TtsService`, immutable và có request code riêng.

### Luồng Play khi process đã chết

```text
Widget Play
  -> PendingIntent.getForegroundService()
  -> TtsService.onCreate()
  -> startForeground(notification Preparing) ngay trong onStartCommand
  -> đọc TtsPlaybackSnapshotStore bất đồng bộ
  -> restore sách/chương/câu
  -> phát audio
  -> cập nhật widget + notification + bubble nếu bubble đang bật
```

Nếu không có snapshot, Play dùng PendingIntent.getActivity() mở Library; không khởi động service.

Widget không dùng ticker tiến trình 1 giây. AppWidgetManager.updateAppWidget() chỉ được gọi khi đổi trạng thái playback, đổi chunk, đổi chương/sách hoặc snapshot. Ticker 1 giây chỉ phục vụ notification và bubble.

### Đồng bộ trạng thái

`TtsService` cập nhật `TtsWidgetStateStore` cùng nơi đang gọi `publishBubbleModel()` và notification update. Widget đọc store khi `onUpdate()` và render trạng thái mặc định an toàn nếu store trống/corrupt.

## Kiểm thử

- Provider render đúng với `Idle`, `Preparing`, `Playing`, `Paused`, `Error`, `Completed`.
- Play khi service/process đang chạy.
- Play khi process bị kill nhưng snapshot hợp lệ.
- Play khi không có snapshot mở Library mà không khởi động service.
- Previous/Next khi Idle và khi đang Playing.
- Bubble bật/tắt không ảnh hưởng khả năng điều khiển widget.
- Notification, widget và bubble nhận cùng progress/state.
- Force-stop được xác nhận là giới hạn nền tảng, không retry vô hạn.

