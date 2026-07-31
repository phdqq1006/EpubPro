package com.epubpro.core.tts

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun downloadModel(modelName: String, onnxUrl: String, tokensUrl: String, dataUrl: String? = null) = withContext(Dispatchers.IO) {
        val ttsDir = File(context.filesDir, "tts_models/$modelName")
        if (!ttsDir.exists()) {
            ttsDir.mkdirs()
        }

        // Tải các file cần thiết
        downloadFile(onnxUrl, ttsDir, "model.onnx")
        downloadFile(tokensUrl, ttsDir, "tokens.txt")
        dataUrl?.let { downloadFile(it, ttsDir, "espeak_ng_data.zip") } // Nếu cần espeak-ng-data
    }

    private fun downloadFile(url: String, destDir: File, fileName: String): Long {
        val destFile = File(destDir, fileName)
        if (destFile.exists()) {
            return -1L // Đã tải
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Tải giọng đọc: $fileName")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        return downloadManager.enqueue(request)
    }

    fun isModelDownloaded(modelName: String): Boolean {
        val ttsDir = File(context.filesDir, "tts_models/$modelName")
        val onnx = File(ttsDir, "model.onnx")
        val tokens = File(ttsDir, "tokens.txt")
        return onnx.exists() && tokens.exists()
    }
    
    fun getModelPath(modelName: String): String {
        return File(context.filesDir, "tts_models/$modelName/model.onnx").absolutePath
    }
    
    fun getTokensPath(modelName: String): String {
        return File(context.filesDir, "tts_models/$modelName/tokens.txt").absolutePath
    }
}
