# ĐẶC TẢ THIẾT KẾ VÀ NHẬT KÝ QUYẾT ĐỊNH
## Tính năng: AI thuần Việt cho chương EPUB

---

## 1. Tóm tắt hiểu biết

- Xây dựng tính năng **AI thuần Việt** cho nội dung truyện đã là tiếng Việt nhưng còn máy móc, khó hiểu hoặc chưa tự nhiên.
- Người dùng mục tiêu ở giai đoạn đầu là chủ ứng dụng; AI được gọi trực tiếp từ Android, không có backend trung gian.
- Người dùng tự nhập Gemini API key và được chọn model; model mặc định là `Gemini 2.5 Flash`.
- Xử lý thủ công từng chương đang đọc, lưu kết quả để không phát sinh lại chi phí ngoài ý muốn.
- EPUB gốc không bị chỉnh sửa. Người đọc có thể chuyển giữa `Bản gốc` và `AI thuần Việt`.
- TTS đọc đúng phiên bản đang hiển thị.
- Hỗ trợ quy tắc mặc định, quy tắc chung và quy tắc riêng từng sách để bảo toàn danh xưng, tên riêng và thuật ngữ.
- Không thuộc phạm vi bản đầu: xử lý hàng loạt cả cuốn, xuất EPUB mới, đồng bộ nhiều thiết bị, backend, nhiều nhà cung cấp AI.

## 2. Giả định và yêu cầu phi chức năng

1. Ứng dụng phục vụ một người dùng và lượng yêu cầu thấp; không cần hệ thống hàng đợi phía server.
2. Thiết bị có kết nối Internet khi gọi Gemini. Nội dung gốc và cache vẫn đọc được khi ngoại tuyến.
3. Người dùng chịu chi phí và hạn mức của Gemini API key do họ cung cấp.
4. Xử lý chương không được khóa giao diện; tiến trình phải được hiển thị và có thể tiếp tục sau gián đoạn.
5. Không có giới hạn dung lượng EPUB do tính năng AI đặt ra. Chương dài được tự động chia thành nhiều nhóm văn bản.
6. API key phải được bảo vệ bằng Android Keystore, không xuất hiện trong log, database, file sao lưu hoặc thông báo lỗi.
7. Kết quả AI phải giữ nguyên cấu trúc chương, thứ tự đoạn, ảnh, liên kết và các thành phần không phải văn bản.
8. Thiết kế do dự án sở hữu và bảo trì. Lớp gọi AI cần độc lập với Reader để có thể thêm nhà cung cấp khác về sau.

## 3. Phạm vi MVP

### Bao gồm

- Cấu hình Gemini API key.
- Danh sách model Gemini được ứng dụng hỗ trợ; mặc định `Gemini 2.5 Flash`.
- Nút **AI thuần Việt** trong Reader.
- Xử lý chương hiện tại theo yêu cầu của người dùng.
- Hiển thị tiến trình theo từng phần và cho phép hủy.
- Cache cục bộ kết quả và tiến trình trung gian.
- Chuyển đổi `Bản gốc | AI thuần Việt`.
- Tạo lại hoặc xóa bản AI của chương.
- Quy tắc chung và quy tắc riêng từng sách.
- TTS sử dụng đúng nội dung đang hiển thị.

### Không bao gồm

- Tự động xử lý chương tiếp theo hoặc toàn bộ sách.
- Sửa trực tiếp hay xuất lại file EPUB.
- Đồng bộ cache, API key hoặc quy tắc qua thiết bị khác.
- Backend proxy, tài khoản người dùng hoặc thanh toán trong ứng dụng.
- OpenAI, Claude hay endpoint tùy chỉnh.
- Tự động tạo bảng thuật ngữ bằng AI.

## 4. Kiến trúc

```text
Chương EPUB hiện tại
        |
        v
Trích xuất các khối văn bản có ID ổn định
        |
        v
Áp dụng quy tắc và chia thành các nhóm an toàn
        |
        v
Gọi Gemini trực tiếp bằng API key của người dùng
        |
        v
Kiểm tra, thử lại và ghép kết quả theo ID
        |
        v
Lưu file nội dung AI + metadata trong Room
        |
        v
Reader và TTS dùng phiên bản đang được chọn
```

