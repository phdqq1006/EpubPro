# Android Project Instructions

Khi phát triển hoặc sửa code Android trong repository này:

- Luôn áp dụng skill `senior-android`.
- Khi review code, sử dụng `android-code-review`.
- Khi điều tra bug/regression, sử dụng `android-bug-investigation`.
- Khi viết hoặc đánh giá test, sử dụng `android-testing`.

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