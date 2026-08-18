# Kế hoạch tối ưu mở lại sách và chuyển chương EPUB

## 1. Mục tiêu

- Khi mở lại app hoặc mở lại sách đã đọc, hiển thị chương hiện tại từ cache mà không phải quét lại toàn bộ EPUB.
- Trên cache hit, không mở `ZipFile`, không chạy lại `HtmlNormalizer` và không chạy lại `EpubHtmlSanitizer` trước khi emit nội dung hiện tại.
- Khi chuyển chương, hiển thị current trước; không chờ previous, next hoặc AI cache nếu đang đọc bản gốc.
- Cập nhật preview chương kề mà không gọi lại `loadDataWithBaseURL()` và không reset trang hiện tại.
- Giữ nguyên cơ chế chuyển chương `PixelCopy`, `loadGeneration`, `postVisualStateCallback()` và timeout đang được bảo vệ bởi `docs/reader-chapter-transition-snapshot-design.md`.
- Giữ nguyên mức bảo vệ của `EpubHtmlSanitizer` đối với nội dung EPUB không tin cậy.
- Giữ đúng lifecycle, cancellation và behavior khi người dùng đổi chương nhanh hoặc app bị process death.

## 2. Ngoài phạm vi

- Không thay reader engine hoặc cơ chế CSS multi-column hiện tại.
- Không thêm WebView thứ hai.
- Không lưu HTML của toàn bộ sách xuống disk.
- Không thay database schema chỉ để phục vụ cache.
- Không thay flow thuần Việt AI, thuật toán phân trang hoặc animation chuyển trang.
- Không thêm dependency mới nếu Android/Kotlin/JDK API hiện có đáp ứng được.

## 3. Giả định đã thống nhất

- EPUB có thể có khoảng 3.000 chương.
- File EPUB đã được import vào internal app storage và không bị ứng dụng khác sửa trực tiếp.
- Cache persistent là dữ liệu có thể tái tạo; cache hỏng hoặc không tương thích phải fallback an toàn về ZIP.
- Cache current chapter ưu tiên tốc độ mở lại; previous và next chỉ cần cache RAM/preload nền.
- Lần mở app mặc định tiếp tục hiển thị bản original như behavior hiện tại.
- Mọi function mới phải có KDoc tiếng Việt đầy đủ theo `AGENTS.md`.

## 4. Kiến trúc được chọn

### 4.1 Cache cấu trúc EPUB

Thêm trong `core:epub`:

- `EpubCacheFingerprint.kt`
- `EpubStructureCache.kt`
- Có thể tách `EpubPackageStructureParser.kt` nếu việc parse OPF/nav/NCX làm `EpubEngine` quá lớn.

Fingerprint gồm:

- canonical path;
- file length;
- `lastModified`;
- `CACHE_SCHEMA_VERSION`;
- `HEADER_PARSER_VERSION`.

`EpubStructureCache` lưu danh sách `EpubChapterHeader` dưới dạng JSON trong internal files directory. Tên file cache phải được tạo từ SHA-256 của canonical path hoặc `bookId`, không dùng trực tiếp path do EPUB cung cấp.

Thứ tự chapter luôn lấy từ OPF `spine`. Title được map từ:

1. EPUB 3 navigation document;
2. EPUB 2 NCX;
3. sample tối đa 8 KiB của entry khi TOC thiếu mapping;
4. tên sinh tự động `Chương N`.

Phải chuẩn hóa href tương đối, URL decode, loại fragment và xử lý nhiều TOC item trỏ cùng một spine entry. TOC không được thay thế `spine` làm nguồn reading order.

### 4.2 Resume snapshot

Thêm trong `core:storage`:

- `ReaderResumeSnapshot.kt`
- `ReaderResumeSnapshotStore.kt`

Mỗi sách chỉ có một snapshot current chapter, gồm:

- `bookId`;
- `chapterIndex`;
- `entryName`;
- fingerprint EPUB;
- `sourceHash`;
- `normalizerVersion`;
- `sanitizerVersion`;
- normalized original HTML;
- sanitized original HTML;
- thời điểm tạo.