### Trách nhiệm theo module

- `feature:reader`: điểm vào AI, trạng thái xử lý, chuyển phiên bản, thao tác tạo lại/xóa và tích hợp TTS.
- `core:ai`: Gemini client, model catalog, prompt, chia nhóm, kiểm tra phản hồi, retry và điều phối tác vụ.
- `core:storage`: lưu nội dung AI và dữ liệu tạm trong vùng nội bộ của ứng dụng.
- `core:database`: metadata cache, trạng thái tác vụ, model, phiên bản prompt và phiên bản quy tắc.
- `domain`: model và repository contract, không phụ thuộc trực tiếp vào Gemini.

Giao diện chỉ hỗ trợ Gemini trong MVP, nhưng `domain` và `core:ai` không để Reader phụ thuộc vào kiểu phản hồi cụ thể của Gemini.

## 5. Mô hình quy tắc

### Thứ tự ưu tiên

1. Quy tắc riêng của sách.
2. Quy tắc chung.
3. Quy tắc mặc định của ứng dụng.

Quy tắc cùng tầng không được trùng thuật ngữ. Nếu phát hiện trùng, ứng dụng cảnh báo và yêu cầu sửa trước khi lưu.

### Loại quy tắc

- `KEEP`: giữ nguyên thuật ngữ, ví dụ `Long -> Long` hoặc `Long Vương -> Long Vương`.
- `REPLACE`: bắt buộc dùng cách viết chỉ định, ví dụ `Huyền khí -> linh lực`.

Mỗi quy tắc gồm phạm vi, nội dung nguồn, hành động, nội dung thay thế nếu có và tùy chọn phân biệt chữ hoa/chữ thường. Việc khớp áp dụng cho từ hoặc cụm từ hoàn chỉnh, không thay một phần bên trong từ dài hơn.

Quy tắc mặc định yêu cầu AI:

- Chỉ làm câu văn tự nhiên, rõ nghĩa và dễ đọc hơn.
- Không tóm tắt, thêm tình tiết hoặc thay đổi quan hệ nhân quả.
- Không tự dịch tên riêng, danh xưng, địa danh, môn phái, cảnh giới hoặc chiêu thức.
- Không tự chuyển thuật ngữ Hán Việt thành nghĩa thuần Việt.
- Giữ ngôi kể, sắc thái và mức độ trang trọng của đoạn gốc.

## 6. Pipeline xử lý chương

1. Đọc HTML của chương hiện tại.
2. Trích xuất các khối văn bản có ID ổn định; không gửi ảnh, CSS hoặc toàn bộ HTML cho AI.
3. Tính chữ ký cấu hình từ model, prompt và bộ quy tắc hiệu lực.
4. Chia khối văn bản thành các nhóm an toàn theo số đoạn và token ước lượng.
5. Gửi từng nhóm kèm quy tắc và một lượng nhỏ ngữ cảnh chỉ đọc từ nhóm trước.
6. Yêu cầu Gemini trả JSON có cấu trúc `[{"id":"p-01","text":"..."}]`.
7. Kiểm tra ID, số lượng đoạn, độ dài bất thường, thuật ngữ bắt buộc và nội dung ngoài định dạng.
8. Thử lại riêng nhóm lỗi tối đa hai lần.
9. Lưu từng nhóm hoàn tất để có thể tiếp tục sau gián đoạn.
10. Chỉ công bố bản AI hoàn chỉnh khi tất cả nhóm vượt qua kiểm tra.

Token là đơn vị đo độ dài đầu vào và đầu ra của model. Người dùng không cần cấu hình token; ứng dụng tự chia chương và chỉ hiển thị tiến trình dạng `Đang xử lý phần 2/5`.

## 7. Cache và tính nhất quán

Khóa cache logic gồm:

