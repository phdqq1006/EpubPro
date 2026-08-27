# Book converter

Module chuyển PRC, MOBI và AZW3 reflowable không DRM sang EPUB nội bộ để dùng chung reader.

- JNI vendored `libmobi` nằm trong `src/main/cpp/libmobi` (v0.12, commit `85dcfe803fc2a21020ddcf15c3eb66b93d388add`).
- Build Android tắt encryption/DRM (`USE_ENCRYPTION=OFF`); file mã hóa được từ chối.
- `libmobi` được cấp phép LGPL-3.0-or-later. Giữ nguyên file `COPYING` khi phân phối.
- Kotlin chịu trách nhiệm giới hạn 100 MiB, đóng gói ZIP EPUB, kiểm tra container và commit atomically.
