package com.epubpro.core.storage.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit Service Interface cho các endpoint cấu hình Auth từ Backend EpubPro.
 */
interface BackendAuthApiService {

    /**
     * Lấy cấu hình xác thực từ Backend API qua URL trực tiếp (ví dụ: `https://epubbackend.onrender.com/api/auth/config`).
     */
    @GET
    suspend fun getAuthConfigFromUrl(
        @Url url: String,
        @Header(DynamicBaseUrlInterceptor.SKIP_DYNAMIC_BASE_URL_HEADER) skipDynamic: String = "true"
    ): AuthConfigDto

    /**
     * Lấy cấu hình xác thực từ Backend API theo relative path chuẩn.
     *
     * URL: `{{BASE_URL}}/auth/config`
     */
    @GET("auth/config")
    suspend fun getAuthConfig(): AuthConfigDto
}

/**
 * Retrofit Service Interface tương tác trực tiếp với các endpoint xác thực của Supabase Auth.
 */
interface SupabaseAuthApiService {

    /**
     * Đăng nhập bằng Email và Password trực tiếp tới Supabase.
     *
     * Endpoint: `{supabase_url}/auth/v1/token?grant_type=password`
     *
     * @param url Đường dẫn đầy đủ của endpoint token Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param body Thông tin email và mật khẩu.
     * @return [SupabaseTokenResponseDto] chứa token và thông tin user.
     */
    @POST
    suspend fun loginWithPassword(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: SupabaseLoginRequestDto
    ): SupabaseTokenResponseDto

    /**
     * Đăng ký tài khoản người dùng mới trên Supabase.
     *
     * Endpoint: `{supabase_url}/auth/v1/signup`
     *
     * @param url Đường dẫn đầy đủ của endpoint signup Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param body Thông tin email, mật khẩu và metadata (tên hiển thị).
     * @return [SupabaseSignupResponseDto] chứa kết quả đăng ký.
     */
    @POST
    suspend fun signUp(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: SupabaseSignupRequestDto
    ): SupabaseSignupResponseDto

    /**
     * Gửi email khôi phục mật khẩu thông qua Supabase.
     *
     * Endpoint: `{supabase_url}/auth/v1/recover`
     *
     * @param url Đường dẫn đầy đủ của endpoint recover Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param body Email người dùng cần nhận liên kết khôi phục.
     */
    @POST
    suspend fun recoverPassword(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: SupabaseRecoverRequestDto
    ): Response<ResponseBody>

    /**
     * Làm mới token truy cập bằng Refresh Token.
     *
     * Endpoint: `{supabase_url}/auth/v1/token?grant_type=refresh_token`
     *
     * @param url Đường dẫn đầy đủ của endpoint token Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param body Thông tin refresh_token.
     * @return [SupabaseTokenResponseDto] chứa bộ token mới.
     */
    @POST
    suspend fun refreshToken(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Body body: SupabaseRefreshTokenRequestDto
    ): SupabaseTokenResponseDto

    /**
     * Lấy thông tin tài khoản người dùng hiện tại từ Supabase.
     *
     * Endpoint: `{supabase_url}/auth/v1/user`
     *
     * @param url Đường dẫn đầy đủ của endpoint user Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param authorization Bearer access_token của người dùng.
     */
    @GET
    suspend fun getUser(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): SupabaseUserDto

    /**
     * Đăng xuất và thu hồi phiên xác thực trên Supabase.
     *
     * Endpoint: `{supabase_url}/auth/v1/logout`
     *
     * @param url Đường dẫn đầy đủ của endpoint logout Supabase.
     * @param apiKey Khóa public anon/publishable key của Supabase.
     * @param authorization Bearer token của người dùng cần đăng xuất.
     */
    @POST
    suspend fun logout(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}
