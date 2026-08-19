# Book Bible (Tiến trình nhân vật & Dòng thời gian)

> Tổng hợp kiến thức về hệ thống Chapter-Aware Character Profile Book Bible (Tiến trình hồ sơ nhân vật theo chương không spoiler) trong dự án.
> Cập nhật lần cuối: 2026-08-19

---

## Architecture

### Hệ thống Snapshot chống Spoiler theo mốc chương (Chapter-Aware Projection)
- **Ngày**: 2026-08-19
- **Chi tiết**: Phân hệ Book Bible không hiển thị toàn bộ hồ sơ nhân vật tới chương cuối mà sử dụng mô hình snapshot theo mốc chương (`local_chapter` / `canonical_chapter`). Server tính toán projection dựa trên các `CharacterEvent` phát sinh từ chương 1 đến chương hiện tại để ngăn chặn spoiler hoàn toàn cho độc giả.
- **Files liên quan**: `domain/src/main/java/com/epubpro/domain/model/BookBibleModels.kt`, `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Luồng Ingestion Bất đồng bộ với WorkManager & Chống trùng lặp (Idempotency)
- **Ngày**: 2026-08-19
- **Chi tiết**: Do quá trình LLM trích xuất diễn biến chương diễn ra bất đồng bộ, client gửi nội dung chương qua `BookBibleWorker` với header `X-Idempotency-Key` (SHA-256 nội dung chương). Server trả về HTTP 202 Accepted với `submission_id`. Client lưu trạng thái vào Room SQLite và lắng nghe/polling tiến độ cho đến khi `completed`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleWorker.kt`, `core/storage/src/main/java/com/epubpro/core/storage/network/BookBibleApiService.kt`

### Cache đa tầng & Thuật toán dọn dẹp LRU
- **Ngày**: 2026-08-19
- **Chi tiết**: Tích hợp cơ chế Cache 2 tầng: Room SQLite lưu snapshot & timeline metadata, Disk Cache lưu payload JSON. Sử dụng cơ chế giới hạn dung lượng mềm (50 MiB) kèm thuật toán xóa bản ghi ít truy cập nhất (LRU pruning) dựa trên `lastAccessedAt`.
- **Files liên quan**: `core/database/src/main/java/com/epubpro/core/database/dao/BookBibleDao.kt`, `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Thiết kế giao diện phân tầng dọc (Vertical Hierarchy Layout)
- **Ngày**: 2026-08-19
- **Chi tiết**: Trường `role` từ backend là câu mô tả dài (không phải nhãn ngắn), nếu đặt ngang dạng badge sẽ gây vỡ layout và tràn chữ. Sử dụng layout dọc 3 dòng (Tên + Tên gốc $\to$ Vai trò/Mô tả $\to$ Cảnh giới/Thế lực) trên thẻ danh sách full-width kết hợp Hero Card nổi bật cho nhân vật chính.
- **Files liên quan**: `feature/bookbible/src/main/java/com/epubpro/feature/bookbible/BookBibleScreen.kt`

---

## Bugs & Solutions

### Lỗi hiển thị `null` và Raw JSON trong Dòng thời gian (Timeline Events)
- **Ngày**: 2026-08-19
- **Vấn đề**: Các sự kiện kỹ năng/trang bị hiển thị chữ `"null"` thay vì tên kỹ năng, còn sự kiện xưng hô hiển thị chuỗi JSON thô `{"with":"Thẩm Dập",...}`.
- **Root cause**: Backend trả về tên kỹ năng/trang bị trong trường `attribute_key` trong khi trường `value` và `display_value` là `null`. Mapper cũ fallback lấy `value.toString()` dẫn đến chữ `"null"`.
- **Fix**: Trong `mapDtoToTimelineEvent`, bổ sung logic phân giải fallback từ `attribute_key` kết hợp `operation` (ví dụ: *"Lĩnh ngộ: Hoàng Kim Long Thể"*), đồng thời bóc tách object JSON của `address_terms` thành văn bản tiếng Việt tự nhiên *"Xưng hô với X (Tự xưng: Y • Gọi: Z)"*.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Lỗi trùng lặp thẻ Xưng hô và gộp nhầm người cùng họ
- **Ngày**: 2026-08-19
- **Vấn đề**: Giao diện hiển thị nhiều thẻ xưng hô bị lặp cho cùng 1 nhân vật (ví dụ "Thẩm Dập" và "Thẩm lão sư"), hoặc gộp nhầm 2 nhân vật khác nhau có cùng họ.
- **Root cause**: Logic first-word matching so sánh họ (`words1.first() == words2.first()`), và regex `\b` không hỗ trợ Unicode tiếng Việt.
- **Fix**: Xây dựng mảng `TITLE_WORDS` bóc tách danh xưng honorifics an toàn, loại bỏ first-word matching, so khớp sau khi đã strip danh xưng và gộp hợp nhất `selfTerm`, `otherTerm`, `contexts`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Lỗi cờ "Tiến triển mới" hiển thị liên tục (Spam Changed Badge)
- **Ngày**: 2026-08-19
- **Vấn đề**: Lần nào mở lại Book Bible cũng thấy xuất hiện huy hiệu "Tiến triển mới" trên các nhân vật dù không có cập nhật mới ở chương đó.
- **Root cause**: Mapper cũ fallback gán `last_changed_chapter = entity.chapterNumber` khi trường này bị thiếu trong JSON.
- **Fix**: Bóc tách chính xác `last_changed_chapter` từ `attributes["profile"]` hoặc root DTO, chỉ kích hoạt cờ `changedInCurrentChapter` khi `last_changed_chapter == chapterNumber` và `chapterNumber > 1`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Lỗi Locale-unsafe khi gọi `lowercase()` không chỉ định Locale
- **Ngày**: 2026-08-19
- **Vấn đề**: Khi chạy trên thiết bị cài đặt ngôn ngữ Thổ Nhĩ Kỳ (`tr-TR`), các phép so sánh chuỗi enum/status/category như `"SKILL".lowercase()` thành `"skıll"` (không khớp nhánh `when`).
- **Root cause**: Dùng `.lowercase()` mặc định của JVM thay vì `Locale.ROOT`.
- **Fix**: Chuẩn hóa toàn bộ bằng `.lowercase(Locale.ROOT)` và `titlecase(Locale.ROOT)` trên toàn bộ codebase.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`, `domain/src/main/java/com/epubpro/domain/model/BookBibleModels.kt`

