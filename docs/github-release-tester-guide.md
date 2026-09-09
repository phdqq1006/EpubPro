# Hướng dẫn Phân phối bản thử nghiệm (APK) cho Tester qua GitHub Releases

> **Không cần cài đặt Firebase, không cần token phức tạp, chỉ cần 1 cú click trên GitHub!**

---

## 1. Cách phát hành bản APK cho Tester bằng giao diện Web GitHub

1. Truy cập vào Repository của bạn trên GitHub.
2. Bấm vào tab **Actions** (ở thanh menu phía trên).
3. Ở cột bên trái, chọn workflow: **Distribute APK to GitHub Releases (App Tester)**.
4. Bấm vào nút **Run workflow** (ở góc phải):
   - **Tên phiên bản / Git Tag**: Nhập tag bạn muốn (ví dụ: `v1.0.0-beta1`, `v1.0.1-test`,...).
   - **Tiêu đề bản phát hành**: Tiêu đề hiển thị (ví dụ: `EpubPro Beta Test - Sửa lỗi đọc sách`).
   - **Ghi chú cập nhật**: Điền danh sách tính năng mới hoặc bug đã fix để tester nắm được.
   - **Đánh dấu là Pre-release**: Giữ nguyên `true` để đánh dấu đây là bản thử nghiệm.
5. Bấm nút xanh **Run workflow**.

Quá trình build diễn ra hoàn toàn tự động trên máy chủ GitHub (khoảng 3-5 phút):
- Tự động cài đặt môi trường Java 17 và tối ưu bộ nhớ đệm Gradle Cache.
- Build file APK Release có ký số.
- Tự động tạo một bản phát hành mới trong mục **Releases** của repo kèm file APK và **Mã QR Code** tải nhanh.

---

## 2. Cách phát hành tự động bằng Git Tag (dành cho Developer)

Bạn cũng có thể kích hoạt workflow tự động ngay từ máy tính bằng cách push một git tag:

```bash
# Tạo tag phiên bản
git tag v1.0.0-beta1

# Đẩy tag lên GitHub
git push origin v1.0.0-beta1
```

Hệ thống CI/CD sẽ tự động bắt sự kiện tag và tiến hành build rồi đăng bản release.

---

## 3. Cách Tester nhận và cài đặt app

Tester chỉ cần thực hiện 1 trong 2 cách sau:

1. **Cách 1 (Quét mã QR - Nhanh nhất):**
   - Mở trang **Releases** của repository trên máy tính hoặc chia sẻ hình ảnh mã QR.
   - Dùng camera điện thoại Android quét mã QR hiển thị trong phần mô tả bản release -> bấm tải về và mở cài đặt APK.

2. **Cách 2 (Mở trực tiếp trên điện thoại):**
   - Mở trình duyệt trên điện thoại Android, truy cập vào link Release của GitHub repo.
   - Bấm vào file `.apk` (ví dụ: `EpubPro-v1.0.0-beta1.apk`) trong mục Assets -> Cài đặt.

---

## 4. Cấu hình Ký số Release chính thức (Tuỳ chọn)

Nếu bạn muốn bản APK phát hành được ký bằng file Keystore của bạn (thay vì tự động dùng debug signing):

1. Vào GitHub Repo > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**.
2. Thêm các Secrets sau:
   - `RELEASE_KEYSTORE_BASE64`: Mã base64 của file `.jks` hoặc `.keystore` của bạn.
     *(Để chuyển file keystore thành base64 trên Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("path/to/my-release-key.jks")) | Set-Clipboard`)*
   - `RELEASE_STORE_PASSWORD`: Mật khẩu của keystore.
   - `RELEASE_KEY_ALIAS`: Alias của key.
   - `RELEASE_KEY_PASSWORD`: Mật khẩu của key.

Nếu chưa cấu hình các Secret trên, workflow sẽ tự động ký bằng chữ ký Debug để tester vẫn có thể cài đặt và kiểm thử ứng dụng bình thường mà không hề bị lỗi gián đoạn.
