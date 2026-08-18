# TTS Audio Service Lifecycle Design

> Cập nhật: 2026-08-09
> Trạng thái: Đã xác nhận

## Tóm tắt hiểu biết

- TTS tiếp tục khi tắt màn hình, bấm Home, chuyển màn hình hoặc vuốt Recent Apps.
- Chỉ nút Stop trong app hoặc notification mới kết thúc phiên.
- Pause/Resume tiếp tục gần đúng từ câu đang dở cho Native và AI Offline.
- Mất audio focus hoặc rút tai nghe sẽ Pause; chỉ tự Resume nếu hệ thống đã Pause.
- Đổi engine, giọng, ngôn ngữ hoặc tốc độ sẽ tiếp tục từ câu hiện tại.
- AI giữ tiến trình đứng yên trong Preparing và chỉ chạy khi PCM bắt đầu phát.
- Service tự đọc chương tiếp theo; ưu tiên cache AI, fallback EPUB gốc.
- Không tự phát lại sau process death hoặc reboot.

## Giả định và phạm vi

- Một phiên TTS hoạt động tại một thời điểm.
- Notification cập nhật tối đa mỗi giây khi Playing, không polling khi Paused/Preparing.
- Synthesis, tải chương và xử lý PCM chạy ngoài main thread.
- Service giữ exported=false và PendingIntent immutable.
- Resume theo ranh giới câu, không chính xác theo sample/millisecond.
- Không tự sinh nội dung AI online trong background.
- Không hỗ trợ nhiều phiên, khôi phục sau reboot hoặc seek chính xác trên notification.

## Phương án được chọn

Gia cố kiến trúc hiện tại thay vì chuyển Media3 hoặc sinh audio thành file. Giữ TtsService, AndroidNativeTtsEngine và Piper/Sherpa; bổ sung started foreground lifecycle, sentence cursor, audio focus controller, engine phase callbacks và chapter coordinator.

## Thiết kế vòng đời Service

TtsService hoạt động theo mô hình started + bound. Binding là kênh điều khiển của UI; started state sở hữu vòng đời playback. Khi bắt đầu phiên, service chuyển sang started state và gọi startForeground ngay với notification Preparing. Unbind không dừng phiên. onTaskRemoved không dừng playback. Service dùng START_NOT_STICKY để không tự khởi động sau process death.

Mọi đường Stop từ app, notification và MediaSession đi qua stopSession(): hủy engine và coroutine, bỏ audio focus, unregister noisy receiver, xóa phiên, gỡ foreground notification và gọi stopSelf. Pause không kết thúc service. onDestroy release toàn bộ Native TTS, Sherpa, AudioTrack và MediaSession.

## State machine và Resume

Mỗi TtsChunk được chia thành câu bởi TtsSentenceSegmenter. Service giữ chunkIndex, sentenceIndex và playbackGeneration. Pause hủy câu hiện tại nhưng giữ con trỏ; Resume đọc lại từ đầu câu đang dở.

Engine phát các phase Preparing, AudioStarted, Completed và Error. Native phát AudioStarted từ UtteranceProgressListener.onStart. AI chỉ phát AudioStarted sau khi synthesize PCM hoàn tất và AudioTrack bắt đầu nhận dữ liệu. Callback luôn được kiểm tra generation và cursor.

Khi đổi cấu hình, service lưu cursor, invalidate generation, hủy engine cũ, áp dụng cấu hình mới và phát lại câu hiện tại nếu phiên trước đó đang Playing/Preparing. Nếu đang Paused thì giữ Paused.

## Audio focus và notification

TtsAudioFocusController xin AUDIOFOCUS_GAIN với USAGE_MEDIA và CONTENT_TYPE_SPEECH. Mất focus hoặc ACTION_AUDIO_BECOMING_NOISY gây system pause. Audio chỉ tự Resume sau AUDIOFOCUS_GAIN khi pausedBySystem=true; user pause không auto-resume. Stop bỏ focus và receiver.

Notification ánh xạ trực tiếp Preparing, Playing, Paused, Completed và Error. Preparing hiển thị “Đang chuẩn bị giọng…”, position đứng yên. Playing đồng bộ mỗi giây. Notification có Previous, Play/Pause, Next và Stop; Stop gọi stopSession. Notification là ongoing trong phiên hoạt động.

## Tự chuyển chương

TtsChapterPlaybackCoordinator tải sách và chương độc lập với ReaderViewModel bằng BookRepository, EpubStorageManager và EpubEngine. Khi hết chương, service lấy chương kế tiếp, parse câu và tiếp tục. Nếu phiên dùng nội dung AI, coordinator ưu tiên cache AI hợp lệ và fallback HTML EPUB gốc; không gọi xử lý AI online trong background.

Hết sách chuyển Completed, bỏ focus và kết thúc foreground service. Lỗi tải chương tiếp theo giữ phiên ở trạng thái Paused/Error tại đầu chương để người dùng có thể Play/Retry.

## Xử lý lỗi và độ tin cậy

- Không lấy được audio focus: không synthesize và chuyển Paused/Error.
- Model AI thiếu hoặc initialize lỗi: thoát Preparing và giữ phiên để đổi giọng.
- Stop/Pause/đổi cấu hình khi synthesize: hủy job và vô hiệu callback bằng generation.
- Play/Pause/Stop lặp lại phải idempotent.
- Không ghi toàn bộ nội dung sách vào log.

## Chiến lược kiểm thử

- Unit test sentence segmentation, cursor, generation và auto-next chapter.
- Coroutine test Pause, Stop và đổi engine trong Preparing/Playing.
- Test audio focus: system pause được auto-resume, user pause thì không.
- Test nội dung AI cached và fallback EPUB gốc.
- Instrumentation test started + bound service, vuốt task, notification controls và headphone unplug.
- Smoke test Native và AI Offline trên thiết bị thật.

## Nhật ký quyết định

| Quyết định | Phương án thay thế | Lý do chọn |
|---|---|---|
| Started + bound TtsService | Bound-only; Media3 migration | Giữ kiến trúc hiện tại nhưng playback độc lập UI |
| Tiếp tục sau vuốt Recent Apps | Dừng; tự restart sau process death | Đúng kỳ vọng nghe nền nhưng tránh tự phát ngoài ý muốn |
| Resume theo câu | Đầu đoạn; sample-accurate | Ổn định chung cho Native và AI với độ phức tạp hợp lý |
| System pause có auto-resume | Luôn duck; không auto-resume | Hành vi media chuẩn và tôn trọng user pause |
| Preparing tách AudioStarted | Playing ngay khi synthesize | Notification phản ánh đúng audio thực tế |
| Service tự chuyển chương | ViewModel điều phối; dừng cuối chương | Tiếp tục nghe khi UI/task không còn |
| AI cache rồi fallback gốc | Chỉ AI; luôn gốc | Duy trì liên tục mà không chạy AI online nền |
| Gia cố kiến trúc hiện tại | Media3; audio file + ExoPlayer | Ít regression và đáp ứng đủ yêu cầu |
