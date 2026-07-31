package com.herolens.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveScannerTest {
    @Test
    fun geometrySearchIncludesHeaderTrimmedRoleGutterLayout() {
        val region = ScoreboardRegion(
            allyPanel = NormalizedRect(0.20f, 0.10f, 0.80f, 0.42f),
            enemyPanel = NormalizedRect(0.20f, 0.50f, 0.80f, 0.82f),
            confidence = 0.9f
        )
        val profiles = ScoreboardSlots.localizedProfiles(
            region,
            ScoreboardLayout.PORTRAITS_LEFT,
            teamSize = 5
        )
        assertEquals(40, profiles.size)
        val expectedX = region.allyPanel.left + region.allyPanel.width * 0.13f
        val expectedFirstY = region.allyPanel.top + region.allyPanel.height * 0.18f +
            (region.allyPanel.height * (1f - 0.18f - 0.04f) / 5f) * 0.5f
        assertTrue(profiles.any { slots ->
            val first = slots.first().second
            abs(first.centerX - expectedX) < 0.005f && abs(first.centerY - expectedFirstY) < 0.008f
        })
    }

    @Test
    fun textureScoreRejectsFlatTeamPanel() {
        val width = 32
        val height = 32
        val flat = ByteArray(width * height * 4)
        val patterned = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val index = (y * width + x) * 4
            flat[index] = 25
            flat[index + 1] = 150.toByte()
            flat[index + 2] = 220.toByte()
            flat[index + 3] = 0xff.toByte()

            val light = if ((x / 4 + y / 4) % 2 == 0) 230 else 35
            patterned[index] = light.toByte()
            patterned[index + 1] = (255 - light / 2).toByte()
            patterned[index + 2] = (70 + (x * 5) % 170).toByte()
            patterned[index + 3] = 0xff.toByte()
        }
        val flatScore = SignatureMath.textureScore(CroppedImage(flat, width, height))
        val patternedScore = SignatureMath.textureScore(CroppedImage(patterned, width, height))
        assertTrue(patternedScore > flatScore + 0.08f)
    }

    @Test
    fun stabilizerDoesNotLockWeakRepeatedGuesses() {
        val stabilizer = LiveScanStabilizer(
            windowSize = 5,
            minimumVotes = 3,
            minimumAverageConfidence = 0.54f
        )
        repeat(5) {
            val detections = buildList {
                repeat(5) { slot -> add(HeroDetection("ally$slot", TeamSide.ALLY, slot, 0.42f)) }
                repeat(5) { slot -> add(HeroDetection("enemy$slot", TeamSide.ENEMY, slot, 0.42f)) }
            }
            stabilizer.add(
                AutoDetectionResult(
                    DetectionResult(detections, templatesLoaded = 52),
                    ScoreboardLayout.PORTRAITS_LEFT,
                    quality = 0.6f
                )
            )
        }
        val snapshot = stabilizer.add(
            AutoDetectionResult(
                DetectionResult(emptyList(), templatesLoaded = 52),
                ScoreboardLayout.PORTRAITS_LEFT,
                quality = 0f
            )
        )
        assertEquals(0, snapshot.stableSlots)
        assertTrue(!snapshot.ready)
    }
}
