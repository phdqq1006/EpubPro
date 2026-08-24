package com.epubpro.core.storage.network

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object biểu diễn cấu hình xác thực được trả về từ Backend API (`GET /api/auth/config`).
 *
 * @property mode Chế độ xác thực ("supabase" hoặc "local").
 * @property authRequired Cờ báo hiệu backend có bắt buộc xác thực hay không.
 * @property supabaseUrl Địa chỉ endpoint Supabase Auth.
 * @property supabasePublishableKey Khóa public anon/publishable key của Supabase.
 */
data class AuthConfigDto(
    @SerializedName("mode") val mode: String = "supabase",
    @SerializedName("auth_required") val authRequired: Boolean = true,
    @SerializedName("supabase_url") val supabaseUrl: String? = null,
    @SerializedName("supabase_publishable_key") val supabasePublishableKey: String? = null
) {
    val isSupabaseMode: Boolean get() = mode.equals("supabase", ignoreCase = true)
    val isLocalMode: Boolean get() = mode.equals("local", ignoreCase = true)
}

/**
 * Request body cho API đăng nhập Email/Password của Supabase.
 */
data class SupabaseLoginRequestDto(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/**
 * Request body cho API đăng ký tài khoản mới của Supabase.
 */
data class SupabaseSignupRequestDto(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("data") val data: Map<String, Any>? = null
)

/**
 * Request body cho API khôi phục mật khẩu qua Email của Supabase.
 */
data class SupabaseRecoverRequestDto(
    @SerializedName("email") val email: String
)

/**
 * Request body cho API làm mới token bằng Refresh Token của Supabase.
 */
data class SupabaseRefreshTokenRequestDto(
    @SerializedName("refresh_token") val refreshToken: String
)

/**
 * Phản hồi xác thực thành công từ Supabase Auth trả về bộ token và thông tin người dùng.
 *
 * @property accessToken Mã JWT token dùng để truy cập API backend.
 * @property tokenType Loại token (mặc định là bearer).
 * @property expiresIn Thời hạn của access token (giây).
 * @property expiresAt Mốc thời gian Unix timestamp khi access token hết hạn.
 * @property refreshToken Mã token dùng để làm mới access token khi hết hạn.
 * @property user Thông tin người dùng cơ bản từ Supabase.
 */
data class SupabaseTokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String? = "bearer",
    @SerializedName("expires_in") val expiresIn: Long = 3600,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("user") val user: SupabaseUserDto? = null
)

/**
 * Phản hồi sau khi đăng ký tài khoản thành công từ Supabase.
 */
data class SupabaseSignupResponseDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 3600,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("user") val user: SupabaseUserDto? = null
)

/**
 * Thông tin định danh người dùng trả về từ Supabase Auth.
 */
data class SupabaseUserDto(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("role") val role: String? = "authenticated",
    @SerializedName("user_metadata") val userMetadata: Map<String, Any>? = null
)

/**
 * Cấu trúc thông báo lỗi trả về từ Supabase Auth khi xác thực thất bại.
 */
data class SupabaseErrorDto(
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("message") val message: String? = null
) {
    /** Lấy thông báo lỗi thân thiện nhất */
    fun getDisplayMessage(): String {
        return errorDescription ?: message ?: msg ?: error ?: "Đăng nhập thất bại."
    }
}
