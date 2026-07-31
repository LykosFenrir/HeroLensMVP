package com.herolens.app.vision

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Finds the blue/red Overwatch scoreboard tables before hero classification.
 *
 * The enemy table is used as the geometric anchor because its red background is
 * usually isolated from browser chrome and the hero detail panel. The ally table
 * is then searched immediately above the enemy table inside the same horizontal
 * span. This avoids the V6.2 failure where a blue browser toolbar was merged with
 * the actual blue scoreboard.
 */
object ScoreboardLocator {
    private const val SAMPLE_WIDTH = 320

    fun locate(frame: ScoreboardFrame): ScoreboardRegion? = search(frame).region

    fun search(frame: ScoreboardFrame): ScoreboardSearchResult {
        if (frame.width < 64 || frame.height < 64) return notFound("Camera frame is too small")
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

                // Tolerates warm/cool TV white balance while excluding the low-
                // saturation wall, desk and monitor bezel around the scoreboard.
                blue[target] = saturation >= 20 &&
                    b >= 52 && g >= 45 &&
                    b > r * 1.025f && g > r * 0.94f &&
                    (b + g) > r * 1.78f && maxChannel < 253

                // Enemy panels may be deep red, magenta or slightly orange after
                // camera auto white-balance and screen exposure.
                red[target] = saturation >= 36 &&
                    r >= 62 &&
                    (r - g) >= 34 && (r - b) >= 24 &&
                    r > g * 1.12f && r > b * 1.06f &&
                    maxChannel < 253
            }
        }

        val candidates = denseHorizontalBands(red, sampleWidth, sampleHeight)
            .take(18)
            .mapNotNull { enemy ->
                val ally = findAllyAbove(blue, sampleWidth, sampleHeight, enemy) ?: return@mapNotNull null
                buildCandidate(ally, enemy, sampleWidth, sampleHeight)
            }
            .sortedByDescending(Candidate::score)

        val complete = candidates.firstOrNull { it.complete && it.score >= 3.15f }
        if (complete != null) {
            return ScoreboardSearchResult(
                region = complete.region,
                framingBounds = complete.region.bounds,
                confidence = complete.confidence,
                state = ScoreboardSearchState.FOUND,
                message = "Scoreboard found"
            )
        }

        val partial = candidates.firstOrNull { it.score >= 2.70f && it.confidence >= 0.55f }
        if (partial != null) {
            return ScoreboardSearchResult(
                region = null,
                framingBounds = partial.region.bounds,
                confidence = partial.confidence,
                state = ScoreboardSearchState.INCOMPLETE,
                message = "Scoreboard found, but both teams are not fully populated yet"
            )
        }

        // A conservative legacy fallback helps unusual colour profiles without
        // reintroducing fixed screen coordinates.
        legacyLocate(blue, red, sampleWidth, sampleHeight)?.takeIf { it.confidence >= 0.45f }?.let { region ->
            return ScoreboardSearchResult(
                region = region,
                framingBounds = region.bounds,
                confidence = region.confidence,
                state = ScoreboardSearchState.FOUND,
                message = "Scoreboard found with fallback profile"
            )
        }
        return notFound("Blue and red scoreboard panels were not found")
    }

    private fun buildCandidate(
        ally: Band,
        enemy: Band,
        width: Int,
        height: Int
    ): Candidate {
        // The enemy table provides the cleanest left/right limits. Reusing its
        // horizontal span for both teams removes blue browser bars and hero-info
        // panels that otherwise shift portrait crops away from the actual icons.
        // Solid red pixels often begin after the portrait artwork itself. Reserve
        // roughly one portrait-column width on both sides so the later layout search
        // can evaluate the icon edge rather than only the flat team background.
        val portraitAllowance = max(2, enemy.height / 4)
        val horizontalPadding = max(1, (enemy.width * 0.015f).toInt())
        val commonLeft = (enemy.left - portraitAllowance - horizontalPadding).coerceAtLeast(0)
        val commonRight = (enemy.right + portraitAllowance + horizontalPadding).coerceAtMost(width)
        val allyAligned = ally.copy(left = commonLeft, right = commonRight)
        val enemyAligned = enemy.copy(left = commonLeft, right = commonRight)
        val region = ScoreboardRegion(
            allyPanel = allyAligned.toNormalized(width, height).expand(0.002f, 0.004f),
            enemyPanel = enemyAligned.toNormalized(width, height).expand(0.002f, 0.004f),
            confidence = 0f
        )

        val overlap = max(0, min(ally.right, enemy.right) - max(ally.left, enemy.left)).toFloat()
        val overlapRatio = overlap / max(1f, min(ally.width, enemy.width).toFloat())
        val heightRatio = min(ally.height, enemy.height).toFloat() / max(1, max(ally.height, enemy.height))
        val widthRatio = ally.width.toFloat() / max(1, enemy.width)
        val widthSimilarity = 1f - min(1f, abs(ln(max(0.01f, widthRatio))) / ln(2.8f))
        val gap = enemy.top - ally.bottom
        val averageHeight = (ally.height + enemy.height) / 2f
        val gapScore = 1f - min(1f, abs(gap) / max(1f, averageHeight * 0.65f))
        val areaScore = min(1f, (ally.area + enemy.area).toFloat() / (width * height * 0.045f))
        val score = overlapRatio * 1.75f + heightRatio * 0.65f + widthSimilarity * 0.55f +
            gapScore * 0.55f + areaScore * 0.65f + ally.density * 0.50f + enemy.density * 0.55f
        val confidence = ((score - 2.55f) / 2.25f).coerceIn(0.18f, 0.99f)
        val minimumPanelHeight = max(10, (height * 0.025f).toInt())
        val complete = ally.height >= minimumPanelHeight &&
            enemy.height >= minimumPanelHeight &&
            ally.aspect in 1.0f..8.0f && enemy.aspect in 1.0f..8.0f &&
            heightRatio >= 0.34f
        return Candidate(region.copy(confidence = confidence), score, confidence, complete)
    }

    /** Finds the last substantial cyan/blue row block immediately above an enemy table. */
    private fun findAllyAbove(
        mask: BooleanArray,
        width: Int,
        height: Int,
        enemy: Band
    ): Band? {
        val xPadding = max(1, (enemy.width * 0.08f).toInt())
        val left = (enemy.left - xPadding).coerceAtLeast(0)
        val right = (enemy.right + xPadding).coerceAtMost(width)
        val span = right - left
        if (span < 8) return null
        val maxY = (enemy.top + max(2, (enemy.height * 0.15f).toInt())).coerceAtMost(height)
        val rowCounts = IntArray(maxY)
        for (y in 0 until maxY) {
            var count = 0
            val offset = y * width
            for (x in left until right) if (mask[offset + x]) count++
            rowCounts[y] = count
        }
        val threshold = max(4, (span * 0.15f).toInt())
        val activeRows = (0 until maxY).filter { rowCounts[it] >= threshold }
        if (activeRows.isEmpty()) return null
        val ranges = mutableListOf<IntRange>()
        var start = activeRows.first()
        var previous = start
        for (y in activeRows.drop(1)) {
            // Up to two sparse rows are allowed inside portraits/text.
            if (y - previous > 3) {
                ranges += start..previous
                start = y
            }
            previous = y
        }
        ranges += start..previous

        return ranges.mapNotNull { range ->
            var top = range.first
            val bottom = range.last + 1
            var panelHeight = bottom - top
            if (panelHeight > enemy.height * 1.8f) {
                // Browser scoreboards can have a cyan toolbar connected to the ally
                // table. Keep the bottom section nearest the VS divider instead of
                // treating the toolbar as extra player rows.
                panelHeight = max(3, (enemy.height * 1.55f).toInt())
                top = (bottom - panelHeight).coerceAtLeast(0)
            }
            if (panelHeight < 3) return@mapNotNull null
            val gap = enemy.top - bottom
            if (gap < -max(2, enemy.height / 4) || gap > max(8, enemy.height * 2)) return@mapNotNull null
            var area = 0
            var rowsCovered = 0
            for (y in top until bottom) {
                var rowArea = 0
                val offset = y * width
                for (x in left until right) if (mask[offset + x]) rowArea++
                area += rowArea
                if (rowArea >= threshold) rowsCovered++
            }
            val density = area.toFloat() / max(1, span * panelHeight)
            val rowCoverage = rowsCovered.toFloat() / panelHeight
            val heightRatio = panelHeight.toFloat() / max(1, enemy.height)
            val score = density * 2.0f + rowCoverage * 0.55f - max(0, gap) * 0.025f -
                max(0f, heightRatio - 2.2f) * 0.25f
            ScoredBand(Band(left, top, right, bottom, area, density), score)
        }.maxByOrNull(ScoredBand::score)?.band
    }

    /** Finds dense red horizontal rectangles and keeps separate browser/UI components. */
    private fun denseHorizontalBands(mask: BooleanArray, width: Int, height: Int): List<Band> {
        val rowCounts = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            val offset = y * width
            for (x in 0 until width) if (mask[offset + x]) count++
            rowCounts[y] = count
        }
        val peak = rowCounts.maxOrNull() ?: 0
        if (peak < 4) return emptyList()
        val rowThreshold = max(3, (peak * 0.18f).toInt())
        val activeRows = (0 until height).filter { rowCounts[it] >= rowThreshold }
        if (activeRows.isEmpty()) return emptyList()
        val rowRanges = mutableListOf<IntRange>()
        var start = activeRows.first()
        var previous = start
        for (y in activeRows.drop(1)) {
            if (y - previous > 3) {
                rowRanges += start..previous
                start = y
            }
            previous = y
        }
        rowRanges += start..previous

        return buildList {
            for (range in rowRanges) {
                val top = range.first
                val bottom = range.last + 1
                val bandHeight = bottom - top
                if (bandHeight < 3) continue
                val columnCounts = IntArray(width)
                for (y in top until bottom) {
                    val offset = y * width
                    for (x in 0 until width) if (mask[offset + x]) columnCounts[x]++
                }
                val columnPeak = columnCounts.maxOrNull() ?: 0
                val columnThreshold = max(2, max((bandHeight * 0.20f).toInt(), (columnPeak * 0.30f).toInt()))
                val activeColumns = (0 until width).filter { columnCounts[it] >= columnThreshold }
                if (activeColumns.isEmpty()) continue
                var columnStart = activeColumns.first()
                var columnPrevious = columnStart
                fun addRun(left: Int, rightInclusive: Int) {
                    val right = rightInclusive + 1
                    val bandWidth = right - left
                    if (bandWidth <= 0) return
                    var area = 0
                    for (y in top until bottom) {
                        val offset = y * width
                        for (x in left until right) if (mask[offset + x]) area++
                    }
                    val density = area.toFloat() / max(1, bandWidth * bandHeight)
                    val widthFraction = bandWidth.toFloat() / width
                    val heightFraction = bandHeight.toFloat() / height
                    val aspect = bandWidth.toFloat() / bandHeight
                    if (widthFraction >= 0.04f && heightFraction >= 0.010f &&
                        widthFraction <= 0.96f && heightFraction <= 0.62f &&
                        aspect >= 1.0f && density >= 0.075f && area >= 10
                    ) add(Band(left, top, right, bottom, area, density))
                }
                for (x in activeColumns.drop(1)) {
                    if (x - columnPrevious > 4) {
                        addRun(columnStart, columnPrevious)
                        columnStart = x
                    }
                    columnPrevious = x
                }
                addRun(columnStart, columnPrevious)
            }
        }.sortedByDescending { it.area * max(0.20f, it.density) }
    }

    private fun legacyLocate(
        blue: BooleanArray,
        red: BooleanArray,
        width: Int,
        height: Int
    ): ScoreboardRegion? {
        val blueBands = broadBands(blue, width, height)
        val redBands = broadBands(red, width, height)
        var best: Pair<Band, Band>? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (ally in blueBands) for (enemy in redBands) {
            if (enemy.centerY <= ally.centerY) continue
            val overlap = max(0, min(ally.right, enemy.right) - max(ally.left, enemy.left)).toFloat()
            val overlapRatio = overlap / max(1f, min(ally.width, enemy.width).toFloat())
            if (overlapRatio < 0.35f) continue
            val averageHeight = (ally.height + enemy.height) / 2f
            val gap = enemy.top - ally.bottom
            if (gap < -averageHeight * 0.25f || gap > averageHeight * 3.2f) continue
            val score = overlapRatio * 2.2f + min(1f, (ally.area + enemy.area).toFloat() / (width * height * 0.08f))
            if (score > bestScore) { bestScore = score; best = ally to enemy }
        }
        val pair = best ?: return null
        if (bestScore < 2.25f) return null
        val commonLeft = max(pair.first.left, pair.second.left)
        val commonRight = min(pair.first.right, pair.second.right)
        if (commonRight <= commonLeft) return null
        val confidence = ((bestScore - 2.0f) / 1.8f).coerceIn(0.20f, 0.78f)
        return ScoreboardRegion(
            pair.first.copy(left = commonLeft, right = commonRight).toNormalized(width, height).expand(0.004f, 0.006f),
            pair.second.copy(left = commonLeft, right = commonRight).toNormalized(width, height).expand(0.004f, 0.006f),
            confidence
        )
    }

    private fun broadBands(mask: BooleanArray, width: Int, height: Int): List<Band> {
        val rowCounts = IntArray(height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) if (mask[offset + x]) rowCounts[y]++
        }
        val peak = rowCounts.maxOrNull() ?: 0
        if (peak < 5) return emptyList()
        val threshold = max(4, (peak * 0.28f).toInt())
        val rows = (0 until height).filter { rowCounts[it] >= threshold }
        if (rows.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = rows.first()
        var previous = start
        for (y in rows.drop(1)) {
            if (y - previous > 3) { ranges += start..previous; start = y }
            previous = y
        }
        ranges += start..previous
        return ranges.mapNotNull { range ->
            val top = range.first
            val bottom = range.last + 1
            var left = width
            var right = -1
            var area = 0
            for (y in top until bottom) {
                val offset = y * width
                for (x in 0 until width) if (mask[offset + x]) {
                    left = min(left, x); right = max(right, x); area++
                }
            }
            if (right <= left || area < 10) null else {
                val band = Band(left, top, right + 1, bottom, area, area.toFloat() / max(1, (right - left + 1) * (bottom - top)))
                band.takeIf { it.width.toFloat() / width >= 0.05f && it.aspect >= 1.0f }
            }
        }
    }

    private fun notFound(message: String) = ScoreboardSearchResult(
        region = null,
        framingBounds = null,
        confidence = 0f,
        state = ScoreboardSearchState.NOT_FOUND,
        message = message
    )

    private data class Candidate(
        val region: ScoreboardRegion,
        val score: Float,
        val confidence: Float,
        val complete: Boolean
    )

    private data class ScoredBand(val band: Band, val score: Float)

    private data class Band(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val area: Int,
        val density: Float
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
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
