package com.herolens.app.vision

/** On-device scoreboard recognition contract. */
interface HeroDetector {
    suspend fun detect(
        frame: ScoreboardFrame,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit = {}
    ): DetectionResult
}

data class ScoreboardFrame(
    val rgbaBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0
)

data class HeroCandidate(
    val heroId: String,
    val confidence: Float
)

data class HeroDetection(
    val heroId: String?,
    val team: TeamSide,
    val slot: Int,
    val confidence: Float,
    val alternatives: List<HeroCandidate> = emptyList(),
    val bounds: NormalizedRect? = null
)

data class ScoreboardRegion(
    val allyPanel: NormalizedRect,
    val enemyPanel: NormalizedRect,
    val confidence: Float
) {
    val bounds: NormalizedRect
        get() = NormalizedRect(
            left = minOf(allyPanel.left, enemyPanel.left),
            top = minOf(allyPanel.top, enemyPanel.top),
            right = maxOf(allyPanel.right, enemyPanel.right),
            bottom = maxOf(allyPanel.bottom, enemyPanel.bottom)
        )
}



enum class ScoreboardSearchState { FOUND, INCOMPLETE, NOT_FOUND }

data class ScoreboardSearchResult(
    val region: ScoreboardRegion?,
    val framingBounds: NormalizedRect?,
    val confidence: Float,
    val state: ScoreboardSearchState,
    val message: String
)

data class DetectionResult(
    val detections: List<HeroDetection>,
    val templatesLoaded: Int,
    val warnings: List<String> = emptyList(),
    val scoreboardRegion: ScoreboardRegion? = null,
    val slotRects: List<Pair<TeamSide, NormalizedRect>> = emptyList(),
    val teamSize: Int = 5,
    val profileScore: Float = 0f
)

data class AutoDetectionResult(
    val result: DetectionResult,
    val layout: ScoreboardLayout,
    val quality: Float,
    val scoreboardRegion: ScoreboardRegion? = result.scoreboardRegion,
    val slotRects: List<Pair<TeamSide, NormalizedRect>> = result.slotRects,
    val teamSize: Int = result.teamSize
)

enum class TeamSide { ALLY, ENEMY }

enum class ScoreboardLayout(val displayName: String) {
    AUTO("Auto"),
    PORTRAITS_LEFT("Portraits left"),
    PORTRAITS_RIGHT("Portraits right")
}
