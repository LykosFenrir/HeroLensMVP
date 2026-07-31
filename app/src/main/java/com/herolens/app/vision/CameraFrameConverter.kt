package com.herolens.app.vision

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Converts CameraX packed RGBA analysis output into HeroLens' canonical R,G,B,A layout.
 *
 * Current CameraX API documentation specifies R,G,B,A byte order. Some older CameraX
 * documentation and vendor implementations exposed A,R,G,B. HeroLens therefore verifies
 * which byte behaves like the opaque alpha channel before decoding each camera stream.
 * This prevents a channel-order mismatch from turning the blue channel into alpha and
 * making scoreboard localisation permanently return 0/10.
 */
fun ImageProxy.toScoreboardFrame(): ScoreboardFrame? {
    val plane = planes.firstOrNull() ?: return null
    return packedPlaneToScoreboardFrame(
        buffer = plane.buffer,
        width = width,
        height = height,
        rowStride = plane.rowStride,
        pixelStride = plane.pixelStride,
        clockwiseDegrees = imageInfo.rotationDegrees
    )
}

internal enum class PackedChannelOrder { RGBA, ARGB }

/** Pure conversion helper so byte order, row padding and rotation can be unit-tested. */
internal fun packedPlaneToScoreboardFrame(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    clockwiseDegrees: Int,
    forcedOrder: PackedChannelOrder? = null
): ScoreboardFrame? {
    if (width <= 0 || height <= 0 || pixelStride < 4 || rowStride < width * pixelStride) return null

    val baseOffset = buffer.position()
    val limit = buffer.limit()
    val order = forcedOrder ?: detectPackedChannelOrder(
        buffer = buffer,
        baseOffset = baseOffset,
        limit = limit,
        width = width,
        height = height,
        rowStride = rowStride,
        pixelStride = pixelStride
    )

    val rgba = ByteArray(width * height * 4)
    for (y in 0 until height) {
        val rowStart = baseOffset + y * rowStride
        for (x in 0 until width) {
            val source = rowStart + x * pixelStride
            if (source + 3 >= limit) return null
            val target = (y * width + x) * 4
            when (order) {
                PackedChannelOrder.RGBA -> {
                    rgba[target] = buffer.get(source)
                    rgba[target + 1] = buffer.get(source + 1)
                    rgba[target + 2] = buffer.get(source + 2)
                    rgba[target + 3] = buffer.get(source + 3)
                }
                PackedChannelOrder.ARGB -> {
                    rgba[target] = buffer.get(source + 1)
                    rgba[target + 1] = buffer.get(source + 2)
                    rgba[target + 2] = buffer.get(source + 3)
                    rgba[target + 3] = buffer.get(source)
                }
            }
        }
    }
    return rotateFrame(ScoreboardFrame(rgba, width, height), clockwiseDegrees)
}

private fun detectPackedChannelOrder(
    buffer: ByteBuffer,
    baseOffset: Int,
    limit: Int,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int
): PackedChannelOrder {
    var firstAlphaScore = 0.0
    var fourthAlphaScore = 0.0
    var samples = 0
    val yStep = (height / 7).coerceAtLeast(1)
    val xStep = (width / 9).coerceAtLeast(1)

    var y = 0
    while (y < height && samples < 96) {
        var x = 0
        while (x < width && samples < 96) {
            val source = baseOffset + y * rowStride + x * pixelStride
            if (source + 3 < limit) {
                val first = buffer.get(source).toInt() and 0xff
                val fourth = buffer.get(source + 3).toInt() and 0xff
                // Alpha in CameraX's packed output is normally fully opaque. Weight
                // non-opaque values strongly while retaining RGBA as the tie-breaker.
                firstAlphaScore += abs(255 - first)
                fourthAlphaScore += abs(255 - fourth)
                samples++
            }
            x += xStep
        }
        y += yStep
    }
    if (samples == 0) return PackedChannelOrder.RGBA
    return if (firstAlphaScore + samples * 2.0 < fourthAlphaScore) {
        PackedChannelOrder.ARGB
    } else {
        PackedChannelOrder.RGBA
    }
}

internal fun rotateFrame(frame: ScoreboardFrame, clockwiseDegrees: Int): ScoreboardFrame {
    val normalized = ((clockwiseDegrees % 360) + 360) % 360
    if (normalized == 0) return frame.copy(rotationDegrees = 0)

    val sourceWidth = frame.width
    val sourceHeight = frame.height
    val targetWidth = if (normalized == 90 || normalized == 270) sourceHeight else sourceWidth
    val targetHeight = if (normalized == 90 || normalized == 270) sourceWidth else sourceHeight
    val output = ByteArray(targetWidth * targetHeight * 4)

    for (y in 0 until sourceHeight) {
        for (x in 0 until sourceWidth) {
            val source = (y * sourceWidth + x) * 4
            val (targetX, targetY) = when (normalized) {
                90 -> sourceHeight - 1 - y to x
                180 -> sourceWidth - 1 - x to sourceHeight - 1 - y
                270 -> y to sourceWidth - 1 - x
                else -> return frame.copy(rotationDegrees = 0)
            }
            val target = (targetY * targetWidth + targetX) * 4
            frame.rgbaBytes.copyInto(output, target, source, source + 4)
        }
    }
    return ScoreboardFrame(output, targetWidth, targetHeight, rotationDegrees = 0)
}