---

## How-To

### Cách thêm thuộc tính hoặc phân mục mới vào Hồ sơ nhân vật (Book Bible)
- **Ngày**: 2026-08-19
- **Bước thực hiện**:
  1. Thêm model thuộc tính vào `domain/model/BookBibleModels.kt` (ví dụ: `CharacterPet`, `CharacterAddressTerm`).
  2. Khai báo field tương ứng trong `BookBibleDtos.kt` (`CharacterProfileDto`).
  3. Bổ sung parser bóc tách và lọc dữ liệu trong `BookBibleRepositoryImpl.kt` (`extractPetList`, `extractAddressTerms`, thêm key vào `standardKeys`).
  4. Khai báo chuỗi tiêu đề trong `strings.xml` (`core/designsystem`).
  5. Thêm `ProfileSectionCard` tương ứng trong `BookBibleScreen.kt`.
  6. Viết Unit Test trong `BookBibleRepositoryImplTest.kt` và `BookBibleViewModelTest.kt`.
- **Files liên quan**: `domain/src/main/java/com/epubpro/domain/model/BookBibleModels.kt`, `feature/bookbible/src/main/java/com/epubpro/feature/bookbible/BookBibleScreen.kt`

### Cách đóng khung Pretty Box Logger và Redact Header bảo mật
- **Ngày**: 2026-08-19
- **Bước thực hiện**:
  1. Trong `NetworkModule.kt`, tạo `HttpLoggingInterceptor.Logger` custom đóng khung đẹp với tag `API_HTTP`.
  2. Gọi `redactHeader("X-Book-Bible-Client-Key")`, `redactHeader("X-Api-Key")`, `redactHeader("Authorization")` để tránh lộ API key trên Logcat.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/NetworkModule.kt`

---

## Patterns

### Pattern Avatar Gradient Palette theo Hash tên nhân vật
- **Ngày**: 2026-08-19
- **Chi tiết**: Sinh avatar màu sắc sống động tự động dựa trên hash code của tên nhân vật, lấy 1 trong 8 dải gradient màu nghệ thuật, giúp nhận diện thị giác tức thì mà không cần server trả về ảnh avatar.
- **Files liên quan**: `feature/bookbible/src/main/java/com/epubpro/feature/bookbible/BookBibleScreen.kt`

### Pattern chuẩn hóa thuật ngữ tu tiên / huyền huyễn (`ATTRIBUTE_LABEL_TRANSLATIONS`)
- **Ngày**: 2026-08-19
- **Chi tiết**: Khi backend trả về các key động từ LLM prompt (dạng snake_case hoặc tiếng Anh), sử dụng một `Map<String, String>` dịch tự động các thuật ngữ quen thuộc (như hồn lực, cảnh giới, võ hồn, điểm sát hạch) trước khi hiển thị cho người dùng.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/bookbible/BookBibleRepositoryImpl.kt`

### Pattern sắp xếp ưu tiên Nhân vật chính (Protagonist Priority Ordering)
- **Ngày**: 2026-08-19
- **Chi tiết**: Trong ViewModel, sắp xếp danh sách nhân vật đa tiêu chí: Ưu tiên nhân vật chính (`isProtagonist`) $\to$ Nhân vật có tiến triển mới trong chương hiện tại (`changedInCurrentChapter`) $\to$ Theo thứ tự bảng chữ cái (`name`).
- **Files liên quan**: `feature/bookbible/src/main/java/com/epubpro/feature/bookbible/BookBibleViewModel.kt`, `domain/src/main/java/com/epubpro/domain/model/BookBibleModels.kt`
