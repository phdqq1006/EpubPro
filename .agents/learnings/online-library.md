# Online Backend & Library Integration

> Tổng hợp kiến thức về hệ thống kết nối Backend API, Kho Truyện Online, Retrofit, Dynamic Base URL, Cloudflare Workers/R2/Render, quản lý Coroutine/Flow và chuẩn hóa quy định dự án theo AGENTS.md.
> Cập nhật lần cuối: 2026-08-18

---

## Architecture

### Dynamic Base URL Multi-Environment Architecture
- **Ngày**: 2026-08-17
- **Chi tiết**: Sử dụng `DynamicBaseUrlInterceptor` trong OkHttp để định tuyến lại URL động lúc runtime dựa trên `ServerPreferencesManager`. Bóc tách path segments tương đối sau tiền tố `/api/v1` để tránh nhân đôi path segments khi chuyển đổi giữa Cloud (Render/API), Emulator (`10.0.2.2`), Localhost (`127.0.0.1`) và LAN IP.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/DynamicBaseUrlInterceptor.kt`, `core/storage/src/main/java/com/epubpro/core/storage/ServerPreferencesManager.kt`

### Production Render Cloud Deployment Integration
- **Ngày**: 2026-08-18
- **Chi tiết**: Cấu hình `DEFAULT_BASE_URL` sang máy chủ cố định `https://epubbackend.onrender.com/api/v1/` và tự động làm sạch các URL thử nghiệm tạm thời (`trycloudflare.com`, `workers.dev`, `r2.dev`) trong `readBaseUrl()` để người dùng cập nhật phiên bản mới luôn kết nối tới backend chính thức mà không bị dính cache SharedPreferences cũ.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ServerPreferencesManager.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/ServerSettingsDialog.kt`

### 1-Touch Stream & Offline Storage Pipeline
- **Ngày**: 2026-08-17
- **Chi tiết**: Tải nhị phân streaming (`@Streaming` Retrofit `ResponseBody`) file `.epub` từ `/export/epub` -> ghi trực tiếp vào `EpubStorageManager.importDownloadedEpub` -> tự động bóc tách metadata bằng `EpubEngine` -> lưu Room DB -> lập chỉ mục FTS5 ngầm. Không giữ toàn bộ file trong RAM.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/OnlineNovelRepositoryImpl.kt`, `feature/library/src/main/java/com/epubpro/feature/library/online/OnlineLibraryViewModel.kt`

### Hybrid Cloudflare Architecture (API Server + R2 Zero Egress CDN)
- **Ngày**: 2026-08-18
- **Chi tiết**: Tách biệt luồng xử lý: FastAPI/Worker backend xử lý logic tính toán và metadata (`/novels`, `/translate`). Cloudflare R2 (`pub-*.r2.dev`) đóng vai trò lưu trữ tĩnh file EPUB và Cover Images với ưu điểm Zero Egress Fee (miễn phí 100% băng thông tải về). App gọi API lấy metadata nhưng tải trực tiếp binary EPUB và ảnh từ CDN R2.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/OnlineNovelApiService.kt`

### Single Job Coroutine Collector Management in ViewModels
- **Ngày**: 2026-08-18
- **Chi tiết**: Khi ViewModel cần kích hoạt lại việc thu thập Flow từ Repository dựa trên event (ví dụ kiểm tra sách đã tải sau khi fetch API chi tiết), luôn quản lý collector bằng một biến `private var collectJob: Job? = null` và gọi `collectJob?.cancel()` trước khi `launch` mới. Tránh rò rỉ và chồng chéo nhiều coroutine collectors chạy song song.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/online/OnlineNovelDetailViewModel.kt`

---

## Bugs & Solutions

### Lỗi IllegalArgumentException: 25.0.2 khi build Gradle với Java 25
- **Ngày**: 2026-08-17
- **Vấn đề**: `gradlew assembleDebug` bị crash ngay khi khởi động vì `JavaVersion.parse` của Kotlin Compiler 1.9 / Gradle 8.7 không nhận diện được chuỗi phiên bản `25.0.2` của JDK Android Studio mới.
- **Root cause**: Java 25 quá mới so với Kotlin compiler 1.9.23.
- **Fix**: Trỏ biến môi trường `JAVA_HOME` sang JDK 17 LTS chuẩn (`C:\Users\haidu\.jdks\jbr-17.0.11` hoặc `Java 17`).
- **Files liên quan**: Build environment configuration

### Lỗi UnknownHostException trên Android với Cloudflare Tunnel (*.trycloudflare.com)
- **Ngày**: 2026-08-18
- **Vấn đề**: Trình duyệt/curl trên PC phân giải được domain tunnel nhưng Android App ném `java.net.UnknownHostException: No address associated with hostname`.
- **Root cause**: DNS mặc định của Android Emulator hoặc ISP mạng di động không phân giải được tên miền tunnel sinh tự động hoặc ưu tiên IPv6 không có route.
- **Fix**: Cài đặt `FallbackDns` (sử dụng DNS-over-HTTPS tới `1.1.1.1` hoặc `8.8.8.8`) trong `OkHttpClient.Builder().dns(fallbackDns)`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/FallbackDns.kt`, `core/storage/src/main/java/com/epubpro/core/storage/network/NetworkModule.kt`