```text
book fingerprint + chapter identity + source hash
+ provider/model + prompt version + effective rules hash
```

- Room lưu metadata, trạng thái, tiến trình, model, phiên bản cấu hình và đường dẫn file.
- Nội dung AI và dữ liệu tạm được lưu thành file trong vùng dữ liệu nội bộ.
- Thay model, prompt, nội dung gốc hoặc quy tắc sẽ không âm thầm ghi đè kết quả cũ.
- Kết quả dùng cấu hình cũ được đánh dấu; người dùng quyết định có tạo lại hay không.
- Xóa sách phải dọn metadata, file AI và dữ liệu tạm liên quan.

## 8. Trải nghiệm người dùng

### Cài đặt AI

- Nhập hoặc thay Gemini API key.
- Chọn model từ danh sách được hỗ trợ.
- Kiểm tra kết nối trước khi lưu.
- Quản lý quy tắc chung.
- Xóa API key và dữ liệu AI khi cần.

### Trong Reader

- Biểu tượng AI có tooltip **AI thuần Việt** mở bảng điều khiển.
- Nếu chưa cấu hình, hướng người dùng tới Cài đặt AI.
- Nếu chưa có kết quả, hiển thị chương, model, số quy tắc áp dụng và nút **Thuần Việt chương này**.
- Khi đang chạy, hiển thị phần hiện tại/tổng số phần và cho phép hủy.
- Khi hoàn tất, hiển thị lựa chọn `Bản gốc | AI thuần Việt` cùng thao tác tạo lại, xem thông tin và xóa.
- Nếu cấu hình đã thay đổi, hiển thị `Được tạo bằng cấu hình cũ`; không tự gọi lại API.
- TTS luôn đọc phiên bản hiện đang hiển thị.

## 9. Xử lý lỗi và trường hợp biên

- Chương chỉ có ảnh hoặc không có văn bản: không gửi yêu cầu AI.
- Chương ngắn: xử lý trong một nhóm.
- Chương dài: tự động chia nhóm, không yêu cầu người dùng hiểu token.
- Đoạn chỉ có ký hiệu, số hoặc cấu trúc đặc biệt: giữ nguyên khi không cần xử lý.
- Sai API key, hết hạn mức, mất mạng hoặc model không khả dụng: dừng an toàn, giữ dữ liệu đã hoàn tất và cung cấp hành động phù hợp.
- Phản hồi thiếu đoạn, thừa đoạn, sai JSON hoặc vi phạm thuật ngữ: thử lại riêng nhóm lỗi.
- Đóng màn hình, xoay thiết bị hoặc tiến trình bị gián đoạn: không mất các nhóm đã hoàn tất.
- Đổi model khi tác vụ đang chạy: tác vụ hiện tại giữ model ban đầu; model mới áp dụng cho tác vụ sau.
- EPUB gốc luôn là phương án dự phòng và phải đọc được bất kể trạng thái AI.

## 10. Chiến lược kiểm thử

- Unit test parser và quá trình ghép khối để bảo toàn HTML, ảnh, liên kết và thứ tự.
- Unit test chia nhóm cho chương ngắn, dài, đoạn rất lớn và nội dung rỗng.
- Unit test thứ tự ưu tiên, khớp từ hoàn chỉnh và xung đột quy tắc.
- Contract test cho phản hồi Gemini hợp lệ, thiếu ID, trùng ID, sai JSON và có nội dung thừa.
- Test kiểm tra `KEEP` và `REPLACE`, gồm chữ hoa/chữ thường.
- Repository test cho cache hit, cache cũ, tiếp tục tác vụ và dọn dữ liệu khi xóa sách.
- UI test cho cấu hình API, tiến trình, hủy, retry và chuyển bản gốc/bản AI.
- Integration test xác nhận TTS đọc đúng phiên bản đang hiển thị.
- Kiểm tra log và backup để bảo đảm API key không bị lộ.

## 11. Rủi ro chính

