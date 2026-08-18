# Book Bible - Character Progression Design

Status: Approved for implementation planning  
Scope: Android integration and additive backend contract extensions  
Target: MVP/beta under 1,000 users

## 1. Summary

Book Bible provides a spoiler-safe character profile for the chapter currently open in either the local EPUB Reader or the online chapter reader. The backend owns character extraction and progression data, while Android keeps user reading progress local and never uploads it.

When a source chapter is opened, Android stores an idempotent submission job and WorkManager sends the stable source text when any network is available. Only chapters actually opened are submitted. The profile can therefore be partial when earlier chapters were skipped; the UI must disclose this state without submitting missing chapters automatically.

The user opens Book Bible from the Reader overflow menu. A dedicated full-screen feature shows the character list, a character profile, and a timeline bounded by the current chapter. Cached snapshots and timelines remain available offline and are refreshed in the background when connectivity exists.

## 2. Confirmed Product Decisions

- Support local EPUB books and online novels.
- Submit only a chapter that the user actually opens.
- Always use the currently open chapter as the anti-spoiler boundary.
- Keep reading progress entirely on-device.
- Enable submission by default without authentication or consent UI for the MVP.
- Run queued submissions on any active network.
- Share editions by normalized title, author, language, and chapter count.
- Submit stable source content, not AI-polished or translated display content.
- Preserve proper names; normalize profile descriptions and attributes to Vietnamese.
- Model data around characters, with standard groups plus an extensible "Other" section.
- Display chapter, change, value, and evidence in timeline events; confidence is logging-only.
- Keep the MVP read-only and accessible only from a Reader.
- Cache data for offline use and apply cache-first/background-refresh behavior.
- Keep backend and Android releases independently deployable and backward compatible.

## 3. Goals and Non-goals

### Goals

- Reader performance and chapter navigation must not wait for Book Bible work.
- Reopening the same source chapter must not create duplicate AI jobs.
- Previously loaded profiles must open immediately from cache and work offline.
- Backend responses must never contain events after the requested chapter.
- Partial chapter coverage must be represented explicitly.

### Non-goals

- Syncing page, CFI, chapter, or other user reading progress.
- Processing unopened chapters or automatically filling chapter gaps.
- Editing, correcting, reporting, or moderating extracted data in Android.
- A global Book Bible library outside the Reader.
- User accounts, device tokens, or authenticated submissions in the MVP.
- Reworking Reader pagination, WebView rendering, chapter transitions, TTS, or AI Vietnamese behavior.

## 4. Module Boundaries

### `domain`

Add pure Kotlin models and a `BookBibleRepository` contract. The contract exposes enqueue, observe snapshot, refresh snapshot, observe submission, retry, and load timeline operations. It uses an explicit 1-based `chapterNumber`; existing local EPUB indices are converted at the Reader boundary.

### `core:database`

Add Room entities and DAOs for source-to-edition mappings, submission state, snapshot cache, and timeline cache. Upgrade the database from version 4 to 5 with an explicit non-destructive migration.

### `core:storage`

Add Retrofit DTOs/service, DTO-domain mappers, the repository implementation, an internal payload-file store, a submission scheduler, and a Hilt-injected `CoroutineWorker`. This module may depend on `core:database`; no Reader module is referenced from the data layer.

### `feature:bookbible`

Add the Compose screens and Hilt ViewModel. The feature depends on `domain`, `core:common`, and `core:designsystem`; it does not parse EPUB or call Retrofit directly.

### Reader integrations

`feature:reader` and `feature:library` enqueue stable source text after a chapter load succeeds. They expose an `onOpenBookBible` callback and do not store backend IDs or snapshot state in Reader UI state.

The online Reader currently defaults to translated content and knows only `novelId` and chapter number. Its integration must fetch novel metadata and the `original` chapter version in a separate background path before enqueueing. This fetch must not replace, delay, or mutate the translated content being displayed. If the original version is unavailable, Android records a retryable source-content failure and does not fall back to the translated display text.

`app` owns the route `book_bible/{sourceType}/{sourceId}/{chapterNumber}` and supplies navigation callbacks.

## 5. Domain Model

Use explicit types instead of `Map<String, Any>` at the domain boundary:

- `BookBibleSource`: `LOCAL_EPUB` or `ONLINE_NOVEL`, plus local source ID.
- `BookBibleChapter`: metadata and 1-based chapter number used by the backend.
- `BookBibleSnapshot`: requested/canonical chapter, status, coverage, revision, and characters.
- `CharacterProfile`: stable ID, original name, last changed chapter, and categorized attributes.
- Standard groups: cultivation realm, techniques, skills, items/equipment, relationships, affiliations, and titles.
- `extraAttributes`: display-safe key/value entries for unknown backend fields.
- `CharacterTimelineEvent`: chapter, category, operation, display value, evidence, and confidence.
- `SubmissionState`: pending, submitting, accepted, processing, completed, retryable failure, or permanent failure.

