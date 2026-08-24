package com.epubpro.core.storage.network

import com.epubpro.core.storage.AuthPreferencesManager
import com.epubpro.core.storage.ServerPreferencesManager
import com.epubpro.domain.repository.AuthRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

import com.epubpro.core.storage.bookbible.BookBibleRepositoryImpl
import com.epubpro.domain.repository.BookBibleRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindModule {
    @Binds
    @Singleton
    abstract fun bindOnlineNovelRepository(
        impl: OnlineNovelRepositoryImpl
    ): OnlineNovelRepository

    @Binds
    @Singleton
    abstract fun bindBookBibleRepository(
        impl: BookBibleRepositoryImpl
    ): BookBibleRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        fallbackDns: FallbackDns,
        serverPreferencesManager: ServerPreferencesManager,
        authPreferencesManager: AuthPreferencesManager
    ): OkHttpClient {
        val prettyLogger = HttpLoggingInterceptor.Logger { message ->
            val tag = "API_HTTP"
            when {
                message.startsWith("--> POST") || message.startsWith("--> GET") || message.startsWith("--> PUT") || message.startsWith("--> DELETE") -> {
                    android.util.Log.d(tag, "┌────── 🚀 [HTTP REQUEST] ──────────────────────────────────────────────────")
                    android.util.Log.d(tag, "│ $message")
                }
                message.startsWith("--> END") -> {
                    android.util.Log.d(tag, "└───────────────────────────────────────────────────────────────────────────")
                }
                message.startsWith("<-- 200") || message.startsWith("<-- 201") || message.startsWith("<-- 202") || message.startsWith("<-- 204") -> {
                    android.util.Log.d(tag, "┌────── ✅ [HTTP RESPONSE SUCCESS] ─────────────────────────────────────────")
                    android.util.Log.d(tag, "│ $message")
                }
                message.startsWith("<-- 4") || message.startsWith("<-- 5") -> {
                    android.util.Log.e(tag, "┌────── ❌ [HTTP RESPONSE ERROR] ───────────────────────────────────────────")
                    android.util.Log.e(tag, "│ $message")
                }
                message.startsWith("<-- END") -> {
                    android.util.Log.d(tag, "└───────────────────────────────────────────────────────────────────────────")
                }
                else -> {
                    android.util.Log.d(tag, "│ $message")
                }
            }
        }

        val loggingInterceptor = HttpLoggingInterceptor(prettyLogger).apply {
            redactHeader("X-Book-Bible-Client-Key")
            redactHeader("X-Api-Key")
            redactHeader("Authorization")
            redactHeader("apikey")
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val authInterceptor = okhttp3.Interceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            val builder = original.newBuilder()

            // Gắn Authorization Bearer token vào các request gọi tới Backend EpubPro
            if (!url.contains("supabase.co")) {
                val token = authPreferencesManager.getAuthToken()
                if (!token.isNullOrBlank() && original.header("Authorization") == null) {
                    builder.header("Authorization", "Bearer $token")
                    android.util.Log.d("API_HTTP", "🔑 [AUTH] Đã đính kèm Authorization Bearer (${token.take(8)}...) vào request: ${original.method} ${original.url.encodedPath}")
                } else if (token.isNullOrBlank()) {
                    android.util.Log.w("API_HTTP", "⚠️ [AUTH] Request ${original.method} ${original.url.encodedPath} KHÔNG có Token (Chưa đăng nhập / Token trống)")
                }
            }

            if (url.contains("book-bible")) {
                val clientKey = serverPreferencesManager.getBookBibleClientKey()
                if (!clientKey.isNullOrBlank() && original.header("X-Book-Bible-Client-Key") == null) {
                    builder.header("X-Book-Bible-Client-Key", clientKey)
                }

                val apiKey = serverPreferencesManager.getLlmApiKey()
                if (!apiKey.isNullOrBlank() && original.header("X-Api-Key") == null) {
                    builder.header("X-Api-Key", apiKey)
                }

                val model = serverPreferencesManager.getLlmModel()
                if (!model.isNullOrBlank() && original.header("X-Model") == null) {
                    builder.header("X-Model", model)
                }
            }

            chain.proceed(builder.build())
        }

        return OkHttpClient.Builder()
            .dns(fallbackDns)
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
        serverPreferencesManager: ServerPreferencesManager
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(serverPreferencesManager.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideOnlineNovelApiService(retrofit: Retrofit): OnlineNovelApiService {
        return retrofit.create(OnlineNovelApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBookBibleApiService(retrofit: Retrofit): BookBibleApiService {
        return retrofit.create(BookBibleApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBackendAuthApiService(retrofit: Retrofit): BackendAuthApiService {
        return retrofit.create(BackendAuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSupabaseAuthApiService(retrofit: Retrofit): SupabaseAuthApiService {
        return retrofit.create(SupabaseAuthApiService::class.java)
    }
}
