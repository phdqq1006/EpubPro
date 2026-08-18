# Reader Feature Instructions

These instructions apply to every file under `feature/reader`.

## Required context

Before changing horizontal pagination, chapter navigation, `EpubProWebView`,
`ReaderJsBridge`, or reader HTML/CSS/JavaScript, read:

- `docs/reader-horizontal-last-page-geometry-fix.md`
- `docs/reader-chapter-transition-snapshot-design.md`

## Chapter-transition regression contract

The horizontal chapter-boundary flow must preserve all of these invariants:

1. Capture the completed adjacent-chapter preview with `PixelCopy` from the
   composited Window pixels for the WebView bounds.
2. Never replace that capture with `WebView.draw(Canvas)`, `capturePicture()`, or
   a fixed theme-color/loading cover. Hardware-composited WebView layers can be
   absent from software captures and produce a visible content-less frame.
3. Schedule the bitmap cover in Compose before invoking the ViewModel callback
   that replaces the chapter HTML.
4. Give every HTML document a monotonic load generation. A stale document must
   never clear a newer cover.
5. Clear the cover only in this order:
   `onReaderLayoutReady(generation)` -> `postVisualStateCallback()` ->
   `postOnAnimation()` -> matching-generation completion -> cover removal.
6. Keep the timeout, duplicate-request guard, WebView identity check, bitmap
   allocation failure path, and WebView/bridge disposal cleanup.
7. Keep previous- and next-chapter transitions symmetrical. Returning to the
   previous chapter must still settle its canonical last-page offset.

Do not solve transition flashing with an arbitrary delay. A delay does not prove
that the destination visual state is committed and behaves differently across
devices and chapter sizes.

## Required verification

After a relevant change, run at minimum:

```text
./gradlew :core:epub:testDebugUnitTest :core:reader-renderer:testDebugUnitTest :core:playback:testDebugUnitTest :feature:reader:testDebugUnitTest :app:assembleDebug
```

Also verify on a real device in both directions:

- last page of chapter N -> first page of chapter N + 1;
- first page of chapter N + 1 -> last page of chapter N;
- no blank/theme-only frame between preview and destination;
- final-page offset remains canonical;
- no timeout, duplicate load, crash, or stale-generation cover removal.

`ReaderChapterTransitionContractTest` is an intentional source-level architecture
guard. If it fails after a legitimate refactor, update the test only after proving
that every invariant above is still enforced by the new implementation.
