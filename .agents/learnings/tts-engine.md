# Android Text-to-Speech & Background Media Architecture

> Tổng hợp kiến thức về hệ thống TTS Engine, Sherpa-ONNX Offline AI Voice, Foreground Service, MediaSessionCompat và đồng bộ Highlight trong WebView EPUB Reader.
> Cập nhật lần cuối: 2026-08-07

---

## Architecture

### Clean & Modular TTS Engine Abstraction Layer
- **Ngày**: 2026-07-27
- **Chi tiết**: Tách biệt hợp đồng phát giọng đọc qua interface `TtsEngine`. Phase 1 bọc Android System `TextToSpeech` (`AndroidNativeTtsEngine`), sẵn sàng gắn thêm Online AI Voice adapter ở Phase 2 mà không làm thay đổi UI hay ViewModels.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsEngine.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/AndroidNativeTtsEngine.kt`

### Foreground Service with MediaSession & Audio Focus
- **Ngày**: 2026-07-27
- **Chi tiết**: Sử dụng `ForegroundService` kết hợp `MediaSessionCompat` và `NotificationCompat.MediaStyle` để âm thanh phát liên tục khi tắt màn hình hoặc chuyển app. Tự động nhận diện tai nghe Bluetooth, xử lý `AudioFocusRequest` dừng TTS khi có cuộc gọi đến.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsMediaSessionManager.kt`

### HTML Paragraph Chunking & WebView Highlight Sync
- **Ngày**: 2026-07-27
- **Chi tiết**: `TtsTextParser` phân tách mã HTML của chương thành danh sách `TtsChunk` theo chỉ số đoạn (`paragraphIndex`). Khi TTS phát đến đâu, gửi event qua WebView JS Bridge `window.epubproHighlightTtsParagraph(index)` để thêm CSS class `.tts-active-paragraph` tô sáng và gọi `scrollIntoView({ behavior: 'smooth', block: 'center' })`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderScreen.kt`

### Sherpa-ONNX Local AI TTS Engine Integration & Offline Model Download
- **Ngày**: 2026-07-31
- **Chi tiết**: Tích hợp Sherpa-ONNX qua local `.aar` (`sherpa-onnx-1.13.4.aar`). Tải giọng đọc Tiếng Việt offline (Ngọc Ngạn `ngocngan3701.onnx`, Quang Minh `minhquang.onnx`) và `tokens.txt` trực tiếp từ HuggingFace `doof-ferb/nghitts-copy`. Khởi tạo `OfflineTts` với `assetManager = null` để load trực tiếp file từ bộ nhớ máy (`newFromFile`).
- **Files liên quan**: `core/tts/build.gradle.kts`, `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`, `core/tts/src/main/java/com/epubpro/core/tts/VoiceModelDownloader.kt`

### TTS Engine Ownership & Async Readiness
- **Ngày**: 2026-08-06
- **Chi tiết**: `TtsService` sở hữu trạng thái phát, index và vòng đời engine của Reader; màn Settings preview phải dùng instance Sherpa riêng để `release()` không phá engine đang đọc. Wrapper của Android TTS và Sherpa giữ yêu cầu phát tạm thời trong lúc khởi tạo, rồi phát sau callback ready để lần bấm Play đầu tiên không bị mất. Native `OfflineTts.generate()` là lời gọi blocking và không hủy giữa chừng, nên mọi lần generate trên cùng engine phải được tuần tự hóa bằng `Mutex`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/PiperTtsEngineWrapper.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/AndroidNativeTtsEngine.kt`, `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`

