package com.herolens.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModeProfileTest {
    @Test
    fun automaticImportIsLimitedToExplicitStandardModes() {
        assertTrue(GameModeProfile.UNRANKED.allowsAutomaticImport)
        assertTrue(GameModeProfile.COMPETITIVE.allowsAutomaticImport)
        assertFalse(GameModeProfile.AUTO.allowsAutomaticImport)
        assertFalse(GameModeProfile.STADIUM.allowsAutomaticImport)
        assertFalse(GameModeProfile.STADIUM_DRAFT.allowsAutomaticImport)
        assertFalse(GameModeProfile.ARCADE.allowsAutomaticImport)
        assertFalse(GameModeProfile.CUSTOM.allowsAutomaticImport)
    }

    @Test
    fun draftAndStadiumPoliciesAreExplicit() {
        assertTrue(GameModeProfile.COMPETITIVE.usesDraftAssistant)
        assertTrue(GameModeProfile.STADIUM_DRAFT.usesDraftAssistant)
        assertFalse(GameModeProfile.STADIUM.usesDraftAssistant)
        assertTrue(GameModeProfile.STADIUM.usesStadiumRoster)
        assertTrue(GameModeProfile.STADIUM_DRAFT.usesStadiumRoster)
        assertEquals(5, GameModeProfile.STADIUM.fixedTeamSize)
        assertEquals(5, GameModeProfile.STADIUM_DRAFT.fixedTeamSize)
        assertEquals(5, GameModeProfile.COMPETITIVE.unavailableHeroLimit)
        assertEquals(10, GameModeProfile.STADIUM_DRAFT.unavailableHeroLimit)
        assertEquals(0, GameModeProfile.STADIUM.unavailableHeroLimit)
    }

    @Test
    fun persistedModeNamesAreBackwardCompatibleAndSafe() {
        assertEquals(GameModeProfile.COMPETITIVE, GameModeProfile.fromPersistedValue("COMPETITIVE"))
        assertEquals(GameModeProfile.STADIUM_DRAFT, GameModeProfile.fromPersistedValue(" stadium_draft "))
        assertEquals(GameModeProfile.AUTO, GameModeProfile.fromPersistedValue(null))
        assertEquals(GameModeProfile.AUTO, GameModeProfile.fromPersistedValue("future_unknown_mode"))
    }
}
