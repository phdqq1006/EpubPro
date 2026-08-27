# Local Book Import

> Tổng hợp kiến thức về luồng nạp EPUB, PRC, MOBI và AZW3 vào thư viện dùng chung reader.
> Cập nhật lần cuối: 2026-08-27

---

## Architecture

### Pipeline chuyển đổi local dùng chung EPUB reader
- **Ngày**: 2026-08-27
- **Chi tiết**: URI từ Storage Access Framework được sao lưu vào app-private storage, giới hạn 100 MiB, sau đó `WorkManager` tiếp nhận tác vụ. `LocalBookImportWorker` gọi `MobiEpubConverter`; native libmobi dựng resource, Kotlin đóng gói EPUB tạm, kiểm tra container rồi rename atomically. Chỉ sau khi EPUB hoàn tất mới parse metadata, lưu Room và index FTS qua `EpubEngine`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/EpubStorageManager.kt`, `core/storage/src/main/java/com/epubpro/core/storage/worker/LocalBookImportScheduler.kt`, `core/storage/src/main/java/com/epubpro/core/storage/worker/LocalBookImportWorker.kt`, `core/book-converter/src/main/java/com/epubpro/core/bookconverter/MobiEpubConverter.kt`

### Hợp đồng tên resource giữa libmobi và OPF
- **Ngày**: 2026-08-27
- **Chi tiết**: `content.opf` là resource duy nhất được đổi tên cố định. NCX và mọi resource khác phải giữ mẫu `resource%05zu.%s`, vì `opf.c` tham chiếu cùng `uid` trong `manifest` và `spine`. Đổi NCX thành `toc.ncx` làm entry trong ZIP không khớp manifest, khiến reader mất TOC.
- **Files liên quan**: `core/book-converter/src/main/cpp/mobi_jni.c`, `core/book-converter/src/main/cpp/libmobi/src/opf.c`, `core/book-converter/src/main/java/com/epubpro/core/bookconverter/MobiEpubConverter.kt`

### Phân loại format Palm Database
- **Ngày**: 2026-08-27
- **Chi tiết**: `BOOKMOBI` bao quát MOBI/AZW3 ở mức header, còn `TEXtREAd` là PalmDOC/PRC. `BookSourceFormat` chỉ cần lưu phân loại nguồn rộng; native libmobi vẫn chịu trách nhiệm xác thực cấu trúc, DRM, replica và khả năng chuyển đổi thực tế.
- **Files liên quan**: `core/book-converter/src/main/java/com/epubpro/core/bookconverter/BookFormatSniffer.kt`, `domain/src/main/java/com/epubpro/domain/model/Models.kt`, `core/book-converter/src/main/cpp/mobi_jni.c`

---

## Bugs & Solutions

### PalmDOC bị từ chối ở JNI
- **Ngày**: 2026-08-27
- **Vấn đề**: JNI chỉ gọi `mobi_is_mobipocket`, nên file có `type=TEXt` và `creator=REAd` bị trả về unsupported dù pipeline công bố hỗ trợ PRC.
- **Root cause**: Điều kiện native bỏ qua `mobi_is_textread`; prototype của hàm cũng chưa có trong `mobi.h`.
- **Fix**: Cho phép `mobi_is_mobipocket || mobi_is_textread`, vẫn loại `mobi_is_replica`, và thêm khai báo API vào header.
- **Files liên quan**: `core/book-converter/src/main/cpp/mobi_jni.c`, `core/book-converter/src/main/cpp/libmobi/src/mobi.h`

### Mất Snackbar khi import hoàn thành quá nhanh
- **Ngày**: 2026-08-27
- **Vấn đề**: Flow WorkManager có thể emit `SUCCEEDED` trước `ENQUEUED/RUNNING`, khiến UI không phát Snackbar thành công.
- **Root cause**: `hasActiveLocalImportSession` chỉ được bật trong observer.
- **Fix**: Bật cờ ngay trước `localBookImportScheduler.enqueue`; reset cờ trong các nhánh cancellation và exception để không giữ trạng thái giả.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/LibraryViewModel.kt`

