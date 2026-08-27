# Thiết kế import local PRC, MOBI và AZW3 qua pipeline EPUB

## 1. Trạng thái

- Trạng thái: Đã xác nhận thiết kế.
- Phạm vi: Android local library import và luồng upload file độc lập.
- Ngày xác nhận: 2026-08-26.

### Ghi chú triển khai phase 1

Implementation hiện tại đã đưa vào app pipeline `LocalBookImportWorker` duy nhất: source được
copy streaming vào app-private storage, converter chạy trong worker, sau đó strict validation,
FTS indexing và Room commit. Native decoder đang chạy cùng process app; WorkManager
multiprocess, tách worker thành chain và bảng `book_import_jobs` là hardening backlog, chưa bật
trong phase này. Các giới hạn, cleanup khi cancel và retry I/O vẫn được áp dụng ở worker hiện tại.

## 2. Bối cảnh hiện tại

Luồng local hiện tại chỉ chọn `application/epub+zip`, copy file vào internal storage, gọi `EpubEngine.parseEpubMetadata()`, insert `Book`, rồi index FTS ngay trong `LibraryViewModel`. `ReaderViewModel`, TTS, AI, Book Bible và cache chương đều phụ thuộc trực tiếp vào cấu trúc EPUB do `EpubEngine` cung cấp.

`Book` và `BookEntity` chưa lưu định dạng nguồn. Room đang ở version 6. `EpubReadLimits` giới hạn EPUB 500 MiB và markup entry 10 MiB.

Thiết kế này không mở rộng reader thành nhiều engine. Thay vào đó, PRC/MOBI/AZW3 được chuẩn hóa thành EPUB nội bộ trước khi đi vào pipeline hiện có.

## 3. Hiểu biết đã khóa

- Hỗ trợ local các file PRC, MOBI và AZW3 không DRM, dạng sách chữ reflowable.
- Hỗ trợ PalmDOC/PRC, MOBI7, KF8/AZW3 và hybrid MOBI7/KF8; hybrid ưu tiên KF8 và fallback MOBI7 khi cần.
- Chuyển đổi diễn ra một lần, hoàn toàn offline trên thiết bị.
- File nguồn của người dùng không bị sửa; bản copy tạm trong app bị xóa sau khi thành công hoặc hủy.
- EPUB nội bộ phải dùng được toàn bộ reader, progress, search, bookmark/highlight, TTS, AI và Book Bible.
- Giữ metadata, bìa, mục lục, văn bản, ảnh và CSS cơ bản; loại thành phần Kindle không tương thích hoặc không an toàn.
- Input PRC/MOBI/AZW3 có giới hạn cứng 100 MiB. EPUB gốc tiếp tục giới hạn 500 MiB.
- Import chạy nền, có tiến trình trong Library và notification, hỗ trợ hủy và retry an toàn sau process death.
- Native/NDK được phép cho `arm64-v8a` và `armeabi-v7a`.
- Library lưu và hiển thị nhãn định dạng nguồn.
- Upload là pipeline độc lập: cho phép chọn file bất kỳ và gửi nguyên file lên backend; client không convert hoặc áp allowlist ebook cho upload.

## 4. Ngoài phạm vi

- Giải mã DRM hoặc sử dụng API DRM của thư viện upstream.
- Topaz/TPZ, Print Replica/AZW4, comic, manga, image-only hoặc fixed-layout.
- Chuyển đổi local trên backend.
- Thêm reader engine riêng cho PRC/MOBI/AZW3.
- Chuyển đổi lại EPUB hoặc migrate nội dung các sách đã có.

## 5. Kiến trúc

Tạo module Android library mới `core:book-converter`. Module này sở hữu:

- `BookFormatSniffer`: nhận diện header và subtype thật; không tin MIME, đuôi file hoặc metadata từ provider.
- `BookConversionEngine`: API Kotlin điều phối chuyển đổi.
- `MobiNativeDecoder`: JNI bridge tối thiểu tới `libmobi`.
- `EpubPackager`: chuẩn hóa XHTML/CSS, sinh OPF/navigation/spine và đóng gói EPUB.
- `ConversionLimits`: ngân sách byte, số record/resource/chapter và timeout theo stage.
- `ConversionResult` cùng typed error/warning.