Không nhúng hai chuỗi HTML lớn trực tiếp vào JSON. Dùng metadata JSON nhỏ trỏ tới các file HTML có tên theo `sourceHash`. Ghi content file atomic trước và ghi metadata pointer cuối cùng. Nếu app bị kill giữa chừng, metadata cũ vẫn trỏ tới snapshot cũ hợp lệ; orphan file được dọn ở lần ghi/xóa tiếp theo.

Snapshot chỉ hợp lệ khi toàn bộ book, chapter, entry, fingerprint, hash và processor version khớp. Bất kỳ lỗi đọc/parse nào đều xóa cache tương ứng và fallback ZIP.

### 4.3 Kiểu HTML đã sanitize

Thêm contract trong `core:reader-renderer`:

- `SanitizedEpubHtml` không cho khởi tạo tùy ý từ feature layer.
- `EpubHtmlSanitizer` là nơi duy nhất tạo giá trị mới từ HTML thô.
- Khôi phục sanitized HTML từ snapshot chỉ được phép qua API kiểm tra `sourceHash` và `SANITIZER_VERSION`.

`EpubProWebView` nhận `SanitizedEpubHtml` thay vì nhận raw `String` rồi tự quyết định có sanitize hay không. Điều này giữ ranh giới bảo mật rõ ràng khi thêm cache.

### 4.4 Cache RAM chapter

Thêm `EpubChapterMemoryCache` trong `core:epub`:

- Chỉ cache normalized HTML.
- Key gồm EPUB fingerprint, entry name và `NORMALIZER_VERSION`.
- LRU giới hạn theo byte, khởi đầu 4 MiB; `sizeOf` tính tối thiểu theo `String.length * 2`.
- Có single-flight theo key để Reader và TTS không cùng đọc/normalize một entry.
- Không giữ `ZipFile` mở lâu dài.
- Khi xóa sách hoặc fingerprint thay đổi, evict entry liên quan.

## 5. Luồng dữ liệu mục tiêu

### 5.1 Mở lại sách

1. `ReaderViewModel` lấy `Book` và file EPUB.
2. Đọc song song settings, `ReadingProgress` và header cache.
3. Dùng progress để xác định `chapterIndex` và `entryName`.
4. Đọc resume snapshot.
5. Nếu snapshot hợp lệ:
   - emit current normalized/sanitized HTML ngay;
   - giữ nguyên page/CFI restore;
   - không đọc ZIP hoặc chạy Jsoup trước lần render đầu.
6. Nếu snapshot miss:
   - đọc current entry;
   - normalize trên dispatcher phù hợp;
   - sanitize trên `Dispatchers.Default`;
   - emit current;
   - ghi snapshot nền.
7. Sau khi current đã emit, preload previous, next và AI cache bằng child coroutine của request hiện tại.

### 5.2 Chuyển chương

1. Hủy `chapterLoadJob` cũ và tăng `chapterLoadGeneration`.
2. Capture `requestedIndex`, `requestedEntry` và `requestedContentVersion`.
3. Reset preview/AI state của chương cũ nhưng không phá transition cover hiện tại.
4. Lấy current từ RAM cache hoặc ZIP.
5. Nếu đang đọc `ORIGINAL`, emit current ngay.
6. Nếu đang đọc `AI`, giữ behavior hiện tại:
   - kiểm tra AI cache trước khi commit content version;
   - có cache thì hiển thị AI trực tiếp;
   - không có cache thì chuyển sang original.
7. Preload previous/next nền.
8. Trước mọi state update, xác nhận generation và current index vẫn khớp.
9. Sau khi current thành công, ghi resume snapshot mới; thay đổi page/CFI không ghi lại HTML.

## 6. WebView document và preview

### 6.1 Document channel

Trong `EpubProWebView.kt`:

- `loadedHtmlKey` chỉ phụ thuộc current sanitized HTML và rendering settings thực sự cần reload.
- Previous/next không còn nằm trong document reload key.
- Việc dựng CSS, JavaScript và prepared current document được chuyển khỏi `AndroidView.update` càng nhiều càng tốt; CPU-heavy string work chạy trên `Dispatchers.Default`.
- `AndroidView.update` chỉ thực hiện thao tác WebView bắt buộc trên Main Thread.

### 6.2 Preview channel

Trong `CssInjector.kt` thêm JavaScript API tương đương:

```javascript
window.epubproSetAdjacentChapters = function(generation, previousHtml, nextHtml) {
    if (generation !== window.epubproDocumentGeneration) return;
    previousChapterHtml = previousHtml || '';
    nextChapterHtml = nextHtml || '';
};
```

- Preview được sanitize và quote/escape trên `Dispatchers.Default`.
- Native gọi setter trên Main Thread sau khi payload hoàn tất.
- Setter phải reject generation cũ.
- Preview update không gọi `loadDataWithBaseURL()`.
- Khi document generation đổi, preview mặc định rỗng cho đến khi preload mới hoàn tất.
- Không thêm `JavascriptInterface` mới cho JavaScript chủ động lấy raw EPUB HTML.

### 6.3 Contract chuyển chương phải giữ

Không thay đổi thứ tự:

1. JavaScript commit boundary gesture.
2. `PixelCopy` capture frame hiện tại.
3. Compose hiển thị transition cover.
4. ViewModel đổi chapter và WebView load destination document.
5. JavaScript báo matching `onReaderLayoutReady(loadGeneration)`.
6. `postVisualStateCallback()` xác nhận compositor commit.
7. Cover được gỡ ở frame kế tiếp hoặc timeout recovery.

## 7. Concurrency và lifecycle

- Dùng một parent `chapterLoadJob` trong `viewModelScope` cho current, AI lookup và adjacent preload.
- Mỗi request có monotonically increasing generation.
- Child result chỉ được commit nếu generation, index và entry đều khớp.
- `CancellationException` luôn được ném lại.
- Current failure cập nhật `loadError`; adjacent failure chỉ để preview `null`.
- Snapshot corrupt không làm fail màn reader.
- `ON_STOP` yêu cầu flush progress nhưng không khởi động lại parsing HTML.
- Process death không yêu cầu giữ coroutine; lần tạo ViewModel mới khôi phục từ disk snapshot.
- Không dùng `WorkManager` cho preload lifecycle-bound.

## 8. Progress, widget và TTS

### 8.1 Một nguồn phát lệnh lưu vị trí

- `updateCfiPosition()` chỉ cập nhật CFI nếu giá trị thay đổi.
- `updatePageMetrics()` là nơi phát lệnh lưu progress cho một metrics event.
- Debounce progress khoảng 200 ms.
- Force flush trước khi đổi chapter và khi reader nhận `ON_STOP`.
- Không ghi lại resume HTML snapshot khi chỉ thay đổi page/CFI.

### 8.2 Widget projection

- Tách `progressSaveJob` và `widgetProjectionJob`.
- Widget debounce riêng khoảng 500 ms.
- Giữ guard `TtsService.isPlaybackProjectionOwned()`.
- Cache kết quả `TtsTextParser.parseHtmlToChunks()` trong `ReaderViewModel` theo:
  - `bookId`;
  - `chapterIndex`;
  - `contentVersion`;
  - `sourceHash`.
- Jsoup parse chạy trên `Dispatchers.Default`.
- Snapshot/widget storage chạy trên `Dispatchers.IO`.
- `startTtsServicePlayback()` tái sử dụng cached chunks; cache miss phải parse ngoài Main Thread trước khi gọi service.

## 9. Kế hoạch triển khai theo phase

### Phase 0 — Baseline và regression contracts

File dự kiến:

- `feature/reader/.../ReaderViewModel.kt`
- `feature/reader/.../webview/EpubProWebView.kt`
- test hiện có trong `feature/reader` và `core/reader-renderer`.