### File không có extension bị từ chối
- **Ngày**: 2026-08-27
- **Vấn đề**: Một số provider trả URI/tên file dạng `.bin` hoặc không có extension dù nội dung là PDB ebook hợp lệ.
- **Root cause**: Sniffer chỉ xét `file.extension`; hàm kiểm tra Palm Database header không được dùng.
- **Fix**: Fallback đọc 8 byte tại offset 60: `BOOKMOBI` → MOBI, `TEXtREAd` → PRC. Native vẫn xác thực sâu hơn.
- **Files liên quan**: `core/book-converter/src/main/java/com/epubpro/core/bookconverter/BookFormatSniffer.kt`, `core/book-converter/src/test/java/com/epubpro/core/bookconverter/BookFormatSnifferTest.kt`

---

## How-To

### Thêm một nguồn ebook vào pipeline
- **Ngày**: 2026-08-27
- **Bước thực hiện**:
  1. Thêm extension và/hoặc header nhận diện trong `BookFormatSniffer`.
  2. Giữ file nguồn trong app-private storage và truyền format qua `WorkManager` input data.
  3. Đảm bảo native parser cho phép đúng loại header và resource output khớp `content.opf`.
  4. Đóng gói EPUB tạm, validate container, rồi commit atomically.
  5. Parse/index bằng cùng `EpubEngine`, bổ sung test sniffer và build JNI.
- **Files liên quan**: `BookFormatSniffer.kt`, `LocalBookImportScheduler.kt`, `LocalBookImportWorker.kt`, `MobiEpubConverter.kt`

### Xác minh thay đổi converter
- **Ngày**: 2026-08-27
- **Bước thực hiện**:
  1. Chạy `./gradlew :core:book-converter:testDebugUnitTest`.
  2. Chạy `./gradlew :core:book-converter:assembleDebug` để build CMake/JNI.
  3. Chạy test/compile `:feature:library` vì ViewModel và Compose permission nằm ở module này.
  4. Chạy `./gradlew :app:assembleDebug` để kiểm tra resource, manifest và packaging toàn app.
- **Files liên quan**: `core/book-converter/src/test/java/com/epubpro/core/bookconverter/BookFormatSnifferTest.kt`

---

## Patterns

### Foreground notification cho tác vụ dài
- **Ngày**: 2026-08-27
- **Chi tiết**: Trước khi mở picker cho upload/import dài, kiểm tra `POST_NOTIFICATIONS` trên Android 13+ và xin quyền bằng Activity Result launcher. Worker vẫn chạy độc lập qua `WorkManager`; nếu người dùng từ chối, hệ điều hành có thể ẩn notification theo policy.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/LibraryScreen.kt`, `feature/library/src/main/java/com/epubpro/feature/library/online/OnlineLibraryScreen.kt`, `core/storage/src/main/java/com/epubpro/core/storage/worker/LocalBookImportWorker.kt`

### Legacy native packaging là lựa chọn tương thích, không phải fix 16 KB
- **Ngày**: 2026-08-27
- **Chi tiết**: `useLegacyPackaging = true` làm native library được giải nén khi cài đặt, đổi lại tăng storage nhưng có thể hữu ích trong giai đoạn tương thích. Tùy chọn này không sửa ELF segment alignment cho cảnh báo page size 16 KB; muốn xử lý cảnh báo đó phải rebuild `.so` bằng toolchain/linker phù hợp.
- **Files liên quan**: `build-logic/src/main/kotlin/epubpro.android.application.gradle.kts`

### Test header bằng fixture tối thiểu
- **Ngày**: 2026-08-27
- **Chi tiết**: Unit test có thể tạo file tạm dài tối thiểu 78 byte, ghi identifier ASCII tại offset 60 và kiểm tra cả `sniff()` lẫn `hasPalmDatabaseHeader()`. Cách này không cần fixture ebook lớn nhưng vẫn bảo vệ fallback nhận diện extensionless.
- **Files liên quan**: `core/book-converter/src/test/java/com/epubpro/core/bookconverter/BookFormatSnifferTest.kt`
