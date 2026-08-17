package com.epubpro.core.reader.tts

import android.content.ContextWrapper
import com.epubpro.core.tts.SherpaTtsEngine
import com.epubpro.core.tts.VoiceModelDownloader
import com.epubpro.domain.model.TtsChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PiperTtsEngineWrapperTest {

    private lateinit var sherpaTtsEngine: FakeSherpaTtsEngine
    private lateinit var downloader: FakeVoiceModelDownloader
    private lateinit var piperWrapper: PiperTtsEngineWrapper

    private class FakeVoiceModelDownloader : VoiceModelDownloader(ContextWrapper(null)) {
        var isDownloaded = false
        override fun isModelDownloaded(modelName: String): Boolean = isDownloaded
        override fun isEspeakDataReady(): Boolean = true
        override suspend fun downloadEspeakNgDataIfNeeded(onProgress: (Float) -> Unit) {}
        override fun getModelPath(modelName: String): String = "/fake/model.onnx"
        override fun getTokensPath(modelName: String): String = "/fake/tokens.txt"
        override fun getEspeakDataDir(): String = "/fake/espeak-data"
    }

    private class FakeSherpaTtsEngine : SherpaTtsEngine(ContextWrapper(null)) {
        var isInitialized = false
        override suspend fun initialize(onnxPath: String, tokensPath: String, lexiconPath: String, dataDirPath: String) {
            isInitialized = true
        }
        override suspend fun synthesize(text: String, speed: Float): ByteArray = ByteArray(100)
        override suspend fun playPcm(pcm: ByteArray, onAudioStarted: () -> Unit) { onAudioStarted() }
        override fun stop() {}
        override fun release() {}
    }

    @Before
    fun setUp() {
        sherpaTtsEngine = FakeSherpaTtsEngine()
        downloader = FakeVoiceModelDownloader()
        piperWrapper = PiperTtsEngineWrapper(sherpaTtsEngine, downloader)
    }

    @Test
    fun `initialize without voice selected returns error callback without recursion`() {
        var errorReported: String? = null

        piperWrapper.initialize(
            onReady = {},
            onError = { error -> errorReported = error }
        )

        assertEquals("Chưa chọn giọng AI Offline", errorReported)
    }

    @Test
    fun `speak without voice selected returns error callback directly`() {
        var errorReported: String? = null
        val chunk = TtsChunk(id = 0, paragraphIndex = 0, text = "Xin chào")

        piperWrapper.speak(
            chunk = chunk,
            onChunkStart = {},
            onChunkDone = {},
            onError = { error -> errorReported = error }
        )

        assertEquals("Chưa chọn giọng AI Offline", errorReported)
    }

    @Test
    fun `initialize with un-downloaded voice model returns error callback without infinite recursion`() {
        downloader.isDownloaded = false
        piperWrapper.setVoice("ngoc_ngan")

        var errorReported: String? = null
        var readyCalled = false

        piperWrapper.initialize(
            onReady = { readyCalled = true },
            onError = { error -> errorReported = error }
        )

        Thread.sleep(200)

        assertFalse(readyCalled)
        assertTrue(errorReported?.contains("Voice model not downloaded yet") == true)
    }

    @Test
    fun `speak triggers initialization and invokes error callback when model not downloaded without recursion`() {
        downloader.isDownloaded = false
        piperWrapper.setVoice("ngoc_ngan")

        var errorReported: String? = null
        val chunk = TtsChunk(id = 1, paragraphIndex = 0, text = "Đoạn văn thử nghiệm")

        piperWrapper.speak(
            chunk = chunk,
            onChunkStart = {},
            onChunkDone = {},
            onError = { error -> errorReported = error }
        )

        Thread.sleep(200)

        assertTrue(errorReported?.contains("Voice model not downloaded yet") == true)
    }

    @Test
    fun `speak initializes engine successfully and plays speech when model is ready`() {
        downloader.isDownloaded = true
        piperWrapper.setVoice("ngoc_ngan")

        var chunkStarted = false
        val chunk = TtsChunk(id = 1, paragraphIndex = 0, text = "Đoạn văn thử nghiệm thành công")

        piperWrapper.speak(
            chunk = chunk,
            onChunkStart = { chunkStarted = true },
            onChunkDone = {},
            onError = {}
        )

        Thread.sleep(300)

        assertTrue(sherpaTtsEngine.isInitialized)
        assertTrue(chunkStarted)
    }
}