`core:storage` sở hữu scheduler, WorkManager chain, staging directory và commit. `core:epub` tiếp tục là cổng validation và đọc duy nhất. `feature:library` chỉ chọn file, quan sát trạng thái job và phát intent hủy/retry.

```text
Library picker
    |
    v
PrepareLocalBookWorker
    |
    v
ConvertBookRemoteWorker (:book_converter)
    |                    \
    |                     \-- libmobi decoder
    v
EPUB staging output
    |
    v
CommitLocalBookWorker
    |-- EpubEngine strict validation
    |-- FTS indexing
    |-- atomic file promotion
    `-- Room commit
    |
    v
Reader/TTS/AI/Book Bible hiện tại
```

EPUB gốc dùng cùng scheduler và commit pipeline nhưng bỏ qua `ConvertBookRemoteWorker`.

## 6. Hợp đồng converter

API public của module chỉ dùng type Kotlin ổn định, không expose native pointer hoặc raw struct:

```kotlin
interface BookConversionEngine {
    suspend fun convert(
        request: BookConversionRequest,
        onProgress: suspend (BookConversionProgress) -> Unit
    ): BookConversionResult
}
```

Khi triển khai function mới phải có KDoc tiếng Việt theo quy định repo.

`MobiNativeDecoder` nhận input path và staging root do app tạo. Decoder ghi manifest và resource vào staging bằng tên nội bộ được kiểm soát. Không marshal toàn bộ sách thành một `ByteArray` qua JNI.

`DecodeManifest` chứa metadata, ordered flows/chapters, TOC, resources, media type, link mapping và warning. Nội dung hoặc native error thô không được ghi vào log.

## 7. Nhận diện và giải mã

1. Đếm byte khi copy từ `ContentResolver`; dừng ngay khi vượt 100 MiB.
2. Kiểm tra PDB/MOBI header và record structure.
3. Từ chối file không phải ebook dù có đuôi `.prc`, `.mobi` hoặc `.azw3`.
4. Gọi `mobi_is_encrypted()` và từ chối trước khi reconstruct.
5. Gọi `mobi_is_replica()` và từ chối Print Replica.
6. Với hybrid, parse KF8 mặc định; chỉ fallback KF7/MOBI7 khi phần KF8 không thể reconstruct hợp lệ.
7. Reconstruct metadata, markup flows, NCX/TOC, links và resources.

Không compile hoặc gọi code path decrypt của `libmobi` trong wrapper của ứng dụng.

## 8. Tạo EPUB nội bộ

`EpubPackager` không dùng nguyên hàm `create_epub()` mẫu của `mobitool`, vì upstream ghi rõ output mẫu cần được ứng dụng thực tế validate và sửa markup.

Quy tắc chapter:

- KF8/AZW3: giữ ordered flow/spine có ý nghĩa.
- MOBI7/PalmDOC: ưu tiên điểm chia từ NCX/TOC.
- Nếu không có TOC: chia theo heading hoặc block boundary.
- Fallback cuối cùng: chia theo block với byte budget; không cắt giữa UTF-8 sequence hoặc HTML element.
- Rewrite anchor và internal link sau khi chia.

EPUB 3 đầu ra gồm `mimetype` không nén ở entry đầu, `META-INF/container.xml`, `content.opf`, `nav.xhtml`, `toc.ncx`, XHTML, CSS, cover và images. Spine giữ đúng thứ tự đọc.

JavaScript, active content, unsafe external URI, absolute path, path traversal, symlink và font/resource không hợp lệ bị loại. XHTML vẫn phải đi qua sanitizer hiện tại khi reader render.

## 9. Vòng đời WorkManager và commit

Local picker dùng `ActivityResultContracts.OpenDocument`. App lấy `DISPLAY_NAME`, MIME và quyền đọc URI bền vững đủ lâu cho job. Picker dùng `*/*`; `BookFormatSniffer` là cổng xác thực thực tế.

Scheduler enqueue unique WorkManager chain theo `jobId`:

1. `PrepareLocalBookWorker`: copy streaming vào staging, xác định format sơ bộ và kiểm tra dung lượng trống.
2. `ConvertBookRemoteWorker`: decode/package trong process `:book_converter`.
3. `CommitLocalBookWorker`: strict validate, thử load chapter đầu, index FTS, promote file và commit DB.

Long-running worker chạy foreground với service type `dataSync`, khai báo `FOREGROUND_SERVICE_DATA_SYNC` và type tương ứng trong manifest/runtime cho `targetSdk 34`.

Trạng thái job lưu bền vững trong Room: `QUEUED`, `COPYING`, `DECODING`, `PACKAGING`, `VALIDATING`, `INDEXING`, `COMPLETED`, `FAILED`, `CANCELLED`.

Lỗi xác định như DRM, unsupported subtype, corrupt header hoặc vượt giới hạn không retry. Lỗi I/O, remote-process death hoặc system preemption retry tối đa ba lần với backoff. Cancel được kiểm tra giữa record/resource/stage và luôn dọn output chưa commit.

Thứ tự hoàn tất:

1. Validate EPUB staging và load chapter đầu.
2. Chuẩn bị FTS bằng `bookId` ổn định; retry luôn clear index chưa hoàn chỉnh trước khi index lại.
3. Fsync và atomic rename EPUB vào `files/books`.
4. Transaction insert `Book` và đánh dấu job hoàn tất.
5. Xóa source/staging và release URI permission.

Nếu chết giữa promote và DB transaction, orphan sweeper xóa file không được DB tham chiếu. `Book` chưa hoàn chỉnh không xuất hiện như sách có thể mở.

## 10. Cô lập native và giới hạn an toàn

Thêm `androidx.work:work-multiprocess` cùng version WorkManager hiện tại. `ConvertBookRemoteWorker` dùng `RemoteCoroutineWorker` trong service `exported=false`, process `:book_converter`. Native crash hoặc OOM chỉ làm chết converter process; process UI/TTS vẫn sống và scheduler quyết định retry.

Giới hạn tối thiểu:

- Input source: 100 MiB.
- EPUB/output đã giải mã: 500 MiB.
- Một markup/chapter: 10 MiB, đồng bộ với `EpubReadLimits`.
- Có giới hạn riêng cho resource, tổng số record, resource và chapter.
- Canonical path của mọi input/output phải nằm trong staging root của job.

Native return code được map sang domain enum như `DRM_PROTECTED`, `UNSUPPORTED_LAYOUT`, `UNSUPPORTED_FORMAT`, `CORRUPT_INPUT`, `RESOURCE_LIMIT_EXCEEDED`, `INSUFFICIENT_STORAGE`, `NATIVE_PROCESS_FAILED`. UI chỉ hiển thị string resource tương ứng.

## 11. Model và migration

Thêm domain enum:

```kotlin
enum class BookSourceFormat {
    EPUB,
    PRC,
    MOBI,
    AZW3
}
```

`DecodedMobiVariant` (`PALMDOC`, `MOBI7`, `KF8`, `HYBRID`) là type nội bộ của converter, không lưu trong `Book`.

`Book` và `BookEntity` thêm `sourceFormat`. Room migration 6→7 thêm column:

```sql
ALTER TABLE books
ADD COLUMN sourceFormat TEXT NOT NULL DEFAULT 'EPUB'
```

Migration cùng version tạo `book_import_jobs` và index cần thiết. Sách cũ nhận `EPUB`; không re-import hoặc re-index.

`filePath` luôn trỏ tới EPUB nội bộ. Với sách converted, filename và `Book.id` dựa trên `jobId`/UUID ổn định, không dựa trên title hoặc path do sách cung cấp.

## 12. Library UI

Ngay sau khi enqueue, Library hiển thị pending card có filename, badge nguồn, stage, progress và nút hủy. Job thất bại hiển thị lỗi typed và nút retry. Chỉ sách đã commit mới mở được.

Sách hoàn chỉnh hiển thị badge `EPUB`, `PRC`, `MOBI` hoặc `AZW3`; hành vi reader giống nhau. Warning không nghiêm trọng như CSS unsupported bị loại có thể hiển thị một lần sau import.

Mọi text mới đặt trong `core/designsystem/src/main/res/values/strings.xml`; không hardcode chuỗi UI trong Kotlin/XML.

## 13. Upload file lên backend

Upload không đi qua local converter:

- Picker nhận `*/*`.
- Gửi nguyên byte stream, filename và MIME từ `ContentResolver`.
- MIME không có thì dùng `application/octet-stream`.
- Không áp allowlist EPUB/PRC/MOBI/AZW3 hoặc giới hạn 100 MiB của local import.
- Vẫn xử lý thiếu dung lượng staging, network error và giới hạn do backend phản hồi.

Đổi tên `EpubImportScheduler`/`EpubImportWorker` thành `BookUploadScheduler`/`BookUploadWorker`. Giữ unique-work name/contract tương thích để không làm mất upload đang tồn tại khi cập nhật app.

## 14. Kiểm thử và release gate

Corpus fixture hợp pháp hoặc tự tạo phải bao phủ PalmDOC/PRC, MOBI7, KF8/AZW3, hybrid, tiếng Việt, cover, ảnh, CSS, TOC lồng nhau, internal links, thiếu metadata/TOC, truncated input, DRM, Print Replica, path traversal và resource bomb.

Các gate:

- Native C tests cho parse/detect/reconstruct/free trên mọi error path.
- Kotlin unit tests cho sniffer, packager, link rewrite, limits và error mapping.
- `core:epub` integration test cho strict validation, headers, load chapter và FTS.
- WorkManager test cho progress, cancel, retry, process death và orphan cleanup.
- Room migration test 6→7.
- Compose UI test cho pending card, badge, progress, warning, cancel và retry.
- End-to-end trên thiết bị ARM cho reader, progress, bookmark/highlight, search, TTS chunks, AI cache và Book Bible.
- Fuzz target cho binary header/JNI wrapper và XHTML/link rewriting.
- ASan/UBSan ở native CI host; không bật sanitizer trong release.

Performance gate trên thiết bị RAM thấp và tầm trung: không ANR/OOM với input gần 100 MiB, memory theo streaming, cancel giải phóng staging/native memory và EPUB đầu ra luôn load được chapter đầu.

## 15. License và ownership

Pin `libmobi` bằng version/commit cùng checksum. Lưu license, attribution, source tương ứng và build instructions. Trước release phải review tuân thủ LGPL-3.0-or-later, bao gồm cơ chế thay thế/relink native library phù hợp với cách phân phối ứng dụng.

`core:book-converter` là owner duy nhất của JNI, CMake, upstream patch và converter corpus. Reader, feature và storage không gọi `libmobi` trực tiếp.

Không chọn KindleUnpack hoặc calibre để nhúng vì Python/runtime lớn và GPLv3 tạo ràng buộc phân phối rộng hơn. Calibre đầy đủ cũng không hỗ trợ chạy trực tiếp trên Android.

## 16. Rủi ro chính

| Rủi ro | Impact | Giảm thiểu |
|---|---|---|
| Native crash hoặc memory corruption | Mất job, có thể crash process | Remote process, fuzzing, sanitizer, retry hữu hạn |
| MOBI7 thiếu TOC hoặc markup lỗi | Chapter/title/link không chính xác | Fallback splitting, warning, corpus malformed |
| EPUB output không hợp lệ | Reader lỗi hoặc feature lệch | Kotlin packager, strict validation, load chapter gate |
| Output phình lớn, thiếu storage | Import thất bại/OOM | Streaming, budget 500 MiB, preflight và cleanup |
| MIME/extension sai từ provider | Nhận diện sai | Header sniffing và byte-count thực |
| LGPL compliance thiếu | Rủi ro phát hành | Pin source, attribution, relink plan, review trước release |
| Progress native không mượt | UX tưởng bị treo | Progress theo record/resource/stage và notification foreground |

## 17. Tiêu chí chấp nhận

- Import thành công corpus PalmDOC, MOBI7, KF8/AZW3 và hybrid không DRM.
- File DRM/fixed-layout/corrupt bị từ chối bằng lỗi rõ ràng, không tạo `Book` hoặc file orphan.
- Sách converted dùng được toàn bộ tính năng hiện có như EPUB.
- Process death/cancel/retry không tạo dữ liệu dở dang.
- Reader và `EpubEngine` không phụ thuộc `libmobi` hoặc biết source container.
- Upload gửi nguyên file bất kỳ và không bị local ebook allowlist chặn.
- Không hardcode string UI; function mới có KDoc tiếng Việt.
- Build/test chạy cho `arm64-v8a` và `armeabi-v7a`; release hoàn tất license review.

## 18. Nhật ký quyết định

| ID | Quyết định | Phương án đã cân nhắc | Lý do |
|---|---|---|---|
| D-01 | Chỉ hỗ trợ ebook reflowable không DRM | Hỗ trợ DRM/fixed-layout/best effort mọi subtype | Giữ đầy đủ TTS/search/theme và tránh pipeline render riêng |
| D-02 | Convert một lần thành EPUB nội bộ | Parse native mỗi lần đọc; giữ cả source và EPUB | Tái sử dụng toàn bộ reader, giảm runtime coupling và không nhân đôi storage lâu dài |
| D-03 | Chuyển đổi hoàn toàn offline | Backend conversion; hybrid fallback server | Bảo vệ riêng tư và không phụ thuộc availability mạng/backend |
| D-04 | `libmobi` decoder + Kotlin `EpubPackager` | EPUB exporter mẫu của `mobitool`; parser Kotlin thuần | Decoder mature hơn, trong khi app vẫn kiểm soát EPUB và validation |
| D-05 | Module `core:book-converter` độc lập | Nhúng JNI vào `core:epub` hoặc `core:storage` | Giữ `EpubEngine` đơn nhiệm và tạo boundary thay decoder |
| D-06 | Staging manifest và streaming | Truyền toàn bộ DOM/bytes qua JNI | Giảm peak heap và làm rõ giới hạn tài nguyên |
| D-07 | WorkManager chain + Room job state | Coroutine trong ViewModel; service tự quản | Sống qua process death, UI có state bền vững và retry chuẩn |
| D-08 | Native converter chạy remote process | JNI trong main process | Cô lập SIGSEGV/OOM khỏi UI/TTS |
| D-09 | Lưu `BookSourceFormat` | Xem mọi output như EPUB; lưu decoder subtype | Đáp ứng badge nguồn mà không làm reader phụ thuộc format gốc |
| D-10 | Corpus, fuzzing và sanitizer là release gate | Chỉ test thủ công bằng sách thật | Binary parser không tin cậy cần regression/security coverage lâu dài |
| D-11 | Upload gửi nguyên file, tách khỏi local import | Convert trước upload; backend chỉ EPUB | Backend đã chịu trách nhiệm xử lý file và local conversion không nên thay đổi payload |

## 19. Tài liệu tham khảo

- `libmobi`: https://github.com/bfabiszewski/libmobi
- `libmobi` exported API: https://www.fabiszewski.net/libmobi/group__mobi__export.html
- `mobitool` EPUB example: https://github.com/bfabiszewski/libmobi/blob/public/tools/mobitool.c
- WorkManager long-running workers: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- WorkManager multiprocess: https://developer.android.com/reference/androidx/work/multiprocess/package-summary
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Amazon reflowable books: https://kdp.amazon.com/en_US/help/topic/GPNJPYK298J8TRRV
- Amazon format comparison: https://kdp.amazon.com/en_US/help/topic/G42HENP2VHSN8VW8
