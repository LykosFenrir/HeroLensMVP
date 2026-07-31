package com.herolens.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    @Test
    fun returnsThreeHeroesFromRequestedRole() {
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.DAMAGE,
                mapProfile = MapProfile.OPEN,
                allyIds = setOf("mercy"),
                enemyIds = setOf("pharah", "echo"),
                preferences = emptyMap()
            )
        )

        assertEquals(3, result.size)
        assertTrue(result.all { it.hero.role == Role.DAMAGE })
    }

    @Test
    fun comfortCanInfluenceButDoesNotBypassRole() {
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.SUPPORT,
                mapProfile = MapProfile.MIXED,
                allyIds = emptySet(),
                enemyIds = setOf("tracer", "genji"),
                preferences = mapOf("brigitte" to 4, "mercy" to 1)
            )
        )

        assertEquals("brigitte", result.first().hero.id)
        assertTrue(result.all { it.hero.role == Role.SUPPORT })
    }

    @Test
    fun highUltimateChargeAddsSwitchWarning() {
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.TANK,
                mapProfile = MapProfile.VERTICAL,
                allyIds = emptySet(),
                enemyIds = setOf("widowmaker", "ana"),
                preferences = emptyMap(),
                currentHeroId = "reinhardt",
                ultimateCharge = 85
            )
        )

        assertTrue(result.any { recommendation ->
            recommendation.hero.id != "reinhardt" && recommendation.reasons.any { it is Reason.SwitchCost }
        })
    }
    @Test
    fun counterReasonExplainsHowTheMatchupWorks() {
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.DAMAGE,
                mapProfile = MapProfile.OPEN,
                allyIds = emptySet(),
                enemyIds = setOf("pharah"),
                preferences = mapOf("soldier-76" to 4)
            )
        )

        val soldier = result.first { it.hero.id == "soldier-76" }
        val counter = soldier.reasons.filterIsInstance<Reason.Counters>().first()
        assertTrue(counter.detail.contains("hitscan", ignoreCase = true))
        assertTrue(counter.detailAr.isNotBlank())
    }

}
