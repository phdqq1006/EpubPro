package com.epubpro.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences =
        context.getSharedPreferences("server_settings_prefs", Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow(readBaseUrl())
    val baseUrlFlow: StateFlow<String> = _baseUrl.asStateFlow()

    fun getBaseUrl(): String = _baseUrl.value

    fun saveBaseUrl(url: String) {
        val normalized = normalizeUrl(url)
        preferences.edit().putString(KEY_BASE_URL, normalized).apply()
        _baseUrl.value = normalized
    }

    private fun readBaseUrl(): String {
        val stored = preferences.getString(KEY_BASE_URL, null)
        if (stored == null || stored.contains("r2.dev") || stored.contains("trycloudflare.com") || stored.contains("workers.dev")) {
            preferences.edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply()
            return DEFAULT_BASE_URL
        }
        return normalizeUrl(stored)
    }

    private fun normalizeUrl(url: String): String {
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

    companion object {
        const val DEFAULT_BASE_URL = "https://epubbackend.onrender.com/api/v1/"
        const val PRESET_RENDER = "https://epubbackend.onrender.com/api/v1/"
        const val PRESET_EMULATOR = "http://10.0.2.2:8000/api/v1/"
        const val PRESET_LOCALHOST = "http://127.0.0.1:8000/api/v1/"
        private const val KEY_BASE_URL = "server_base_url"
    }
}
