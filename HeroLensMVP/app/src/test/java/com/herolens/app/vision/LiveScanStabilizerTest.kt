package com.herolens.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScanStabilizerTest {
    @Test
    fun requiresMultipleAgreeingFramesBeforeLocking() {
        val stabilizer = LiveScanStabilizer(windowSize = 5, minimumVotes = 3)
        val first = stabilizer.add(frame())
        val second = stabilizer.add(frame())
        val third = stabilizer.add(frame())

        assertEquals(0, first.stableSlots)
        assertEquals(0, second.stableSlots)
        assertEquals(10, third.stableSlots)
        assertTrue(third.ready)
    }

    @Test
    fun oneWrongFrameDoesNotReplaceConsensus() {
        val stabilizer = LiveScanStabilizer(windowSize = 5, minimumVotes = 3)
        stabilizer.add(frame())
        stabilizer.add(frame())
        stabilizer.add(frame(enemyPrefix = "wrong"))
        val snapshot = stabilizer.add(frame())

        assertTrue(snapshot.ready)
        assertFalse(snapshot.detections.any { it.heroId?.startsWith("wrong") == true })
    }

    private fun frame(enemyPrefix: String = "enemy"): AutoDetectionResult {
        val detections = buildList {
            repeat(5) { slot ->
                add(HeroDetection("ally$slot", TeamSide.ALLY, slot, 0.82f))
            }
            repeat(5) { slot ->
                add(HeroDetection("$enemyPrefix$slot", TeamSide.ENEMY, slot, 0.82f))
            }
        }
        return AutoDetectionResult(
            DetectionResult(detections, templatesLoaded = 52),
            ScoreboardLayout.PORTRAITS_LEFT,
            quality = 0.9f
        )
    }
}
