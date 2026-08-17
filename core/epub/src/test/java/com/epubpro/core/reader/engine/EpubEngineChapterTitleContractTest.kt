package com.epubpro.core.reader.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubEngineChapterTitleContractTest {
    @Test
    fun `sanitized blank title falls back to generated chapter title`() {
        val source = findSource().readText()
        val headerExtraction = source
            .substringAfter("orderedEntries.forEachIndexed")
            .substringBefore("headers.add(")

        assertTrue(headerExtraction.contains("sanitizeChapterTitle(rawTitle)"))
        assertTrue(headerExtraction.contains(".ifBlank"))
        assertTrue(headerExtraction.contains("Chương"))
    }

    private fun findSource(): File {
        val relativePath = "src/main/java/com/epubpro/core/reader/engine/EpubEngine.kt"
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .flatMap { root ->
                sequenceOf(
                    File(root, relativePath),
                    File(root, "core/reader/$relativePath")
                )
            }
            .firstOrNull(File::isFile)
            ?: error("Unable to locate EpubEngine.kt from $workingDirectory")
    }
}
