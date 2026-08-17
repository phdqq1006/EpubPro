# Android Project Instructions

Khi phát triển hoặc sửa code Android trong repository này:

- Luôn áp dụng skill `senior-android`.
- Khi review code, sử dụng `android-code-review`.
- Khi điều tra bug/regression, sử dụng `android-bug-investigation`.
- Khi viết hoặc đánh giá test, sử dụng `android-testing`.

## Quy định Documentation (Tài liệu hóa code)

- **Khi tạo function mới**: **BẮT BUỘC** luôn thêm doc comment (KDoc cho Kotlin / Javadoc cho Java) giải thích bằng **tiếng Việt**.
  - Mô tả rõ ràng chức năng, mục đích và ngữ cảnh sử dụng của function.
  - Chú thích chi tiết các tham số (`@param`), giá trị trả về (`@return`), và exception ném ra nếu có (`@throws`).
  - Định dạng chuẩn KDoc (`/** ... */`).

## Quy định String Resources (Không hardcode chuỗi)

- **TUYỆT ĐỐI KHÔNG hardcode chuỗi**: Không viết chuỗi text/giao diện trực tiếp trong code (Compose UI, XML layouts, Activity, ViewModel, Service, Utils,...).
- **LUÔN tạo và sử dụng String Resource (`strings.xml`)**:
  - Mọi văn bản hiển thị (nhãn, tiêu đề, nút bấm, thông báo lỗi, format text) phải được định nghĩa trong `strings.xml` (chuỗi chung đặt tại `core/designsystem/src/main/res/values/strings.xml`).
  - Trong **Jetpack Compose**: Sử dụng `stringResource(R.string.<name>)` hoặc `stringResource(R.string.<name>, formatArgs)`.
  - Trong **Non-Compose / Context**: Sử dụng `context.getString(R.string.<name>)` hoặc khai báo `@StringRes val textRes: Int`.
  - Chuỗi động / có tham số: Định nghĩa placeholder chuẩn trong XML (ví dụ `%1$s`, `%1$d`) và truyền biến qua tham số format.

## Development workflow

Trước khi sửa code:

1. Đọc implementation hiện tại.
2. Search caller/callee liên quan.
3. Xác định impact.
4. Kiểm tra convention hiện có của project.
5. Đưa ra implementation plan ngắn.

Khi sửa code:

- Ưu tiên minimal change.
- Không refactor ngoài scope.
- Không thay architecture nếu không cần thiết.
- Giữ backward compatibility.

Sau khi sửa:

1. Self-review git diff.
2. Kiểm tra regression.
3. Kiểm tra lifecycle.
4. Kiểm tra coroutine/concurrency.
5. Kiểm tra memory leak.
6. Chạy test/build phù hợp.