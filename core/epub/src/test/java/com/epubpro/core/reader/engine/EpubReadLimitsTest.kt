package com.epubpro.core.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EpubReadLimitsTest {

    /**
     * Đảm bảo stream bị chặn trước khi ghi vượt quá giới hạn dung lượng.
     */
    @Test
    fun `copyBounded rejects data exceeding limit`() {
        val output = ByteArrayOutputStream()

        try {
            EpubReadLimits.copyBounded(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                output = output,
                maxBytes = 3
            )
            fail("Expected an IllegalStateException")
        } catch (_: IllegalStateException) {
            // Expected: dữ liệu vượt giới hạn không được ghi thành công.
        }

        assertEquals(0, output.size())
    }

    /**
     * Đảm bảo stream hợp lệ được sao chép đầy đủ trong giới hạn cho phép.
     */
    @Test
    fun `copyBounded copies data within limit`() {
        val input = byteArrayOf(1, 2, 3)
        val output = ByteArrayOutputStream()

        val copied = EpubReadLimits.copyBounded(
            input = ByteArrayInputStream(input),
            output = output,
            maxBytes = input.size.toLong()
        )

        assertEquals(input.size.toLong(), copied)
        assertEquals(input.toList(), output.toByteArray().toList())
    }
}
