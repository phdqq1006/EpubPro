# AI Thuần Việt

> Tổng hợp kiến thức về xử lý lại chương EPUB bằng Gemini, bảo toàn cấu trúc và thuật ngữ trong dự án.
>
> Cập nhật lần cuối: 2026-08-05

## Architecture

### Gemini trực tiếp với BYOK

Ứng dụng cá nhân gọi Gemini trực tiếp, không cần backend. UI chỉ hiển thị danh sách model Gemini đã được kiểm soát, còn Reader phụ thuộc vào AiVietnameseService để không gắn chặt giao diện với SDK hoặc HTTP client. API key mặc định theo máy nằm trong local.properties và không được đưa vào Git.

Các file chính: core/ai/src/main/java/com/epubpro/core/ai/GeminiClient.kt, core/ai/src/main/java/com/epubpro/core/ai/AiVietnameseService.kt và feature/reader/build.gradle.kts.

### Xử lý innerHTML theo block có ID

Nội dung chương được chia thành các block lá như p, heading, li và blockquote. Mỗi block có ID ổn định và được gửi dưới dạng JSON có cấu trúc. Kết quả chỉ được ghép lại khi validator xác nhận đúng ID, đúng thứ tự thẻ và giữ nguyên thuộc tính. Cách này bảo toàn định dạng tốt hơn việc lấy text thuần hoặc gửi toàn bộ HTML thô.

### Cache chương và checkpoint

Nội dung AI được lưu thành file, còn Room giữ metadata, trạng thái và khóa cache. Khóa cache gồm hash nội dung nguồn, model, prompt và rules. Mỗi batch hoàn tất sẽ cập nhật checkpoint; bản thay thế chỉ được công bố sau khi toàn bộ chương vượt qua validation. Trạng thái COMPLETE chỉ được ghi sau bước này.

### Quy tắc hai tầng

Rule hỗ trợ KEEP hoặc REPLACE và phạm vi GLOBAL hoặc BOOK. Rule theo sách được ưu tiên khi xung đột với rule toàn cục. Toàn bộ rule hiệu lực được đưa vào prompt, validator và config hash. Khi rule thay đổi, bản AI cũ được đánh dấu stale để người dùng chủ động chạy lại, tránh phát sinh API call ngoài ý muốn.

### Bảo vệ credential

Key do người dùng lưu được mã hóa AES-GCM bằng Android Keystore và đặt trong noBackupFilesDir. Key mặc định từ local.properties được đưa vào BuildConfig để tiện dùng cá nhân, nhưng có thể bị trích xuất từ APK nên không phù hợp để phát hành rộng. GeminiClient gửi key qua header x-goog-api-key và tuyệt đối không ghi key vào log.

## Bugs & Solutions

### Block lồng nhau bị trùng hoặc rỗng

Nguyên nhân là Element.select có thể trả cả chính phần tử gốc, khiến blockquote và p con cùng được xử lý. Cách sửa là chỉ chọn phần tử lá phù hợp và loại kết quả trùng với phần tử đang xét. Cần có unit test cho cấu trúc blockquote chứa p để khóa hành vi này.

### AI làm hỏng định dạng inline

Gán kết quả qua element.text làm mất các thẻ em, strong, a hoặc span. Cách sửa là xử lý innerHTML, đồng thời so sánh chữ ký cấu trúc đệ quy của phần tử trước và sau. Chỉ nội dung chữ được phép thay đổi; tên thẻ, thứ tự và thuộc tính phải giữ nguyên.

### Migration Room làm mất dữ liệu

Dùng fallbackToDestructiveMigration khi thêm bảng AI có thể xóa thư viện, bookmark và tiến độ đọc. Cách sửa là khai báo MIGRATION_2_3 rõ ràng, tạo các bảng và chỉ mục mới bằng SQL, sau đó đăng ký migration trong DatabaseModule.

### BuildConfig bị lỗi chuỗi Java

Giá trị lấy từ local.properties phải được tạo thành Java string literal hợp lệ trong Gradle Kotlin DSL. Việc ghép dấu nháy sai khiến generated BuildConfig không biên dịch. Cách ổn định là dùng 34.toChar() + value + 34.toChar(), đồng thời kiểm tra giá trị null và chuỗi rỗng.

## How-To

### Thêm model Gemini

Cập nhật SUPPORTED_GEMINI_MODELS trong lớp cấu hình AI, chọn lại model mặc định nếu cần và bảo đảm dropdown chỉ dùng danh sách này. Sau đó build ứng dụng và gọi thử model bằng key hợp lệ để phát hiện sớm model không tồn tại hoặc tài khoản chưa được cấp quyền.

### Cấu hình key mặc định cục bộ

Thêm GEMINI_API_KEY vào local.properties, đọc giá trị trong Gradle và truyền qua BuildConfig. UI dùng giá trị này để khởi tạo ô API key. Khi người dùng bấm lưu, key mới được chuyển sang kho mã hóa bằng Android Keystore. Trước khi hoàn tất, luôn quét git diff và file source theo tiền tố key để tránh lộ bí mật.

### Thêm loại rule mới

Cập nhật enum và domain model trước, sau đó mở rộng Room entity, mapper, repository và UI chỉnh rule. Tiếp theo bổ sung cách diễn đạt rule trong prompt, logic validation tương ứng và config hash. Cuối cùng thêm test cho độ ưu tiên GLOBAL và BOOK, xung đột rule và hành vi stale cache.

### Tích hợp biến thể nội dung vào Reader

Giữ riêng HTML gốc và HTML đã thuần Việt. Tạo một nguồn hiển thị duy nhất như displayedChapterHtml dựa trên trạng thái bật AI và cache hợp lệ. WebView và TTS phải cùng đọc nguồn này. Khi đổi biến thể, reset phân trang; nếu cache lỗi hoặc stale thì quay về nội dung gốc an toàn.

## Patterns

### Structured output trước khi merge

Mọi block gửi AI đều có ID và schema rõ ràng. Kết quả phải vượt qua các invariant về số lượng, ID, cấu trúc HTML và rule trước khi ghép vào EPUB. Không hiển thị trực tiếp HTML chưa được kiểm chứng từ model.

### Room giữ metadata, file giữ nội dung

Room phù hợp cho trạng thái truy vấn, checkpoint, hash và liên kết sách-chương. HTML dài nên nằm trong file riêng để tránh phình database. Ghi file tạm rồi rename nguyên tử giúp bản cache cũ vẫn dùng được nếu ứng dụng bị dừng giữa chừng.

### Retry theo batch và loại lỗi

Chỉ retry batch gặp lỗi tạm thời như timeout, mạng hoặc rate limit. Không retry mù với lỗi xác thực, hết quota hoặc model không hợp lệ. Số lần retry phải hữu hạn và mỗi batch thành công cần lưu checkpoint để lần chạy tiếp theo không trả phí lại cho phần đã hoàn tất.

### Một nguồn nội dung hiển thị

Reader, TTS, tìm kiếm trong chương và các thao tác phụ thuộc nội dung phải dùng cùng một biến thể đã chọn. Một thuộc tính trung tâm như displayedChapterHtml ngăn tình trạng màn hình đọc bản AI nhưng TTS lại phát bản gốc, đồng thời làm cho fallback và kiểm thử đơn giản hơn.
