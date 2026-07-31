package com.epubpro.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null

    /**
     * Khởi tạo engine với file model từ bộ nhớ thiết bị.
     * Sherpa-ONNX API dùng Kotlin data class (no-arg constructor + property setters).
     * Khi assetManager == null, thư viện sẽ dùng newFromFile() để đọc theo đường dẫn tuyệt đối.
     *
     * @param onnxPath   Đường dẫn tuyệt đối tới file .onnx
     * @param tokensPath Đường dẫn tuyệt đối tới file tokens.txt
     * @param dataDirPath Đường dẫn thư mục espeak-ng-data (để trống nếu không dùng)
     */
    suspend fun initialize(
        onnxPath: String,
        tokensPath: String = "",
        dataDirPath: String = ""
    ) = withContext(Dispatchers.IO) {
        release()

        // OfflineTtsVitsModelConfig — no-arg constructor + setters
        val vitsConfig = OfflineTtsVitsModelConfig()
        vitsConfig.model = onnxPath
        vitsConfig.tokens = tokensPath
        vitsConfig.dataDir = dataDirPath

        // OfflineTtsModelConfig — no-arg constructor + setters
        val modelConfig = OfflineTtsModelConfig()
        modelConfig.vits = vitsConfig
        modelConfig.numThreads = 2
        modelConfig.debug = false

        // OfflineTtsConfig — no-arg constructor + setters
        val ttsConfig = OfflineTtsConfig()
        ttsConfig.model = modelConfig
        ttsConfig.maxNumSentences = 1

        // assetManager = null → Sherpa-ONNX gọi newFromFile (đường dẫn tuyệt đối)
        tts = OfflineTts(assetManager = null, config = ttsConfig)

        val sampleRate = tts!!.sampleRate()   // sampleRate() là fun, không phải property

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }

    suspend fun speak(text: String, speed: Float = 1.0f) = withContext(Dispatchers.IO) {
        val currentTts = tts ?: return@withContext
        val audio = currentTts.generate(text, sid = 0, speed = speed)

        val samples = audio.samples
        // Convert float array to 16-bit PCM byte array
        val pcmBytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            var sample = samples[i]
            if (sample > 1.0f) sample = 1.0f
            if (sample < -1.0f) sample = -1.0f
            val s = (sample * 32767).toInt().toShort()
            pcmBytes[i * 2] = (s.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        audioTrack?.write(pcmBytes, 0, pcmBytes.size)
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        tts?.release()
        tts = null
    }
}
