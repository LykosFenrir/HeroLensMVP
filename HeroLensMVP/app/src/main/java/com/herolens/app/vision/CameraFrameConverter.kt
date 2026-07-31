package com.herolens.app.vision

import androidx.camera.core.ImageProxy

/** Converts CameraX RGBA_8888 output (A,R,G,B bytes) into the detector's R,G,B,A layout. */
fun ImageProxy.toScoreboardFrame(): ScoreboardFrame? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    if (pixelStride < 4) return null

    val raw = ByteArray(width * height * 4)
    val baseOffset = buffer.position()
    for (y in 0 until height) {
        val rowStart = baseOffset + y * rowStride
        for (x in 0 until width) {
            val source = rowStart + x * pixelStride
            val target = (y * width + x) * 4
            if (source + 3 >= buffer.limit()) return null
            raw[target] = buffer.get(source + 1)     // red
            raw[target + 1] = buffer.get(source + 2) // green
            raw[target + 2] = buffer.get(source + 3) // blue
            raw[target + 3] = buffer.get(source)     // alpha
        }
    }
    return rotateFrame(ScoreboardFrame(raw, width, height), imageInfo.rotationDegrees)
}

private fun rotateFrame(frame: ScoreboardFrame, clockwiseDegrees: Int): ScoreboardFrame {
    val normalized = ((clockwiseDegrees % 360) + 360) % 360
    if (normalized == 0) return frame

    val sourceWidth = frame.width
    val sourceHeight = frame.height
    val targetWidth = if (normalized == 90 || normalized == 270) sourceHeight else sourceWidth
    val targetHeight = if (normalized == 90 || normalized == 270) sourceWidth else sourceHeight
    val output = ByteArray(targetWidth * targetHeight * 4)

    for (y in 0 until sourceHeight) {
        for (x in 0 until sourceWidth) {
            val source = (y * sourceWidth + x) * 4
            val targetX: Int
            val targetY: Int
            when (normalized) {
                90 -> {
                    targetX = sourceHeight - 1 - y
                    targetY = x
                }
                180 -> {
                    targetX = sourceWidth - 1 - x
                    targetY = sourceHeight - 1 - y
                }
                270 -> {
                    targetX = y
                    targetY = sourceWidth - 1 - x
                }
                else -> return frame
            }
            val target = (targetY * targetWidth + targetX) * 4
            frame.rgbaBytes.copyInto(output, target, source, source + 4)
        }
    }
    return ScoreboardFrame(output, targetWidth, targetHeight, normalized)
}
