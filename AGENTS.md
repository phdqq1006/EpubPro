# Android Project Instructions

Repository này là project Android/Kotlin. Khi phát triển, review hoặc sửa code, toàn bộ phân tích và output phải bằng **tiếng Việt**.

Giữ nguyên tên class, function, package, Gradle task, XML tag, resource name, log và error text bằng đúng nguyên bản.

---

## Skill Routing

Khi task liên quan Android/Kotlin/Jetpack Compose, **luôn ưu tiên dùng skill router mới trước**:

- `android-compose-router-vi`

Router có nhiệm vụ chọn skill phù hợp theo ngữ cảnh task.

### Baseline skills luôn áp dụng

Khi phát triển hoặc sửa code Android:

- Luôn áp dụng `senior-android`.
- Khi review code, sử dụng `android-code-review`.
- Khi điều tra bug/regression, sử dụng `android-bug-investigation`.
- Khi viết hoặc đánh giá test, sử dụng `android-testing`.

### Android / Kotlin / Compose skills mới

Khi task phù hợp, dùng thêm các skill sau:

#### Kotlin / Coroutine / Flow / Architecture

- `cb-kotlin-concurrency-and-flow`: dùng cho coroutine, Flow, StateFlow, SharedFlow, lifecycle, cancellation, one-shot event.
- `cb-kotlin-api-design`: dùng khi thiết kế API Kotlin, function owner, domain type, public/private boundary.
- `cb-kotlin-control-flow`: dùng khi review `when`, nullability, smart cast, sealed class, early return.

#### Jetpack Compose

- `android-compose-styles`: dùng khi thiết kế style/theme/token trong Compose.
- `cb-compose-state-and-effects`: dùng cho state hoisting, side effect, `LaunchedEffect`, `DisposableEffect`, `rememberUpdatedState`, collect Flow.
- `cb-compose-component-design`: dùng khi thiết kế Composable API, slot API, caller-placeable component.
- `cb-compose-performance`: dùng khi review hiệu năng Compose tổng quan.
- `cb-compose-animations`: dùng khi làm animation Compose.
- `cb-compose-focus-navigation`: dùng khi xử lý focus, keyboard, D-pad, TV navigation.
- `cb-compose-ui-testing-patterns`: dùng khi viết/review test Compose UI.

#### Compose performance chuyên sâu

Khi task liên quan jank, recomposition, stability, LazyColumn/LazyGrid, modifier, Flow collection trong Compose, dùng:

- `compose-perf-auditing-compose-performance`
- `compose-perf-diagnosing-stability`
- `compose-perf-stabilizing-types`
- `compose-perf-debugging-recompositions`
- `compose-perf-deferring-state-reads`
- `compose-perf-choosing-derivedstateof`
- `compose-perf-optimizing-lazy-layouts`
- `compose-perf-configuring-lazy-prefetch`
- `compose-perf-ordering-modifier-chains`
- `compose-perf-migrating-to-modifier-node`
- `compose-perf-collecting-flows-safely`
- `compose-perf-using-efficient-effects`
- `compose-perf-testing-release-mode`
- `compose-perf-tracing-recompositions`

#### Android platform / system

- `android-intent-security`: dùng cho Intent, DeepLink, exported Activity/Service/Receiver, PendingIntent, URI permission.
- `android-edge-to-edge`: dùng cho edge-to-edge, status bar, navigation bar, window insets, IME inset.
- `android-camerax`: dùng cho CameraX, Preview, ImageCapture, ImageAnalysis, lifecycle binding.
- `android-navigation-3`: dùng cho navigation, back stack, deep link, result passing.
- `android-testing-setup`: dùng khi setup/review unit test, instrumentation test, Compose test.
- `android-r8-analyzer`: dùng cho R8, ProGuard, keep rules, minify release.
- `android-agp-9-upgrade`: dùng khi nâng AGP/Gradle/Kotlin/KSP.
- `android-migrate-xml-views-to-compose`: dùng khi migrate XML/View sang Compose.
- `android-compose-adaptive`: dùng khi review responsive/adaptive layout, tablet, foldable, landscape.
- `android-stitch-ui-integration`: dùng khi chuyển đổi giao diện/mockup/code từ Stitch sang Jetpack Compose chuẩn dự án.

---

## Rule chọn skill theo loại task

### Review màn hình UI / Compose

Khi user yêu cầu review màn hình, design, UI, hiệu năng hoặc accessibility, bắt buộc cân nhắc:

- `android-compose-router-vi`
- `senior-android`
- `android-code-review`
- `cb-compose-performance`
- `cb-compose-state-and-effects`
- `android-compose-adaptive`
- `compose-perf-auditing-compose-performance`

Nếu màn hình có list/scroll, dùng thêm:

- `compose-perf-optimizing-lazy-layouts`
- `compose-perf-diagnosing-stability`
- `compose-perf-debugging-recompositions`

Nếu màn hình collect Flow từ ViewModel, dùng thêm:

- `cb-kotlin-concurrency-and-flow`
- `compose-perf-collecting-flows-safely`
- `compose-perf-using-efficient-effects`

Nếu visual reference đến từ Google Stitch (screenshot, MCP, HTML/CSS,
share link hoặc `DESIGN.md`), dùng thêm:

- `android-stitch-ui-integration`

### Review bug/regression

Khi user yêu cầu điều tra bug/regression:

- `android-bug-investigation`
- `senior-android`
- `android-compose-router-vi`

Nếu bug liên quan Compose/performance thì dùng thêm nhóm `compose-perf-*` phù hợp.

### Review bảo mật

