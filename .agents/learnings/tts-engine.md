# Android Text-to-Speech & Background Media Architecture

> Tổng hợp kiến thức về hệ thống TTS Engine, Sherpa-ONNX Offline AI Voice, Foreground Service, MediaSessionCompat và đồng bộ Highlight trong WebView EPUB Reader.
> Cập nhật lần cuối: 2026-07-31

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

---

## Patterns

### DOM-Aligned Jsoup Parsing & Auto-Save Last TTS Paragraph Position
- **Ngày**: 2026-07-31
- **Chi tiết**: Kết hợp `Jsoup` ở phía Kotlin và `document.elementsFromPoint` / `querySelectorAll` ở phía WebView JS. Mỗi khi người dùng cuộn trang, JS phát `onPageChanged(page, totalPages, firstVisibleChunkIndex)` về `ReaderViewModel` để tự động lưu `lastTtsChunkIndex`. Khi bật phát Audio, TTS tự động bắt đầu ngay tại đoạn văn bản đang hiển thị trên màn hình.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsTextParser.kt`, `core/reader/src/main/java/com/epubpro/core/reader/style/CssInjector.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Single Source of Truth via Service StateFlow
- **Ngày**: 2026-07-27
- **Chi tiết**: `TtsService` nắm giữ `MutableStateFlow<TtsPlayerState>` duy nhất. `ReaderViewModel` và `ReaderScreen` lắng nghe flow này để cập nhật đồng bộ cho cả Fullscreen Player, Mini Player Bar, và vị trí highlight trong WebView.
- **Files liên quan**: `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `feature/reader/src/main/java/com/epubpro/feature/reader/ReaderViewModel.kt`

### Real-Time Audio Settings Synchronization via Preference Flow
- **Ngày**: 2026-07-31
- **Chi tiết**: `TtsPreferencesManager` phát ra `StateFlow<TtsSettings>` mỗi khi thay đổi cài đặt (giọng đọc, tốc độ, cao độ, bật/tắt AI voice). `TtsService` lắng nghe (`collect`) flow này để tự động cập nhật engine đang phát mà không cần restart Service hay truyền Intent thủ công từ UI Tab Cá nhân.
- **Files liên quan**: `core/storage/src/main/java/com/epubpro/core/storage/TtsPreferencesManager.kt`, `core/reader/src/main/java/com/epubpro/core/reader/tts/TtsService.kt`, `feature/profile/src/main/java/com/epubpro/feature/profile/audio/AudioSettingsViewModel.kt`
