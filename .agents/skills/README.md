# Senior Android Codex Skills

Bộ skill dành cho Codex/AI coding agent khi làm việc với dự án Android production.

## Mục tiêu

- Code theo tư duy Senior Android Engineer.
- Ưu tiên correctness, maintainability và regression safety.
- Không refactor lan man ngoài scope.
- Phân tích impact trước khi sửa.
- Tôn trọng architecture và convention hiện có của project.
- Kiểm tra lifecycle, coroutine, concurrency, security, compatibility và data migration.
- Self-review diff sau khi implement.

## Cấu trúc

```text
senior-android-codex-skills/
├── senior-android/
│   ├── SKILL.md
│   ├── architecture.md
│   ├── kotlin.md
│   ├── android.md
│   ├── coroutine-flow.md
│   ├── compose.md
│   ├── security.md
│   └── performance.md
├── android-code-review/
│   └── SKILL.md
├── android-bug-investigation/
│   └── SKILL.md
└── android-testing/
    └── SKILL.md
```

## Cách dùng

Copy các thư mục skill vào vị trí Codex đọc skills của workspace hoặc repository.

Có thể gọi theo mục đích:

- Phát triển feature: `senior-android`
- Review MR/diff: `android-code-review`
- Điều tra regression/bug: `android-bug-investigation`
- Xây test plan/test code: `android-testing`

## Khuyến nghị

Nên bổ sung thêm một file `PROJECT_RULES.md` ở repository chứa convention riêng của dự án:

- Module graph.
- BaseActivity/BaseFragment/BaseViewModel.
- MVI/MVVM conventions.
- Navigation.
- Network layer.
- Local storage.
- Cache.
- Analytics.
- Error handling.
- Naming.
- Các API nội bộ không được tự ý thay đổi.

Skill `senior-android` được thiết kế để ưu tiên project convention hơn textbook architecture.
