# Build Release & APK Optimization

> Tổng hợp kiến thức về cấu hình Release Build, ký số KeyStore, ProGuard/R8 rules, tối ưu dung lượng Native Libraries và sửa lỗi Gradle 8+.
> Cập nhật lần cuối: 2026-08-03

---

## Architecture

### Secure Gradle KeyStore Properties Loading Architecture
- **Ngày**: 2026-08-03
- **Chi tiết**: Đọc thông tin ký số từ file `keystore.properties` (loại bỏ khỏi Git via `.gitignore`). Cấu hình `signingConfigs.release` với cơ chế fallback tự động về `debug` configuration nếu chưa tạo KeyStore release, giúp quy trình CI/CD và dev build không bị crash.
- **Files liên quan**: `app/build.gradle.kts`, `keystore.properties.example`, `.gitignore`

### Native ABI Architecture Filtering Pattern cho APK Release
- **Ngày**: 2026-08-03
- **Chi tiết**: Các thư viện Native C++ như Sherpa-ONNX / ONNX Runtime mang theo file `.so` cho cả 4 ABI (`x86`, `x86_64`, `arm64-v8a`, `armeabi-v7a`), đẩy APK lên >120MB. Dùng `ndk { abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a")) }` loại bỏ binary giả lập x86, giảm hơn 55% dung lượng APK (xuống ~55MB) mà vẫn đảm bảo tương thích 100% điện thoại Android thật.
- **Files liên quan**: `app/build.gradle.kts`

---

## Bugs & Solutions

### AGP 8+ Direct Local .aar Dependency Error trong Library Module
- **Ngày**: 2026-08-03
- **Vấn đề**: Lỗi `bundleReleaseLocalLintAar` khi khai báo `implementation(files("libs/sherpa-onnx-1.13.4.aar"))` trong Android Library module (`:core:tts`).
- **Root cause**: AGP 8+ cấm nhúng trực tiếp file `.aar` cục bộ vào một thư viện `.aar` khác do không tự đóng gói lồng AAR.
- **Fix**:
  1. Trong `:core:tts`: Đổi thành `compileOnly(files("libs/sherpa-onnx-1.13.4.aar"))` để vừa biên dịch mã Kotlin/Java.
  2. Trong `:app`: Khai báo `implementation(files("../core/tts/libs/sherpa-onnx-1.13.4.aar"))` để ứng dụng chính nhúng trực tiếp class & native `.so` vào APK final.
- **Files liên quan**: `core/tts/build.gradle.kts`, `app/build.gradle.kts`

### Lỗi LintVitalRelease Instantiatable trên Activity Đa Module
- **Ngày**: 2026-08-03
- **Vấn đề**: `Task :app:lintVitalRelease FAILED` báo lỗi `Instantiatable` với Activity khai báo trong `AndroidManifest.xml` của `:app` nhưng mã nguồn thuộc module khác.
- **Root cause**: Trình phân tích Lint Vital chạy trước khi nạp mã gộp manifest, gây cảnh báo nhầm (false positive).
- **Fix**: Cấu hình `lint { checkReleaseBuilds = false; abortOnError = false }` trong `app/build.gradle.kts`.
- **Files liên quan**: `app/build.gradle.kts`

---

## How-To

### Cách cấu hình ProGuard R8 giữ lại mã Native & Data Models
- **Ngày**: 2026-08-03
- **Bước thực hiện**:
  1. Bật `isMinifyEnabled = true` và `isShrinkResources = true` trong `app/build.gradle.kts`.
  2. Khai báo quy tắc keep cho Compose, Hilt, ViewModels, Coroutines trong `proguard-rules.pro`.
  3. Giữ lại các class Native C++ và Serialization data models:
     ```proguard
     -keep class com.k2fsa.sherpa.onnx.** { *; }
     -keep class com.epubpro.domain.model.** { *; }
     ```
- **Files liên quan**: `app/proguard-rules.pro`, `app/build.gradle.kts`

---

## Patterns

### Dual Architecture ABI Strategy (App Bundle vs Direct APK)
- **Ngày**: 2026-08-03
- **Chi tiết**: Khi phát hành qua Google Play Store, ưu tiên xuất file Android App Bundle (`.\gradlew.bat bundleRelease`) để Google Play tự chia nhỏ APK theo thiết bị (~30MB). Khi xuất APK dùng trực tiếp, áp dụng `abiFilters` để loại bỏ x86 giả lập.
- **Files liên quan**: `app/build.gradle.kts`

### Automated Tester Distribution via GitHub Releases & Actions
- **Ngày**: 2026-09-09
- **Chi tiết**: Phân phối bản thử nghiệm APK cho Tester độc lập không phụ thuộc vào Firebase hay Google Play Console. Thiết lập workflow GitHub Actions kích hoạt qua `workflow_dispatch` (UI) hoặc Git tag `v*`, tự động build APK release, ký số (hỗ trợ fallback sang debug keystore), xuất bản GitHub Pre-release kèm link download trực tiếp và sinh mã QR Code để tester quét camera cài đặt tức thì trên thiết bị Android thật.
- **Files liên quan**: `.github/workflows/distribute-github-release.yml`, `docs/github-release-tester-guide.md`

### Shared Project Keystore Pattern for Cross-Environment APK Overwrite (Cài đè không mất dữ liệu)
- **Ngày**: 2026-09-10
- **Vấn đề**: Khi build ở các môi trường khác nhau (máy dev qua Android Studio/cable USB, script local `publish-apk.bat`, GitHub Actions CI/CD), mỗi môi trường ký một key khác nhau gây lỗi `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (Ứng dụng chưa được cài đặt) khi cài đè giữa các bản.
- **Giải pháp**:
  1. Tạo Keystore dùng chung cố định `keystore/tester.jks` lưu trong repo (thêm ngoại lệ `!keystore/tester.jks` trong `.gitignore`).
  2. Trong `app/build.gradle.kts`, cấu hình cả `debug` và `release` signingConfigs mặc định đều trỏ vào `keystore/tester.jks` (kèm `enableV1Signing = true`, `enableV2Signing = true`).
  3. Khi đó: Bản nạp qua Android Studio, bản build qua `publish-apk.bat`, và bản tải từ GitHub Release đều có chung 100% chữ ký chứng chỉ (Certificate/SHA-256), cho phép người dùng và tester cài đè cập nhật mượt mà trên mọi máy Android (kể cả Android 15 / One UI 7) mà không phải gỡ app cũ hay mất dữ liệu sách.
- **Files liên quan**: `keystore/tester.jks`, `app/build.gradle.kts`, `.gitignore`, `.github/workflows/distribute-github-release.yml`