### HtmlNormalizer Auto-Wrapping Raw Text into <p> Tags
- **Ngày**: 2026-08-07
- **Chi tiết**: Khi nạp chương EPUB thiếu thẻ đoạn văn (sách convert từ web/TXT qua `GetTextFromHtml` không có `<p>`), `HtmlNormalizer` tự động phân tách văn bản nối bởi `<br>` hoặc dấu xuống dòng thành các thẻ `<p>` riêng biệt. Giúp cả WebView hiển thị đẹp, TTS chia đoạn mượt và Highlight tô sáng đúng từng dòng thay vì tô sáng cả trang `<body>`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/HtmlNormalizer.kt`, `core/reader/src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt`

---

## Bugs & Solutions

### Highlight TTS bị lệch đoạn văn so với âm thanh đang đọc
- **Ngày**: 2026-07-31
- **Vấn đề**: Hiệu ứng tô màu đoạn văn (highlight) nhảy sai dòng so với âm thanh.
- **Root cause**: Android dùng Regex tự tách các tag (bắt nhầm cả `<div>`, `<section>`). Còn JS WebView dùng `querySelectorAll` chỉ chọn `p, h1..h6, li, blockquote`. Sự chênh lệch số lượng Element làm `paragraphIndex` giữa Android và JS không khớp 1:1.
- **Fix**: Tích hợp `Jsoup` vào Android. Gọi `document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")` hệt như JS để đảm bảo số thứ tự index luôn chính xác tuyệt đối ở 2 môi trường.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`

### Lỗi mất chữ và sai lệch vị trí đọc TTS khi HTML chứa mã hóa Hex/Dec Unicode Entity
- **Ngày**: 2026-07-31
- **Vấn đề**: Các từ chứa Unicode entity (như `L&#xFD; M&#x1ED9;c &#x110;i&#x1EC1;n`) hiển thị nguyên chuỗi mã hóa thô trong trình phát TTS thay vì tiếng Việt chuẩn có dấu (`Lý Mộc Điền`).
- **Root cause**: Hàm strip HTML thô trước đây không decode mã Hex (`&#x...;`) và Dec (`&#...;`) Unicode point trước khi gửi sang TTS engine.
- **Fix**: Sử dụng `Jsoup.parse(htmlContent)` bóc tách văn bản qua `document.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")`. Jsoup tự động decode toàn bộ HTML entities thành tiếng Việt chuẩn và đảm bảo chỉ số `paragraphIndex` trùng khớp 100% với `querySelectorAll` trong JavaScript.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`

### Lỗi Unresolved reference / Builder Pattern không hợp lệ trên Sherpa-ONNX Java/Kotlin Bindings
- **Ngày**: 2026-07-31
- **Vấn đề**: `SherpaTtsEngine.kt` gặp lỗi compile khi dùng Builder Pattern (`OfflineTtsVitsModelConfig.builder()`) và gọi `sampleRate` như một property.
- **Root cause**: Trong `sherpa-onnx-1.13.4.aar`, các class config (`OfflineTtsVitsModelConfig`, `OfflineTtsModelConfig`, `OfflineTtsConfig`) là Kotlin data classes sử dụng constructor mặc định + property setters (hoặc constructor đầy đủ), không cung cấp Builder pattern. Đồng thời `sampleRate()` là phương thức (function).
- **Fix**: Khởi tạo các class config bằng no-arg constructor và gán thuộc tính (`vitsConfig.model = ...`, `vitsConfig.tokens = ...`), gọi `sampleRate()` dưới dạng hàm.
- **Files liên quan**: `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`

### Lỗi Unresolved reference: getJsonPath khi chuyển từ Piper sang Sherpa-ONNX
- **Ngày**: 2026-07-31
- **Vấn đề**: `PiperTtsEngineWrapper.kt` gặp lỗi compile `Unresolved reference: getJsonPath`.
- **Root cause**: `VoiceModelDownloader.kt` đã đổi tên và cấu trúc file từ `.json` (Piper) sang `tokens.txt` (`getTokensPath`) cho phù hợp với Sherpa-ONNX.
- **Fix**: Cập nhật `PiperTtsEngineWrapper.kt` đổi `getJsonPath()` thành `getTokensPath()` và truyền tham số dạng named parameters cho `SherpaTtsEngine.initialize(onnxPath = ..., tokensPath = ...)`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/PiperTtsEngineWrapper.kt`, `core/tts/src/main/java/com/epubpro/core/tts/VoiceModelDownloader.kt`

