package com.herolens.app.vision

import kotlin.math.abs
import kotlin.math.max

/** Lightweight quality gate used before expensive hero matching. */
data class FrameQuality(
    val brightness: Float,
    val sharpness: Float,
    val usable: Boolean,
    val hint: QualityHint
)

enum class QualityHint { GOOD, TOO_DARK, TOO_BRIGHT, BLURRY }

object FrameQualityEvaluator {
    fun evaluate(frame: ScoreboardFrame): FrameQuality {
        val step = max(1, minOf(frame.width, frame.height) / 150)
        var count = 0
        var brightnessSum = 0f
        var edgeSum = 0f
        var previous = -1f

        var y = 0
        while (y < frame.height) {
            var x = 0
            previous = -1f
            while (x < frame.width) {
                val index = (y * frame.width + x) * 4
                val r = frame.rgbaBytes[index].toInt() and 0xff
                val g = frame.rgbaBytes[index + 1].toInt() and 0xff
                val b = frame.rgbaBytes[index + 2].toInt() and 0xff
                val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                brightnessSum += luminance
                if (previous >= 0f) edgeSum += abs(luminance - previous)
                previous = luminance
                count++
                x += step
            }
            y += step
        }

        val brightness = if (count == 0) 0f else brightnessSum / count
        val sharpness = if (count <= 1) 0f else edgeSum / (count - 1)
        val hint = when {
            brightness < 0.07f -> QualityHint.TOO_DARK
            brightness > 0.94f -> QualityHint.TOO_BRIGHT
            sharpness < 0.018f -> QualityHint.BLURRY
            else -> QualityHint.GOOD
        }
        return FrameQuality(brightness, sharpness, hint == QualityHint.GOOD, hint)
    }
}
