# Reader chapter-transition snapshot design

## Problem

Horizontal page turns already preview the first page of the next chapter or the
last page of the previous chapter inside the current WebView document. When the
gesture commits, Android changes the chapter and `loadDataWithBaseURL()` replaces
that entire document. The preview overlays are destroyed before the destination
document has parsed its HTML, applied reader CSS, measured pages, and restored its
target offset. The exposed intermediate WebView frame produces a visible flash in
both chapter-transition directions.

This is separate from final-page geometry. Device verification shows the final
page reaches its canonical offset after layout.

## Goals

- Preserve the exact final preview frame while the destination chapter reloads.
- Remove the cover only after the destination document has reached its target
  page and rendered a stable frame.
- Handle next- and previous-chapter transitions symmetrically.
- Reject stale readiness callbacks from an older WebView document.
- Bound bitmap lifetime and prevent a failed load from freezing the reader.

## Non-goals

- Replacing the existing horizontal page-turn animation.
- Changing pagination, margins, chapter content, TTS, or selection behavior.
- Adding snapshots to page turns inside a chapter.
- Building a generic design-system WebView widget.

## Decision

Extract the existing reader WebView composable into
`feature/reader/webview/EpubProWebView.kt`. It remains a reader-specific Compose
wrapper and owns the Android WebView, JavaScript bridge integration, HTML loading,
chapter-transition snapshot, and cleanup. `ReaderScreen` supplies content,
settings, initial position, and ViewModel callbacks only. `CssInjector` continues
to own CSS and pagination JavaScript.

At a committed chapter boundary:

1. Receive the JavaScript chapter request.
2. Post to the WebView/main thread.
3. Capture the currently composited Window pixels for the WebView bounds with
   `PixelCopy`, which preserves the hardware-rendered adjacent-chapter preview.
4. Display that bitmap above the WebView in the Compose wrapper.
5. After the cover is scheduled for composition, invoke the ViewModel chapter
   callback and reload the WebView underneath it.
6. The destination JavaScript applies layout, measures pages, settles the target
   offset, and reports `onReaderLayoutReady(loadGeneration)`.
7. If the generation matches the pending destination, call
   `WebView.postVisualStateCallback()` so DOM readiness is upgraded to a WebView
   compositor-commit barrier.
8. After the committed visual state reaches the following animation frame,
   complete the generation and remove the bitmap cover.

## Ownership and threading

- `ReaderScreen -> EpubProWebView -> WebView / ReaderJsBridge / CssInjector`.
- Every `@JavascriptInterface` transition callback is marshalled through
  `webView.post` before touching a View, Compose state, or ViewModel callback.
- Every HTML load receives a monotonically increasing `loadGeneration`, embedded
  in its JavaScript. Readiness from another generation cannot clear the cover.
- JavaScript layout readiness alone never removes the cover. The matching WebView
  must also confirm that its visual state is ready to draw.
- Only one `ARGB_8888` bitmap may be retained at a time.
- Do not use `WebView.draw(Canvas)` for the transition cover. Hardware-composited
  fixed/transform layers can be missing from that software draw, producing a
  content-less bitmap even though the preview is visible on screen.
- The bitmap is removed from Compose state before its storage is released on a
  later frame, so the renderer never observes a recycled bitmap.
- Disposal, a superseding transition, and a roughly 2.5-second load timeout all
  clear pending state. Timeout falls back to the real WebView and logs the reason
  instead of leaving a frozen reader.

## Alternatives considered

### Two alternating WebViews

Preloading the adjacent document in a second WebView can be seamless, but doubles
the expensive WebView surface and substantially complicates bridge ownership,
TTS, selection, lifecycle, settings synchronization, and state restoration.

### Theme-color loading cover

A solid background is simpler but merely changes a content flash into a color
flash and does not preserve visual continuity.

### Keep the DOM overlay alive

The overlay belongs to the document being replaced, so it cannot survive
`loadDataWithBaseURL()` on the same WebView.

## Verification

- Unit-test bridge delivery of `loadGeneration`.
- Test that stale readiness cannot clear a newer transition cover.
- Test cleanup on superseding transition, disposal, and timeout.
- Assert JavaScript emits readiness only after canonical page settling.
- Verify capture precedes ViewModel navigation in both directions.
- Run all `core:reader` and `feature:reader` tests plus `:app:assembleDebug`.
- On device, repeatedly verify final-page to next-chapter-first-page and reverse
  transitions, rapid repeated transitions, asymmetric margins, theme/settings
  changes, and visible/hidden reader controls.

## Regression contract

The chapter-transition capture and handoff are protected by three layers:

1. `feature/reader/AGENTS.md` gives future coding agents the scoped invariants
   before they edit reader code.
2. `ReaderChapterTransitionContractTest` fails if software WebView capture returns
   or if cover removal moves ahead of the visual-state barriers.
3. This document records the platform cause, required ordering, alternatives, and
   device verification matrix.

The source-level contract test is intentional. A local JVM test cannot reproduce
Android WebView compositor behavior, while a full visual instrumentation test is
too device-dependent to be a reliable unit-test gate. Legitimate refactors may
update the test only when they preserve the same observable guarantees.

## Decision log

### 2026-08-13: Protect composited transition capture

- **Evidence:** the original device recording contained eight consecutive
  content-less frames, about 270 ms, between a visible adjacent-chapter preview
  and the rendered destination chapter. A post-fix recording contained zero
  blank-like frames across 220 analyzed frames and two boundary directions.
- **Root cause:** `WebView.draw(Canvas)` performed a software draw that omitted
  hardware-composited fixed/transform preview layers.
- **Decision:** keep `PixelCopy` plus generation-aware DOM and visual-state
  barriers as a reader architecture invariant.
- **Rejected:** documentation alone, arbitrary delays, theme-color covers, and a
  custom Android Lint module. Documentation alone is not enforceable; delays and
  color covers do not preserve continuity; custom Lint is disproportionate for
  this local platform invariant.