Công việc:

1. Thêm timing/debug counters cho header, snapshot, current ready, WebView load, visual ready và preview ready.
2. Ghi kích thước normalized/sanitized/prepared HTML.
3. Bổ sung contract test chứng minh preview hiện đang tham gia reload key trước khi sửa.
4. Ghi baseline trên EPUB nhỏ, EPUB 3.000 chapter và chapter HTML lớn.

Không thay behavior trong phase này.

### Phase 1 — Persistent header cache và TOC-first title mapping

File dự kiến:

- `core/epub/.../EpubEngine.kt`
- `core/epub/.../EpubCacheFingerprint.kt` mới
- `core/epub/.../EpubStructureCache.kt` mới
- `core/epub/.../EpubPackageStructureParser.kt` mới nếu cần
- `feature/library/.../LibraryViewModel.kt`
- test/factory trong `core/epub/src/test`.

Công việc:

1. Tách parse OPF structure đủ để tái sử dụng manifest/spine/nav/NCX.
2. Implement cache read/write/version/invalidation.
3. `extractChapterHeaders()` dùng cache trước, parse và ghi khi miss.
4. Import hiện tại tự populate cache qua `parseEpubMetadata()`.
5. Delete book xóa structure cache.
6. Giữ fallback natural sort cho EPUB legacy/malformed.

### Phase 2 — Sanitized HTML contract và WebView preview channel

File dự kiến:

- `core/reader-renderer/.../filter/EpubHtmlSanitizer.kt`
- `core/reader-renderer/.../filter/SanitizedEpubHtml.kt` mới nếu tách file
- `core/reader-renderer/.../style/CssInjector.kt`
- `feature/reader/.../webview/EpubProWebView.kt`
- test trong `core/reader-renderer` và `feature/reader`.

Công việc:

1. Tạo typed sanitized HTML contract và version.
2. Tách current document reload key khỏi preview.
3. Thêm generation-aware preview setter.
4. Chuẩn bị preview payload ngoài Main Thread.
5. Giữ toàn bộ transition snapshot/visual barrier invariant.
6. Chứng minh preview update không làm document reload.

### Phase 3 — Resume snapshot và current-first loading

File dự kiến:

- `core/storage/.../ReaderResumeSnapshot.kt` mới
- `core/storage/.../ReaderResumeSnapshotStore.kt` mới
- `feature/reader/.../ReaderViewModel.kt`
- `feature/reader/.../ReaderScreen.kt`
- `feature/reader/.../webview/EpubProWebView.kt`
- test trong `core/storage` và `feature/reader`.

Công việc:

1. Implement snapshot metadata/content files và atomic commit.
2. Khôi phục snapshot sau khi xác định progress chapter.
3. Emit current trước adjacent preload.
4. Thêm generation guard cho mọi async result.
5. Giữ AI behavior theo `requestedContentVersion`.
6. Ghi snapshot sau current load/sanitize thành công.
7. Delete book xóa resume snapshot.

### Phase 4 — RAM cache và single-flight

File dự kiến:

- `core/epub/.../EpubChapterMemoryCache.kt` mới
- `core/epub/.../EpubEngine.kt`
- `core/playback/.../TtsChapterPlaybackCoordinator.kt` nếu cần dùng chung API cache.

Công việc:

1. Implement byte-budget LRU.
2. Deduplicate concurrent load theo fingerprint + entry.
3. Cho `loadChapterHtml()` đọc cache trước.
4. Xác nhận N → N+1 chỉ đọc/normalize N+2 khi N và N+1 còn trong cache.
5. Evict theo sách và fingerprint.

### Phase 5 — Progress/widget/TTS projection

File dự kiến:

- `feature/reader/.../ReaderViewModel.kt`
- `feature/reader/.../ReaderScreen.kt`
- `core/playback/.../TtsTextParser.kt` chỉ khi cần API hỗ trợ cache key; không thêm state toàn cục vào parser.

Công việc:

