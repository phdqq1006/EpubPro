# Kế hoạch tinh gọn Thư viện và Kho truyện

## Mục tiêu

Loại bỏ các destination trùng, phân biệt rõ sách local với kho truyện online và gỡ lối vào Search global đang sai mà không thay đổi Room/FTS.

## Phạm vi quyết định

- Giữ hai tab nghiệp vụ: `Thư viện` cho sách local và `Kho truyện` cho nội dung online.
- Bỏ tab `Trang chủ` vì đang render đúng cùng `LibraryScreen` với `Thư viện`.
- Đổi tab `Duyệt` thành `Kho truyện` và dùng icon cloud/explore thay cho icon Search.
- Bỏ nút Search riêng trên TopAppBar của Library; giữ ô lọc tên sách/tác giả ngay trong màn hình.
- Giữ `SearchRepository`, `BookSearchEntity` và quá trình index EPUB. Tìm trong nội dung sách sẽ là tác vụ Reader riêng, không xóa schema trong thay đổi này.

## Công việc

- [x] Sửa `TopLevelDestination`: bỏ `HOME`, đặt `LIBRARY` trước `BROWSE`, cập nhật nhãn/icon `Kho truyện`. Kiểm chứng tĩnh đã xác nhận bottom bar chỉ còn bốn destination.
- [x] Hợp nhất thư viện local: dùng `Screen.Bookshelf` làm start destination và đích của `openLibraryRequests`, sau đó xóa `Screen.Library` cùng composable trùng.
- [x] Hợp nhất kho online: xóa `Screen.OnlineLibrary`, cho mọi nút Cloud/Add Online điều hướng tới `Screen.Browse`, và bỏ nút Back khỏi `OnlineLibraryScreen` top-level.
- [x] Gỡ Search global khỏi Library: xóa `onNavigateToSearch`, icon TopAppBar và route invocation dùng `bookId = "global"`. Giữ màn/FTS hiện có cho tác vụ tìm kiếm trong Reader sau này.
- [x] Dọn import/string/callback không còn dùng và rà soát reference/back stack bằng tìm kiếm toàn repo; `git diff --check` không phát hiện lỗi whitespace.
- [ ] Chạy kiểm chứng cuối: `.\gradlew.bat :feature:library:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`; kiểm tra thủ công Library → Kho truyện → chi tiết → Back, mở kho từ Add Book, và luồng widget mở Library.

> Ghi chú kiểm chứng: Gradle chưa chạy tới bước compile vì máy chỉ có JDK `25.0.2`, trong khi Kotlin `1.9.23` của dự án lỗi khi parse phiên bản này. Cần chạy lại bằng JDK tương thích (thường là JDK 17) để hoàn tất build và kiểm thử UI.

## Hoàn thành khi

- [x] Bottom bar còn `Thư viện`, `Kho truyện`, `Sổ tay`, `Cá nhân`.
- [x] Chỉ có một route local-library và một route online-library.
- [x] Không còn Search global hỏng; tìm tên/tác giả local và tìm online vẫn hoạt động.
- [x] Không thay đổi Room schema, dữ liệu FTS hoặc Reader pagination/chapter transition.
