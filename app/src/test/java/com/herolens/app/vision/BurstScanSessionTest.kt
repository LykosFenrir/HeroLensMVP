package com.herolens.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstScanSessionTest {
    @Test
    fun completesAtFixedFrameCountAndUsesConsensus() {
        val session = BurstScanSession(targetFrames = 3, minimumVotes = 2, minimumAverageConfidence = 0.60f)
        val frame = AutoDetectionResult(
            result = DetectionResult(
                detections = buildList {
                    repeat(5) { slot -> add(HeroDetection("hero_$slot", TeamSide.ALLY, slot, 0.85f)) }
                    repeat(5) { slot -> add(HeroDetection("enemy_$slot", TeamSide.ENEMY, slot, 0.86f)) }
                },
                templatesLoaded = 52,
                teamSize = 5
            ),
            layout = ScoreboardLayout.PORTRAITS_LEFT,
            quality = 0.9f,
            teamSize = 5
        )

        assertFalse(session.add(frame).complete)
        assertFalse(session.add(frame).complete)
        val final = session.add(frame)
        assertTrue(final.complete)
        assertEquals(3, final.framesCaptured)
        assertEquals(10, final.snapshot.stableSlots)
        assertTrue(final.snapshot.ready)
    }
}
