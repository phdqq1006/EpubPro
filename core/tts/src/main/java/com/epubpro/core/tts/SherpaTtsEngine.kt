package com.epubpro.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SherpaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 22050

    companion object {
        private const val TAG = "EpubProTTS"
    }

    /**
     * Khởi tạo engine với file model từ bộ nhớ thiết bị.
     * Dùng dataDir (espeak-ng-data) thay vì lexicon để VITS Piper phoneme frontend hoạt động đúng.
     */
    suspend fun initialize(
        onnxPath: String,
        tokensPath: String = "",
        lexiconPath: String = "",
        dataDirPath: String = ""
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "initialize() onnx=$onnxPath dataDirPath=$dataDirPath")
        release()

        val onnxFile = File(onnxPath)
        val tokensFile = File(tokensPath)

        Log.d(TAG, "ONNX: exists=${onnxFile.exists()}, size=${onnxFile.length()}")
        Log.d(TAG, "Tokens: exists=${tokensFile.exists()}, size=${tokensFile.length()}")

        if (!onnxFile.exists() || onnxFile.length() < 1_000_000L) {
            throw FileNotFoundException("ONNX file invalid (${onnxFile.length()} bytes): $onnxPath")
        }
        if (!tokensFile.exists() || tokensFile.length() == 0L) {
            throw FileNotFoundException("tokens.txt missing: $tokensPath")
        }

        // Kiểm tra espeak-ng-data
        if (dataDirPath.isNotEmpty()) {
            val phondata = File(dataDirPath, "phondata")
            val viDict = File(dataDirPath, "vi_dict")
            Log.d(TAG, "espeak-ng-data dir=$dataDirPath, phondata=${phondata.exists()}(${phondata.length()}), vi_dict=${viDict.exists()}(${viDict.length()})")
        }

        try {
            val vitsConfig = OfflineTtsVitsModelConfig()
            vitsConfig.model = onnxPath
            vitsConfig.tokens = tokensPath
            // Dùng dataDir để VITS Piper phoneme frontend hoạt động đúng với tiếng Việt
            vitsConfig.dataDir = dataDirPath
            // Chỉ set lexicon nếu có và không dùng dataDir
            vitsConfig.lexicon = if (dataDirPath.isEmpty() && lexiconPath.isNotEmpty()) lexiconPath else ""
            vitsConfig.dictDir = ""
            vitsConfig.noiseScale = 0.667f
            vitsConfig.noiseScaleW = 0.8f
            vitsConfig.lengthScale = 1.0f

            val modelConfig = OfflineTtsModelConfig()
            modelConfig.vits = vitsConfig
            modelConfig.numThreads = 2
            modelConfig.debug = true

            val ttsConfig = OfflineTtsConfig()
            ttsConfig.model = modelConfig
            ttsConfig.ruleFsts = ""
            ttsConfig.ruleFars = ""
            ttsConfig.maxNumSentences = 1

            Log.d(TAG, "Creating OfflineTts with dataDir='$dataDirPath', lexicon='${vitsConfig.lexicon}'")
            tts = OfflineTts(assetManager = null, config = ttsConfig)

            currentSampleRate = try {
                tts?.sampleRate() ?: 22050
            } catch (t: Throwable) {
                Log.e(TAG, "Error getting sampleRate", t); 22050
            }
            Log.d(TAG, "OfflineTts created OK. sampleRate=$currentSampleRate Hz")

            if (currentSampleRate <= 0) {
                Log.e(TAG, "Invalid sample rate: $currentSampleRate")
                return@withContext
            }

            val minBuf = AudioTrack.getMinBufferSize(
                currentSampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(currentSampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                minBuf,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
            Log.d(TAG, "AudioTrack ready, minBuf=$minBuf")
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error in initialize()", t)
            release()
            throw t
        }
    }

    suspend fun speak(text: String, speed: Float = 1.0f) = withContext(Dispatchers.IO) {
        val currentTts = tts ?: run {
            Log.e(TAG, "speak(): tts is null!")
            return@withContext
        }
        val track = audioTrack ?: run {
            Log.e(TAG, "speak(): audioTrack is null!")
            return@withContext
        }

        Log.d(TAG, "speak() text='$text' speed=$speed")

        val audio = try {
            currentTts.generate(text, sid = 0, speed = speed)
        } catch (t: Throwable) {
            Log.e(TAG, "OfflineTts.generate() threw", t); null
        }

        val samples = audio?.samples
        if (samples == null || samples.isEmpty()) {
            Log.w(TAG, "No audio samples generated (samples=${samples?.size ?: "null"})")
            return@withContext
        }
        Log.d(TAG, "Generated ${samples.size} samples, converting to PCM16...")

        // Float32 → PCM16LE
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = (samples[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            pcm[i * 2] = (s.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }

        val written = track.write(pcm, 0, pcm.size)
        Log.d(TAG, "Written $written bytes to AudioTrack")

        // track.write() ở MODE_STREAM đã tự động block trong thời gian âm thanh phát ra loa
        // Không cần delay thêm durationMs để tránh bị nhân đôi thời gian chờ UI
        delay(100L)
    }

    fun release() {
        Log.d(TAG, "release()")
        try { audioTrack?.stop(); audioTrack?.release() } catch (t: Throwable) { Log.e(TAG, "release audioTrack", t) }
        audioTrack = null
        try { tts?.release() } catch (t: Throwable) { Log.e(TAG, "release tts", t) }
        tts = null
    }
}
