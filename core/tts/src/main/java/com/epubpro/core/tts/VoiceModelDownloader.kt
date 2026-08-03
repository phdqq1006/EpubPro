package com.epubpro.core.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Danh sách các file cốt lõi cần thiết cho espeak-ng với ngôn ngữ Tiếng Việt (vi_VN).
 *
 * Cấu trúc thư mục espeak-ng-data:
 * - phondata, phonindex, phontab, intonations: Dữ liệu phoneme chung
 * - vi_dict: Từ điển phiên âm tiếng Việt
 * - lang/aav/vi: Định nghĩa ngôn ngữ tiếng Việt
 * - voices/!v: Các biến thể giọng đọc (m1, m2, f1, f2, Nguyen...)
 */
private val ESPEAK_NG_CORE_FILES = listOf(
    // Core phoneme data
    "espeak-ng-data/intonations",
    "espeak-ng-data/phondata",
    "espeak-ng-data/phondata-manifest",
    "espeak-ng-data/phonindex",
    "espeak-ng-data/phontab",
    // Vietnamese + English dict
    "espeak-ng-data/vi_dict",
    "espeak-ng-data/en_dict",
    // Vietnamese language voice definitions
    "espeak-ng-data/lang/aav/vi",
    "espeak-ng-data/lang/aav/vi-VN-x-central",
    "espeak-ng-data/lang/aav/vi-VN-x-south",
    // Root lang files
    "espeak-ng-data/lang/eu",
    "espeak-ng-data/lang/ko",
    // Common voice variant files used by VITS Piper
    "espeak-ng-data/voices/!v/m1",
    "espeak-ng-data/voices/!v/m2",
    "espeak-ng-data/voices/!v/m3",
    "espeak-ng-data/voices/!v/m4",
    "espeak-ng-data/voices/!v/m7",
    "espeak-ng-data/voices/!v/f1",
    "espeak-ng-data/voices/!v/f2",
    "espeak-ng-data/voices/!v/f3",
    "espeak-ng-data/voices/!v/f4",
    "espeak-ng-data/voices/!v/Nguyen",
)

private const val ESPEAK_NG_BASE_URL =
    "https://huggingface.co/csukuangfj/vits-piper-vi_VN-vais1000-medium/resolve/main"

