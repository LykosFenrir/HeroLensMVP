package com.herolens.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroGuideTest {
    @Test
    fun everyCatalogHeroHasACompleteSafeGuide() {
        HeroCatalog.heroes.forEach { hero ->
            val maybeGuide = RecommendationEngine.guideFor(hero.id)
            assertNotNull(maybeGuide)
            val guide = requireNotNull(maybeGuide)
            assertEquals(hero, guide.hero)
            assertTrue(guide.archetype.isNotBlank())
            assertTrue(guide.overview.isNotBlank())
            assertTrue(guide.strengths.isNotEmpty())
            assertTrue(guide.gamePlan.size >= 4)
            val relationships = guide.counters + guide.threats + guide.synergies
            assertTrue(relationships.all { it.hero.id in HeroCatalog.byId })
            assertTrue(relationships.none { it.hero.id == hero.id })
            assertTrue(relationships.all { it.explanation.isNotBlank() && it.howToPlay.isNotBlank() })
            assertEquals(guide.counters.map { it.hero.id }.distinct().size, guide.counters.size)
            assertEquals(guide.threats.map { it.hero.id }.distinct().size, guide.threats.size)
            assertEquals(guide.synergies.map { it.hero.id }.distinct().size, guide.synergies.size)
            assertTrue(guide.counters.map { it.hero.id }.intersect(guide.threats.map { it.hero.id }.toSet()).isEmpty())
        }
    }

    @Test
    fun knownRelationshipsRemainAvailable() {
        val dva = RecommendationEngine.guideFor("dva")!!
        assertTrue(dva.counters.any { it.hero.id == "pharah" && it.evidence == GuideEvidence.CURATED })

        val pharah = RecommendationEngine.guideFor("pharah")!!
        assertTrue(pharah.threats.any { it.hero.id == "dva" })

        val mercy = RecommendationEngine.guideFor("mercy")!!
        assertTrue(mercy.synergies.any { it.hero.id == "pharah" && it.evidence == GuideEvidence.CURATED })

        val lifeweaver = RecommendationEngine.guideFor("lifeweaver")!!
        val roadhog = lifeweaver.counters.first { it.hero.id == "roadhog" }
        assertTrue(roadhog.explanation.contains("Life Grip"))
        assertTrue(roadhog.howToPlay.contains("Hook"))

        val widowWithLifeweaver = RecommendationEngine.guideFor("widowmaker")!!.synergies
            .first { it.hero.id == "lifeweaver" }
        val lifeweaverWithWidow = lifeweaver.synergies.first { it.hero.id == "widowmaker" }
        assertEquals(lifeweaverWithWidow.explanation, widowWithLifeweaver.explanation)
    }

    @Test
    fun unknownHeroDoesNotProduceGuide() {
        assertNull(RecommendationEngine.guideFor("not-a-hero"))
        val empty = RecommendationEngine.guideFor("dva", relationshipLimit = 0)!!
        assertTrue(empty.counters.isEmpty() && empty.threats.isEmpty() && empty.synergies.isEmpty())
    }

    @Test
    fun weakOrContradictoryMatchupsAreNotPresentedAsStrong() {
        val wuyang = RecommendationEngine.guideFor("wuyang")!!
        assertFalse(
            wuyang.counters.any { it.hero.id == "doomfist" } &&
                wuyang.threats.any { it.hero.id == "doomfist" }
        )
        assertFalse(RecommendationEngine.guideFor("anran")!!.counters.any { it.hero.id == "juno" })
        assertEquals("Tempo support", RecommendationEngine.guideFor("lucio")!!.archetype)
    }

    @Test
    fun searchIgnoresPunctuationSpacingAndDiacritics() {
        assertTrue(HeroSearch.matches(HeroCatalog.byId.getValue("dva"), "dva", null))
        assertTrue(HeroSearch.matches(HeroCatalog.byId.getValue("soldier-76"), "soldier76", null))
        assertTrue(HeroSearch.matches(HeroCatalog.byId.getValue("torbjorn"), "torbjorn", Role.DAMAGE))
        assertEquals("torbjorn", HeroSearch.normalize("Torbjörn"))
        assertFalse(HeroSearch.matches(HeroCatalog.byId.getValue("torbjorn"), "torbjorn", Role.TANK))
    }
}
