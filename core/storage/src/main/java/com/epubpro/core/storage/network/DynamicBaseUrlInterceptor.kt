package com.epubpro.core.storage.network

import com.epubpro.core.storage.ServerPreferencesManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val serverPreferencesManager: ServerPreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentBaseUrlString = serverPreferencesManager.getBaseUrl()
        val newBaseUrl = currentBaseUrlString.toHttpUrlOrNull() ?: return chain.proceed(originalRequest)

        val originalUrl = originalRequest.url
        
        // Find relative path from original endpoint (excluding leading /api/v1 if present)
        val relativePathSegments = originalUrl.pathSegments
            .dropWhile { it.equals("api", ignoreCase = true) || it.equals("v1", ignoreCase = true) }

        val newUrlBuilder = newBaseUrl.newBuilder()
        val baseSegments = newBaseUrl.pathSegments.filter { it.isNotEmpty() }

        // Clear and rebuild clean path
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