Khi task liên quan DeepLink, Intent, exported component, clipboard, WebView, storage, token, logging sensitive data:

- `android-intent-security`
- `android-code-review`
- `senior-android`

### Review test

Khi task liên quan test:

- `android-testing`
- `android-testing-setup`
- `cb-compose-ui-testing-patterns`

---

## Quy định Documentation

Khi tạo function mới: **BẮT BUỘC** luôn thêm doc comment.

- Kotlin: dùng KDoc.
- Java: dùng Javadoc.
- Nội dung comment bằng **tiếng Việt**.
- Mô tả rõ chức năng, mục đích và ngữ cảnh sử dụng.
- Chú thích tham số bằng `@param`.
- Chú thích giá trị trả về bằng `@return` nếu function có return value.
- Chú thích exception bằng `@throws` nếu function có thể ném exception.
- Định dạng chuẩn:

```kotlin
/**
 * Mô tả chức năng của function.
 *
 * @param value Mô tả tham số.
 * @return Mô tả giá trị trả về.
 */
```

Không thêm comment hình thức hoặc lặp lại đúng tên function mà không giải thích ý nghĩa.

---

## Quy định String Resources

**Tuyệt đối không hardcode chuỗi hiển thị UI.**

Không viết text giao diện trực tiếp trong:

- Compose UI
- XML layout
- Activity
- Fragment
- ViewModel
- Service
- Utils
- custom view
- adapter
- mapper hiển thị UI

Luôn tạo và sử dụng String Resource trong:

```text
core/designsystem/src/main/res/values/strings.xml
```

### Jetpack Compose

Dùng:

```kotlin
stringResource(R.string.<name>)
```

hoặc:

```kotlin
stringResource(R.string.<name>, formatArgs)
```

### Non-Compose / Context

Dùng:

```kotlin
context.getString(R.string.<name>)
```

hoặc truyền resource id:

```kotlin
@StringRes val textRes: Int
```

### Chuỗi động

Định nghĩa placeholder chuẩn trong XML:

```xml
<string name="example">Xin chào %1$s</string>
```

Sau đó truyền biến qua format args.

Không tự nối chuỗi UI trong Kotlin nếu có thể dùng placeholder trong `strings.xml`.

---

## Development Workflow

Trước khi sửa code:

1. Đọc implementation hiện tại.
2. Search caller/callee liên quan.
3. Xác định impact.
4. Kiểm tra convention hiện có của project.
5. Chọn skill phù hợp theo phần `Skill Routing`.
6. Đưa ra implementation plan ngắn.

Khi sửa code:

- Ưu tiên minimal change.
- Không refactor ngoài scope.
- Không thay architecture nếu không cần thiết.
- Giữ backward compatibility.
- Không đổi behavior business nếu user không yêu cầu.
- Không sửa formatting hàng loạt nếu không cần.
- Không thêm dependency nếu chưa giải thích lý do và impact.

Sau khi sửa:

1. Self-review git diff.
2. Kiểm tra regression.
3. Kiểm tra lifecycle.
4. Kiểm tra coroutine/concurrency.
5. Kiểm tra memory leak.
6. Kiểm tra hardcoded string.
7. Kiểm tra KDoc cho function mới.
8. Chạy test/build phù hợp hoặc nêu rõ command cần chạy.

---

## Review Output Format

Khi review code hoặc màn hình, output bằng tiếng Việt theo format:

```markdown
Tổng quan:
- ...

Skill đã dùng:
- ...

Phạm vi đã kiểm tra:
- ...

Vấn đề tìm thấy:
1. [Severity: HIGH/MEDIUM/LOW/INFO]
   - Evidence:
   - Impact:
   - Suggested fix:

Regression risk:
- ...

Validation nên chạy:
- ...

Kết luận:
- ...
```

Severity dùng theo nguyên tắc:

- `HIGH`: có thể gây crash, sai nghiệp vụ, mất dữ liệu, security issue, regression nghiêm trọng.
- `MEDIUM`: có thể gây lỗi UI/lifecycle/performance đáng kể.
- `LOW`: vấn đề maintainability, readability, edge case nhỏ.
- `INFO`: góp ý cải thiện, không bắt buộc sửa ngay.

---

## Compose Review Checklist

Khi review Compose screen, luôn kiểm tra:

- State hoisting có hợp lý không.
- Có collect Flow lifecycle-aware không.
- `LaunchedEffect` / `DisposableEffect` key có đúng không.
- Có risk recomposition toàn màn hình không.
- Param truyền vào Composable có stable/immutable không.
- List có dùng `key` và `contentType` nếu cần không.
- Modifier order có đúng không.
- Có dùng `remember` / `derivedStateOf` đúng chỗ không.
- Có đọc state ở Composition phase khi có thể defer sang Layout/Draw không.
- Có hardcode text không.
- Có `contentDescription` / semantics / touch target phù hợp không.
- Có loading/empty/error state không.
- Có hỗ trợ dark mode/adaptive/insets nếu màn hình yêu cầu không.

---

## Android Safety Rules

Không tự ý:

- Xóa file hoặc module lớn.
- Đổi package structure hàng loạt.
- Nâng version Gradle/AGP/Kotlin nếu task không yêu cầu.
- Thêm library mới nếu chưa phân tích impact.
- Đổi navigation flow nếu không có yêu cầu.
- Đổi API contract/backend model nếu không có xác nhận.
- Log token, password, OTP, PII hoặc dữ liệu nhạy cảm.
- Hardcode URL/key/secret.
- Bỏ qua lỗi build/test bằng cách comment code hoặc suppress bừa.
