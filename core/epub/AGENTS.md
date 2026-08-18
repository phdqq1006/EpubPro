# core:epub Module Instructions

## Trách nhiệm
Module chịu trách nhiệm thuần túy về việc đọc, giải nén (Zip/Stream parsing), kiểm soát giới hạn an toàn bộ nhớ (EpubReadLimits) và chuẩn hóa HTML (HtmlNormalizer) từ tệp EPUB.

## Quy tắc bắt buộc
- **Doc comment**: Bắt buộc KDoc bằng tiếng Việt cho mọi hàm và class mới.
- **Security & Limits**: Luôn áp dụng `readBoundedText()` và `EpubReadLimits` khi giải nén các file trong EPUB để ngăn chặn lỗ hổng Zip Bomb.
- **Natural Sorting**: Luôn duy trì thuật toán Natural Numeric Sort khi fallback không có OPF spine.

## Lệnh kiểm thử
```powershell
.\gradlew.bat :core:epub:testDebugUnitTest
```
