# core:reader-renderer Module Instructions

## Trách nhiệm
Module chịu trách nhiệm làm sạch mã HTML (`EpubHtmlSanitizer`), cầu nối giao tiếp JavaScript (`ReaderJsBridge`) và sinh CSS cho các chế độ hiển thị (Multi-column horizontal pagination vs Vertical scroll) qua `CssInjector`.

## Quy tắc bắt buộc
- **Doc comment**: Bắt buộc KDoc bằng tiếng Việt cho mọi hàm và class mới.
- **Security**: Luôn làm sạch HTML trước khi nạp vào WebView để triệt tiêu mã độc XSS và các active tags nguy hiểm.
- **Independence**: Module này độc lập hoàn toàn với `core:epub` và `core:playback`.

## Lệnh kiểm thử
```powershell
.\gradlew.bat :core:reader-renderer:testDebugUnitTest
```