### Lỗi Failed to set eSpeak-ng voice khi gọi OfflineTts.generate()
- **Ngày**: 2026-08-03
- **Vấn đề**: Khi bấm Nghe thử, C++ nổ lỗi `Failed to set eSpeak-ng voice` và văng app (SIGABRT).
- **Root cause**: Thư viện `espeak-ng` tìm kiếm file voice định nghĩa ngôn ngữ Tiếng Việt theo nhiều đường dẫn khác nhau như `lang/aav/vi`, `voices/vi`, và `voices/!v/vi`. Nếu thiếu bất kỳ đường dẫn nào trong số này, `espeak_SetVoiceByName("vi")` sẽ thất bại và quăng ra `std::runtime_error`.
- **Fix**: Trong `VoiceModelDownloader.kt`, sau khi tải `lang/aav/vi`, tự động thực hiện hàm `ensureVoiceAlias()` tạo bản sao alias đồng thời tại `espeak-ng-data/voices/vi` và `espeak-ng-data/voices/!v/vi`. Cập nhật `isEspeakDataReady()` kiểm tra cả 2 vị trí này.
- **Files liên quan**: `core/tts/src/main/java/com/epubpro/core/tts/VoiceModelDownloader.kt`

### Lỗi Trễ UI Sau Khi Đọc Xong Nút "Đang phát..." Mới Đổi Thành "Nghe thử"
- **Ngày**: 2026-08-03
- **Vấn đề**: Sau khi loa đọc hết câu văn, nút bấm UI phải chờ rất lâu (bằng đúng thời lượng audio) mới quay lại trạng thái "Nghe thử".
- **Root cause**: `AudioTrack.write()` ở chế độ `MODE_STREAM` của Android đã là một hàm đồng bộ tự dừng thread trong thời gian phát âm thanh. Việc gọi thêm `delay(durationMs)` phía sau `track.write()` đã làm thời gian chờ bị **nhân đôi** (2x duration).
- **Fix**: Bỏ hàm `delay(durationMs)` thừa trong `SherpaTtsEngine.speak()`, chỉ giữ lại `delay(100L)` nhỏ để flush audio. UI lập tức đổi trạng thái ngay khi loa kết thúc đọc.
- **Files liên quan**: `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`

