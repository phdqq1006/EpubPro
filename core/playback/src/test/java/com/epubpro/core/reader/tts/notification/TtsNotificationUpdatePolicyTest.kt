package com.epubpro.core.reader.tts.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsNotificationUpdatePolicyTest {
    @Test
    fun `starts foreground when no episode exists`() {
        assertEquals(
            TtsNotificationUpdateAction.START_FOREGROUND,
            TtsNotificationUpdatePolicy.resolve(false, 0, 1, false)
        )
    }

    @Test
    fun `updates notification in place when service types are unchanged`() {
        assertEquals(
            TtsNotificationUpdateAction.UPDATE_IN_PLACE,
            TtsNotificationUpdatePolicy.resolve(true, 1, 1, false)
        )
    }

    @Test
    fun `updates foreground types without detach when adding a type`() {
        assertEquals(
            TtsNotificationUpdateAction.UPDATE_FOREGROUND_TYPES,
            TtsNotificationUpdatePolicy.resolve(true, 1, 3, false)
        )
    }

    @Test
    fun `restarts foreground when removing a type`() {
        assertEquals(
            TtsNotificationUpdateAction.RESTART_FOREGROUND,
            TtsNotificationUpdatePolicy.resolve(true, 3, 2, false)
        )
    }

    @Test
    fun `forced active episode restarts foreground`() {
        assertEquals(
            TtsNotificationUpdateAction.RESTART_FOREGROUND,
            TtsNotificationUpdatePolicy.resolve(true, 1, 1, true)
        )
    }
}
