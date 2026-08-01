package com.herolens.app.vision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Estimates whether a localized scoreboard contains five or six rows per team.
 *
 * Six-row scoreboards can otherwise look deceptively good when divided into
 * five tall crops, causing confident but vertically shifted heroes.
 * This estimator looks for the repeated bright role/status glyph bands near both
 * panel edges and scores how well they form a regular five-row or six-row grid.
 */
object ScoreboardTeamSizeEstimator {
    data class Estimate(
        val teamSize: Int?,
        val fiveScore: Float,
        val sixScore: Float,
        val confidence: Float
    )

    fun estimate(frame: ScoreboardFrame, region: ScoreboardRegion): Estimate {
        val five = score(frame, region, 5)
        val six = score(frame, region, 6)
        val difference = abs(five - six)
        val winner = when {
            six >= 0.46f && six > five + 0.055f -> 6
            five >= 0.46f && five > six + 0.055f -> 5
            else -> null
        }
        val confidence = ((difference - 0.025f) / 0.24f).coerceIn(0f, 0.98f)
        return Estimate(winner, five, six, confidence)
    }

    private fun score(frame: ScoreboardFrame, region: ScoreboardRegion, rows: Int): Float {
        val enemy = panelScore(frame, region.enemyPanel, rows)
        val ally = panelScore(frame, region.allyPanel, rows)
        // Enemy panels normally have no white table header and are the cleaner cue.
        return (enemy * 0.68f + ally * 0.32f).coerceIn(0f, 1f)
    }

    private fun panelScore(frame: ScoreboardFrame, panel: NormalizedRect, rows: Int): Float {
        val left = stripEvidence(frame, panel, rows, fromLeft = true)
        val right = stripEvidence(frame, panel, rows, fromLeft = false)
        val leftBands = regularBandScore(frame, panel, rows, fromLeft = true)
        val rightBands = regularBandScore(frame, panel, rows, fromLeft = false)
        return max(max(left, right), max(leftBands, rightBands))
    }

