package com.epubpro.feature.library

import com.epubpro.core.reader.tts.TtsWidgetContract
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModelTest {
    /**
     * Kiểm tra contract broadcast cập nhật widget được giới hạn trong package ứng dụng.
     */
    @Test
    fun widgetStateChangedBroadcastUsesApplicationPackage() {
        val request = createTtsWidgetStateChangedRequest("com.epubpro.test")

        assertEquals(TtsWidgetContract.ACTION_STATE_CHANGED, request.action)
        assertEquals("com.epubpro.test", request.packageName)
    }
}