1. Loại save trùng từ CFI/page callbacks.
2. Tách progress job và widget projection job.
3. Thêm TTS chunk cache theo content identity.
4. Parse TTS ngoài Main Thread.
5. Flush progress ở chapter transition và `ON_STOP`.

### Phase 6 — Validation và cleanup

1. Self-review toàn bộ diff.
2. Kiểm tra lifecycle, cancellation, memory retention và WebView cleanup.
3. Chạy test module, build app và kiểm tra thiết bị.
4. So sánh timing/counters với Phase 0.
5. Xóa log quá chi tiết khỏi release hoặc giữ dưới debug guard.
6. Cập nhật tài liệu transition nếu contract generation có thay đổi hình thức nhưng không đổi behavior.

## 10. Test matrix bắt buộc

### Core EPUB

- EPUB 3 nav map title đúng theo spine.
- EPUB 2 NCX map title đúng theo spine.
- TOC thiếu entry fallback sample.
- Nhiều TOC item trỏ cùng entry.
- Fragment và relative href được chuẩn hóa.
- EPUB thiếu container/OPF fallback natural sort.
- Cache hit trả đúng headers.
- Fingerprint/version thay đổi làm cache miss.
- Cache JSON corrupt fallback parse và tự phục hồi.

### Resume snapshot/storage

- Save/read round trip với HTML Unicode lớn.
- Metadata được commit sau content files.
- Process interruption/orphan file không phá snapshot cũ.
- Source hash hoặc sanitizer version lệch bị reject.
- Path traversal qua bookId/entryName không thoát cache root.
- Delete book xóa snapshot.

### Reader ViewModel/coroutine

- Cached reopen emit current trước adjacent/AI.
- Cache hit không gọi chapter ZIP loader.
- Cache miss fallback đúng và ghi snapshot.
- N → N+1 → N+2 nhanh không nhận state của request cũ.
- Adjacent failure không làm current lỗi.
- Current failure hiển thị error.
- ORIGINAL không chờ AI cache.
- AI mode không chớp original rồi tự reload AI.
- Cancellation không bị chuyển thành load error.

### WebView/renderer

- Preview hash thay đổi không đổi document reload key.
- Preview setter reject stale generation.
- Current/settings cần render vẫn reload đúng.
- Script, iframe, event handlers và URL nguy hiểm vẫn bị loại bỏ.
- Snapshot sanitize restore chỉ chấp nhận đúng hash/version.
- Transition cover chỉ gỡ sau matching visual-state barrier.
- Timeout/disposal/superseding transition cleanup bitmap đúng.

### Progress/TTS/widget

- Một metrics event chỉ schedule một progress save.
- Debounce giữ lần cập nhật cuối.
- `ON_STOP` và chapter switch flush đúng.
- Widget parse một lần cho cùng content identity.
- Đổi AI/original invalidates TTS chunk cache.
- TTS start không parse Jsoup trên Main Thread.

## 11. Lệnh validation dự kiến

```powershell
.\gradlew.bat :core:epub:testDebugUnitTest `
  :core:storage:testDebugUnitTest `
  :core:reader-renderer:testDebugUnitTest `
  :core:playback:testDebugUnitTest `
  :feature:reader:testDebugUnitTest `
  :app:assembleDebug --no-daemon