- Gọi API trực tiếp khiến key tồn tại trên thiết bị. Android Keystore giảm rủi ro nhưng không bảo vệ tuyệt đối trên thiết bị root hoặc bị can thiệp.
- Model có thể thay đổi hành vi hoặc ngừng khả dụng; cần lỗi rõ ràng và catalog model có thể cập nhật trong phiên bản ứng dụng.
- AI có thể làm sai nghĩa dù phản hồi hợp lệ về cấu trúc; prompt, kiểm tra thuật ngữ và khả năng chuyển về bản gốc là lớp bảo vệ chính.
- Ước lượng token không hoàn toàn chính xác; cần biên an toàn và khả năng chia lại nhóm khi API từ chối kích thước.
- Ngữ cảnh giữa các nhóm có thể thiếu nhất quán; cung cấp đoạn ngữ cảnh trước dưới dạng chỉ đọc và không cho phép AI trả lại đoạn đó.

## 12. Nhật ký quyết định

| Quyết định | Phương án đã xem xét | Lý do lựa chọn |
| :--- | :--- | :--- |
| Tên tính năng là **AI thuần Việt** | Dịch truyện, AI trau chuốt, AI thuần Việt hóa | Phản ánh đúng việc cải thiện bản tiếng Việt đã có, không khiến người dùng hiểu là dịch từ ngôn ngữ khác. |
| Gọi Gemini trực tiếp từ Android bằng key người dùng | Backend proxy, model chạy cục bộ | Phù hợp ứng dụng cá nhân và không cần vận hành server; local model chưa phù hợp chất lượng, dung lượng và hiệu năng MVP. |
| Có chọn model trong MVP | Cố định một model, hỗ trợ nhiều provider | Người dùng muốn quyền lựa chọn; giới hạn trong Gemini để giữ độ phức tạp vừa phải. |
| Mặc định `Gemini 2.5 Flash` | Các model Gemini khác | Cân bằng chất lượng, tốc độ và chi phí cho xử lý văn bản dài. |
| Xử lý thủ công từng chương | Xử lý cả sách, hàng đợi tự động | Kiểm soát chi phí, dễ phục hồi lỗi và phù hợp nhu cầu đọc hiện tại. |
| Không sửa EPUB gốc | Ghi đè hoặc xuất EPUB ngay | Bảo toàn dữ liệu nguồn và cho phép chuyển đổi tức thời giữa hai phiên bản. |
| Quy tắc chung và riêng từng sách | Chỉ một loại quy tắc | Quy tắc chung giảm lặp; quy tắc sách xử lý đúng ngữ cảnh và được ưu tiên khi xung đột. |
| Hỗ trợ `KEEP` và `REPLACE` | Chỉ prompt văn xuôi tự do | Có thể biểu diễn rõ cả yêu cầu giữ nguyên và chuẩn hóa thuật ngữ. |
| Gửi các khối text có ID thay vì HTML | Gửi HTML nguyên chương, gửi plain text toàn chương | Giữ cấu trúc EPUB, giảm token và cho phép kiểm tra/ghép kết quả chắc chắn. |
| Cache file + metadata Room | Chỉ Room, chỉ file | Tránh database phình lớn nhưng vẫn truy vấn được trạng thái và tính hợp lệ của cache. |
| Không tự tạo lại khi cấu hình đổi | Tự động cập nhật cache | Ngăn phát sinh chi phí API ngoài ý muốn. |

## 13. Hướng bàn giao triển khai

1. Bổ sung domain models, repository contracts và cấu trúc metadata/cache.
2. Xây dựng lưu trữ API key, cài đặt model và màn hình kiểm tra kết nối.
3. Xây dựng mô hình quy tắc chung/riêng cùng UI quản lý.
4. Xây dựng Gemini client, prompt, text block parser, chunker, validator và retry.
5. Tích hợp trạng thái xử lý và lựa chọn phiên bản vào Reader.
6. Đồng bộ nội dung đang hiển thị với TTS.
7. Bổ sung kiểm thử theo từng lớp và kiểm thử tích hợp cho luồng chương hoàn chỉnh.