    /**
     * Some scoreboard layouts expose one bright status/role glyph per row near
     * the table edge. Counting a regular sequence is a much stronger six-row signal
     * than classifier confidence, especially when one ally row says WAITING FOR PLAYER.
     */
    private fun regularBandScore(
        frame: ScoreboardFrame,
        panel: NormalizedRect,
        rows: Int,
        fromLeft: Boolean
    ): Float {
        val panelLeft = (panel.left * frame.width).toInt().coerceIn(0, frame.width - 1)
        val panelTop = (panel.top * frame.height).toInt().coerceIn(0, frame.height - 1)
        val panelRight = (panel.right * frame.width).toInt().coerceIn(panelLeft + 1, frame.width)
        val panelBottom = (panel.bottom * frame.height).toInt().coerceIn(panelTop + 1, frame.height)
        val panelWidth = panelRight - panelLeft
        val panelHeight = panelBottom - panelTop
        if (panelWidth < 24 || panelHeight < 40) return 0f

        val stripWidth = max(8, (panelWidth * 0.15f).toInt())
        val stripLeft = if (fromLeft) panelLeft else panelRight - stripWidth
        val stripRight = if (fromLeft) panelLeft + stripWidth else panelRight
        val counts = FloatArray(panelHeight)
        for (localY in 0 until panelHeight) {
            val y = panelTop + localY
            var bright = 0
            for (x in stripLeft until stripRight) {
                val index = (y * frame.width + x) * 4
                val r = frame.rgbaBytes[index].toInt() and 0xff
                val g = frame.rgbaBytes[index + 1].toInt() and 0xff
                val b = frame.rgbaBytes[index + 2].toInt() and 0xff
                val maximum = max(r, max(g, b))
                val minimum = min(r, min(g, b))
                val saturation = maximum - minimum
                if (maximum >= 142 && minimum >= 72 && saturation <= 148) bright++
            }
            counts[localY] = bright.toFloat() / stripWidth
        }

        val smoothed = FloatArray(panelHeight)
        for (index in counts.indices) {
            var sum = 0f
            var samples = 0
            for (offset in -2..2) {
                val source = index + offset
                if (source in counts.indices) {
                    sum += counts[source]
                    samples++
                }
            }
            smoothed[index] = if (samples == 0) 0f else sum / samples
        }

        val active = BooleanArray(panelHeight) { smoothed[it] >= 0.035f }
        repeat(2) {
            val previous = active.copyOf()
            for (index in 1 until panelHeight - 1) {
                if (previous[index - 1] && previous[index + 1]) active[index] = true
            }
        }

        data class Band(val start: Int, val end: Int, val center: Float, val strength: Float)
        val bands = mutableListOf<Band>()
        var start: Int? = null
        for (index in 0..panelHeight) {
            val enabled = index < panelHeight && active[index]
            if (enabled && start == null) start = index
            if (!enabled && start != null) {
                val bandStart = start
                val bandEnd = index
                val bandHeight = bandEnd - bandStart
                val center = (bandStart + bandEnd) / 2f
                if (bandHeight >= max(2, (panelHeight * 0.015f).toInt()) &&
                    bandHeight <= panelHeight * 0.20f &&
                    center >= panelHeight * 0.08f && center <= panelHeight * 0.98f
                ) {
                    var strength = 0f
                    for (y in bandStart until bandEnd) strength = max(strength, smoothed[y])
                    bands += Band(bandStart, bandEnd, center, strength)
                }
                start = null
            }
        }

        val merged = mutableListOf<Band>()
        for (band in bands) {
            val last = merged.lastOrNull()
            if (last == null || band.center - last.center > panelHeight * 0.055f) {
                merged += band
            } else if (band.strength > last.strength) {
                merged[merged.lastIndex] = band
            }
        }
        if (merged.size < rows) return 0f

        var best = 0f
        for (offset in 0..merged.size - rows) {
            val sequence = merged.subList(offset, offset + rows)
            val gaps = sequence.zipWithNext { first, second -> second.center - first.center }
            val meanGap = gaps.average().toFloat()
            if (meanGap <= 0f) continue
            val variance = gaps.fold(0f) { total, value ->
                val delta = value - meanGap
                total + delta * delta
            } / gaps.size.coerceAtLeast(1)
            val coefficient = sqrt(variance) / max(1f, meanGap)
            val regularity = (1f - coefficient / 0.35f).coerceIn(0f, 1f)
            val span = (sequence.last().center - sequence.first().center) / panelHeight
            val expectedSpan = (rows - 1f) / rows
            val spanScore = (1f - abs(span - expectedSpan) / 0.18f).coerceIn(0f, 1f)
            val edgeMargin = (sequence.first().center / panelHeight +
                (1f - sequence.last().center / panelHeight)) / 2f
            val expectedMargin = 1f / (rows * 2f)
            val edgeScore = (1f - abs(edgeMargin - expectedMargin) / 0.14f).coerceIn(0f, 1f)
            val strength = sequence.map(Band::strength).average().toFloat().coerceIn(0f, 1f)
            val candidate = regularity * 0.44f + spanScore * 0.31f + edgeScore * 0.13f + strength * 0.12f
            best = max(best, candidate)
        }
        return best.coerceIn(0f, 1f)
    }

