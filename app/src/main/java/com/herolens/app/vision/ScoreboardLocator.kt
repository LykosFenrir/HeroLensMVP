package com.herolens.app.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Locates the blue and red Overwatch scoreboard panels before hero classification.
 *
 * V6.2 uses horizontal colour-band projections rather than fixed screen coordinates.
 * This is much less sensitive to TV distance, bezels, phone zoom and where the TV is
 * positioned inside the camera preview.
 */
object ScoreboardLocator {
    private const val SAMPLE_WIDTH = 320

    fun locate(frame: ScoreboardFrame): ScoreboardRegion? {
        if (frame.width < 64 || frame.height < 64) return null
        val sampleWidth = min(SAMPLE_WIDTH, frame.width)
        val sampleHeight = max(72, (frame.height.toFloat() * sampleWidth / frame.width).toInt())
        val blue = BooleanArray(sampleWidth * sampleHeight)
        val red = BooleanArray(sampleWidth * sampleHeight)

        for (sy in 0 until sampleHeight) {
            val sourceY = ((sy + 0.5f) * frame.height / sampleHeight).toInt().coerceIn(0, frame.height - 1)
            for (sx in 0 until sampleWidth) {
                val sourceX = ((sx + 0.5f) * frame.width / sampleWidth).toInt().coerceIn(0, frame.width - 1)
                val index = (sourceY * frame.width + sourceX) * 4
                val r = frame.rgbaBytes[index].toInt() and 0xff
                val g = frame.rgbaBytes[index + 1].toInt() and 0xff
                val b = frame.rgbaBytes[index + 2].toInt() and 0xff
                val maxChannel = max(r, max(g, b))
                val minChannel = min(r, min(g, b))
                val saturation = maxChannel - minChannel
                val target = sy * sampleWidth + sx

                // Broad enough for TV white balance and exposure shifts, while still
                // excluding the low-saturation wall/background visible around the TV.
                blue[target] = saturation >= 22 &&
                    b >= 60 && g >= 50 &&
                    b > r * 1.04f && g > r * 0.98f &&
                    (b + g) > r * 1.90f && maxChannel < 252

                // Enemy panels may appear red, magenta or slightly orange on warm TVs.
                red[target] = saturation >= 24 &&
                    r >= 72 &&
                    r > g * 1.08f && r > b * 1.02f &&
                    maxChannel < 252
            }
        }

        val blueBands = horizontalBands(blue, sampleWidth, sampleHeight)
        val redBands = horizontalBands(red, sampleWidth, sampleHeight)
        var best: Pair<Band, Band>? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (ally in blueBands) {
            for (enemy in redBands) {
                if (enemy.centerY <= ally.centerY) continue
                val overlap = max(0, min(ally.right, enemy.right) - max(ally.left, enemy.left)).toFloat()
                val overlapRatio = overlap / max(1f, min(ally.width, enemy.width).toFloat())
                if (overlapRatio < 0.35f) continue

                val widthRatio = ally.width.toFloat() / max(1, enemy.width)
                if (widthRatio !in 0.45f..2.20f) continue

                val averageHeight = (ally.height + enemy.height) / 2f
                val verticalGap = enemy.top - ally.bottom
                if (verticalGap < -averageHeight * 0.25f || verticalGap > averageHeight * 3.2f) continue

                val heightRatio = min(ally.height, enemy.height).toFloat() / max(1, max(ally.height, enemy.height))
                val centerDelta = abs(ally.centerX - enemy.centerX) / sampleWidth
                val widthSimilarity = 1f - min(1f, abs(1f - widthRatio))
                val gapScore = 1f - min(1f, abs(verticalGap) / max(1f, averageHeight * 1.8f))
                val areaScore = min(1f, (ally.area + enemy.area).toFloat() / (sampleWidth * sampleHeight * 0.08f))
                val aspectScore = min(1f, min(ally.aspect, enemy.aspect) / 3f)
                val score = overlapRatio * 2.4f + widthSimilarity * 1.1f + gapScore * 0.65f +
                    areaScore * 0.70f + aspectScore * 0.45f + heightRatio * 0.45f - centerDelta * 1.3f
                if (score > bestScore) {
                    bestScore = score
                    best = ally to enemy
                }
            }
        }

        val pair = best ?: return null
        if (bestScore < 3.25f) return null
        val allyRect = pair.first.toNormalized(sampleWidth, sampleHeight).expand(0.008f, 0.010f)
        val enemyRect = pair.second.toNormalized(sampleWidth, sampleHeight).expand(0.008f, 0.010f)
        val confidence = ((bestScore - 3.0f) / 2.7f).coerceIn(0.20f, 0.99f)
        return ScoreboardRegion(allyRect, enemyRect, confidence)
    }

