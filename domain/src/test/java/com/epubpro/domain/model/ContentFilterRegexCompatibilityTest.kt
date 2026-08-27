package com.epubpro.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterRegexCompatibilityTest {

    @Test
    fun javascriptCompatibleRegex_acceptsCommonPattern() {
        assertTrue(isJavaScriptCompatibleRegex("""www\.[a-z]+\.com"""))
    }

    @Test
    fun javascriptCompatibleRegex_rejectsJavaInlineFlags() {
        assertFalse(isJavaScriptCompatibleRegex("(?s)a"))
    }

    @Test
    fun javascriptCompatibleRegex_rejectsInvalidPattern() {
        assertFalse(isJavaScriptCompatibleRegex("[invalid-regex"))
    }
}
