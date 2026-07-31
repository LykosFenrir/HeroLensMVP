package com.herolens.app.vision

import java.util.ArrayDeque

/**
 * Combines several live camera frames so a single blurry or misaligned frame cannot
 * immediately change a detected hero. The most recent frames receive more weight.
 */
class LiveScanStabilizer(
    private val windowSize: Int = 5,
    private val minimumVotes: Int = 3,
    private val minimumAverageConfidence: Float = 0.50f
) {
    private val history = ArrayDeque<AutoDetectionResult>()

    @Synchronized
    fun reset() = history.clear()

    @Synchronized
    fun add(result: AutoDetectionResult): StableDetectionSnapshot {
        history.addLast(result)
        while (history.size > windowSize) history.removeFirst()

        val frames = history.toList()
        val chosenLayout = frames
            .groupBy { it.layout }
            .maxByOrNull { (_, values) -> values.sumOf { it.quality.toDouble() } }
            ?.key ?: result.layout

        val stable = (0 until 10).map { absoluteSlot ->
            val matching = frames.mapNotNull { frame ->
                frame.result.detections.firstOrNull { detection ->
                    detection.absoluteSlot == absoluteSlot && detection.heroId != null
                }?.let { detection -> frame to detection }
            }
            val groups = matching.groupBy { it.second.heroId!! }
            val winner = groups.maxByOrNull { (_, values) ->
                values.mapIndexed { index, pair ->
                    val recencyWeight = 1f + index * 0.10f
                    pair.second.confidence * recencyWeight
                }.sum()
            }
            val heroId = winner?.key
            val winnerValues = winner?.value.orEmpty()
            val votes = winnerValues.size
            val averageConfidence = if (winnerValues.isEmpty()) 0f else {
                winnerValues.map { it.second.confidence }.average().toFloat()
            }
            val latest = frames.asReversed().firstNotNullOfOrNull { frame ->
                frame.result.detections.firstOrNull {
                    it.absoluteSlot == absoluteSlot && it.heroId == heroId
                }
            }
            val accepted = votes >= minimumVotes && averageConfidence >= minimumAverageConfidence
            (latest ?: result.result.detections.first { it.absoluteSlot == absoluteSlot }).copy(
                heroId = heroId.takeIf { accepted },
                confidence = averageConfidence
            )
        }

        val stableAllies = stable.count { it.team == TeamSide.ALLY && it.heroId != null }
        val stableEnemies = stable.count { it.team == TeamSide.ENEMY && it.heroId != null }
        return StableDetectionSnapshot(
            detections = stable,
            layout = chosenLayout,
            stableSlots = stableAllies + stableEnemies,
            framesObserved = frames.size,
            ready = stableEnemies == 5 && stableAllies >= 4
        )
    }
}

val HeroDetection.absoluteSlot: Int
    get() = if (team == TeamSide.ALLY) slot else slot + 5

data class StableDetectionSnapshot(
    val detections: List<HeroDetection>,
    val layout: ScoreboardLayout,
    val stableSlots: Int,
    val framesObserved: Int,
    val ready: Boolean
)
