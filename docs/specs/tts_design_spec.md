# ĐẶC TẢ THIẾT KẾ VÀ NHẬT KÝ QUYẾT ĐỊNH (DESIGN SPEC & DECISION LOG)
## Tính năng: Text-to-Speech (TTS - Đọc sách bằng giọng nói) cho EpubPro

---

### 1. TÓM TẮT HIỂU BIẾT (UNDERSTANDING SUMMARY)

- **Mục tiêu**: Cung cấp trải nghiệm nghe sách nói (Full Book/Chapter Reader) tự động phát liên tục các chương, chạy ngầm dưới nền khi ứng dụng thu nhỏ hoặc tắt màn hình.
- **Trải nghiệm UI/UX**:
  - **Popup "Thiết lập giọng đọc" (BottomSheet Config)**: Hiển thị trong lần đầu tiên người dùng bấm phát audio. Cho phép chọn loại giọng (Hệ thống/AI), Ngôn ngữ, Giọng đọc, Tốc độ (0.5x–2.0x), Cao độ (Pitch) và nút Nghe thử. Nút "Bắt đầu nghe" lưu cấu hình và khởi chạy phát ngay.
  - **Màn hình Fullscreen Audio Player**: Giao diện cao cấp theo phong cách Readexa (Bìa sách, Thẻ đoạn văn bản đang đọc "Đoạn X / Y", Hiệu ứng sóng âm động, Progress slider, Bộ nút điều khiển, Thanh công cụ nhanh: Tốc độ, Giọng đọc, Hẹn giờ ngủ).
  - **Mini Player Bar**: Thanh điều khiển thu nhỏ xuất hiện ở chân WebView `ReaderScreen` khi thu nhỏ Player.
  - **Đồng bộ WebView**: Tự động tô sáng (highlight) và cuộn mượt (auto-scroll) đến đoạn/câu tương ứng đang phát trong WebView via JS Bridge.
- **Kiến trúc Kỹ thuật**:
  - **Engine Abstraction Layer (`TtsEngine`)**: Triển khai `AndroidNativeTtsEngine` dùng `android.speech.tts.TextToSpeech` ở Phase 1 (100% offline, miễn phí). Sẵn sàng tích hợp AI Online TTS ở Phase 2.
  - **Background Playback**: Sử dụng Android `ForegroundService` kết hợp `MediaSessionCompat` và `MediaNotificationManager` để điều khiển phát nhạc từ Lockscreen, Thanh thông báo, Tai nghe Bluetooth.
  - **Text Chunking**: Parse mã XHTML từ `ReadiumEngine` thành các đơn vị `TtsChunk` theo đoạn/câu (`Paragraph Indexing`).
  - **Sleep Timer**: Hẹn giờ tắt (15p, 30p, 45p, 60p, Hết chương) với hiệu ứng fade-out âm thanh giảm dần 10 giây cuối.

---

### 2. GIẢ ĐỊNH (ASSUMPTIONS)

1. Thiết bị Android của người dùng có sẵn Google Speech Services hoặc Samsung TTS Engine.
2. Quá trình parse HTML chương sách từ `ReadiumEngine` có thể trích xuất Plain Text theo từng thẻ `<p>`/`<div>` và gán thuộc tính `data-tts-id` trong WebView để JS nhận diện.
3. Trạng thái cấu hình giọng đọc (`TtsSettings`) được lưu trữ vĩnh viễn thông qua `TtsPreferencesManager`.

---

### 3. NHẬT KÝ QUYẾT ĐỊNH (DECISION LOG)

