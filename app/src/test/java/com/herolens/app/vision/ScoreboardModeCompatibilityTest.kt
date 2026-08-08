package com.herolens.app.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreboardModeCompatibilityTest {
    @Test
    fun explicitArcadeAndStandardSizesAreNeverOverridden() {
        for (size in 3..6) {
            assertEquals(size, ScoreboardTeamSizeResolver.resolve(size, 5, 6, 6))
        }
    }

    @Test
    fun oneVisibleSixRowPanelSelectsSixWhenAutoIsUncertain() {
        assertEquals(6, ScoreboardTeamSizeResolver.resolve(null, null, 6, 5))
        assertEquals(6, ScoreboardTeamSizeResolver.resolve(null, 5, 4, 6))
    }

    @Test
    fun incompleteBoxDetectionDoesNotPretendStandardFiveIsThree() {
        assertEquals(5, ScoreboardTeamSizeResolver.resolve(null, 5, 3, 3))
        assertEquals(5, ScoreboardTeamSizeResolver.resolve(null, null, 3, 2))
    }

    @Test
    fun localizedProfilesSupportTwoTeamThreeThroughSixPlayerModes() {
        val region = ScoreboardRegion(
            allyPanel = NormalizedRect(0.10f, 0.10f, 0.90f, 0.44f),
            enemyPanel = NormalizedRect(0.10f, 0.56f, 0.90f, 0.90f),
            confidence = 0.9f
        )
        for (size in 3..6) {
            for (layout in listOf(ScoreboardLayout.PORTRAITS_LEFT, ScoreboardLayout.PORTRAITS_RIGHT)) {
                val profiles = ScoreboardSlots.localizedProfiles(region, layout, size)
                assertEquals(40, profiles.size)
                assertEquals(size * 2, profiles.first().size)
                assertEquals(size, profiles.first().count { it.first == TeamSide.ALLY })
                assertEquals(size, profiles.first().count { it.first == TeamSide.ENEMY })
            }
        }
    }
}
