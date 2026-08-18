package com.epubpro.core.storage.network

import com.epubpro.core.storage.ServerPreferencesManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

class DynamicBaseUrlInterceptorTest {

    @Test
    fun testInterceptReplacesBaseUrlAndPreservesPathAndQueryParams() {
        val serverPreferencesManager = mock(ServerPreferencesManager::class.java)
        `when`(serverPreferencesManager.getBaseUrl()).thenReturn("https://epubbackend.onrender.com/api/v1/")

        val interceptor = DynamicBaseUrlInterceptor(serverPreferencesManager)

        val originalRequest = Request.Builder()
            .url("http://10.0.2.2:8000/api/v1/library/novels/pham-nhan-tu-tien/chapters/1/content?version=translated")
            .build()

        var executedRequest: Request? = null

        val fakeChain = object : Interceptor.Chain {
            override fun request(): Request = originalRequest
            override fun proceed(request: Request): Response {
                executedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun connection(): Connection? = null
            override fun call(): Call = throw UnsupportedOperationException()
            override fun connectTimeoutMillis(): Int = 1000
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 1000
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 1000
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        }

        interceptor.intercept(fakeChain)

        val expectedUrl = "https://epubbackend.onrender.com/api/v1/library/novels/pham-nhan-tu-tien/chapters/1/content?version=translated"
        assertEquals(expectedUrl, executedRequest?.url.toString())
    }
}