### Lỗi SIGSEGV (SEGV_MAPERR fault addr 0x0) Khi Bấm Tạm Dừng Rồi Bấm Nghe Tiếp
- **Ngày**: 2026-08-03
- **Vấn đề**: Đang nghe đọc sách, bấm Tạm dừng rồi bấm Nghe tiếp làm app văng với lỗi native crash `Fatal signal 11 (SIGSEGV)`.
- **Root cause**: `PiperTtsEngineWrapper.pause()` và `stop()` trước đó để trống (empty). Khi tạm dừng rồi bấm nghe tiếp, coroutine `speak()` cũ vẫn đang chạy ghi PCM vào `AudioTrack` trên background thread, song song đó coroutine `speak()` mới được tạo ra. Hai luồng cùng truy cập đồng thời vào native `AudioTrack` và `OfflineTts` C++ object dẫn đến va chạm vùng nhớ (Race Condition / Null Pointer dereference).
- **Fix**:
  1. Trong `PiperTtsEngineWrapper`, lưu `speakJob: Job?`. Khi gọi `pause()` hoặc `stop()`, thực hiện `speakJob?.cancel()` ngay lập tức để hủy job coroutine cũ và gọi `sherpaTtsEngine.stop()`.
  2. Trong `SherpaTtsEngine.speak()`, thực hiện ghi PCM vào `AudioTrack` theo từng khối nhỏ 4KB (`chunkSize = 4096`) và kiểm tra `coroutineContext.isActive` trong mỗi vòng lặp. Nếu bị cancel, vòng lặp dừng ngay lập tức và xả buffer `AudioTrack.pause() + flush()`.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/PiperTtsEngineWrapper.kt`, `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`

### Nút Next không chuyển đúng đoạn trong chương EPUB
- **Ngày**: 2026-08-06
- **Vấn đề**: Player chỉ hiện một đoạn hoặc Next vẫn tiếp tục âm thanh cũ, đặc biệt với EPUB dùng `div`/`br` thay cho thẻ `p`.
- **Root cause**: Parser chỉ chọn nhóm thẻ semantic; khi có một heading, fallback rỗng không chạy dù phần lớn body bị bỏ sót. Đoạn quá dài tạo một request synthesize blocking. Callback hoàn tất của request cũ còn có thể tăng index sau khi người dùng đã Next.
- **Fix**: Fallback sang toàn bộ body khi độ phủ text của selector quá thấp; chia nội dung tối đa 280 ký tự tại biên câu/từ. Trước Next/Previous/Seek phải stop và vô hiệu hóa lượt phát cũ; gắn generation token cùng expected chunk/index để bỏ callback stale. Dùng `Mutex` ngăn hai lần Sherpa generate chạy đồng thời.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `core/tts/src/main/java/com/epubpro/core/tts/SherpaTtsEngine.kt`

### Highlight tô sáng cả trang thay vì từng đoạn văn đang đọc
- **Ngày**: 2026-08-07
- **Vấn đề**: Khi phát TTS trên sách EPUB convert (như tool `GetTextFromHtml`), toàn bộ màn hình (`<body>`) bị bôi màu đỏ highlight cùng lúc.
- **Root cause**: Chương sách không có thẻ `<p>`, toàn bộ văn bản nằm trực tiếp trong `<body>` phân tách bằng `<br>`. `<body>` bị nhận diện là Block Element duy nhất, nên khi highlight `index=0`, thẻ `<body>` bị tô đỏ toàn bộ.
- **Fix**: Thêm `HtmlNormalizer` tự động phân tách văn bản thô bọc thành các thẻ `<p>` chuẩn khi nạp chương trong `EpubEngine.loadChapterHtml()`. Sử dụng thuật toán `TreeWalker` đồng bộ 100% giữa Kotlin (`TtsTextParser.kt`) và JavaScript (`CssInjector.kt`) để bắt đúng index từng đoạn văn.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/engine/HtmlNormalizer.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

---

## How-To

### Đồng bộ vị trí bắt đầu nghe TTS với đoạn đang đọc dở trên màn hình
- **Ngày**: 2026-07-31
- **Bước thực hiện**:
  1. Trong JS (`CssInjector.kt`), tạo hàm `getFirstVisibleParagraphIndex()` dùng `getBoundingClientRect()` để tính đoạn văn đầu tiên đang lọt vào khung màn hình khi User lướt (scroll).
  2. Truyền `visibleIndex` qua cầu nối `ReaderJsBridge.onPageChanged` về cho Android mỗi khi cuộn trang.
  3. Ở Android, lưu `visibleIndex` xuống `SharedPreferences` cho từng ID Sách và Chương. (Cũng lưu lại khi TTS tự động đọc sang câu mới).
  4. Lần tới bấm Play TTS, lấy index đã lưu làm `startIndex` thay vì bằng 0, giúp nghe tiếp liền mạch chính xác ngay câu chữ đang nhìn thấy.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Chẩn đoán lỗi chuyển đoạn TTS trên thiết bị ADB
- **Ngày**: 2026-08-06
- **Bước thực hiện**:
  1. Xóa logcat, mở đúng chương và phát TTS; đối chiếu tổng số đoạn trên UI với log tag `EpubProTTS`.
  2. Nếu chương chỉ có tiêu đề hoặc `0/0`, kiểm tra tỷ lệ text do selector Jsoup thu được so với `document.body().text()`.
  3. Bấm Next khi đang synthesize; xác nhận audio cũ dừng trước khi request đoạn mới bắt đầu và callback cũ không đổi index.
  4. Chạy unit test parser với HTML dùng `p` và HTML chỉ dùng `div`/`br`, sau đó build APK, cài ADB và smoke-test lại.
- **Files liên quan**: `core/reader/src/test/java/com/epubpro/core/reader/tts/TtsTextParserTest.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`

---

## Patterns

### DOM-Aligned Jsoup Parsing & Auto-Save Last TTS Paragraph Position
- **Ngày**: 2026-07-31
- **Chi tiết**: Kết hợp `Jsoup` ở phía Kotlin và `document.elementsFromPoint` / `querySelectorAll` ở phía WebView JS. Mỗi khi người dùng cuộn trang, JS phát `onPageChanged(page, totalPages, firstVisibleChunkIndex)` về `ReaderViewModel` để tự động lưu `lastTtsChunkIndex`. Khi bật phát Audio, TTS tự động bắt đầu ngay tại đoạn văn bản đang hiển thị trên màn hình.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Synchronized DOM TreeWalker Extraction for Kotlin & JS
- **Ngày**: 2026-08-07
- **Chi tiết**: Sử dụng thuật toán `TreeWalker` (JS) và `NodeVisitor` (Jsoup) để duyệt các TextNode theo đúng thứ tự hiển thị DOM thực tế rồi gom nhóm theo Block Element gần nhất. Cách này đảm bảo số lượng và chỉ số đoạn văn (`paragraphIndex`) khớp 100% tuyệt đối giữa Kotlin TTS Engine và JavaScript Highlight Bridge trong WebView.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`