### Lỗi 404 Not Found khi gọi API vào Cloudflare Worker chỉ phục vụ HTML
- **Ngày**: 2026-08-18
- **Vấn đề**: `GET https://*.workers.dev/api/v1/library/novels` trả về HTTP 404 (0-byte body).
- **Root cause**: Cloudflare Worker chỉ có route xử lý `GET /` (serve HTML Dashboard), chưa có handler định tuyến cho các API path `/api/v1/...` hoặc chưa proxy về origin backend.
- **Fix**: Viết thêm API router (`Hono` / `itty-router`) kết nối D1/KV/R2 trên Worker HOẶC deploy FastAPI backend lên Render/Fly.io và trỏ Base URL của App vào đúng server API.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ServerPreferencesManager.kt`

### Lỗi Smart Cast không hợp lệ cho thuộc tính public module khác
- **Ngày**: 2026-08-17
- **Vấn đề**: Thuộc tính `val description: String?` khai báo trong `:domain` khi kiểm tra `if (!detail.description.isNullOrBlank())` trong `:feature:library` báo lỗi compilation `Smart cast to String is impossible`.
- **Root cause**: Kotlin không thể đảm bảo tính bất biến của public property từ module khác tại thời điểm runtime.
- **Fix**: Gán vào biến cục bộ trước khi kiểm tra: `val desc = detail.description; if (!desc.isNullOrBlank()) { ... }`.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/online/OnlineNovelDetailScreen.kt`

### Lỗi Mockito NPE trên Kotlin Non-null Parameter khi test Interceptor.Chain
- **Ngày**: 2026-08-18
- **Vấn đề**: Trong unit test cho `DynamicBaseUrlInterceptor`, gọi `when(chain.proceed(any()))` ném `java.lang.NullPointerException`.
- **Root cause**: Mockito `any()` trả về `null` tạm thời trong bytecode, vi phạm non-null constraint của signature Kotlin `proceed(request: Request): Response`.
- **Fix**: Sử dụng Test Double (Fake Object) triển khai trực tiếp `val fakeChain = object : Interceptor.Chain { ... }` thay vì Mockito mock.
- **Files liên quan**: `core/storage/src/test/java/com/epubpro/core/storage/network/DynamicBaseUrlInterceptorTest.kt`

---

## How-To

### Cách tích hợp thêm một API Endpoint Backend mới
- **Ngày**: 2026-08-17
- **Bước thực hiện**:
  1. Khai báo DTO và method trong `OnlineNovelApiService.kt` (dùng Retrofit annotations).
  2. Khai báo Model và method trong Domain (`OnlineModels.kt` & `OnlineNovelRepository.kt`).
  3. Triển khai ánh xạ DTO -> Domain Model trong `OnlineNovelRepositoryImpl.kt`.
  4. Gọi từ ViewModel tương ứng với xử lý lỗi `Result<T>` và hiển thị lên Compose UI.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/OnlineNovelApiService.kt`, `domain/src/main/java/com/epubpro/domain/repository/OnlineNovelRepository.kt`

### Cách deploy FastAPI Backend lên Render.com kết hợp Cloudflare R2
- **Ngày**: 2026-08-18
- **Bước thực hiện**:
  1. Đẩy code Backend lên GitHub repo (có `requirements.txt`).
  2. Tạo Web Service trên Render.com (Runtime: Python 3, Build: `pip install -r requirements.txt`, Start: `uvicorn main:app --host 0.0.0.0 --port $PORT`).
  3. Điền Environment Variables (`R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_KEY`, `R2_BUCKET_NAME`).
  4. Lấy link public `https://xxx.onrender.com/api/v1/` cấu hình vào App.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/ServerPreferencesManager.kt`

