package com.herolens.app.vision

/**
 * Fixed-length scan burst used by V7. A burst avoids endless camera processing and
 * makes the capture moment explicit: aim, tap Scan, hold steady, review.
 */
class BurstScanSession(
    private val targetFrames: Int,
    minimumVotes: Int,
    minimumAverageConfidence: Float
) {
    private val stabilizer = LiveScanStabilizer(
        windowSize = targetFrames,
        minimumVotes = minimumVotes,
        minimumAverageConfidence = minimumAverageConfidence
    )
    private var acceptedFrames = 0

    init {
        require(targetFrames >= 2)
    }

    @Synchronized
    fun reset() {
        acceptedFrames = 0
        stabilizer.reset()
    }

    @Synchronized
    fun add(result: AutoDetectionResult): BurstScanProgress {
        acceptedFrames++
        val snapshot = stabilizer.add(result)
        return BurstScanProgress(
            framesCaptured = acceptedFrames,
            targetFrames = targetFrames,
            snapshot = snapshot,
            complete = acceptedFrames >= targetFrames
        )
    }
}

data class BurstScanProgress(
    val framesCaptured: Int,
    val targetFrames: Int,
    val snapshot: StableDetectionSnapshot,
    val complete: Boolean
) {
    val fraction: Float get() = (framesCaptured / targetFrames.toFloat()).coerceIn(0f, 1f)
}
