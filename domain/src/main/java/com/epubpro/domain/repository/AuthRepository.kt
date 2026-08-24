package com.epubpro.domain.repository

import com.epubpro.domain.model.AuthState
import com.epubpro.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Interface Repository quản lý trạng thái xác thực và các thao tác tài khoản người dùng.
 */
interface AuthRepository {

    /**
     * Luồng phát ra trạng thái xác thực hiện tại của người dùng trong ứng dụng.
     */
    val authState: Flow<AuthState>

    /**
     * Lấy thông tin tài khoản người dùng hiện tại nếu đã đăng nhập.
     *
     * @return [User] nếu đã đăng nhập, hoặc null nếu chưa xác thực.
     */
    suspend fun getCurrentUser(): User?

    /**
     * Thực hiện đăng nhập bằng tài khoản Email và Mật khẩu (thông qua Supabase hoặc Local Backend).
     *
     * @param email Địa chỉ email của người dùng.
     * @param password Mật khẩu tài khoản.
     * @return [Result] chứa [User] nếu đăng nhập thành công, hoặc lỗi nếu thất bại.
     */
    suspend fun login(email: String, password: String): Result<User>

    /**
     * Đăng ký tài khoản mới trong hệ thống.
     *
     * @param email Địa chỉ email đăng ký.
     * @param password Mật khẩu mới.
     * @param displayName Tên hiển thị người dùng muốn đặt.
     * @return [Result] chứa [User] vừa được tạo thành công, hoặc lỗi nếu thất bại.
     */
    suspend fun register(email: String, password: String, displayName: String): Result<User>

    /**
     * Thực hiện đăng nhập bằng tài khoản Google.
     *
     * @param idToken Token xác thực từ Google Sign-In nếu có.
     * @param email Địa chỉ email Google nếu đã lấy được.
     * @param displayName Tên tài khoản Google nếu đã lấy được.
     * @return [Result] chứa [User] nếu đăng nhập thành công.
     */
    suspend fun loginWithGoogle(
        idToken: String? = null,
        email: String? = null,
        displayName: String? = null
    ): Result<User>

    /**
     * Đăng nhập ở chế độ Khách (Guest) để trải nghiệm nhanh mà không cần tài khoản.
     *
     * @return [Result] chứa [User] tài khoản khách tạm thời.
     */
    suspend fun loginAsGuest(): Result<User>

    /**
     * Làm mới token truy cập (Access Token) bằng Refresh Token hiện có.
     *
     * @return [Result] chứa [User] với token mới, hoặc lỗi nếu không thể làm mới.
     */
    suspend fun refreshToken(): Result<User>

    /**
     * Đăng xuất khỏi tài khoản hiện tại, thu hồi token trên Supabase và xóa phiên làm việc.
     *
     * @return [Result] thành công sau khi đã đăng xuất.
     */
    suspend fun logout(): Result<Unit>

    /**
     * Cập nhật thông tin hồ sơ người dùng.
     *
     * @param displayName Tên hiển thị mới.
     * @param avatarUrl Đường dẫn ảnh đại diện mới nếu có.
     * @return [Result] chứa [User] đã được cập nhật.
     */
    suspend fun updateProfile(displayName: String, avatarUrl: String?): Result<User>

    /**
     * Gửi yêu cầu khôi phục mật khẩu tới địa chỉ email đã đăng ký.
     *
     * @param email Địa chỉ email cần khôi phục mật khẩu.
     * @return [Result] thành công nếu yêu cầu được tiếp nhận.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
}
