package com.herolens.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanHistoryEntryTest {
    @Test
    fun legacyEntryDefaultsRemainValid() {
        val entry = historyEntry().copy(gameMode = "", bannedHeroIds = emptyList(), teamSize = null)

        val normalized = entry.normalizedForPersistence()!!

        assertEquals(GameModeProfile.AUTO.name, normalized.gameMode)
        assertEquals(emptyList<String>(), normalized.bannedHeroIds)
        assertNull(normalized.teamSize)
    }

    @Test
    fun normalizationPreservesDraftFieldsAndEnforcesFixedTeamSize() {
        val normalized = historyEntry(
            mode = " stadium_draft ",
            bans = listOf("ana", "", "ana", "cassidy "),
            teamSize = 6
        ).copy(
            role = "support",
            currentHeroId = " kiriko ",
            allyIds = listOf("lucio", "", "lucio", "mei "),
            enemyIds = listOf("reinhardt", " tracer", "tracer"),
            scanConfidence = 140
        ).normalizedForPersistence()!!

        assertEquals(GameModeProfile.STADIUM_DRAFT.name, normalized.gameMode)
        assertEquals(5, normalized.teamSize)
        assertEquals(listOf("ana", "cassidy"), normalized.bannedHeroIds)
        assertEquals(listOf("lucio", "mei"), normalized.allyIds)
        assertEquals(listOf("reinhardt", "tracer"), normalized.enemyIds)
        assertEquals("kiriko", normalized.currentHeroId)
        assertEquals("SUPPORT", normalized.role)
        assertEquals(100, normalized.scanConfidence)
    }

    @Test
    fun duplicateIdentityIgnoresDetectorOrdering() {
        val first = historyEntry(
            timestamp = 100_000L,
            bans = listOf("ana", "doomfist")
        )
        val reordered = first.copy(
            timestamp = 129_999L,
            allyIds = first.allyIds.reversed(),
            enemyIds = first.enemyIds.reversed(),
            bannedHeroIds = first.bannedHeroIds.reversed()
        )

        assertTrue(first.isDuplicateOf(reordered))
        assertEquals(listOf(first.normalizedForPersistence()), deduplicateScanHistory(listOf(first, reordered)))
    }

    @Test
    fun duplicateIdentityDistinguishesEveryPersistedMatchContextField() {
        val base = historyEntry(timestamp = 100_000L, bans = listOf("ana"), teamSize = 5)

        assertFalse(base.isDuplicateOf(base.copy(gameMode = GameModeProfile.UNRANKED.name)))
        assertFalse(base.isDuplicateOf(base.copy(bannedHeroIds = listOf("cassidy"))))
        assertFalse(base.isDuplicateOf(base.copy(currentHeroId = "mercy")))
        assertFalse(base.isDuplicateOf(base.copy(allyIds = listOf("mei"))))
        assertFalse(base.isDuplicateOf(base.copy(enemyIds = listOf("tracer"))))
        assertFalse(base.isDuplicateOf(base.copy(teamSize = 6)))
        assertFalse(base.isDuplicateOf(base.copy(role = "TANK")))
        assertFalse(base.isDuplicateOf(base.copy(bestHeroId = "ashe")))
        assertFalse(base.isDuplicateOf(base.copy(timestamp = 130_000L)))
    }

    @Test
    fun malformedEntriesAreExcludedFromPersistence() {
        val valid = historyEntry()
        val noTimestamp = valid.copy(timestamp = 0L)
        val noRecommendation = valid.copy(bestHeroId = " ")

        assertEquals(listOf(valid.normalizedForPersistence()), deduplicateScanHistory(listOf(noTimestamp, valid, noRecommendation)))
    }

    private fun historyEntry(
        timestamp: Long = 100_000L,
        mode: String = GameModeProfile.COMPETITIVE.name,
        bans: List<String> = emptyList(),
        teamSize: Int? = 5
    ) = ScanHistoryEntry(
        timestamp = timestamp,
        role = "DAMAGE",
        currentHeroId = "soldier-76",
        bestHeroId = "sojourn",
        fitScore = 84,
        scanConfidence = 91,
        allyIds = listOf("ana", "winston"),
        enemyIds = listOf("pharah", "mercy"),
        gameMode = mode,
        bannedHeroIds = bans,
        stadiumRosterVersion = null,
        teamSize = teamSize
    )
}
