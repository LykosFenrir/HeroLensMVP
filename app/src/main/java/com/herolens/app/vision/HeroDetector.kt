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
    val alternatives: List<HeroCandidate> = emptyList()
)

data class DetectionResult(
    val detections: List<HeroDetection>,
    val templatesLoaded: Int,
    val warnings: List<String> = emptyList()
)

data class AutoDetectionResult(
    val result: DetectionResult,
    val layout: ScoreboardLayout,
    val quality: Float
)

enum class TeamSide { ALLY, ENEMY }

enum class ScoreboardLayout(val displayName: String) {
    AUTO("Auto"),
    PORTRAITS_LEFT("Portraits left"),
    PORTRAITS_RIGHT("Portraits right")
}