    /**
     * Finds dense horizontal colour bands. The threshold is relative to the strongest
     * row, so a distant TV and a close monitor are handled using the same logic.
     */
    private fun horizontalBands(mask: BooleanArray, width: Int, height: Int): List<Band> {
        val rowCounts = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            val offset = y * width
            for (x in 0 until width) if (mask[offset + x]) count++
            rowCounts[y] = count
        }
        val peak = rowCounts.maxOrNull() ?: 0
        if (peak < 5) return emptyList()
        val threshold = max(4, (peak * 0.28f).toInt())
        val ranges = mutableListOf<IntRange>()
        var start = -1
        var previous = -100
        for (y in 0 until height) {
            if (rowCounts[y] < threshold) continue
            if (start < 0) {
                start = y
            } else if (y - previous > 3) {
                ranges += start..previous
                start = y
            }
            previous = y
        }
        if (start >= 0) ranges += start..previous

        return ranges.mapNotNull { range ->
            val top = range.first
            val bottom = range.last + 1
            if (bottom - top < 2) return@mapNotNull null
            val columnCounts = IntArray(width)
            var total = 0
            for (y in top until bottom) {
                val offset = y * width
                for (x in 0 until width) {
                    if (mask[offset + x]) {
                        columnCounts[x]++
                        total++
                    }
                }
            }
            if (total < 10) return@mapNotNull null

            // Trim isolated colour pixels by using cumulative 3%/97% bounds.
            val lowerTarget = total * 0.03f
            val upperTarget = total * 0.97f
            var cumulative = 0
            var left = 0
            while (left < width) {
                cumulative += columnCounts[left]
                if (cumulative >= lowerTarget) break
                left++
            }
            cumulative = 0
            var right = width - 1
            while (right >= 0) {
                cumulative += columnCounts[right]
                if (cumulative >= total - upperTarget) break
                right--
            }
            left = (left - 2).coerceAtLeast(0)
            right = (right + 3).coerceAtMost(width)
            if (right <= left) return@mapNotNull null

            var area = 0
            for (y in top until bottom) {
                val offset = y * width
                for (x in left until right) if (mask[offset + x]) area++
            }
            val band = Band(left, top, right, bottom, area)
            val widthFraction = band.width.toFloat() / width
            val heightFraction = band.height.toFloat() / height
            band.takeIf {
                widthFraction >= 0.06f &&
                    heightFraction >= 0.018f &&
                    widthFraction <= 0.95f &&
                    heightFraction <= 0.55f &&
                    band.aspect >= 1.05f
            }
        }.sortedByDescending(Band::area)
    }

    private data class Band(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        val aspect: Float get() = width.toFloat() / max(1, height)

        fun toNormalized(frameWidth: Int, frameHeight: Int) = NormalizedRect(
            left = left.toFloat() / frameWidth,
            top = top.toFloat() / frameHeight,
            right = right.toFloat() / frameWidth,
            bottom = bottom.toFloat() / frameHeight
        )
    }
}

private fun NormalizedRect.expand(dx: Float, dy: Float) = NormalizedRect(
    (left - dx).coerceIn(0f, 1f),
    (top - dy).coerceIn(0f, 1f),
    (right + dx).coerceIn(0f, 1f),
    (bottom + dy).coerceIn(0f, 1f)
)