Retrofit may deserialize extensible JSON using Gson `JsonElement`, but mapping must convert it to display-safe domain values before the UI receives it.

## 6. Backend Contract Extensions

All changes are additive within `/api/v1`.

### Corrections required before Android integration

- Replace `async suspend fun resolveBook` with valid Kotlin `suspend fun resolveBook`.
- Add `candidates` to `BookResolutionResponse` or remove it from the documented response.
- Add `chapter_count` and `mapping_revision` to `EditionRecord`.
- Add `book_id`, `edition_id`, and `local_chapter_index` to the submission DTO when returned by the endpoint.
- Add `canonical_chapter` to `CharacterSnapshotResponse`.
- Make `character_original_name` optional in `CharacterEvent` unless the backend guarantees it; the supplied timeline JSON does not contain this field.
- Replace UI-facing `Map<String, Any>` and `Any?` usage with `JsonElement` DTO fields followed by validated domain mapping.
- Define one backward-compatible error envelope for validation, conflict, rate-limit, payload-too-large, and server failures.

### Edition reuse

`POST /book-bible/books/{book_id}/editions` must behave as an idempotent resolve/create operation for normalized title, author, language, and chapter count. The response adds `status` (`matched` or `new_edition`) and retains `edition_id`, `book_id`, `chapter_count`, and `mapping_revision`.

The acknowledged limitation is that different translations with the same metadata may be merged.

### Submission

Keep `Idempotency-Key`, and add `source_hash` to the request. Android derives a deterministic SHA-256 key from edition identity, chapter number, and source hash. The server must return the existing submission for duplicate keys.

Chapter numbers are 1-based in both the path and request. A mismatch returns `400`. Payloads are UTF-8 plain text with a proposed 2 MiB limit; `413` is a permanent client-visible failure.

### Snapshot coverage

Extend the snapshot response with:

- `snapshot_revision` and `updated_at` for cache validation.
- `snapshot_status`: `complete`, `partial`, `processing`, `empty`, or `failed`.
- `coverage.processed_ranges` and `coverage.missing_ranges`, represented as inclusive start/end pairs.
- `profile` with typed standard groups while retaining legacy `attributes` for compatibility.

`canonical_chapter` must never exceed `requested_chapter`. Timeline endpoints must filter every event to the requested chapter boundary server-side.

### Timeline display values

Add `display_value` as a Vietnamese display string while retaining the existing JSON `value`. Android logs confidence only and never renders the numeric score.

## 7. Submission and Refresh Flow

### Chapter opened

1. The Reader finishes loading the stable source chapter.
2. Local EPUB uses the original normalized chapter HTML already loaded by `EpubEngine`. The online Reader obtains the `original` API version and novel metadata in the background, independently of the displayed version.
3. Off the main thread, Android converts HTML to plain text when needed and computes a SHA-256 source hash.
4. The payload is written atomically under internal `noBackupFilesDir`; the Room row stores only its path and metadata.
5. A unique WorkManager job is enqueued for source, chapter number, and source hash with `NetworkType.CONNECTED`.
6. The worker resolves the backend book and edition if no valid local mapping exists, then submits the chapter.
7. On `200/202/409`, it stores the submission ID/status and deletes the payload file. Network, `408`, `429`, and `5xx` errors use exponential retry. Validation errors and `413` are permanent.

Reader rendering is never blocked, and preloaded adjacent chapters are not submitted until actually opened.

### Book Bible opened

1. The ViewModel observes cached snapshot data and renders it immediately.
2. When online, it requests the current snapshot in the background.
3. A newer revision replaces the cache transactionally.
4. If the snapshot is processing, polling runs only while the screen is active, every 2 seconds for at most 60 seconds.
5. Timeline data is loaded only after a character is selected and the Timeline tab is opened.

## 8. Local Persistence

Room version 5 adds:

- `book_bible_editions`: local source key, backend book/edition IDs, mapping revision, and metadata fingerprint.
- `book_bible_submissions`: chapter/source hash, payload path, submission ID, state, attempts, error code, and timestamps.
- `book_bible_snapshots`: edition/chapter key, revision, status, coverage JSON, payload JSON, byte size, and timestamps.
- `book_bible_timelines`: edition/chapter/character key, payload JSON, byte size, and timestamps.

Snapshot and timeline JSON are cached because the backend schema is extensible. The repository remains responsible for converting JSON into typed domain models.

Cache entries are retained until the source is deleted, with a 50 MiB global soft limit. Oldest-accessed snapshot/timeline entries are pruned after successful writes. Pending payload files have the same 50 MiB soft limit; the app records an explicit local failure rather than silently deleting an unsent chapter.

Deleting a local EPUB removes its mappings, payloads, snapshots, timelines, and unique work. Online cache is pruned by the global limit.

## 9. UI and Navigation

The local Reader replaces the direct display-settings action with an overflow menu containing:

