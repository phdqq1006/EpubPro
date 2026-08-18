# TTS Playback Notification Manager

## Understanding

- The Android media card must remain visually stable while TTS advances between sentences and chapters.
- A technical TTS `Preparing` state during continuous playback must not be exposed to SystemUI as media buffering.
- Pause during chapter loading must immediately expose paused controls while allowing the chapter load to finish.
- The media progress bar must use a consistent duration, position, playback state, and playback speed.
- Notification construction and foreground lifecycle management must be separated from `TtsMediaSessionManager`.
- Widget, snapshot, bubble, and TTS engine behavior remain outside this refactor except where they consume the shared playback presentation.

## Assumptions and non-functional requirements

- There is one active `TtsService` and one TTS media notification per app process.
- Notification updates are lightweight and execute on the service main thread.
- No new persistence, network access, permissions, or exported components are introduced.
- Existing notification channel and notification ID remain stable for installed users.
- Foreground promotion failures preserve the existing safe shutdown behavior.
- The manager lifetime is exactly the `TtsService` lifetime and therefore may safely reference the service.

## Selected design

### Naming and responsibilities

Use `TtsPlaybackNotificationManager`. The name is intentionally narrower than `EpubProNotificationManager`, which would imply ownership of every application notification, and more explicit than `TtsNotificationManager`, which could later include voice-model download notifications.

`TtsMediaSessionManager` owns only the media session, metadata, playback state, and transport callbacks. `TtsPlaybackNotificationManager` owns the notification channel, `MediaStyle` notification construction, action `PendingIntent`s, foreground promotion, in-place updates, and removal.

### Foreground update policy

- Start foreground when no foreground episode exists.
- Restart only when explicitly forced or when a foreground-service type must be removed.
- Call `startForeground` again when adding a required service type.
- For ordinary title, text, and play/pause changes with unchanged service types, call `notify` with the existing notification ID.
- Preserve `setOnlyAlertOnce(true)` and the existing low-importance channel.
- Return failures to `TtsService`, which keeps the existing snapshot-and-shutdown recovery path.

### Media control and progress behavior

- Initial playback and snapshot restoration may expose `STATE_BUFFERING`.
- Once playback has started, sentence preparation and automatic chapter loading remain `STATE_PLAYING` with speed `1f`; SystemUI therefore retains the pause control instead of a loading spinner.
- Pause exposes `STATE_PAUSED`, speed `0f`, and the latest position.
- Resume exposes `STATE_PLAYING` without an intermediate buffering state.
- Position continues to be refreshed through `MediaSession` once per second while playing; the notification itself is not rebuilt for progress ticks.
- Duration comes from `MediaMetadata`. At a chapter boundary, the old chapter remains at its final position until the new chapter is ready; then new duration and position are published together before speech starts.
- Do not advertise `ACTION_SEEK_TO` until millisecond-accurate seeking is implemented.

## Alternatives considered

1. Keep foreground management in `TtsService` and extract only the builder. Rejected because repeated promotion and notification lifecycle remain distributed.
2. Name the class `EpubProNotificationManager`. Rejected because its scope would be misleading and encourage unrelated notification responsibilities.
3. Migrate to Media3 `MediaSessionService`. Deferred because adapting the custom TTS engines to a `Player` contract and revalidating every playback integration is disproportionate to this UI defect.

## Test strategy

- Unit-test the pure notification update policy for start, in-place update, add-type update, restart, and forced restart.
- Unit-test media presentation for initial buffering, continuous preparation, chapter loading, pause, and resume semantics.
- Run `:core:reader:testDebugUnitTest` and `:app:assembleDebug`.
- Manually verify on a device that the media card remains present, the center control never spins between sentences, pause does not blink the card, and progress is monotonic within a chapter.

## Decision log

- Chose `TtsPlaybackNotificationManager` to keep ownership specific and maintainable.
- Chose in-place `notify` for ordinary updates to avoid unnecessary foreground promotion and media-card rebinding.
- Chose presentation continuity (`STATE_PLAYING`) over exposing internal engine preparation because the user-visible playback intent remains active.
- Kept initial buffering to communicate genuine startup latency.
- Kept the current media stack; Media3 migration is a separate architectural project rather than a bug fix.
