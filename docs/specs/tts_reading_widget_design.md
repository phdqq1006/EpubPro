# Thiết kế Widget Đọc sách Full màn hình (Text Reader Widget)

## Tóm tắt hiểu biết

- Xây dựng Android home-screen widget kích thước lớn (mặc định 4x3 / 4x4, hỗ trợ resize full màn hình).
- Đã xác nhận cơ chế trải nghiệm: Đọc trực tiếp nội dung văn bản đoạn/trang sách ngay trên màn hình chính mà không cần mở ứng dụng.
- Tương tác lật trang bằng **Vùng chạm Nổi (Touch Zones)**:
  - Nửa bên Phải (65% diện tích): Chạm vào để lật sang đoạn/trang tiếp theo (`ACTION_WIDGET_NEXT_PARAGRAPH`).
  - Nửa bên Trái (35% diện tích): Chạm vào để lùi về đoạn/trang trước đó (`ACTION_WIDGET_PREV_PARAGRAPH`).
- Thanh Header hiển thị Bìa sách, Tên sách, Tên chương và nút mở ứng dụng.
- Thanh Footer hiển thị % tiến trình đọc, vị trí đoạn văn và các nút chuyển chương nhanh.
- Tự động chuyển sang chương tiếp theo khi đọc đến cuối chương, hoặc lùi về chương trước khi ở đầu chương.
- Đồng bộ 2 chiều tức thì với `TtsPlaybackSnapshotStore` và `ReadingProgress` của dự án.

## Giả định

- Dùng `RemoteViews` + `AppWidgetProvider`, tương thích `minSdk 26` tới `targetSdk 34+`.
- Tái sử dụng và gộp thông tin vào `TtsWidgetStateStore` (bổ sung `paragraphText`, `paragraphIndex`, `totalParagraphs`).
- Khung văn bản được phân chia theo khối đoạn văn (`paragraphIndex`), đảm bảo nguyên vẹn câu văn và từ ngữ không bị ngắt đôi.
- Khi App bị kill (Cold Start), Widget tự đọc I/O file EPUB qua `goAsync()` và render trong ngầm (< 50ms).

## Nhật ký quyết định

| Quyết định | Phương án thay thế | Lý do lựa chọn |
|---|---|---|
| Dùng chung `TtsWidgetStateStore` hiện có | Tạo Store / DB mới riêng cho Widget đọc | Tiết kiệm bộ nhớ, đồng bộ 100% giữa Widget Nghe 4x1 và Widget Đọc 4x4, không duplicate code. |
| Chạm lật trang theo đoạn (`paragraphIndex`) | Cắt chữ theo số ký tự cố định / Vuốt swipe | Tương thích 100% với Android `RemoteViews`, không bao giờ bị ngắt đôi từ văn bản, lật mượt không giật. |
| Vùng chạm Nửa Trái (35%) & Nửa Phải (65%) | Chỉ bấm nút mũi tên Prev/Next nhỏ | Trải nghiệm chạm lật trang tự nhiên như Kindle / Kobo / App đọc thật trên màn hình chính. |
| Tự chuyển chương ở đầu/cuối chương | Khóa lật trang khi hết chương | Giúp người dùng đọc liên tục từ chương này sang chương khác mà không bị gián đoạn. |

## Kiến trúc & Thiết kế Chi tiết

### 1. Thành phần

- `TtsReadingWidgetProvider`: nhận lifecycle/update callback và xử lý lật trang/đổi chương ngầm cho Widget Đọc sách.
- `tts_reading_widget.xml`: layout XML 4x3 / 4x4, màu Dark Mode Premium.
- `tts_reading_widget_info.xml`: metadata kích thước (targetCellWidth=4, targetCellHeight=3, minWidth=250dp, minHeight=180dp).
- `TtsWidgetStateStore`: mở rộng chứa `paragraphText`, `paragraphIndex`, `totalParagraphs`.

### 2. Mô hình dữ liệu mở rộng (`TtsWidgetState`)

```kotlin
data class TtsWidgetState(
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val playbackStatus: TtsWidgetPlaybackStatus = TtsWidgetPlaybackStatus.IDLE,
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasSnapshot: Boolean = false,
    val coverPath: String? = null,
    // Thông tin phục vụ Widget Đọc sách Full màn hình
    val paragraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val paragraphText: String = ""
)
```

### 3. Action

- `ACTION_WIDGET_NEXT_PARAGRAPH`
- `ACTION_WIDGET_PREV_PARAGRAPH`
- `ACTION_WIDGET_NEXT_CHAPTER`
- `ACTION_WIDGET_PREV_CHAPTER`

Mỗi action sử dụng explicit PendingIntent tới `TtsReadingWidgetProvider`, `FLAG_IMMUTABLE` và có request code riêng biệt.

## Kiểm thử

- Provider render đúng văn bản với đoạn đầu, đoạn giữa và đoạn cuối chương.
- Chạm nửa bên phải lật sang đoạn tiếp theo.
- Chạm nửa bên trái lùi về đoạn trước đó.
- Lật ở cuối chương tự chuyển sang đoạn đầu chương kế tiếp.
- Lùi ở đầu chương tự chuyển sang đoạn cuối chương trước đó.
- Mở App từ Header Widget nhảy đúng cuốn sách, chương và vị trí đoạn văn đang đọc trên Widget.
- Force-stop hoặc reboot máy: Widget khôi phục vị trí đọc chính xác từ Snapshot.
