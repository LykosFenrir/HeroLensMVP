package com.herolens.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchStatsTest {
    @Test
    fun resultParserAcceptsLegacyLabelsWithoutInventingAResult() {
        assertEquals(MatchResult.WIN, MatchResult.fromPersistedValue(" victory "))
        assertEquals(MatchResult.LOSS, MatchResult.fromPersistedValue("defeat"))
        assertEquals(MatchResult.DRAW, MatchResult.fromPersistedValue("tied"))
        assertEquals(MatchResult.UNKNOWN, MatchResult.fromPersistedValue("abandoned"))
        assertEquals(MatchResult.UNKNOWN, MatchResult.fromPersistedValue(null))
    }

    @Test
    fun normalizationKeepsOnlySafeBoundedValues() {
        val normalized = match(
            timestamp = 42L,
            heroId = "  Soldier 76  ",
            result = MatchResult.WIN
        ).copy(
            battleTag = "  Player#1234\n ",
            mode = " competitive role queue ",
            mapLabel = "  King's Row\t ",
            eliminations = -2,
            assists = 9,
            damage = Int.MAX_VALUE,
            source = " scoreboard scan ",
            confidence = 140
        ).normalizedOrNull()!!

        assertEquals("soldier-76", normalized.heroId)
        assertEquals("Player#1234", normalized.battleTag)
        assertEquals("COMPETITIVE_ROLE_QUEUE", normalized.mode)
        assertEquals("King's Row", normalized.mapLabel)
        assertEquals(0, normalized.eliminations)
        assertEquals(9, normalized.assists)
        assertEquals(MAX_STAT_VALUE, normalized.damage)
        assertEquals(MatchStatsSources.SCOREBOARD_SCAN, normalized.source)
        assertEquals(100, normalized.confidence)
        assertNull(match(timestamp = 0L).normalizedOrNull())
        assertNull(match(heroId = "  ").normalizedOrNull())
    }

    @Test
    fun aggregateReportsDecidedWinRateRatiosTotalsAndTopHero() {
        val entries = listOf(
            match(400L, "ana", MatchResult.WIN, eliminations = 8, assists = 12, deaths = 4),
            match(300L, "ana", MatchResult.LOSS, eliminations = 4, assists = 8, deaths = 4),
            match(200L, "tracer", MatchResult.DRAW, eliminations = 10, assists = 2, deaths = 2),
            match(100L, "tracer", MatchResult.UNKNOWN, eliminations = 8, assists = 0, deaths = 2),
            // An invalid row is not allowed to distort a profile.
            match(0L, "widowmaker", MatchResult.WIN, eliminations = 100, assists = 100, deaths = 1)
        )

        val aggregate = entries.aggregateMatchStats()

        assertEquals(4, aggregate.matches)
        assertEquals(1, aggregate.wins)
        assertEquals(1, aggregate.losses)
        assertEquals(1, aggregate.draws)
        assertEquals(1, aggregate.unknownResults)
        assertEquals(50.0, aggregate.winRatePercent!!, 0.0001)
        assertEquals(30L, aggregate.eliminations)
        assertEquals(22L, aggregate.assists)
        assertEquals(12L, aggregate.deaths)
        assertEquals(52.0 / 12.0, aggregate.kdaRatio!!, 0.0001)
        assertEquals(30.0 / 12.0, aggregate.eliminationDeathRatio!!, 0.0001)
        // Same play count/wins: the most recently played hero wins the tie.
        assertEquals("ana", aggregate.topHeroId)
    }

    @Test
    fun aggregateUsesNullForUndefinedRates() {
        val aggregate = listOf(
            match(10L, "mercy", MatchResult.DRAW, eliminations = 2, assists = 20, deaths = 0)
        ).aggregateMatchStats()

        assertNull(aggregate.winRatePercent)
        assertNull(aggregate.kdaRatio)
        assertNull(aggregate.eliminationDeathRatio)
        assertEquals("mercy", aggregate.topHeroId)
    }

    @Test
    fun normalizedStorageIsNewestFirstDeduplicatedAndBounded() {
        val repeated = match(5_000L, "ana", MatchResult.WIN)
        val matches = buildList {
            add(repeated)
            add(repeated)
            for (timestamp in 1L..1_010L) add(match(timestamp))
        }

        val normalized = normalizeStoredMatches(matches)

        assertEquals(MAX_STORED_MATCHES, normalized.size)
        assertEquals(5_000L, normalized.first().timestamp)
        assertEquals(1, normalized.count { it == repeated })
        assertTrue(normalized.zipWithNext().all { (first, second) ->
            first.timestamp >= second.timestamp
        })
    }

    private fun match(
        timestamp: Long = 1_000L,
        heroId: String = "kiriko",
        result: MatchResult = MatchResult.UNKNOWN,
        eliminations: Int = 0,
        assists: Int = 0,
        deaths: Int = 0
    ) = MatchStatsEntry(
        timestamp = timestamp,
        heroId = heroId,
        result = result,
        eliminations = eliminations,
        assists = assists,
        deaths = deaths,
        damage = 1_500,
        healing = 2_500,
        mitigation = 500
    )
}
