# Kế hoạch tính năng Book Bible

## Mục tiêu

Tích hợp hồ sơ và timeline nhân vật chống spoiler vào cả hai Reader mà không thay đổi tiến trình đọc cục bộ hoặc các contract render/chuyển chương hiện có.

## Công việc

- [ ] Chốt contract backend v1 mở rộng cho edition reuse, source hash, coverage ranges, profile typed, revision và timeline display value. Kiểm chứng: Swagger có ví dụ matched/new edition, submission trùng, snapshot partial/processing và timeline bị chặn đúng chương.
- [ ] Đăng ký `:feature:bookbible`, thêm WorkManager/Hilt/Room test dependencies và cấu hình worker trong Application. Kiểm chứng: `.\gradlew.bat :feature:bookbible:assembleDebug :app:assembleDebug` thành công với feature shell.
- [ ] Thêm domain model và repository contract Book Bible với `chapterNumber` 1-based. Kiểm chứng: test bao phủ EPUB `index + 1`, online giữ nguyên số chương, coverage và status mapping.
- [ ] Thêm Room v5 entities, DAO, migration, cache pruning và payload metadata. Kiểm chứng: migration 4-to-5 giữ nguyên toàn bộ bảng cũ; DAO bảo đảm duy nhất theo source/chapter/hash và dọn dữ liệu khi xóa sách local.
- [ ] Cài Retrofit DTO/mapper, payload file atomic, repository, unique WorkManager, idempotency, phân loại retry và cleanup. Kiểm chứng: test bao phủ `200/202/409`, mất mạng, `408/429/5xx`, lỗi vĩnh viễn `4xx/413`, enqueue trùng và xóa payload.
- [ ] Gắn hook sau khi chương nguồn được mở: EPUB dùng HTML gốc đã nạp; online tải metadata và bản `original` ở luồng nền, không thay nội dung dịch đang hiển thị. Thêm route encode và menu overflow. Kiểm chứng: prefetch không submit, bản AI/bản dịch không được gửi và hai Reader truyền đúng chương 1-based.
- [ ] Xây màn danh sách nhân vật và chi tiết Profile/Timeline với cache-first refresh, polling processing, trạng thái partial/empty/offline/error và khôi phục bằng SavedStateHandle. Kiểm chứng: ViewModel/UI test bao phủ mọi trạng thái và polling dừng khi rời màn hình.
- [ ] Self-review dependency, lifecycle, concurrency, logging, payload limit, cleanup và backward compatibility. Kiểm chứng: không log source text/body JSON và không đưa state Book Bible vào state render của Reader.
- [ ] Chạy kiểm chứng cuối cùng: `.\gradlew.bat :domain:test :core:database:testDebugUnitTest :core:storage:testDebugUnitTest :core:epub:testDebugUnitTest :core:reader-renderer:testDebugUnitTest :core:playback:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest :feature:bookbible:testDebugUnitTest :app:assembleDebug`; sau đó test thiết bị thật với cả hai Reader, offline/reconnect, process death, partial coverage và chuyển chương hai chiều.

## Hoàn thành khi

- [ ] Cả hai Reader mở được Book Bible cho chương hiện tại và không bị ảnh hưởng khi mạng/backend lỗi.
- [ ] Submission được deduplicate, retry và không chứa chương chưa mở hoặc nội dung AI/bản dịch.
- [ ] Hồ sơ bị chặn spoiler đúng chương, xem offline được, làm mới được và thể hiện rõ coverage thiếu.
- [ ] Room migration bảo toàn dữ liệu hiện có; toàn bộ kiểm thử tự động và thủ công đều đạt.

## Lưu ý

Contract backend hoàn chỉnh là dependency bên ngoài duy nhất. Trước khi triển khai phải đọc `feature/reader/AGENTS.md`, `docs/reader-horizontal-last-page-geometry-fix.md` và `docs/reader-chapter-transition-snapshot-design.md`. Function Kotlin mới phải có KDoc tiếng Việt; mọi chuỗi hiển thị phải dùng resource trong `core/designsystem`.