```

Sau unit/build:

- Test thiết bị với EPUB 100, 1.000 và 3.000 chapter.
- Test process death rồi mở lại đúng chương/trang.
- Test next/previous chapter liên tục theo hai hướng.
- Test original và AI mode.
- Test horizontal/vertical pagination.
- Test app background/foreground trong lúc preload và lúc ghi snapshot.

## 12. Tiêu chí nghiệm thu

Trên reference device được ghi rõ model/API/build:

- Header cache + resume snapshot sẵn sàng trong tối đa 100 ms ở cache hit.
- WebView committed visual của current chapter trong tối đa 500 ms ở cache hit.
- Trước current render của cache hit:
  - 0 lần mở `ZipFile`;
  - 0 lần normalize Jsoup;
  - 0 lần sanitize Jsoup.
- Preview hoàn tất sau không làm tăng document load generation.
- N → N+1 với cache hợp lệ chỉ cần đọc N+2.
- Không có stale preview/AI/current sau rapid navigation.
- Không có blank frame mới tại chapter boundary.
- Cache RAM không vượt byte budget.
- Cache corrupt/version mismatch luôn fallback và không crash.
- Test liên quan và `:app:assembleDebug` thành công.

Các ngưỡng thời gian phải được xác nhận bằng trace thực tế. Nếu thiết bị tham chiếu không đạt, ưu tiên kiểm tra WebView first visual và disk snapshot read trước khi tăng cache hoặc thay architecture.

## 13. Rủi ro và rollback

### Rủi ro

- Snapshot cũ được dùng sau khi sanitizer thay đổi.
- Preview async làm reload/reset page nếu còn nằm trong document key.
- Child coroutine cũ ghi state sang chapter mới.
- AI mode bị chớp original.
- Cache HTML làm tăng disk/RAM quá mức.
- Ghi progress quá thường xuyên sau khi tách widget.
- Thay đổi JavaScript vô tình phá `PixelCopy` transition invariant.

### Biện pháp

- Version/hash validation bắt buộc.
- Generation guard ở native và JavaScript.
- Byte budget và một persistent snapshot mỗi sách.
- Atomic commit và fallback ZIP.
- Contract tests cho reload key và transition barrier.
- Phase 0 baseline trước khi tối ưu.

### Rollback

- Tăng cache schema/processor version để vô hiệu hóa toàn bộ cache cũ.
- Có thể tắt đọc resume snapshot và fallback về ZIP mà không ảnh hưởng dữ liệu người dùng.
- Persistent cache không phải source of truth; không rollback Room migration vì plan không thay Room schema.

## 14. Nhật ký quyết định

### 2026-08-18: Chọn Resume Snapshot hai tầng

- **Quyết định:** persistent header cache + persistent current chapter snapshot + adjacent RAM cache.
- **Thay thế đã xem xét:** chỉ header cache; lưu toàn bộ chapter vào Room.
- **Lý do:** chỉ header cache chưa loại read/normalize/sanitize current; Room làm tăng migration và dữ liệu không cần thiết.

### 2026-08-18: Chỉ persist current chapter HTML

- **Quyết định:** mỗi sách giữ một resume snapshot.
- **Thay thế:** persist previous/next hoặc toàn sách.
- **Lý do:** current là dữ liệu bắt buộc để mở lại; adjacent có thể tái tạo nền và cache RAM.

### 2026-08-18: Tách document và preview WebView

- **Quyết định:** preview update qua generation-aware JavaScript setter, không tham gia document reload key.
- **Thay thế:** emit preview state rồi reload document; WebView thứ hai.
- **Lý do:** reload làm mất lợi ích current-first; WebView thứ hai tăng lifecycle/memory complexity.

### 2026-08-18: Giữ AI behavior hiện tại

- **Quyết định:** original không chờ AI; AI mode vẫn resolve cache trước khi commit chapter content.
- **Thay thế:** luôn render original rồi đổi sang AI.
- **Lý do:** tránh chớp, reload và reset vị trí cho người đang đọc AI.

### 2026-08-18: Cache theo byte và không giữ ZipFile

- **Quyết định:** normalized HTML LRU 4 MiB ban đầu, có single-flight.
- **Thay thế:** cache 10-15 chapter; giữ một `ZipFile` lâu dài.
- **Lý do:** chapter có kích thước rất khác nhau; giữ ZIP tăng file descriptor, concurrency và replacement risk.

### 2026-08-18: Không thay transition snapshot contract

- **Quyết định:** giữ `PixelCopy`, generation-aware readiness và compositor barrier.
- **Thay thế:** bỏ cover hoặc dùng delay/theme color.
- **Lý do:** contract hiện có đã giải quyết blank frame trên thiết bị và được bảo vệ bằng test/tài liệu.