    private fun stripEvidence(
        frame: ScoreboardFrame,
        panel: NormalizedRect,
        rows: Int,
        fromLeft: Boolean
    ): Float {
        val panelLeft = (panel.left * frame.width).toInt().coerceIn(0, frame.width - 1)
        val panelTop = (panel.top * frame.height).toInt().coerceIn(0, frame.height - 1)
        val panelRight = (panel.right * frame.width).toInt().coerceIn(panelLeft + 1, frame.width)
        val panelBottom = (panel.bottom * frame.height).toInt().coerceIn(panelTop + 1, frame.height)
        val panelWidth = panelRight - panelLeft
        val panelHeight = panelBottom - panelTop
        if (panelWidth < 24 || panelHeight < 40) return 0f

        val stripWidth = max(8, (panelWidth * 0.15f).toInt())
        val stripLeft = if (fromLeft) panelLeft else panelRight - stripWidth
        val stripRight = if (fromLeft) panelLeft + stripWidth else panelRight
        val rowCounts = FloatArray(panelHeight)

        for (localY in 0 until panelHeight) {
            val y = panelTop + localY
            var bright = 0
            for (x in stripLeft until stripRight) {
                val index = (y * frame.width + x) * 4
                val r = frame.rgbaBytes[index].toInt() and 0xff
                val g = frame.rgbaBytes[index + 1].toInt() and 0xff
                val b = frame.rgbaBytes[index + 2].toInt() and 0xff
                val maximum = max(r, max(g, b))
                val minimum = min(r, min(g, b))
                val saturation = maximum - minimum
                // Includes white role/status icons after TV white-balance shifts.
                if (maximum >= 142 && minimum >= 72 && saturation <= 148) bright++
            }
            rowCounts[localY] = bright.toFloat() / stripWidth
        }

        val smoothed = FloatArray(panelHeight)
        for (index in rowCounts.indices) {
            var sum = 0f
            var count = 0
            for (offset in -2..2) {
                val source = index + offset
                if (source in rowCounts.indices) {
                    sum += rowCounts[source]
                    count++
                }
            }
            smoothed[index] = if (count == 0) 0f else sum / count
        }

        var best = 0f
        val topTrims = floatArrayOf(0f, 0.025f, 0.05f, 0.075f, 0.10f, 0.14f, 0.18f, 0.22f)
        val bottomTrims = floatArrayOf(0f, 0.02f, 0.04f, 0.07f)
        for (topTrim in topTrims) {
            for (bottomTrim in bottomTrims) {
                val contentTop = panelHeight * topTrim
                val contentBottom = panelHeight * (1f - bottomTrim)
                val contentHeight = contentBottom - contentTop
                if (contentHeight < panelHeight * 0.55f) continue
                val rowHeight = contentHeight / rows
                if (rowHeight < 7f) continue

                val centerStrengths = FloatArray(rows)
                for (row in 0 until rows) {
                    val center = contentTop + rowHeight * (row + 0.5f)
                    val radius = max(2, (rowHeight * 0.24f).toInt())
                    var peak = 0f
                    val start = (center.toInt() - radius).coerceAtLeast(0)
                    val end = (center.toInt() + radius).coerceAtMost(panelHeight - 1)
                    for (y in start..end) peak = max(peak, smoothed[y])
                    centerStrengths[row] = peak
                }

                val mean = centerStrengths.average().toFloat()
                val weakest = centerStrengths.minOrNull() ?: 0f
                val variance = centerStrengths.fold(0f) { total, value ->
                    val delta = value - mean
                    total + delta * delta
                } / rows
                val consistency = (1f - sqrt(variance) / max(0.08f, mean)).coerceIn(0f, 1f)

                // Also reward horizontal transitions at the expected row borders.
                var border = 0f
                for (row in 1 until rows) {
                    val y = (contentTop + rowHeight * row).toInt().coerceIn(1, panelHeight - 2)
                    var local = 0f
                    for (offset in -2..2) {
                        val a = smoothed[(y + offset - 1).coerceIn(0, panelHeight - 1)]
                        val b = smoothed[(y + offset + 1).coerceIn(0, panelHeight - 1)]
                        local = max(local, abs(a - b))
                    }
                    border += local
                }
                border /= max(1, rows - 1)

                val candidate = (
                    mean * 0.48f +
                        weakest * 0.24f +
                        consistency * 0.18f +
                        (border * 2.4f).coerceIn(0f, 1f) * 0.10f
                    ).coerceIn(0f, 1f)
                best = max(best, candidate)
            }
        }
        return best
    }
}