| Quyết định | Các phương án đã xem xét | Lý do lựa chọn |
| :--- | :--- | :--- |
| **Phạm vi tính năng** | 1. Đọc liên tục (Full Reader)<br>2. Chỉ đọc đoạn bôi đen | Chọn **Đọc liên tục** để mang lại trải nghiệm nghe sách nói chuyên nghiệp, tự động chuyển chương. |
| **Kiến trúc TTS Engine** | 1. Abstraction Layer + Native TTS<br>2. Hardcode Native TTS trực tiếp | Chọn **Abstraction Layer** để Phase 1 chạy mượt 100% offline với Native TTS, đồng thời dễ dàng cắm giọng AI Online ở Phase 2. |
| **Chế độ UI** | 1. Fullscreen + Mini Player + Sync Highlight<br>2. Chỉ Fullscreen Player | Chọn **Đầy đủ 3 chế độ UI** để tối ưu hóa trải nghiệm người dùng theo thiết kế mẫu Readexa. |
| **Bẻ nhỏ văn bản (Parsing)** | 1. Phân tách theo Đoạn & Câu (`Paragraph Indexing`)<br>2. Chuỗi Plain Text thô | Chọn **Phân tách theo Đoạn & Câu** để WebView tô sáng và tự động cuộn trang chính xác 100%. |
| **Quản lý Chạy ngầm** | 1. Foreground Service + MediaSessionCompat<br>2. ViewModel Lifecycle | Chọn **Foreground Service + MediaSessionCompat** để ngăn Android OS kill ứng dụng khi tắt màn hình và hỗ trợ nút bấm tai nghe/lockscreen. |

---

### 4. THIẾT KẾ CHI TIẾT (DETAILED DESIGN)

#### 4.1 Module & Data Layer (`core/tts`)
- `TtsChunk`: `data class TtsChunk(val id: Int, val paragraphIndex: Int, val text: String)`
- `TtsSettings`: `data class TtsSettings(val isConfigured: Boolean, val isAiVoice: Boolean, val language: String, val voiceId: String?, val speed: Float, val pitch: Float)`
- `TtsPlayerState`: `Sealed class` (Idle, Loading, Playing, Paused, Error)
- `TtsEngine`: Interface định nghĩa các phương thức `initialize`, `speak`, `pause`, `resume`, `stop`, `setSpeed`, `setPitch`, `setVoice`, `getAvailableVoices`.
- `AndroidNativeTtsEngine`: Class thực thi `TtsEngine` bọc Android `TextToSpeech`.
- `TtsPreferencesManager`: Quản lý lưu trữ thiết lập giọng đọc.

#### 4.2 Background Service & MediaSession (`core/tts/service`)
- `TtsService`: Service kiểu `foregroundServiceType="mediaPlayback"`.
- `TtsMediaSessionManager`: Khởi tạo `MediaSessionCompat`, lắng nghe `MediaButtonReceiver`, tạo Notification giao diện `MediaStyle`.
- Quản lý Audio Focus với `AudioManager` (Pause khi có cuộc gọi, Resume khi cuộc gọi kết thúc).
- Sleep Timer Coroutine: Hẹn giờ dừng dịch vụ với hiệu ứng Fade-Out âm lượng.

#### 4.3 UI & WebView Sync (`feature/reader`)
- `TtsSetupBottomSheet`: BottomSheet Compose cấu hình giọng đọc lần đầu.
- `TtsAudioPlayerScreen`: Dialog/BottomSheet tràn màn hình trình phát Audio (Readexa UI).
- `TtsMiniPlayerBar`: Component chân màn hình đọc sách khi thu nhỏ Player.
- `EpubJsBridge`: Tiêm (Inject) đoạn script `highlightTtsParagraph(index)` vào WebView để đổi CSS class và gọi `scrollIntoView()`.

---

### 5. KẾ HOẠCH BÀN GIAO TRIỂN KHAI (IMPLEMENTATION STEPS)

1. **Bước 1**: Tạo package `core/tts` chứa Data models, `TtsPreferencesManager`, `TtsEngine` interface và `AndroidNativeTtsEngine`.
2. **Bước 2**: Triển khai `TtsService`, `TtsMediaSessionManager` và đăng ký Service vào `AndroidManifest.xml`.
3. **Bước 3**: Xây dựng `TtsTextParser` để bẻ mã HTML chương sách thành `List<TtsChunk>` và thêm JS Highlight Bridge cho WebView.
4. **Bước 4**: Xây dựng các UI Components Jetpack Compose (`TtsSetupBottomSheet`, `TtsAudioPlayerScreen`, `TtsMiniPlayerBar`).
5. **Bước 5**: Kết nối ViewModel với `TtsService`, thực hiện kiểm thử trên thiết bị thực.