- Book Bible profile.
- Display settings.

Other existing Reader actions remain unchanged. The online Reader receives the same Book Bible overflow entry.

The Book Bible feature uses two full-screen states under one navigation destination:

- Character list: current chapter, snapshot/coverage state, and list items sorted by changed-in-current-chapter first, then name.
- Character detail: Profile and Timeline tabs. Profile presents standard sections followed by Other; Timeline lists bounded events with evidence.

The selected character ID and selected tab are stored in `SavedStateHandle`, so process recreation restores the detail state. Back returns from detail to the character list, then to the Reader.

Required UI states are cached data with background refresh, first load, processing with automatic update, partial coverage, empty snapshot, offline without cache, retryable failure, permanent payload failure, and normal content. All display text belongs in `core/designsystem/src/main/res/values/strings.xml`.

## 10. Reliability, Security, and Privacy

- Use unique work and deterministic idempotency keys to prevent duplicate submissions.
- Guard database replacement by source, chapter, edition, and revision so stale responses cannot overwrite newer data.
- Cancel ViewModel polling when the screen stops; backend processing continues independently.
- Never include source text, evidence, Retrofit bodies, or full snapshot JSON in logs or crash reports.
- Keep OkHttp logging at BASIC or NONE for release builds.
- Production uses HTTPS; cleartext HTTP is limited to explicitly configured local development.
- Validate response sizes and field lengths before mapping to UI models.
- Backend should still apply IP-based rate limiting, payload limits, content hashing, and input validation despite having no authentication.

Accepted MVP risks:

- Automatic upload has privacy and copyright implications and no consent gate.
- Anonymous shared submissions can be spammed or poisoned.
- Metadata-only edition matching can merge incompatible translations.
- Skipped chapters produce incomplete progression data.

These risks must be documented in release notes and revisited before public scale exceeds the MVP target.

## 11. Test Strategy

- Domain unit tests: chapter-number conversion, status mapping, coverage ranges, typed/extra attributes, and display value fallback.
- Database tests: migration 4-to-5, DAO uniqueness, transactional revision replacement, pruning, and cascade cleanup behavior.
- Storage tests: DTO mapping, edition reuse, deterministic idempotency, duplicate enqueue, transient/permanent HTTP handling, payload cleanup, and response size limits.
- WorkManager tests: connected-network constraint, retry/backoff, process recreation, and unique-work replacement rules.
- ViewModel tests: cache-first rendering, refresh, partial/processing/empty/error states, polling cancellation, timeline lazy load, and stale response rejection.
- Navigation tests: URL encoding and exact 1-based conversion for both Reader types.
- Regression tests: existing EPUB parser, renderer, playback, Reader transition, and app assembly suites.
- Manual device tests: local EPUB and online novel, offline queue/reconnect, cache-only profile, skipped chapters, process death, duplicate reopen, and Reader forward/back chapter transitions.

## 12. Decision Log

| ID | Decision | Alternatives considered | Reason |
|---|---|---|---|
| D-01 | Add `feature:bookbible`; reuse existing domain/storage/database modules | New `core:bookbible`; embed in Reader | Preserves Reader boundaries without overbuilding MVP infrastructure |
| D-02 | Support both local and online Readers | Local-only; online-only | Product scope requires one consistent profile experience |
| D-03 | Submit opened chapters only | Backfill earlier chapters; manual range sync | Controls network and AI cost |
| D-04 | Use current chapter as spoiler boundary | Furthest-read chapter; selectable chapter | Matches Reader context and avoids accidental spoilers |
| D-05 | Use metadata and chapter count for edition sharing | Content fingerprint; per-device edition | Simpler MVP, with collision risk accepted |
| D-06 | Use stable source content | Displayed/translated content | Keeps submissions reproducible across display modes |
| D-07 | Cache first, refresh in background | Network-first; manual refresh | Meets offline and 1-2 second response goals |
| D-08 | Queue with WorkManager on any network | Retry on next open; Wi-Fi only | Reliability and low-friction operation were prioritized |
| D-09 | No authentication and always enabled | Device token; user account; per-book consent | Fastest MVP, with security/privacy risks explicitly accepted |
| D-10 | Read-only UI with standard groups plus Other | Fixed schema; fully dynamic UI | Keeps UI coherent while allowing additive backend fields |

## 13. Completion Criteria

- Both Readers enqueue only the stable source chapter that was actually opened.
- Reader rendering and chapter transitions behave exactly as before when backend work succeeds, fails, or is offline.
- Reopening identical content produces at most one backend submission.
- Book Bible never displays data after the requested chapter.
- Cached snapshots and timelines remain usable without network.
- Partial coverage, processing, empty, and failure states are distinguishable.
- Database upgrades preserve existing books, reading progress, bookmarks, highlights, AI rules, and AI chapter caches.
- New unit/integration tests and the existing Reader regression suite pass.
