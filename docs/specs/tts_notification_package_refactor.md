# Đóng gói thành phần TTS notification

## Tóm tắt

- Tạo package `com.epubpro.core.reader.tts.notification` để tập trung các thành phần trực tiếp xây dựng và cập nhật notification phát TTS.
- Di chuyển `TtsPlaybackNotificationManager`, `TtsPlaybackNotificationModel`, `TtsNotificationUpdatePolicy` và `TtsNotificationUpdateAction` vào package mới.
- Di chuyển `TtsNotificationUpdatePolicyTest` sang test package tương ứng.
- Giữ `TtsService`, `TtsMediaSessionManager`, `TtsMediaPlaybackContinuityPolicy` và `TtsPlaybackPresentation` trong package `tts` vì chúng phục vụ playback hoặc nhiều bề mặt giao diện, không chỉ notification.
- Refactor chỉ thay đổi package và import, không thay đổi hành vi runtime, Intent action, notification ID, channel ID hoặc visibility.

## Giả định và ràng buộc

- Package mới vẫn nằm trong module `core:reader`, nên modifier `internal` tiếp tục giữ nguyên contract hiện tại.
- Không tách module, không thay đổi kiến trúc MediaSession và không chuyển sang Media3.
- Hiệu năng, bảo mật và vòng đời service không thay đổi vì không có logic thực thi nào được sửa.

## Các phương án đã xem xét

1. **Package notification tối thiểu — được chọn:** chỉ di chuyển manager, model, update policy và test trực tiếp liên quan. Ranh giới rõ, diff nhỏ và không gắn các thành phần playback dùng chung vào notification.
2. Di chuyển cả `TtsMediaSessionManager` và continuity policy: gom được toàn bộ phần tác động đến SystemUI nhưng làm package `notification` sở hữu cả media transport, gây sai trách nhiệm.
3. Giữ nguyên package phẳng: không phát sinh import mới nhưng không đáp ứng mục tiêu tổ chức source code.

## Nhật ký quyết định

- Chọn package `tts.notification` theo convention `tts.bubble` hiện có.
- Chọn phạm vi tối thiểu để tránh refactor ngoài yêu cầu.
- Giữ nguyên tên class và hằng số public/internal để giảm rủi ro regression.

## Kiểm thử

- Chạy unit test module `core:reader`, bao gồm test policy sau khi đổi package.
- Compile module ứng dụng để xác nhận toàn bộ import và symbol reference vẫn hợp lệ.
- Kiểm tra `git diff --check` và search toàn repository để không còn reference tới vị trí package cũ.
