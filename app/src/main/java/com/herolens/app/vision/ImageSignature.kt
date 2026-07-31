package com.herolens.app.vision

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class CroppedImage(val rgba: ByteArray, val width: Int, val height: Int)

data class ImageSignature(
    val luminance: FloatArray,
    val edges: FloatArray,
    val colorHistogram: FloatArray,
    val hash: Long
)

object ScoreboardSlots {
    /* Legacy full-frame profiles are kept as a fallback when panel localization fails. */
    private val tvAllyCenters = floatArrayOf(0.155f, 0.225f, 0.295f, 0.365f, 0.435f)
    private val tvEnemyCenters = floatArrayOf(0.575f, 0.645f, 0.715f, 0.785f, 0.855f)
    private val closeAllyCenters = floatArrayOf(0.185f, 0.255f, 0.325f, 0.395f, 0.465f)
    private val closeEnemyCenters = floatArrayOf(0.605f, 0.675f, 0.745f, 0.815f, 0.885f)

    fun slots(layout: ScoreboardLayout): List<Pair<TeamSide, NormalizedRect>> =
        profiles(layout).first()

    fun profiles(layout: ScoreboardLayout): List<List<Pair<TeamSide, NormalizedRect>>> {
        require(layout != ScoreboardLayout.AUTO) { "AUTO layout must be resolved before requesting slots" }
        val tvCenterX = if (layout == ScoreboardLayout.PORTRAITS_LEFT) 0.064f else 0.936f
        val closeCenterX = if (layout == ScoreboardLayout.PORTRAITS_LEFT) 0.155f else 0.845f
        return listOf(
            buildFixedProfile(tvCenterX, 0.020f, 0.035f, tvAllyCenters, tvEnemyCenters),
            buildFixedProfile(closeCenterX, 0.030f, 0.048f, closeAllyCenters, closeEnemyCenters)
        )
    }

    /**
     * Builds candidate slots relative to the actually detected blue/red panels.
     * Both 5v5 and 6v6 are evaluated because current Overwatch modes can use either.
     */
    fun localizedProfiles(
        region: ScoreboardRegion,
        layout: ScoreboardLayout,
        teamSize: Int
    ): List<List<Pair<TeamSide, NormalizedRect>>> {
        require(layout != ScoreboardLayout.AUTO)
        require(teamSize in 5..6)
        // Three compact profiles are enough after panel localization. Older builds
        // evaluated 32 full combinations per frame, which was too slow on phones.
        val portraitOffsets = listOf(0.030f, 0.070f, 0.110f)
        val widthScales = listOf(0.095f)
        return buildList {
            portraitOffsets.forEach { offset ->
                widthScales.forEach { widthScale ->
                    add(
                        buildLocalizedProfile(
                            allyPanel = region.allyPanel,
                            enemyPanel = region.enemyPanel,
                            layout = layout,
                            teamSize = teamSize,
                            portraitOffset = offset,
                            portraitWidthScale = widthScale
                        )
                    )
                }
            }
        }
    }

    private fun buildLocalizedProfile(
        allyPanel: NormalizedRect,
        enemyPanel: NormalizedRect,
        layout: ScoreboardLayout,
        teamSize: Int,
        portraitOffset: Float,
        portraitWidthScale: Float
    ): List<Pair<TeamSide, NormalizedRect>> = buildList {
        addAll(panelSlots(TeamSide.ALLY, allyPanel, layout, teamSize, portraitOffset, portraitWidthScale))
        addAll(panelSlots(TeamSide.ENEMY, enemyPanel, layout, teamSize, portraitOffset, portraitWidthScale))
    }

    private fun panelSlots(
        team: TeamSide,
        panel: NormalizedRect,
        layout: ScoreboardLayout,
        teamSize: Int,
        portraitOffset: Float,
        portraitWidthScale: Float
    ): List<Pair<TeamSide, NormalizedRect>> {
        val rowHeight = panel.height / teamSize
        val halfHeight = rowHeight * 0.46f
        val halfWidth = panel.width * portraitWidthScale / 2f
        val centerX = if (layout == ScoreboardLayout.PORTRAITS_LEFT) {
            panel.left + panel.width * portraitOffset
        } else {
            panel.right - panel.width * portraitOffset
        }
        return (0 until teamSize).map { slot ->
            val centerY = panel.top + rowHeight * (slot + 0.5f)
            team to NormalizedRect(
                (centerX - halfWidth).coerceIn(0f, 1f),
                (centerY - halfHeight).coerceIn(0f, 1f),
                (centerX + halfWidth).coerceIn(0f, 1f),
                (centerY + halfHeight).coerceIn(0f, 1f)
            )
        }
    }

    private fun buildFixedProfile(
        centerX: Float,
        halfWidth: Float,
        halfHeight: Float,
        allyCenters: FloatArray,
        enemyCenters: FloatArray
    ): List<Pair<TeamSide, NormalizedRect>> = buildList {
        allyCenters.forEach { centerY -> add(TeamSide.ALLY to rect(centerX, centerY, halfWidth, halfHeight)) }
        enemyCenters.forEach { centerY -> add(TeamSide.ENEMY to rect(centerX, centerY, halfWidth, halfHeight)) }
    }

    private fun rect(centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float) =
        NormalizedRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)

    /** Offset and scale search tolerates bezel framing, UI scale and mild perspective. */
    fun jittered(rect: NormalizedRect): List<NormalizedRect> {
        val offsets = listOf(
            0f to 0f,
            -0.006f to 0f,
            0.006f to 0f
        )
        val shifted = offsets.map { (dx, dy) -> shift(rect, dx, dy) }
        return shifted + resize(rect, 0.88f) + resize(rect, 1.12f)
    }

    private fun shift(rect: NormalizedRect, dx: Float, dy: Float) = NormalizedRect(
        (rect.left + dx).coerceIn(0f, 1f),
        (rect.top + dy).coerceIn(0f, 1f),
        (rect.right + dx).coerceIn(0f, 1f),
        (rect.bottom + dy).coerceIn(0f, 1f)
    )

    private fun resize(rect: NormalizedRect, scale: Float): NormalizedRect {
        val cx = rect.centerX
        val cy = rect.centerY
        val hw = rect.width * scale / 2f
        val hh = rect.height * scale / 2f
        return NormalizedRect(
            (cx - hw).coerceIn(0f, 1f),
            (cy - hh).coerceIn(0f, 1f),
            (cx + hw).coerceIn(0f, 1f),
            (cy + hh).coerceIn(0f, 1f)
        )
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

    fun quickSimilarity(a: ImageSignature, b: ImageSignature): Float {
        val color = histogramIntersection(a.colorHistogram, b.colorHistogram)
        val hash = 1f - java.lang.Long.bitCount(a.hash xor b.hash) / 64f
        return (hash * 0.72f + color * 0.28f).coerceIn(0f, 1f)
    }

    fun similarity(a: ImageSignature, b: ImageSignature): Float {
        val lum = correlation(a.luminance, b.luminance)
        val edge = correlation(a.edges, b.edges)
        val color = histogramIntersection(a.colorHistogram, b.colorHistogram)
        val hash = 1f - java.lang.Long.bitCount(a.hash xor b.hash) / 64f
        return (lum * 0.47f + edge * 0.32f + color * 0.08f + hash * 0.13f).coerceIn(-1f, 1f)
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
