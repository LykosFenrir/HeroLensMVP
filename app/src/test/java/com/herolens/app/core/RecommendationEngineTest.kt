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

    @Test
    fun mixedRolePoolCanRecommendAcrossAllRoles() {
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.DAMAGE,
                mapProfile = MapProfile.MIXED,
                allyIds = setOf("mercy"),
                enemyIds = setOf("pharah", "echo"),
                preferences = emptyMap(),
                allRoles = true
            ),
            limit = HeroCatalog.heroes.size
        )

        assertEquals(HeroCatalog.heroes.size - 1, result.size)
        assertTrue(result.none { it.hero.id == "mercy" })
        assertEquals(Role.entries.toSet(), result.map { it.hero.role }.toSet())
    }

    @Test
    fun draftAvailabilityAndBansAreHardRecommendationConstraints() {
        assertEquals(
            setOf(
                "ana", "ashe", "brigitte", "cassidy", "dva", "doomfist", "freja", "genji",
                "hazard", "jetpack-cat", "junker-queen", "junkrat", "juno", "kiriko", "lucio",
                "mei", "mercy", "moira", "orisa", "pharah", "ramattra", "reaper", "reinhardt",
                "sigma", "sojourn", "soldier-76", "torbjorn", "tracer", "vendetta", "winston",
                "wuyang", "zarya", "zenyatta"
            ),
            HeroCatalog.stadiumHeroIds
        )
        val result = RecommendationEngine.recommend(
            MatchContext(
                role = Role.DAMAGE,
                mapProfile = MapProfile.MIXED,
                allyIds = emptySet(),
                enemyIds = emptySet(),
                preferences = emptyMap(),
                allRoles = true,
                unavailableHeroIds = setOf("ana", "tracer"),
                availableHeroIds = HeroCatalog.stadiumHeroIds
            ),
            limit = HeroCatalog.heroes.size
        )

        assertTrue(result.all { it.hero.id in HeroCatalog.stadiumHeroIds })
        assertTrue(result.none { it.hero.id == "ana" || it.hero.id == "tracer" })
        assertEquals(HeroCatalog.stadiumHeroIds.size - 2, result.size)
        assertTrue(HeroCatalog.stadiumHeroIds.all { it in HeroCatalog.byId })
    }

}
