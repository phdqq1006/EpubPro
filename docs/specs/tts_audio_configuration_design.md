# Thiết kế chuẩn hóa cấu hình Audio/TTS

## Tóm tắt hiểu biết

- Chuẩn hóa cấu hình giữa màn hình Profile, trình đọc và `TtsService`.
- Giọng AI offline không có lựa chọn mặc định; `voiceId = null` cho tới khi người dùng chủ động chọn model.
- Chỉ khởi tạo hoặc phát Piper khi model đã được chọn và tải đầy đủ.
- Giọng AI hiện chỉ hỗ trợ tiếng Việt và không hỗ trợ thay đổi cao độ.
- Giọng hệ thống hỗ trợ lọc ngôn ngữ, chọn voice, tốc độ và cao độ theo Android `TextToSpeech`.
- Cấu hình đã lưu của người dùng cũ được giữ nguyên; thay đổi chỉ loại bỏ fallback `ngoc_ngan`.
- Danh mục model phải có một nguồn dùng chung để Profile, Reader và downloader không lệch nhau.

## Giả định và ràng buộc

- Không xóa model đã tải và không thay đổi URL hoặc nội dung ONNX hiện tại.
- Không tự động chuyển cấu hình cũ đang có `voiceId` về rỗng.
- AI chỉ công bố ngôn ngữ `vi` cho tới khi có model ngôn ngữ khác.
- `pitch` của AI được chuẩn hóa về `1.0f` khi lưu; UI không hiển thị slider không có tác dụng.
- Thay đổi không làm tăng hoạt động nền, quyền truy cập hoặc dữ liệu nhạy cảm.
- Danh mục nhỏ, được lưu tĩnh trong mã nguồn và không ảnh hưởng đáng kể đến hiệu năng.

## Phương án được chọn

Tạo `TtsVoiceCatalog` làm nguồn dữ liệu duy nhất cho model AI. Mỗi mục chứa `id`, tên hiển thị, ngôn ngữ, dung lượng hiển thị và tên file ONNX. Profile dùng catalog để hiển thị/tải model; Piper dùng catalog để liệt kê model và lọc theo ngôn ngữ; downloader nhận metadata từ catalog thay vì các khối `when` và danh sách trùng lặp.

`AudioSettingsUiState.selectedVoiceId` chuyển thành nullable. Trạng thái UI ban đầu khớp mặc định domain (`isAiVoice = false`, `voiceId = null`) và sau đó lấy cấu hình đã lưu. Không còn fallback `ngoc_ngan` ở ViewModel hoặc Piper.

## Luồng hành vi

### Giọng AI offline

1. Khi chuyển sang AI, ngôn ngữ được đặt là `vi`, cao độ đặt `1.0f`.
2. Nếu chưa chọn model, UI hiển thị trạng thái “Chưa chọn giọng”; hành động nghe thử/phát bị vô hiệu hóa.
3. Danh sách hiển thị toàn bộ model trong catalog cùng trạng thái đã tải hoặc chưa tải.
4. Chọn model chưa tải chỉ cập nhật lựa chọn để người dùng có thể tải; không được phát cho tới khi tải xong.
5. Piper không khởi tạo nếu `voiceId` rỗng hoặc model chưa tải và trả lỗi có thể hiểu được cho UI/service.

### Giọng hệ thống Android

1. Ngôn ngữ `vi` hoặc `en` được lưu trong `TtsSettings`.
2. Engine áp dụng locale ngay cả khi `voiceId = null`, thay vì luôn giữ `vi-VN`.
3. Profile và Reader cùng liệt kê voice Android theo ngôn ngữ; lựa chọn mặc định hệ thống vẫn hợp lệ.
4. Tốc độ và cao độ tiếp tục được áp dụng bằng `setSpeechRate()` và `setPitch()`.

## Xử lý tương thích và lỗi

- SharedPreferences giữ nguyên key và kiểu dữ liệu, không cần migration.
- Giá trị cũ hợp lệ tiếp tục được sử dụng.
- `voiceId` cũ không còn trong catalog được coi là chưa chọn ở AI, nhưng không làm ứng dụng crash.
- Khi đổi model trong lúc đang phát, service dừng engine cũ trước khi khởi tạo model mới.
- Model thiếu file hoặc tải chưa hoàn tất không được báo là sẵn sàng.

## Kiểm thử

- Unit test catalog: ID duy nhất, ánh xạ ONNX đúng, lọc ngôn ngữ chỉ trả model phù hợp.
- Unit test chuẩn hóa: AI không có voice, AI pitch luôn `1.0f`, native giữ ngôn ngữ và pitch.
- Test ViewModel: cấu hình mặc định không tự chọn `ngoc_ngan`; lựa chọn cũ vẫn được phục hồi.
- Test engine/service: không khởi tạo Piper khi chưa chọn model; native áp dụng locale khi dùng voice mặc định.
- Biên dịch và chạy unit test các module `domain`, `core:tts`, `core:reader`, `core:storage`, `feature:profile`, `feature:reader`.

## Nhật ký quyết định

| Quyết định | Phương án thay thế | Lý do |
|---|---|---|
| Dùng `TtsVoiceCatalog` chung | Vá riêng từng màn hình; tạo repository model hoàn chỉnh | Loại bỏ dữ liệu trùng mà không mở rộng quá mức |
| `voiceId = null` là mặc định AI | Tự chọn `ngoc_ngan`; tự chọn model đầu tiên đã tải | Tôn trọng lựa chọn rõ ràng của người dùng |
| AI chỉ cho chọn `vi` | Vẫn cho chọn English nhưng trả danh sách rỗng | Tránh lưu cấu hình không thể phát |
| Ẩn pitch AI và lưu `1.0f` | Giữ slider vô hiệu hóa; mô phỏng pitch hậu kỳ | Phản ánh đúng khả năng engine, tránh xử lý âm thanh ngoài phạm vi |
| Giữ cấu hình người dùng cũ | Xóa hoặc migrate tất cả về rỗng | Tránh hồi quy và mất lựa chọn hiện có |

