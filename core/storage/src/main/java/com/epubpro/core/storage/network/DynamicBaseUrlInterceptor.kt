package com.epubpro.core.storage.network

import com.epubpro.core.storage.ServerPreferencesManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor hỗ trợ ghi đè và tái cấu trúc URL của request Retrofit theo địa chỉ Base URL động lúc runtime.
 *
 * Xử lý bóc tách linh hoạt các path segments (bỏ qua tiền tố `/api/v1` mặc định của Retrofit) để ghép chính xác
 * vào Base URL mới mà không gây trùng lặp đường dẫn.
 */
@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverPreferencesManager: ServerPreferencesManager
) : Interceptor {

    /**
     * Chặn và biến đổi request để cập nhật Host, Port, Scheme và Base Path theo giá trị trong [ServerPreferencesManager].
     *
     * @param chain Chuỗi interceptor của OkHttp.
     * @return [Response] Phản hồi mạng sau khi đã thực thi request với URL mới.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentBaseUrlString = serverPreferencesManager.getBaseUrl()
        val newBaseUrl = currentBaseUrlString.toHttpUrlOrNull() ?: return chain.proceed(originalRequest)

        val originalUrl = originalRequest.url
        
        // Bóc tách relative path segments từ endpoint gốc (bỏ qua /api/v1 ở đầu nếu có)
        val relativePathSegments = originalUrl.pathSegments
            .dropWhile { it.equals("api", ignoreCase = true) || it.equals("v1", ignoreCase = true) }

        val newUrlBuilder = newBaseUrl.newBuilder()
        val baseSegments = newBaseUrl.pathSegments.filter { it.isNotEmpty() }

        // Xóa sạch các segments cũ và xây dựng lại path chuẩn
        for (i in 0 until newBaseUrl.pathSegments.size) {
            newUrlBuilder.removePathSegment(0)
        }
        for (seg in baseSegments) {
            newUrlBuilder.addPathSegment(seg)
        }
        for (seg in relativePathSegments) {
            newUrlBuilder.addPathSegment(seg)
        }
        
        newUrlBuilder.encodedQuery(originalUrl.encodedQuery)

        val newRequest = originalRequest.newBuilder()
            .url(newUrlBuilder.build())
            .build()

        return chain.proceed(newRequest)
    }
}
