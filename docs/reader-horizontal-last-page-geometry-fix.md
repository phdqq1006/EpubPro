# Reader horizontal last-page geometry fix

## Problem

In horizontal reading mode, the final page of a chapter can flash and then keep
an incorrect horizontal margin after a successful page transition. The issue is
reproducible both when moving from the penultimate page to the final page in the
same chapter and when moving backward from the first page of chapter N to the
final page of chapter N-1.

Device diagnostics showed a 384 px viewport with 24 px left and 12 px right
margins. For an eight-page chapter, the expected final offset was 2688 px, but
the WebView stopped at 2676 px. The 12 px shortfall exactly matched the right
margin.

## Root cause

The document body currently has two conflicting responsibilities:

1. It is the one-viewport-wide CSS multi-column layout container.
2. Its `min-width` is expanded to `totalPages * viewportWidth` to synthesize the
   native horizontal scroll range.

Expanding the body changes the real document geometry. The swipe overlay remains
one viewport wide, so the overlay and the committed WebView content do not share
the same layout. When the overlay is removed, the WebView reveals the clamped
final position and the margin visibly changes.

## Decision

Keep the body permanently one viewport wide and remove the synthetic body
`min-width`. Create a dedicated, inert scroll-extent element outside the body
column layout. It is responsible only for making the document scroll range equal
to `totalPages * viewportWidth`.

All reader layers use the same geometry:

- `contentWidth = viewportWidth - marginLeft - marginRight`
- `columnWidth = contentWidth`
- `columnGap = marginLeft + marginRight`
- `pageOffset = pageIndex * viewportWidth`

The scroll extent must not contain content, receive pointer events, participate
in accessibility, or influence page measurement.

## Lifecycle

For every initial load or repagination:

1. Apply the canonical one-viewport body geometry.
2. Exclude the scroll extent from page measurement.
3. Measure the stable natural multi-column width and total page count.
4. Create or update the dedicated scroll extent.
5. Restore the requested page using the canonical page offset.
6. Publish page metrics to Android only after the stable layout is ready.

After a successful swipe, remove the overlay only after the real document has
reached the target offset. A retry on a following animation frame is allowed;
changing margins or applying a last-page-specific compensation is not.

## Rejected alternatives

- Adding the right margin to the last-page target or body width: this treats the
  symptom and is sensitive to asymmetric margins, orientation, and WebView
  behavior.
- Reparenting all EPUB content into a new pagination wrapper: deterministic, but
  unnecessarily broad and carries higher compatibility risk for selectors,
  selection, highlighting, and TTS.

## Acceptance criteria

- No horizontal-margin flash when the final page becomes committed content.
- Final `scrollX` equals `(totalPages - 1) * viewportWidth`.
- Overlay and committed content have the same text position and edge spacing.
- The fix covers internal final-page navigation, previous-chapter navigation,
  direct restore, repeated boundary swipes, asymmetric margins, display setting
  changes, and orientation changes.
- Vertical mode, selection, highlighting, and TTS behavior remain unchanged.
- Transient pre-layout page counts are not published to Android.

