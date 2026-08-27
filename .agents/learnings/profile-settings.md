# Profile & Settings Screen

> Tổng hợp kiến thức về màn hình Cá nhân & Cài đặt (Profile & Settings), chuyển đổi Stitch sang Compose và tối ưu hiệu năng cuộn trong dự án EpubPro.
> Cập nhật lần cuối: 2026-08-27

---

## Architecture

### Phong cách Data-Focused & Editorial cho màn hình Profile
- **Ngày**: 2026-08-27
- **Chi tiết**: Áp dụng triết lý thiết kế Data-Focused từ Google Stitch cho tab Cá nhân: Nền sáng ấm (`#F8F9FA` / `#FCF9F8`), Avatar tròn 96dp có badge VIP pill ở góc dưới phải, 3 thẻ thống kê ngang (Đã đọc, Chuỗi ngày đọc với nền Terracotta `#D97757` ngọn lửa trắng, Giờ đọc với nền kem `#FFF7F2`) và các khối cài đặt dạng Card phân nhóm (Đồng bộ, Cài đặt ứng dụng, Nâng cao, Tài khoản).
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`, `core/designsystem/src/main/res/values/strings.xml`

### Tối ưu kiến trúc cuộn: Column + verticalScroll cho layout cố định
- **Ngày**: 2026-08-27
- **Chi tiết**: Đối với màn hình cài đặt có số lượng section cố định (~5 khối Card lớn), thay vì dùng `LazyColumn` (gây overhead đo đạc, subcomposition và tái tạo Composable tree mỗi khi cuộn qua lại), sử dụng `Column(modifier = Modifier.verticalScroll(rememberScrollState()))` giúp Compose dựng sẵn toàn bộ layout một lần duy nhất, GPU xử lý chuyển dịch vẽ (draw offset) thuần túy đạt 60fps/120fps mượt mà.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`

---

## Bugs & Solutions

### Hiện tượng vuốt giật/khựng (Scroll Jank) trên màn hình Profile
- **Ngày**: 2026-08-27
- **Vấn đề**: Người dùng thực hiện thao tác vuốt cuộn trên Tab Cá nhân thì giao diện bị khựng, phản hồi không mượt mà.
- **Root cause**: `LazyColumn` với các item chứa cây Composable lồng nhau phức tạp (`ProfileSectionCard` chứa nhiều `ProfileCardItem` và clickable ripple) liên tục bị compose/dispose trên từng frame khi cuộn.
- **Fix**: Chuyển `LazyColumn` sang `Column(Modifier.verticalScroll(rememberScrollState()))` và bọc các phép tính chuỗi (`remember(readHours)`, `remember(user.displayName)`) để tránh re-allocate bộ nhớ.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`

### Avatar hiển thị mờ nhạt và mất tương phản
- **Ngày**: 2026-08-27
- **Vấn đề**: Avatar chữ cái đại diện bị nhạt nhòa, chữ "H" mảnh và vòng tròn gần như chìm vào nền trắng.
- **Root cause**: Background avatar dùng `primaryContainer.copy(alpha = 0.5f)` kết hợp màu nền sáng khiến độ tương phản < 1.5:1.
- **Fix**: Áp dụng màu ấm đào đặc `#FFE8E0`, chữ cái in đậm `FontFamily.Serif` 38sp màu `#D97757`, kết hợp Badge VIP viên thuốc viền trắng `2.5dp` đổ bóng nhẹ `2dp` tại góc `BottomEnd`.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`

---

## How-To

### Quy trình chuyển đổi giao diện Stitch sang Jetpack Compose chuẩn dự án
- **Ngày**: 2026-08-27
- **Bước thực hiện**:
  1. Trích xuất mã màu tokens và HTML/Tailwind config từ Stitch (`bg-stats-bg: #FFF7F2`, `custom-terracotta: #D97757`, `rounded-3xl: 24px`).
  2. Bổ sung 100% chuỗi UI vào `core/designsystem/src/main/res/values/strings.xml`, không hardcode chuỗi trong Kotlin.
  3. Xây dựng các Composable theo slot API, bố trí padding và typography đúng tỷ lệ.
  4. Đảm bảo toàn bộ logic ViewModel/StateFlow không bị thay đổi, giữ backward compatibility.
  5. Chạy `./gradlew :feature:profile:testDebugUnitTest` để xác thực 0 regression.
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`, `core/designsystem/src/main/res/values/strings.xml`

---

## Patterns

### Card Grouping cho Cài đặt & NavigationBarItem Styling
- **Ngày**: 2026-08-27
- **Chi tiết**: Pattern chuẩn cho các nhóm cài đặt bo góc 24dp kèm divider mỏng và thanh điều hướng BottomBar 4 tabs chuẩn màu Material 3.
- **Ví dụ code**:
  ```kotlin
  // Card Container cho nhóm cài đặt
  Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
  ) { ... }

  // Bottom Navigation styling
  NavigationBarItemDefaults.colors(
      selectedIconColor = MaterialTheme.colorScheme.primary,
      selectedTextColor = MaterialTheme.colorScheme.primary,
      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
      unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
      unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
  )
  ```
- **Files liên quan**: `feature/profile/src/main/java/com/epubpro/feature/profile/ProfileScreen.kt`, `app/src/main/java/com/epubpro/app/navigation/AppNavigation.kt`
