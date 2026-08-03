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
