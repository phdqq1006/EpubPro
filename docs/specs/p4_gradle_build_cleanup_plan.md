# P4 — Kế hoạch Gradle & Build Cleanup

> **Trạng thái**: ĐÃ HOÀN THÀNH TRIỂN KHAI (COMPLETED)  
> **Ngày hoàn thành**: 2026-08-17  
> **Baseline code**: `8119e3e0588eb8ce7dd126dd09cd7b04342cf9f5`  
> **Phạm vi hoàn tất**: Version Catalog toàn diện, Dependency pruning, Sherpa AAR local Maven repository & sole ownership, `domain` Kotlin/JVM thuần túy, và Included Build `build-logic` Convention Plugins.

---

## 1. Kết Quả Đạt Được

- [x] **100% Dependency & Plugin trong Version Catalog**: Đưa toàn bộ chuỗi hardcode (`jsoup`, `junit4`, `json`, `mockito`, `javax-inject`, `lifecycle-process`, `savedstate-ktx`, `androidx-media`) vào `gradle/libs.versions.toml`.
- [x] **Dependency Pruning**: Xóa toàn bộ dependency thừa (`core:common` ở các core module, `core-ktx`, mockito thừa) sau khi đã kiểm chứng compile và runtime test.
- [x] **Sherpa-ONNX Local Maven Repository & Sole Ownership**:
  - Tạo local Maven repository tại `third_party/maven/com/epubpro/vendor/sherpa-onnx/1.13.4/` kèm POM và checksum SHA-256 (`03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780`).
  - `:core:tts` là owner duy nhất phụ thuộc `implementation(libs.sherpa.onnx)`.
  - `:app` nhận runtime artifact transitively và xóa bỏ direct file dependency.
  - Xóa bỏ file cũ `core/tts/libs/sherpa-onnx-1.13.4.aar`.
- [x] **`domain` Kotlin/JVM Pure Library**:
  - Chuyển `domain` thành pure Kotlin/JVM module với Java toolchain 17.
  - Loại bỏ hoàn toàn Android Gradle Plugin (AGP), Android SDK và Android build overhead.
- [x] **Included Build `build-logic` Convention Plugins**:
  - Tạo 5 convention plugins composable, không cấu hình chéo:
    1. `epubpro.android.application`
    2. `epubpro.android.library`
    3. `epubpro.android.compose`
    4. `epubpro.android.hilt`
    5. `epubpro.kotlin.jvm.library`
  - Loại bỏ toàn bộ boilerplate `compileSdk 34`, `minSdk 26`, Java 17, Compose compiler `1.5.11`, KAPT & Hilt dependencies lặp lại ở tất cả 14 module con.

---

## 2. Bảng Đối Chiếu Module Sau Migration

| Module | Convention Plugins | Namespace / Type | Dependencies Chính |
|---|---|---|---|
| `:domain` | `epubpro.kotlin.jvm.library` | Pure JVM | `kotlinx.coroutines.core` |
| `:core:common` | `epubpro.android.library` | `com.epubpro.core.common` | `core-ktx`, `coroutines`, `javax.inject` |
| `:core:designsystem` | `epubpro.android.library`, `epubpro.android.compose` | `com.epubpro.core.designsystem` | `core:common`, Compose UI & M3 |
| `:core:database` | `epubpro.android.library`, `epubpro.android.hilt` | `com.epubpro.core.database` | `domain`, `core:common`, Room |
| `:core:storage` | `epubpro.android.library`, `epubpro.android.hilt` | `com.epubpro.core.storage` | `domain`, `core:common`, `core-ktx` |
| `:core:tts` | `epubpro.android.library`, `epubpro.android.hilt` | `com.epubpro.core.tts` | `core-ktx`, `coroutines`, `libs.sherpa.onnx` (Sole Owner) |
| `:core:ai` | `epubpro.android.library`, `epubpro.android.hilt` | `com.epubpro.core.ai` | `domain`, `core:storage`, `jsoup` |
| `:core:epub` | `epubpro.android.library`, `epubpro.android.hilt` | `com.epubpro.core.epub` | `domain`, `jsoup` |
| `:core:reader-renderer` | `epubpro.android.library` | `com.epubpro.core.reader.renderer` | `domain`, `jsoup` |
| `:core:playback` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.core.playback` | `domain`, `core:designsystem`, `core:storage`, `core:tts`, `core:epub`, `media`, `jsoup` |
| `:feature:bookmark` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.feature.bookmark` | `domain`, `core:common`, `core:designsystem` |
| `:feature:library` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.feature.library` | `domain`, `core:common`, `core:designsystem`, `core:storage`, `core:epub`, `coil` |
| `:feature:profile` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.feature.profile` | `domain`, `core:common`, `core:designsystem`, `core:storage`, `core:playback`, `core:tts`, `coil` |
| `:feature:search` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.feature.search` | `domain`, `core:common`, `core:designsystem` |
| `:feature:reader` | `epubpro.android.library`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.feature.reader` | `domain`, `core:common`, `core:designsystem`, `core:storage`, `core:epub`, `core:reader-renderer`, `core:playback`, `core:ai`, `appcompat` |
| `:app` | `epubpro.android.application`, `epubpro.android.compose`, `epubpro.android.hilt` | `com.epubpro.app` | Toàn bộ feature & core modules, signing, ABI filters, R8 Proguard |

---

## 3. Lệnh Kiểm Thử Chuẩn Hóa

```powershell
# Chạy Unit Tests toàn bộ các module
.\gradlew.bat :domain:test :core:epub:testDebugUnitTest :core:reader-renderer:testDebugUnitTest :core:tts:testDebugUnitTest :core:playback:testDebugUnitTest :feature:reader:testDebugUnitTest

# Kiểm tra dependency graph của Sherpa-ONNX
.\gradlew.bat :app:dependencyInsight --dependency sherpa-onnx --configuration debugRuntimeClasspath

# Biên dịch toàn bộ ứng dụng Debug, Release và Lint
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug
```