### Single Source of Truth via Service StateFlow
- **Ngày**: 2026-07-27
- **Chi tiết**: `TtsService` nắm giữ `MutableStateFlow<TtsPlayerState>` duy nhất. `ReaderViewModel` và `ReaderScreen` lắng hệ flow này để cập nhật đồng bộ cho cả Fullscreen Player, Mini Player Bar, và vị trí highlight trong WebView.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Real-Time Audio Settings Synchronization via Preference Flow
- **Ngày**: 2026-07-31
- **Chi tiết**: `TtsPreferencesManager` phát ra `StateFlow<TtsSettings>` mỗi khi thay đổi cài đặt (giọng đọc, tốc độ, cao độ, bật/tắt AI voice). `TtsService` lắng nghe (`collect`) flow me để tự động cập nhật engine đang phát mà không cần restart Service hay truyền Intent thủ công từ UI Tab Cá nhân.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/TtsPreferencesManager.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/audio/AudioSettingsViewModel.kt`

### Atomic Temp File Downloading with Fallback Renaming
- **Ngày**: 2026-08-03
- **Chi tiết**: Khi tải file binary lớn (ONNX model, dict files), luôn tải về file `.tmp` trước. Dùng `renameTo()` và fallback sang `copyTo(overwrite = true) + delete()` để đảm bảo tính nguyên tử, không để lại file hỏng/rác khi gặp ngắt kết nối mạng.
- **Files liên quan**: `core/tts/src/main/java/com/epubpro/core/tts/VoiceModelDownloader.kt`

### Playback Generation Token Pattern
- **Ngày**: 2026-08-06
- **Chi tiết**: Mỗi lệnh Play/Next/Previous/Seek tạo một generation mới. Callback hoàn tất chỉ được cập nhật state hoặc tự chuyển đoạn khi generation, expected index và chunk ID vẫn khớp lượt phát hiện tại. Lệnh điều hướng phải invalidate generation và stop engine trước khi đổi index. Pattern này tách ý định mới của người dùng khỏi callback bất đồng bộ đến muộn, tránh nhảy hai đoạn, quay lại đoạn cũ hoặc tự phát tiếp sau khi đã dừng.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`
