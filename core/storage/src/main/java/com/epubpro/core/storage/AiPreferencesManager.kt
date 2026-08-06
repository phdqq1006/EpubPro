package com.epubpro.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.epubpro.domain.model.AiSettings
import com.epubpro.domain.model.DEFAULT_AI_MODEL_ID
import com.epubpro.domain.model.SUPPORTED_GEMINI_MODELS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences =
        context.getSharedPreferences("ai_settings_prefs", Context.MODE_PRIVATE)
    private val credentialStore = AiCredentialStore(context)

    private val _settings = MutableStateFlow(readSettings())
    val settingsFlow: StateFlow<AiSettings> = _settings.asStateFlow()

    fun getSettings(): AiSettings = _settings.value

    @Synchronized
    fun saveConfiguration(apiKey: String?, modelId: String) {
        val supportedModel = modelId.takeIf { candidate ->
            SUPPORTED_GEMINI_MODELS.any { it.id == candidate }
        } ?: DEFAULT_AI_MODEL_ID

        if (!apiKey.isNullOrBlank()) {
            credentialStore.save(apiKey.trim())
        }
        preferences.edit().putString(KEY_MODEL_ID, supportedModel).apply()
        _settings.value = AiSettings(
            modelId = supportedModel,
            hasApiKey = credentialStore.load() != null
        )
    }

    fun getApiKey(): String? = credentialStore.load()

    @Synchronized
    fun clearApiKey() {
        credentialStore.clear()
        _settings.value = readSettings()
    }

    private fun readSettings(): AiSettings {
        val storedModel = preferences.getString(KEY_MODEL_ID, DEFAULT_AI_MODEL_ID)
            ?: DEFAULT_AI_MODEL_ID
        val modelId = storedModel.takeIf { candidate ->
            SUPPORTED_GEMINI_MODELS.any { it.id == candidate }
        } ?: DEFAULT_AI_MODEL_ID
        return AiSettings(modelId = modelId, hasApiKey = credentialStore.load() != null)
    }

    private companion object {
        const val KEY_MODEL_ID = "model_id"
    }
}

private class AiCredentialStore(context: Context) {
    private val credentialFile = File(context.noBackupFilesDir, FILE_NAME)

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encodedIv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encodedValue = Base64.encodeToString(
            cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )

        val temporaryFile = File(credentialFile.parentFile, "$FILE_NAME.tmp")
        temporaryFile.writeText(
            listOf(encodedIv, encodedValue).joinToString(System.lineSeparator()),
            Charsets.UTF_8
        )
        if (credentialFile.exists() && !credentialFile.delete()) {
            temporaryFile.delete()
            error("Không thể cập nhật API key")
        }
        if (!temporaryFile.renameTo(credentialFile)) {
            temporaryFile.delete()
            error("Không thể lưu API key")
        }
    }

    fun load(): String? {
        if (!credentialFile.exists()) return null
        return runCatching {
            val values = credentialFile.readLines(Charsets.UTF_8)
            require(values.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(values[0], Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(values[1], Base64.NO_WRAP)),
                Charsets.UTF_8
            ).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun clear() {
        if (credentialFile.exists()) {
            credentialFile.delete()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE_NAME = "ai_credentials"
        const val KEY_ALIAS = "epubpro_ai_api_key"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
