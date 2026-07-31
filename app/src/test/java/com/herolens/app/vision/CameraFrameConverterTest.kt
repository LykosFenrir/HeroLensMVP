package com.herolens.app.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class CameraFrameConverterTest {
    @Test
    fun detectsAndKeepsRgbaChannelOrder() {
        val bytes = byteArrayOf(
            10, 20, 30, 0xff.toByte(),
            50, 60, 70, 0xff.toByte()
        )
        val frame = packedPlaneToScoreboardFrame(
            buffer = ByteBuffer.wrap(bytes),
            width = 2,
            height = 1,
            rowStride = 8,
            pixelStride = 4,
            clockwiseDegrees = 0
        )
        assertNotNull(frame)
        assertArrayEquals(bytes, frame!!.rgbaBytes)
    }

    @Test
    fun detectsLegacyArgbChannelOrder() {
        val argb = byteArrayOf(
            0xff.toByte(), 10, 20, 30,
            0xff.toByte(), 50, 60, 70
        )
        val frame = packedPlaneToScoreboardFrame(
            buffer = ByteBuffer.wrap(argb),
            width = 2,
            height = 1,
            rowStride = 8,
            pixelStride = 4,
            clockwiseDegrees = 0
        )!!
        assertArrayEquals(
            byteArrayOf(
                10, 20, 30, 0xff.toByte(),
                50, 60, 70, 0xff.toByte()
            ),
            frame.rgbaBytes
        )
    }

    @Test
    fun packedPlaneHonorsRowPaddingAndRotation() {
        val bytes = byteArrayOf(
            1, 2, 3, 0xff.toByte(), 5, 6, 7, 0xff.toByte(), 99, 99, 99, 99,
            9, 10, 11, 0xff.toByte(), 13, 14, 15, 0xff.toByte(), 99, 99, 99, 99
        )
        val frame = packedPlaneToScoreboardFrame(
            buffer = ByteBuffer.wrap(bytes),
            width = 2,
            height = 2,
            rowStride = 12,
            pixelStride = 4,
            clockwiseDegrees = 90,
            forcedOrder = PackedChannelOrder.RGBA
        )!!
        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertArrayEquals(
            byteArrayOf(
                9, 10, 11, 0xff.toByte(), 1, 2, 3, 0xff.toByte(),
                13, 14, 15, 0xff.toByte(), 5, 6, 7, 0xff.toByte()
            ),
            frame.rgbaBytes
        )
    }
}
