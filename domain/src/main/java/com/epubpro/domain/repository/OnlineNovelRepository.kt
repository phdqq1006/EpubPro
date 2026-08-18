package com.epubpro.domain.repository

import com.epubpro.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository quản lý toàn bộ các tương tác dữ liệu với máy chủ Backend của Kho Truyện Online.
 */
interface OnlineNovelRepository {

    /**
     * Lấy danh sách tóm tắt tất cả các bộ truyện hiện có trên server backend.
     *
     * @return [Result] chứa danh sách [OnlineNovelSummary] nếu thành công, hoặc lỗi nếu thất bại.
     */
    suspend fun getNovels(): Result<List<OnlineNovelSummary>>

    /**
     * Lấy thông tin chi tiết và danh sách toàn bộ các chương của một bộ truyện.
     *
     * @param novelId Mã định danh duy nhất của bộ truyện (ví dụ: `pham-nhan-tu-tien`).
     * @return [Result] chứa [OnlineNovelDetail] nếu thành công, hoặc lỗi nếu thất bại.
     */
    suspend fun getNovelDetail(novelId: String): Result<OnlineNovelDetail>

    /**
     * Lấy nội dung văn bản của một chương cụ thể để đọc trực tuyến.
     *
     * @param novelId Mã định danh duy nhất của bộ truyện.
     * @param chapterIndex Thứ tự của chương cần lấy (1-indexed).
     * @param version Phiên bản văn bản cần lấy: `"translated"` (Bản dịch tiếng Việt) hoặc `"original"` (Bản gốc).
     * @return [Result] chứa [OnlineChapterContent] nếu thành công.
     */
    suspend fun getChapterContent(
        novelId: String,
        chapterIndex: Int,
        version: String = "translated"
    ): Result<OnlineChapterContent>

    /**
     * Tải về toàn bộ file sách định dạng `.epub` của một bộ truyện dưới dạng luồng dữ liệu (Stream).
     *
     * @param novelId Mã định danh của bộ truyện cần tải.
     * @param saveFileName Tên file `.epub` khi lưu vào bộ nhớ cục bộ.
     * @return [Flow] phát ra các trạng thái tải [DownloadState] (tiến độ, thành công hoặc lỗi).
     */
    fun downloadEpub(novelId: String, saveFileName: String): Flow<DownloadState>

    /**
     * Yêu cầu máy chủ backend thực hiện dịch tự động bằng AI cho một chương truyện chưa dịch.
     *
     * @param novelId Mã định danh của bộ truyện.
     * @param chapterIndex Thứ tự của chương cần dịch.
     * @param apiKey Khóa API của dịch vụ AI do người dùng cung cấp.
     * @param provider Tên nhà cung cấp AI (ví dụ: `gemini`, `claude`, `openai`).
     * @param model Tên mô hình AI sử dụng (ví dụ: `gemini-1.5-flash`).
     * @return [Result] chứa [TranslateChapterResult] với thông tin chương sau khi dịch.
     */
    suspend fun translateChapter(
        novelId: String,
        chapterIndex: Int,
        apiKey: String,
        provider: String,
        model: String
    ): Result<TranslateChapterResult>

    /**
     * Tải (Upload) trực tiếp một file sách `.epub` từ thiết bị lên máy chủ backend.
     *
     * @param filePath Đường dẫn tuyệt đối của file `.epub` trên thiết bị.
     * @param isTranslated Đánh dấu file này đã được dịch hoàn chỉnh hay là bản gốc thô.
     * @return [Result] chứa thông điệp phản hồi từ máy chủ nếu upload thành công.
     */
    suspend fun uploadEpub(
        filePath: String,
        isTranslated: Boolean
    ): Result<String>

    /**
     * Lấy luồng phát ra địa chỉ Base URL hiện tại của backend server.
     *
     * @return [Flow] phát ra chuỗi URL API backend.
     */
    fun getBaseUrl(): Flow<String>

    /**
     * Cập nhật địa chỉ Base URL mới cho backend server.
     *
     * @param url Chuỗi URL API mới (ví dụ: `https://epubbackend.onrender.com/api/v1/`).
     */
    suspend fun setBaseUrl(url: String)

    /**
     * Kiểm tra ping kết nối mạng tới máy chủ backend bằng cách gọi thử endpoint lấy danh sách truyện.
     *
     * @return [Result] trả về `true` nếu kết nối thông suốt, `false` hoặc exception nếu thất bại.
     */
    suspend fun testServerConnection(): Result<Boolean>
}
