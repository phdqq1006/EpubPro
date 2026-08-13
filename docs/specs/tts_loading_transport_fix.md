# TTS Loading Transport Fix

> Cập nhật: 2026-08-13
> Trạng thái: Đã xác nhận

## Tóm tắt hiểu biết

- `TtsService` vẫn là owner của playback state khi phát hoặc chuyển chapter.
- Notification, MediaSession, bubble và audio widget phải biểu diễn cùng một ý nghĩa play/pause.
- Khi người dùng pause trong lúc tải chapter, việc tải vẫn hoàn tất nhưng chapter mới không tự phát.
- Khi tải xong với pause đang được yêu cầu, playback dừng tại câu đầu chapter mới ở `Paused`.
- Mỗi `AppWidgetProvider` chỉ cập nhật widget của chính nó cho một broadcast state.
- Không thay kiến trúc service, snapshot format hoặc cơ chế chapter coordinator.

## Giả định và yêu cầu phi chức năng

- Chỉ có một phiên TTS tại một thời điểm.
- Chapter loading có thể tiếp tục ở background; pause không cần hủy I/O đang chạy.
- Thay đổi phải giữ tương thích snapshot/widget state hiện có.
- Không tăng tần suất cập nhật notification hoặc widget.
- Ưu tiên minimal change và state mapping có thể unit test.

## Thiết kế được chọn

`TtsService` giữ cờ `playWhenReady` cho `TtsPlayerState.Loading`:

- Bắt đầu chuyển chapter: `playWhenReady = true`.
- Pause trong `Loading`: đặt `playWhenReady = false`, cập nhật mọi projection thành paused nhưng tiếp tục tải.
- Resume trong `Loading`: đặt `playWhenReady = true`, cập nhật mọi projection thành preparing/playing intent.
- Tải xong: gọi `playCurrentChunk()` khi `playWhenReady = true`; nếu false thì tạo `TtsPlayerState.Paused` tại câu đầu chapter mới.

Một playback presentation thuần ánh xạ `TtsPlayerState`, restore state và `playWhenReady` sang:

- `isPlaybackRunning` cho command/notification.
- `TtsWidgetPlaybackStatus` cho widget.
- `TtsBubblePlaybackStatus` cho overlay.

## Phương án đã loại

- Vô hiệu hóa play/pause trong `Loading`: đơn giản nhưng làm mất quyền pause khi chapter tải chậm.
- Hủy chuyển chapter khi pause: có nguy cơ quay lại hoặc phát lại câu cuối chapter cũ.
- Chỉ thêm `Loading` vào `isPlaybackRunning()`: không đủ vì `pauseInternal()` hiện bỏ qua `Loading`.

## Nhật ký quyết định

| Quyết định | Phương án thay thế | Lý do chọn |
|---|---|---|
| Dùng `playWhenReady` | Disable control; hủy transition | Giữ tải liên tục và cho command có hiệu lực thật |
| Dùng presentation thuần | Mapping riêng ở từng UI | Tránh notification/widget/bubble lệch state và dễ unit test |
| Mỗi provider tự update | Provider gọi chéo | Broadcast đã giao cho cả hai; tránh render và Binder IPC trùng |
| Gỡ `@AndroidEntryPoint` không dùng | Giữ annotation | Provider không inject dependency; giảm generated entry-point không cần thiết |

## Kiểm thử

- Unit test presentation cho `Loading` với `playWhenReady=true/false` và restore.
- Unit test các state chuẩn để ngăn regression mapping.
- Build debug toàn app.
- Smoke test thủ công: pause/resume khi tự chuyển chapter và khi chapter tải chậm.
