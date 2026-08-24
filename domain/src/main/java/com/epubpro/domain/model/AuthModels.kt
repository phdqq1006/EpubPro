package com.epubpro.domain.model

/**
 * Nhà cung cấp phương thức xác thực tài khoản.
 */
enum class AuthProvider {
    /** Đăng nhập bằng Email và Mật khẩu (Supabase / Local) */
    EMAIL,
    /** Đăng nhập bằng tài khoản Google */
    GOOGLE,
    /** Đăng nhập ở chế độ Khách trải nghiệm */
    GUEST
}

/**
 * Thông tin hồ sơ tài khoản người dùng trong hệ thống EpubPro.
 *
 * @property id Mã định danh duy nhất của người dùng.
 * @property email Địa chỉ email của người dùng.
 * @property displayName Tên hiển thị công khai của người dùng.
 * @property avatarUrl Đường dẫn ảnh đại diện (nếu có).
 * @property token Mã token xác thực phiên đăng nhập (Access Token JWT).
 * @property refreshToken Mã token làm mới phiên đăng nhập (Refresh Token).
 * @property provider Phương thức xác thực đã sử dụng.
 * @property membershipTier Hạng thành viên (ví dụ: Thành viên EpubPro, VIP).
 * @property readingStreakDays Số ngày đọc sách liên tiếp (streak).
 * @property totalReadBooks Tổng số cuốn sách đã đọc.
 * @property totalReadHours Tổng số giờ đã dành để đọc sách trên ứng dụng.
 * @property joinedDate Thời điểm tạo tài khoản hoặc tham gia.
 */
data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val provider: AuthProvider = AuthProvider.EMAIL,
    val membershipTier: String = "Thành viên EpubPro",
    val readingStreakDays: Int = 1,
    val totalReadBooks: Int = 0,
    val totalReadHours: Double = 0.0,
    val joinedDate: String = ""
)

/**
 * Trạng thái xác thực phiên người dùng của toàn ứng dụng.
 */
sealed class AuthState {
    /** Đang tải hoặc kiểm tra trạng thái đăng nhập từ bộ nhớ */
    object Loading : AuthState()

    /** Chưa đăng nhập (Người dùng vãng lai hoặc đã đăng xuất) */
    object Unauthenticated : AuthState()

    /** Đã đăng nhập thành công với thông tin tài khoản người dùng */
    data class Authenticated(val user: User) : AuthState()
}
