# Authentication & Supabase Integration

> Tổng hợp kiến thức về hệ thống xác thực tài khoản, tích hợp Supabase Auth, Dynamic URL routing và quản lý phiên trong dự án EpubPro.
> Cập nhật lần cuối: 2026-08-24

---

## Architecture

### Backend Config kết hợp Supabase Direct Auth
- **Ngày**: 2026-08-24
- **Chi tiết**: Hệ thống áp dụng mô hình 2 tầng: (1) Client gọi `GET /api/auth/config` từ Backend để lấy thông số môi trường động (`supabase_url`, `supabase_publishable_key`, `mode`, `auth_required`); (2) Client gọi trực tiếp các endpoint Supabase Auth (`/auth/v1/token`, `/auth/v1/logout`, `/auth/v1/recover`) bằng Supabase anon key; (3) Access Token (JWT) nhận được từ Supabase sẽ được lưu vào SharedPreferences và tự động gắn vào header `Authorization: Bearer <token>` cho tất cả các API backend nghiệp vụ (`/api/v1/library/...`).
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/AuthRepositoryImpl.kt`, `core/storage/src/main/java/com/epubpro/core/storage/network/NetworkModule.kt`

### Cô lập định tuyến Auth trong DynamicBaseUrlInterceptor
- **Ngày**: 2026-08-24
- **Chi tiết**: Base URL mặc định của ứng dụng trỏ tới `/api/v1/`. Tuy nhiên, các endpoint cấu hình Auth nằm ở `/api/auth/config` (không có `/v1`). `DynamicBaseUrlInterceptor` phải phân biệt các endpoint auth để chỉ cập nhật host/port/scheme động theo cấu hình server mà không tự ý chèn `/v1/` vào path.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/DynamicBaseUrlInterceptor.kt`

---

## Bugs & Solutions

### Lỗi 404 do DynamicBaseUrlInterceptor tự động chèn /v1/ vào /api/auth/config
- **Ngày**: 2026-08-24
- **Vấn đề**: Request `GET /api/auth/config` bị biến đổi thành `GET /api/v1/auth/config` và trả về lỗi HTTP 404.
- **Root cause**: `DynamicBaseUrlInterceptor` bóc tách `api` và ghép nối với pathSegments của base URL mặc định (`["api", "v1"]`).
- **Fix**: Thêm bộ lọc trong interceptor: nếu path bắt đầu bằng `/api/auth/` hoặc có header `SKIP_DYNAMIC_BASE_URL_HEADER`, chỉ tái cấu trúc scheme/host/port và giữ nguyên toàn bộ path gốc.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/DynamicBaseUrlInterceptor.kt`, `core/storage/src/main/java/com/epubpro/core/storage/network/AuthApiService.kt`

### Lỗi 401 Unauthorized do Fallback ngầm tài khoản Local Dev
- **Ngày**: 2026-08-24
- **Vấn đề**: Người dùng nhập tài khoản thật nhưng app tự nhận diện là "Thành viên Local Dev", gửi token giả `local_jwt_...` lên backend và bị từ chối 401 khi truy cập thư viện sách.
- **Root cause**: Khi `fetchAuthConfig()` bị lỗi 404, logic cũ rơi vào nhánh fallback ngầm tạo tài khoản offline thay vì báo lỗi.
- **Fix**: Loại bỏ hoàn toàn fallback ngầm. Nếu không lấy được cấu hình hoặc Supabase từ chối (sai mật khẩu, lỗi mạng), ném `Result.failure` và hiển thị thông báo lỗi cụ thể lên UI.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/AuthRepositoryImpl.kt`

---

## How-To

### Quy trình tích hợp và kiểm tra luồng xác thực Supabase
- **Ngày**: 2026-08-24
- **Bước thực hiện**:
  1. Lấy root URL từ `ServerPreferencesManager.getBaseUrl()` và tạo endpoint tuyệt đối `$rootUrl/api/auth/config`.
  2. Định nghĩa Retrofit interface với `@GET suspend fun getAuthConfigFromUrl(@Url url: String, @Header(...) skipDynamic: String)` để tránh bị rewrite URL.
  3. Gửi payload đăng nhập tới `{supabase_url}/auth/v1/token?grant_type=password` kèm header `apikey: {supabase_publishable_key}`.
  4. Lưu `accessToken`, `refreshToken` và `expiresAt` vào `AuthPreferencesManager`.
  5. Đăng ký `authInterceptor` trong `NetworkModule` để tự động thêm `Authorization: Bearer <token>` vào mọi request Retrofit.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/AuthApiService.kt`, `core/storage/src/main/java/com/epubpro/core/storage/network/NetworkModule.kt`, `core/storage/src/main/java/com/epubpro/core/storage/AuthPreferencesManager.kt`

---

## Patterns

### Safe Logging trong Android Module thuần JVM Unit Tests
- **Ngày**: 2026-08-24
- **Chi tiết**: Trong module Android library (như `:core:storage`), gọi trực tiếp `android.util.Log` trong các class được test bằng JUnit 4 thuần JVM sẽ ném `RuntimeException: Method d in android.util.Log not mocked`.
- **Ví dụ code**:
  ```kotlin
  private fun logDebug(tag: String, msg: String) {
      try {
          android.util.Log.d(tag, msg)
      } catch (_: Throwable) {
          println("[$tag] $msg")
      }
  }
  ```
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/AuthRepositoryImpl.kt`
