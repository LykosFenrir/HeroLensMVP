package com.herolens.app.vision

import java.util.ArrayDeque

/**
 * Combines several live camera frames so a single blurry or misaligned frame cannot
 * immediately change a detected hero. Supports both 5v5 and 6v6 scoreboards.
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
        val chosenTeamSize = frames
            .groupBy { it.teamSize }
            .maxByOrNull { (_, values) -> values.sumOf { it.quality.toDouble() } }
            ?.key?.coerceIn(5, 6) ?: result.teamSize.coerceIn(5, 6)
        val eligibleFrames = frames.filter { it.teamSize == chosenTeamSize }.ifEmpty { listOf(result) }
        val chosenLayout = eligibleFrames
            .groupBy { it.layout }
            .maxByOrNull { (_, values) -> values.sumOf { it.quality.toDouble() } }
            ?.key ?: result.layout

        val totalSlots = chosenTeamSize * 2
        val stable = (0 until totalSlots).map { absoluteSlot ->
            val matching = eligibleFrames.mapNotNull { frame ->
                frame.result.detections.firstOrNull { detection ->
                    detection.absoluteSlot(chosenTeamSize) == absoluteSlot && detection.heroId != null
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
            val latest = eligibleFrames.asReversed().firstNotNullOfOrNull { frame ->
                frame.result.detections.firstOrNull {
                    it.absoluteSlot(chosenTeamSize) == absoluteSlot && it.heroId == heroId
                }
            }
            val team = if (absoluteSlot < chosenTeamSize) TeamSide.ALLY else TeamSide.ENEMY
            val slot = absoluteSlot % chosenTeamSize
            val fallback = result.result.detections.firstOrNull {
                it.team == team && it.slot == slot
            } ?: HeroDetection(null, team, slot, 0f)
            val accepted = votes >= minimumVotes && averageConfidence >= minimumAverageConfidence
            (latest ?: fallback).copy(
                heroId = heroId.takeIf { accepted },
                confidence = averageConfidence
            )
        }

        val stableAllies = stable.count { it.team == TeamSide.ALLY && it.heroId != null }
        val stableEnemies = stable.count { it.team == TeamSide.ENEMY && it.heroId != null }
        val latestGeometry = eligibleFrames.lastOrNull { it.scoreboardRegion != null }
        return StableDetectionSnapshot(
            detections = stable,
            layout = chosenLayout,
            stableSlots = stableAllies + stableEnemies,
            framesObserved = eligibleFrames.size,
            ready = stableEnemies == chosenTeamSize && stableAllies >= chosenTeamSize - 1,
            teamSize = chosenTeamSize,
            scoreboardRegion = latestGeometry?.scoreboardRegion,
            slotRects = latestGeometry?.slotRects.orEmpty()
        )
    }
}

fun HeroDetection.absoluteSlot(teamSize: Int): Int =
    if (team == TeamSide.ALLY) slot else slot + teamSize

data class StableDetectionSnapshot(
    val detections: List<HeroDetection>,
    val layout: ScoreboardLayout,
    val stableSlots: Int,
    val framesObserved: Int,
    val ready: Boolean,
    val teamSize: Int,
    val scoreboardRegion: ScoreboardRegion?,
    val slotRects: List<Pair<TeamSide, NormalizedRect>>
)
