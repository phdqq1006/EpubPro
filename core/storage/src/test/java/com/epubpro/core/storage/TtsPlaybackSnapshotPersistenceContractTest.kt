package com.epubpro.core.storage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackSnapshotPersistenceContractTest {
    @Test
    fun `snapshot persistence does not synchronously commit on caller thread`() {
        val source = findSource().readText()

        assertTrue(source.contains(".apply()"))
        assertFalse(source.contains(".commit()"))
    }

    private fun findSource(): File {
        val relativePath = "src/main/java/com/epubpro/core/storage/TtsPlaybackSnapshotStore.kt"
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(workingDirectory)) { it.parentFile }
            .flatMap { root ->
                sequenceOf(
                    File(root, relativePath),
                    File(root, "core/storage/$relativePath")
                )
            }
            .firstOrNull(File::isFile)
            ?: error("Unable to locate TtsPlaybackSnapshotStore.kt from $workingDirectory")
    }
}
