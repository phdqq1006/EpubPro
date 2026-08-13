# Core Reader Instructions

These instructions apply to every file under `core/reader`.

Before changing `CssInjector`, `ReaderJsBridge`, horizontal pagination,
chapter-boundary gestures, layout readiness, or page-offset settling, also read
and preserve the regression contract in:

- `feature/reader/AGENTS.md`
- `docs/reader-horizontal-last-page-geometry-fix.md`
- `docs/reader-chapter-transition-snapshot-design.md`

Changes in this module can invalidate the `EpubProWebView` generation and visual
handoff even when no `feature/reader` file changes. Run both module test suites
and `:app:assembleDebug` after any related change.
