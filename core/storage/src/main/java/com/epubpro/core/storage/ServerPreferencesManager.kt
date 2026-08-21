package com.epubpro.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trình quản lý lưu trữ cài đặt cấu hình địa chỉ Server Backend vào SharedPreferences.
 *
 * Cho phép người dùng chuyển đổi địa chỉ máy chủ API linh hoạt giữa các môi trường:
 * Cloud Server (Render), Máy ảo (10.0.2.2), Localhost (127.0.0.1) hoặc IP mạng LAN.
 */
@Singleton
class ServerPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences =
        context.getSharedPreferences("server_settings_prefs", Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(readBaseUrl())
    val baseUrlFlow: StateFlow<String> = _baseUrl.asStateFlow()

    /**
     * Lấy giá trị Base URL hiện tại đang được cấu hình.
     *
     * @return Chuỗi URL API đang hoạt động (ví dụ: `https://epubbackend.onrender.com/api/v1/`).
     */
    fun getBaseUrl(): String = _baseUrl.value

    /**
     * Lưu trữ và phát ra địa chỉ Base URL mới cho toàn ứng dụng.
     *
     * @param url Chuỗi URL mới do người dùng nhập hoặc chọn từ preset.
     */
    fun saveBaseUrl(url: String) {
        val normalized = normalizeUrl(url)
        preferences.edit().putString(KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    /**
     * Đọc giá trị Base URL đã lưu từ bộ nhớ SharedPreferences, tự động làm sạch các URL cũ không còn hợp lệ.
     *
     * @return Chuỗi Base URL đã được chuẩn hóa.
     */
    private fun readBaseUrl(): String {
        val stored = preferences.getString(KEY_BASE_URL, null)
        if (stored == null || stored.contains("r2.dev") || stored.contains("trycloudflare.com") || stored.contains("workers.dev")) {
            preferences.edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply()
            return DEFAULT_BASE_URL
        }
        return normalizeUrl(stored)
    }

    /**
     * Chuẩn hóa định dạng chuỗi URL: loại bỏ khoảng trắng, bổ sung `http://` nếu thiếu và đảm bảo kết thúc bằng dấu gạch chéo `/`.
     *
     * @param url Chuỗi URL đầu vào cần chuẩn hóa.
     * @return Chuỗi URL hoàn chỉnh và hợp lệ.
     */
    internal fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isBlank()) {
            clean = DEFAULT_BASE_URL
        }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }

    /**
     * Lấy khóa xác thực Book Bible Client Key nếu có.
     *
     * @return Chuỗi token hoặc null nếu chưa thiết lập.
     */
    fun getBookBibleClientKey(): String? = preferences.getString(KEY_BOOK_BIBLE_CLIENT_KEY, null)?.takeIf { it.isNotBlank() }

    /**
     * Lưu trữ khóa xác thực Book Bible Client Key.
     *
     * @param key Khóa token do quản trị viên cung cấp hoặc null để xóa.
     */
    fun saveBookBibleClientKey(key: String?) {
        preferences.edit().putString(KEY_BOOK_BIBLE_CLIENT_KEY, key?.trim()).apply()
    }

    /**
     * Lấy khóa LLM API Key (Gemini/Claude) cá nhân nếu có.
     *
     * @return Chuỗi API Key hoặc null.
     */
    fun getLlmApiKey(): String? = preferences.getString(KEY_LLM_API_KEY, null)?.takeIf { it.isNotBlank() }

    /**
     * Lưu trữ khóa LLM API Key cá nhân.
     *
     * @param key Chuỗi API Key hoặc null để xóa.
     */
    fun saveLlmApiKey(key: String?) {
        preferences.edit().putString(KEY_LLM_API_KEY, key?.trim()).apply()
    }

    /**
     * Lấy tên model LLM tùy chỉnh.
     *
     * @return Tên model (ví dụ: "gemini-2.5-flash") hoặc null nếu dùng mặc định.
     */
    fun getLlmModel(): String? = preferences.getString(KEY_LLM_MODEL, null)?.takeIf { it.isNotBlank() }

    /**
     * Lưu trữ tên model LLM tùy chỉnh.
     *
     * @param model Tên model hoặc null để dùng mặc định của backend.
     */
    fun saveLlmModel(model: String?) {
        preferences.edit().putString(KEY_LLM_MODEL, model?.trim()).apply()
    }

    companion object {
        /** Địa chỉ máy chủ Cloud Production mặc định */
        const val DEFAULT_BASE_URL = "https://epubbackend.onrender.com/api/v1/"

        /** Preset cấu hình cho Cloud Server (Render) */
        const val PRESET_RENDER = "https://epubbackend.onrender.com/api/v1/"

        /** Preset cấu hình cho Android Emulator trỏ về máy tính host */
        const val PRESET_EMULATOR = "http://10.0.2.2:8000/api/v1/"

        /** Preset cấu hình cho Localhost / adb reverse */
        const val PRESET_LOCALHOST = "http://127.0.0.1:8000/api/v1/"

        private const val KEY_BASE_URL = "server_base_url"
        private const val KEY_BOOK_BIBLE_CLIENT_KEY = "book_bible_client_key"
        private const val KEY_LLM_API_KEY = "book_bible_llm_api_key"
        private const val KEY_LLM_MODEL = "book_bible_llm_model"
    }
}
