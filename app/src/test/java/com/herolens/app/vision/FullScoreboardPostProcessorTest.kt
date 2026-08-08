package com.herolens.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScoreboardPostProcessorTest {
    private val labels = listOf(
        "__unknown__", "sierra", "dva", "mercy", "genji", "ana", "ashe",
        "baptiste", "cassidy", "echo", "juno", "kiriko", "lucio"
    )
    private val roi = NormalizedRect(0.10f, 0.05f, 0.90f, 0.90f)
    private val region = ScoreboardRegion(
        allyPanel = NormalizedRect(0.20f, 0.10f, 0.80f, 0.40f),
        enemyPanel = NormalizedRect(0.20f, 0.55f, 0.80f, 0.85f),
        confidence = 0.9f
    )

    @Test
    fun assignsDetectionsToCorrectTeamAndRow() {
        val rows = listOf(
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.91f, classIndex = 1),
            row(globalX = 0.25f, globalY = 0.25f, confidence = 0.86f, classIndex = 2),
            row(globalX = 0.25f, globalY = 0.37f, confidence = 0.82f, classIndex = 3),
            row(globalX = 0.25f, globalY = 0.58f, confidence = 0.90f, classIndex = 4),
            row(globalX = 0.25f, globalY = 0.70f, confidence = 0.84f, classIndex = 1),
            row(globalX = 0.25f, globalY = 0.82f, confidence = 0.80f, classIndex = 2)
        )

        val result = FullScoreboardPostProcessor.process(rows, labels, roi, region, 5)

        assertEquals(5, result.teamSize)
        assertEquals("sierra", detection(result, TeamSide.ALLY, 0).heroId)
        assertEquals("dva", detection(result, TeamSide.ALLY, 2).heroId)
        assertEquals("mercy", detection(result, TeamSide.ALLY, 4).heroId)
        assertEquals("genji", detection(result, TeamSide.ENEMY, 0).heroId)
        assertEquals("sierra", detection(result, TeamSide.ENEMY, 2).heroId)
        assertEquals("dva", detection(result, TeamSide.ENEMY, 4).heroId)
        assertTrue(result.warnings.contains("Full-scoreboard detector active."))
    }

    @Test
    fun keepsHighestOverlappingBoxAndPreservesMissingSlot() {
        val rows = listOf(
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.92f, classIndex = 1),
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.51f, classIndex = 2),
            row(globalX = 0.25f, globalY = 0.37f, confidence = 0.88f, classIndex = 3)
        )

        val result = FullScoreboardPostProcessor.process(rows, labels, roi, region, 5)

        assertEquals("sierra", detection(result, TeamSide.ALLY, 0).heroId)
        assertNull(detection(result, TeamSide.ALLY, 1).heroId)
        assertNull(detection(result, TeamSide.ALLY, 2).heroId)
        assertEquals("mercy", detection(result, TeamSide.ALLY, 4).heroId)
    }

    @Test
    fun rejectsUnknownAndLowConfidenceRows() {
        val rows = listOf(
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.99f, classIndex = 0),
            row(globalX = 0.25f, globalY = 0.19f, confidence = 0.31f, classIndex = 1),
            row(globalX = 0.25f, globalY = 0.25f, confidence = 0.90f, classIndex = 99)
        )

        val result = FullScoreboardPostProcessor.process(rows, labels, roi, region, 5)

        assertTrue(result.detections.all { it.heroId == null })
    }

    @Test
    fun genericBoxPostProcessorSupportsFiveColumnDetectorOutput() {
        val rows = listOf(
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.93f, classIndex = 1).copyOf(5),
            row(globalX = 0.25f, globalY = 0.13f, confidence = 0.50f, classIndex = 2).copyOf(5),
            row(globalX = 0.25f, globalY = 0.25f, confidence = 0.88f, classIndex = 2).copyOf(5)
        )

        val boxes = FullScoreboardBoxPostProcessor.process(rows, roi)

        assertEquals(2, boxes.size)
        assertEquals(0.93f, boxes[0].confidence, 0.0001f)
    }

    @Test
    fun classAwarePostProcessorHonorsEveryForcedTeamSize() {
        for (teamSize in 3..6) {
            val rows = buildList {
                repeat(teamSize) { slot ->
                    add(row(0.25f, region.allyPanel.top + region.allyPanel.height * (slot + 0.5f) / teamSize, 0.90f, slot + 1))
                    add(row(0.25f, region.enemyPanel.top + region.enemyPanel.height * (slot + 0.5f) / teamSize, 0.90f, slot + teamSize + 1))
                }
            }

            val result = FullScoreboardPostProcessor.process(rows, labels, roi, region, teamSize)

            assertEquals(teamSize, result.teamSize)
            assertEquals(teamSize * 2, result.detections.size)
            assertTrue(result.detections.all { it.heroId != null })
        }
    }

    @Test
    fun genericBoxPostProcessorRetainsMoreThanTwelveDistinctCandidates() {
        val rows = buildList {
            repeat(4) { rowIndex ->
                repeat(5) { columnIndex ->
                    val candidate = row(
                        globalX = 0.16f + columnIndex * 0.15f,
                        globalY = 0.12f + rowIndex * 0.18f,
                        confidence = 0.95f - (rowIndex * 5 + columnIndex) * 0.005f,
                        classIndex = 1
                    ).copyOf(5)
                    candidate[2] = 0.025f
                    candidate[3] = 0.025f
                    add(candidate)
                }
            }
        }

        val boxes = FullScoreboardBoxPostProcessor.process(rows, roi)

        assertEquals(20, boxes.size)
    }

    private fun detection(result: DetectionResult, team: TeamSide, slot: Int): HeroDetection =
        result.detections.single { it.team == team && it.slot == slot }

    private fun row(
        globalX: Float,
        globalY: Float,
        confidence: Float,
        classIndex: Int
    ): FloatArray {
        val centerX = (globalX - roi.left) / roi.width
        val centerY = (globalY - roi.top) / roi.height
        return floatArrayOf(centerX, centerY, 0.07f, 0.055f, confidence, classIndex.toFloat())
    }
}
