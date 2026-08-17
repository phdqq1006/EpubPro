package com.epubpro.core.reader.tts

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-level guard for Android TextToSpeech initialization, which cannot be exercised by a
 * local JVM test without replacing the platform TextToSpeech implementation.
 */
class TtsServiceNativeEngineContractTest {
    private val source: String by lazy { findSource().readText() }

    @Test
    fun `service initializes native engine during onCreate`() {
        val onCreate = source
            .substringAfter("override fun onCreate()")
            .substringBefore("override fun onStartCommand")

        assertAppearsInOrder(
            onCreate,
            "nativeTtsEngine = AndroidNativeTtsEngine(applicationContext)",
            "applySettingsToEngines(activeSettings)",
            "nativeTtsEngine.initialize("
        )
    }

    private fun assertAppearsInOrder(text: String, vararg markers: String) {
        var previousIndex = -1
        markers.forEach { marker ->
            val index = text.indexOf(marker)
            assertTrue("Missing native TTS lifecycle marker: $marker", index >= 0)
            assertTrue("Native TTS lifecycle marker is out of order: $marker", index > previousIndex)
            previousIndex = index
        }
    }

    private fun findSource(): File {
        val relativePath = "src/main/java/com/epubpro/core/reader/tts/TtsService.kt"
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .flatMap { root ->
                sequenceOf(
                    File(root, relativePath),
                    File(root, "core/reader/$relativePath")
                )
            }
            .firstOrNull(File::isFile)
            ?: error("Unable to locate TtsService.kt from $workingDirectory")
    }
}