### Cách resolve Merge Conflicts giữa nhánh Online Library và Display Settings/Modularization
- **Ngày**: 2026-08-18
- **Bước thực hiện**:
  1. `AndroidManifest.xml`: Kết hợp đầy đủ quyền mạng (Online Library) và quyền TTS Floating Bubble/Notification (`SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`).
  2. `strings.xml`: Giữ song song cả nhóm chuỗi Online Library (`online_novel_*`, `server_*`) và nhóm TTS Bubble / Display Settings.
  3. `LibraryViewModel.kt`: Kết hợp StateFlow kết nối Room reading progress vừa đọc tiến độ `getAllReadingProgress()` vừa hỗ trợ upload sách lên server và dọn dẹp snapshot store khi xóa sách.
  4. `libs.versions.toml`: Giữ cả Retrofit/OkHttp/Gson và Sherpa-ONNX/Jsoup/Testing harness.
  5. Chạy `./gradlew compileDebugKotlin` với JDK 17 để đảm bảo build xanh trước khi push merge commit.
- **Files liên quan**: `app/src/main/AndroidManifest.xml`, `core/designsystem/src/main/res/values/strings.xml`, `feature/library/src/main/java/com/epubpro/feature/library/LibraryViewModel.kt`, `gradle/libs.versions.toml`

### Cách chuẩn hóa dự án tuân thủ AGENTS.md
- **Ngày**: 2026-08-18
- **Bước thực hiện**:
  1. Chuyển toàn bộ UI text & dialog/snackbar messages sang `core/designsystem/src/main/res/values/strings.xml` và sử dụng `stringResource(R.string...)` hoặc `@StringRes`.
  2. Bổ sung KDoc tiếng Việt đầy đủ (`/** ... */`) cho mọi interface, class và function mới tạo với `@param`, `@return`, `@throws`.
  3. Thay thế các icon deprecated sang `Icons.AutoMirrored.*` (`ArrowBack`, `MenuBook`) để hỗ trợ layout RTL.
- **Files liên quan**: `AGENTS.md`, `core/designsystem/src/main/res/values/strings.xml`

---

## Patterns

### DNS-over-HTTPS (DoH) Fallback & Cache TTL Pattern
- **Ngày**: 2026-08-18
- **Chi tiết**: Cấu hình custom `okhttp3.Dns` với logic 2 tầng: tầng 1 gọi `Dns.SYSTEM` (lọc IPv4), tầng 2 fallback DoH Cloudflare (`1.1.1.1`) và Google (`8.8.8.8`). Sử dụng `CachedDnsEntry` kèm TTL (10 phút) và giải phóng `HttpURLConnection.disconnect()` trong khối `finally`.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/network/FallbackDns.kt`

### Cleartext HTTP Traffic cho môi trường Dev & Emulator
- **Ngày**: 2026-08-17
- **Chi tiết**: Trong `AndroidManifest.xml`, bật `android:usesCleartextTraffic="true"` để cho phép ứng dụng gọi các API nội bộ qua `http://` (`10.0.2.2`, `192.168.x.x`) mà không bị Android 9+ chặn kết nối không mã hóa.
- **Files liên quan**: `app/src/main/AndroidManifest.xml`

### Multi-Source Add Book BottomSheet Pattern
- **Ngày**: 2026-08-17
- **Chi tiết**: Thay vì mở trực tiếp File Picker khi bấm nút Add (`+`), mở một Material 3 `ModalBottomSheet` hiển thị danh sách các nguồn nhập sách (Kho truyện Online, File máy, Upload lên Server) giúp mở rộng tính năng mà không phá vỡ UX cũ.
- **Files liên quan**: `feature/library/src/main/java/com/epubpro/feature/library/AddBookBottomSheet.kt`
