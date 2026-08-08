package com.herolens.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchStatsStoreTest {
    @Test
    fun legacyRowAliasesAndNumericFormatsDecode() {
        val decoded = decodeMatchStatsFields(
            mapOf(
                "playedAt" to "1,234",
                "battle_tag" to "Player#9876",
                "gameMode" to "competitive",
                "mapName" to "Circuit Royal",
                "hero" to " Soldier 76 ",
                "outcome" to "won",
                "elims" to "28",
                "assists" to -4,
                "deaths" to 7.9,
                "damageDone" to Int.MAX_VALUE.toLong(),
                "healingDone" to "4,500",
                "damageMitigated" to 900,
                "ocrConfidence" to 0.84
            )
        )!!

        assertEquals(1_234L, decoded.timestamp)
        assertEquals("Player#9876", decoded.battleTag)
        assertEquals("COMPETITIVE", decoded.mode)
        assertEquals("Circuit Royal", decoded.mapLabel)
        assertEquals("soldier-76", decoded.heroId)
        assertEquals(MatchResult.WIN, decoded.result)
        assertEquals(28, decoded.eliminations)
        assertEquals(0, decoded.assists)
        assertEquals(7, decoded.deaths)
        assertEquals(MAX_STAT_VALUE, decoded.damage)
        assertEquals(4_500, decoded.healing)
        assertEquals(900, decoded.mitigation)
        assertEquals(MatchStatsSources.LEGACY_IMPORT, decoded.source)
        assertEquals(84, decoded.confidence)
    }

    @Test
    fun damagedRowsAreRejectedIndividuallyAndOptionalValuesFallBack() {
        assertNull(decodeMatchStatsFields(mapOf("timestamp" to 10L, "heroId" to "")))
        assertNull(decodeMatchStatsFields(mapOf("timestamp" to "bad", "heroId" to "ana")))

        val minimal = decodeMatchStatsFields(
            mapOf(
                "timestamp" to 99L,
                "hero_id" to "ana",
                "confidence" to "92%",
                "result" to listOf("not", "a", "string")
            )
        )!!

        assertEquals(UNKNOWN_MODE, minimal.mode)
        assertEquals(UNKNOWN_MAP_LABEL, minimal.mapLabel)
        assertEquals(MatchResult.UNKNOWN, minimal.result)
        assertEquals(92, minimal.confidence)
        assertEquals(MatchStatsSources.LEGACY_IMPORT, minimal.source)
    }

    @Test
    fun sourceAndConfidenceAreForwardCompatibleButNormalized() {
        val decoded = decodeMatchStatsFields(
            mapOf(
                "timestamp" to 20L,
                "heroId" to "mei",
                "source" to " future api v2 ",
                "confidence" to 155
            )
        )!!

        assertEquals("FUTURE_API_V2", decoded.source)
        assertEquals(100, decoded.confidence)

        val fractionalString = decodeMatchStatsFields(
            mapOf(
                "timestamp" to 21L,
                "heroId" to "mei",
                "confidence" to "0.91"
            )
        )!!
        assertEquals(91, fractionalString.confidence)
    }
}
