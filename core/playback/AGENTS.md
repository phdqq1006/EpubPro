# core:playback Module Instructions

## Trách nhiệm
Module chịu trách nhiệm toàn bộ hệ thống phát âm thanh TTS (Text-To-Speech), bao gồm Android Native TTS, Piper AI TTS Wrapper, `TtsService`, Floating Bubble Overlay, Notification Media Controls và Audio Focus.

## Quy tắc bắt buộc
- **Doc comment**: Bắt buộc KDoc bằng tiếng Việt cho mọi hàm và class mới.
- **Resource strings**: Mọi chuỗi hiển thị phải được đặt tại `core/designsystem/src/main/res/values/strings.xml`.
- **Atomic package & visibility**: Duy trì nguyên vẹn các lớp `internal` kết nối với `TtsService`.
- **Service Identity**: Giữ nguyên FQCN `com.epubpro.core.reader.tts.TtsService` để đảm bảo tính toàn vẹn của Android Manifest.

## Lệnh kiểm thử
```powershell
.\gradlew.bat :core:playback:testDebugUnitTest
```