@Singleton
class VoiceModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EpubProTTS"
    }

    /**
     * Tải mô hình giọng đọc ONNX và tokens.txt vào bộ nhớ trong của ứng dụng (context.filesDir).
     * Đồng thời tự động tải espeak-ng-data nếu chưa có.
     */
    suspend fun downloadModel(
        modelName: String,
        onnxUrl: String,
        tokensUrl: String,
        dataUrl: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val ttsDir = File(context.filesDir, "tts_models/$modelName")
        if (!ttsDir.exists()) ttsDir.mkdirs()

        val finalOnnx = File(ttsDir, "model.onnx")
        val finalTokens = File(ttsDir, "tokens.txt")

        Log.d(TAG, "downloadModel() modelName=$modelName, ttsDir=${ttsDir.absolutePath}")

        try {
            // 1. Tải tokens.txt
            if (!finalTokens.exists() || finalTokens.length() == 0L) {
                Log.d(TAG, "Downloading tokens.txt...")
                val tempTokens = File(ttsDir, "tokens.txt.tmp")
                downloadDirect(tokensUrl, tempTokens)
                if (!tempTokens.renameTo(finalTokens)) {
                    tempTokens.copyTo(finalTokens, overwrite = true)
                    tempTokens.delete()
                }
                Log.d(TAG, "tokens.txt done (${finalTokens.length()} bytes)")
            }

            // 2. Tải model.onnx (chiếm 95% tiến trình)
            if (!finalOnnx.exists() || finalOnnx.length() < 5_000_000L) {
                Log.d(TAG, "Downloading model.onnx...")
                val tempOnnx = File(ttsDir, "model.onnx.tmp")
                downloadDirect(onnxUrl, tempOnnx) { progress ->
                    onProgress(progress * 0.95f)  // 0..95%
                }
                if (!tempOnnx.renameTo(finalOnnx)) {
                    tempOnnx.copyTo(finalOnnx, overwrite = true)
                    tempOnnx.delete()
                }
                Log.d(TAG, "model.onnx done (${finalOnnx.length()} bytes)")
            }

            onProgress(0.95f)

            // 3. Tải espeak-ng-data (5% cuối)
            downloadEspeakNgDataIfNeeded { progress ->
                onProgress(0.95f + progress * 0.05f)
            }

            onProgress(1.0f)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Exception in downloadModel() for '$modelName'", t)
            File(ttsDir, "model.onnx.tmp").delete()
            File(ttsDir, "tokens.txt.tmp").delete()
            false
        }
    }

    /**
     * Tải các file espeak-ng-data cần thiết cho Sherpa-ONNX VITS Piper tiếng Việt.
     * Giữ đúng cấu trúc thư mục con (lang/aav/vi, voices/!v/m1...).
     * Tự động liên kết file vi sang voices/vi và voices/!v/vi để eSpeak-ng set voice không bị lỗi.
     */
    suspend fun downloadEspeakNgDataIfNeeded(onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val espeakRootDir = File(context.filesDir, "espeak-ng-data")

        Log.d(TAG, "downloadEspeakNgDataIfNeeded: checking readiness...")
        if (isEspeakDataReady()) {
            Log.d(TAG, "espeak-ng-data already complete, skipping download")
            return@withContext
        }

        Log.d(TAG, "Downloading espeak-ng-data core files (${ESPEAK_NG_CORE_FILES.size} files)...")

        val total = ESPEAK_NG_CORE_FILES.size
        ESPEAK_NG_CORE_FILES.forEachIndexed { index, relativePath ->
            val subPath = relativePath.removePrefix("espeak-ng-data/")
            val destFile = File(espeakRootDir, subPath)

            destFile.parentFile?.mkdirs()

            if (!destFile.exists() || destFile.length() == 0L) {
                val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
                val url = "$ESPEAK_NG_BASE_URL/$relativePath"
                Log.d(TAG, "Downloading $subPath ...")
                try {
                    downloadDirect(url, tempFile)
                    if (!tempFile.renameTo(destFile)) {
                        tempFile.copyTo(destFile, overwrite = true)
                        tempFile.delete()
                    }
                    Log.d(TAG, "$subPath done (${destFile.length()} bytes)")
                } catch (e: Exception) {
                    tempFile.delete()
                    Log.e(TAG, "Failed to download $subPath: ${e.message}")
                }
            } else {
                Log.d(TAG, "$subPath already OK (${destFile.length()} bytes)")
            }
            onProgress((index + 1).toFloat() / total.toFloat())
        }

        // Tạo alias cho voices/vi và voices/!v/vi để eSpeak-ng luôn tìm thấy voice tiếng Việt
        ensureVoiceAlias(espeakRootDir)

        Log.d(TAG, "espeak-ng-data download complete. Ready=${isEspeakDataReady()}")
    }

    private fun ensureVoiceAlias(espeakRootDir: File) {
        val viLangFile = File(espeakRootDir, "lang/aav/vi")
        if (viLangFile.exists() && viLangFile.length() > 0L) {
            val voiceVi = File(espeakRootDir, "voices/vi")
            voiceVi.parentFile?.mkdirs()
            if (!voiceVi.exists() || voiceVi.length() == 0L) {
                viLangFile.copyTo(voiceVi, overwrite = true)
                Log.d(TAG, "Created voice alias at ${voiceVi.absolutePath}")
            }
            val voiceVVi = File(espeakRootDir, "voices/!v/vi")
            voiceVVi.parentFile?.mkdirs()
            if (!voiceVVi.exists() || voiceVVi.length() == 0L) {
                viLangFile.copyTo(voiceVVi, overwrite = true)
                Log.d(TAG, "Created voice alias at ${voiceVVi.absolutePath}")
            }
        }
    }

    fun getEspeakDataDir(): String {
        return File(context.filesDir, "espeak-ng-data").absolutePath
    }

    /**
     * Kiểm tra sự hiện diện của các file eSpeak-ng quan trọng:
     * phondata (core) + vi_dict (từ điển tiếng Việt) + lang/aav/vi và voices/vi (voice tiếng Việt)
     */
    fun isEspeakDataReady(): Boolean {
        val base = File(context.filesDir, "espeak-ng-data")
        val phondata = File(base, "phondata")
        val viDict = File(base, "vi_dict")
        val viLang = File(base, "lang/aav/vi")
        val voiceVi = File(base, "voices/vi")
        return phondata.exists() && phondata.length() > 100_000L
                && viDict.exists() && viDict.length() > 10_000L
                && viLang.exists() && viLang.length() > 50L
                && voiceVi.exists() && voiceVi.length() > 50L
    }

    private fun downloadDirect(urlStr: String, destFile: File, onProgress: ((Float) -> Unit)? = null) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 60000
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode} for $urlStr")
        }

        val fileLength = connection.contentLengthLong
        connection.inputStream.use { input ->
            destFile.outputStream().use { output ->
                val buf = ByteArray(16384)
                var total = 0L
                var count: Int
                var lastPct = -1
                while (input.read(buf).also { count = it } != -1) {
                    total += count
                    output.write(buf, 0, count)
                    if (fileLength > 0 && onProgress != null) {
                        val pct = ((total * 100) / fileLength).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(total.toFloat() / fileLength.toFloat())
                        }
                    }
                }
            }
        }
    }

    fun isModelDownloaded(modelName: String): Boolean {
        val ttsDir = File(context.filesDir, "tts_models/$modelName")
        val onnx = File(ttsDir, "model.onnx")
        val tokens = File(ttsDir, "tokens.txt")
        val downloaded = onnx.exists() && onnx.length() > 5_000_000L && tokens.exists() && tokens.length() > 100L
        if (!downloaded) {
            if (onnx.exists() && onnx.length() < 5_000_000L) onnx.delete()
            if (tokens.exists() && tokens.length() < 100L) tokens.delete()
        }
        return downloaded
    }

    fun getModelPath(modelName: String) =
        File(context.filesDir, "tts_models/$modelName/model.onnx").absolutePath

    fun getTokensPath(modelName: String) =
        File(context.filesDir, "tts_models/$modelName/tokens.txt").absolutePath
}
