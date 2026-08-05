# Page Turn State Machine Fix

## Scope

- Preserve the existing WebView multi-column and dual-overlay implementation.
- Fix invalid page state at chapter boundaries.
- Ensure canceled gestures always restore the native document.
- Measure and clamp pagination before restoring a saved page.
- Keep readingMode and isHorizontalPagination consistent at default and in-reader controls.

## Assumptions

- FLIP and SCROLL_HORIZONTAL continue to use horizontal pagination for now.
- SCROLL and CONTINUOUS continue to use vertical rendering for now.
- Keep at most the previous, current, and next chapter HTML in memory.

## Decision Log

1. Keep the current dual-overlay animation instead of replacing the reader engine.
   This minimizes behavioral and visual changes while fixing broken transitions.
2. Handle chapter boundaries before normal page commits.
   Page indexes must remain within 1..totalPages; chapter navigation is separate.
3. Use a gesture token to invalidate delayed callbacks from older animations.
   A new or canceled gesture must not let an old timeout mutate the current page.
4. Keep readingMode and isHorizontalPagination synchronized for compatibility.
   A migration to one persisted field can be done separately.
5. Replace nested BODY clones with valid DIV pagination layers.
   Android WebView may reflow invalid nested BODY elements and leak unrelated columns.
6. Preload only the adjacent chapters for boundary previews.
   The first page of the next chapter and last page of the previous chapter are shown
   while dragging; chapter state changes only after the gesture commits.
7. Keep the boundary preview visible while the selected chapter is loaded.
   A timed cleanup remains as a recovery path if loading fails.

## Verification

- Compile the Android app.
- On device, test short and long chapters for next/previous page, chapter boundaries,
  canceled gestures, restored progress, and vertical/horizontal mode switching.