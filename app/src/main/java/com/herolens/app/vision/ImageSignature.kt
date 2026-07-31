package com.herolens.app.vision

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class CroppedImage(val rgba: ByteArray, val width: Int, val height: Int)

data class ImageSignature(
    val luminance: FloatArray,
    val edges: FloatArray,
    val colorHistogram: FloatArray,
    val hash: Long
)

object ScoreboardSlots {
    private val allyCenters = floatArrayOf(0.185f, 0.255f, 0.325f, 0.395f, 0.465f)
    private val enemyCenters = floatArrayOf(0.605f, 0.675f, 0.745f, 0.815f, 0.885f)

    fun slots(layout: ScoreboardLayout): List<Pair<TeamSide, NormalizedRect>> {
        require(layout != ScoreboardLayout.AUTO) { "AUTO layout must be resolved before requesting slots" }
        val centerX = if (layout == ScoreboardLayout.PORTRAITS_LEFT) 0.155f else 0.845f
        val halfWidth = 0.032f
        val halfHeight = 0.050f
        return buildList {
            allyCenters.forEach { centerY ->
                add(TeamSide.ALLY to NormalizedRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight))
            }
            enemyCenters.forEach { centerY ->
                add(TeamSide.ENEMY to NormalizedRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight))
            }
        }
    }

    /** Small horizontal/vertical offsets make template matching more tolerant of phone framing. */
    fun jittered(rect: NormalizedRect): List<NormalizedRect> {
        val offsets = listOf(
            0f to 0f,
            -0.006f to 0f,
            0.006f to 0f,
            0f to -0.004f,
            0f to 0.004f
        )
        return offsets.map { (dx, dy) ->
            NormalizedRect(
                left = (rect.left + dx).coerceIn(0f, 1f),
                top = (rect.top + dy).coerceIn(0f, 1f),
                right = (rect.right + dx).coerceIn(0f, 1f),
                bottom = (rect.bottom + dy).coerceIn(0f, 1f)
            )
        }
    }
}

object SignatureMath {
    private const val TARGET = 32
    private const val COLOR_BINS = 4

    fun crop(frame: ScoreboardFrame, rect: NormalizedRect): CroppedImage {
        val left = (rect.left.coerceIn(0f, 1f) * frame.width).toInt().coerceIn(0, frame.width - 1)
        val top = (rect.top.coerceIn(0f, 1f) * frame.height).toInt().coerceIn(0, frame.height - 1)
        val right = (rect.right.coerceIn(0f, 1f) * frame.width).toInt().coerceIn(left + 1, frame.width)
        val bottom = (rect.bottom.coerceIn(0f, 1f) * frame.height).toInt().coerceIn(top + 1, frame.height)
        val width = right - left
        val height = bottom - top
        val out = ByteArray(width * height * 4)
        var dst = 0
        for (y in top until bottom) {
            val src = (y * frame.width + left) * 4
            frame.rgbaBytes.copyInto(out, dst, src, src + width * 4)
            dst += width * 4
        }
        return CroppedImage(out, width, height)
    }

    fun signature(rgba: ByteArray, width: Int, height: Int): ImageSignature {
        require(width > 0 && height > 0)
        require(rgba.size >= width * height * 4)
        val gray = FloatArray(TARGET * TARGET)
        val histogram = FloatArray(COLOR_BINS * COLOR_BINS * COLOR_BINS)
        for (y in 0 until TARGET) {
            val sy = ((y + 0.5f) * height / TARGET).toInt().coerceIn(0, height - 1)
            for (x in 0 until TARGET) {
                val sx = ((x + 0.5f) * width / TARGET).toInt().coerceIn(0, width - 1)
                val i = (sy * width + sx) * 4
                val r = rgba[i].toInt() and 0xff
                val g = rgba[i + 1].toInt() and 0xff
                val b = rgba[i + 2].toInt() and 0xff
                gray[y * TARGET + x] = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val rb = (r * COLOR_BINS / 256).coerceIn(0, COLOR_BINS - 1)
                val gb = (g * COLOR_BINS / 256).coerceIn(0, COLOR_BINS - 1)
                val bb = (b * COLOR_BINS / 256).coerceIn(0, COLOR_BINS - 1)
                histogram[(rb * COLOR_BINS + gb) * COLOR_BINS + bb] += 1f
            }
        }
        val sampleCount = (TARGET * TARGET).toFloat()
        histogram.indices.forEach { histogram[it] /= sampleCount }

        val normalized = normalize(gray)
        val edges = FloatArray(TARGET * TARGET)
        for (y in 1 until TARGET - 1) {
            for (x in 1 until TARGET - 1) {
                val gx = normalized[y * TARGET + x + 1] - normalized[y * TARGET + x - 1]
                val gy = normalized[(y + 1) * TARGET + x] - normalized[(y - 1) * TARGET + x]
                edges[y * TARGET + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return ImageSignature(normalized, normalize(edges), histogram, differenceHash(gray))
    }

    fun similarity(a: ImageSignature, b: ImageSignature): Float {
        val lum = correlation(a.luminance, b.luminance)
        val edge = correlation(a.edges, b.edges)
        val color = histogramIntersection(a.colorHistogram, b.colorHistogram)
        val hash = 1f - java.lang.Long.bitCount(a.hash xor b.hash) / 64f
        return (lum * 0.40f + edge * 0.25f + color * 0.20f + hash * 0.15f).coerceIn(-1f, 1f)
    }

    private fun normalize(values: FloatArray): FloatArray {
        val mean = values.average().toFloat()
        var variance = 0f
        for (value in values) {
            val delta = value - mean
            variance += delta * delta
        }
        val std = sqrt(max(variance / max(1, values.size), 1e-6f))
        return FloatArray(values.size) { (values[it] - mean) / std }
    }

    private fun correlation(a: FloatArray, b: FloatArray): Float {
        val size = min(a.size, b.size)
        if (size == 0) return 0f
        var dot = 0f
        var aa = 0f
        var bb = 0f
        for (i in 0 until size) {
            dot += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        val denominator = sqrt(max(aa * bb, 1e-6f))
        return (dot / denominator).coerceIn(-1f, 1f)
    }

    private fun histogramIntersection(a: FloatArray, b: FloatArray): Float {
        val size = min(a.size, b.size)
        var sum = 0f
        for (index in 0 until size) sum += min(a[index], b[index])
        return sum.coerceIn(0f, 1f)
    }

    private fun differenceHash(gray: FloatArray): Long {
        var result = 0L
        var bit = 0
        for (y in 0 until 8) {
            val sy = (y * TARGET / 8).coerceAtMost(TARGET - 1)
            for (x in 0 until 8) {
                val sx1 = (x * TARGET / 9).coerceAtMost(TARGET - 1)
                val sx2 = ((x + 1) * TARGET / 9).coerceAtMost(TARGET - 1)
                if (gray[sy * TARGET + sx1] > gray[sy * TARGET + sx2]) {
                    result = result or (1L shl bit)
                }
                bit++
            }
        }
        return result
    }
}
