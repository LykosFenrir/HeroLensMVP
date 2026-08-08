package com.herolens.app.postmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostMatchScoreboardTextParserTest {
    @Test
    fun parsesStandardSeparatedRowWithThousandsCommas() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            textBlocks = listOf(
                "ELIMINATIONS | ASSISTS | DEATHS | DAMAGE | HEALING | MITIGATION",
                "YOU PlayerOne | 31 | 14 | 6 | 12,345 | 8,765 | 2,100"
            ),
            expectedTeamSize = 5
        )

        val draft = requireNotNull(result.draft)
        assertEquals("PlayerOne", draft.playerName)
        assertEquals(31, draft.eliminations)
        assertEquals(14, draft.assists)
        assertEquals(6, draft.deaths)
        assertEquals(12_345, draft.damage)
        assertEquals(8_765, draft.healing)
        assertEquals(2_100, draft.mitigation)
        assertTrue(draft.isComplete)
        assertEquals(5, result.detectedTeamSize)
        assertTrue(PostMatchParseWarning.POSITIONAL_COLUMNS_ASSUMED in result.warnings)
    }

    @Test
    fun repairsLetterOOnlyInsideNumericTokens() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            textBlocks = listOf("PLAYERONE: 2O / 1O / O / 1O,5OO / 8,OOO / 3,O5O"),
            playerAliases = listOf("PlayerOne#1847"),
            expectedTeamSize = 6
        )

        val draft = requireNotNull(result.draft)
        assertEquals("PlayerOne#1847", draft.playerName)
        assertEquals(20, draft.eliminations)
        assertEquals(10, draft.assists)
        assertEquals(0, draft.deaths)
        assertEquals(10_500, draft.damage)
        assertEquals(8_000, draft.healing)
        assertEquals(3_050, draft.mitigation)
        assertTrue(PostMatchParseWarning.OCR_ZERO_CORRECTED in result.warnings)
    }

    @Test
    fun ignoresBattleTagDigitsAndCombinesPartialLabelsWithStandardColumns() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            textBlocks = listOf("YOU PlayerOne#1847 | 2O | 9 | 3 | DMG: 11,500 | 4,OOO | 750")
        )

        val draft = requireNotNull(result.draft)
        assertEquals("PlayerOne#1847", draft.playerName)
        assertEquals(20, draft.eliminations)
        assertEquals(9, draft.assists)
        assertEquals(3, draft.deaths)
        assertEquals(11_500, draft.damage)
        assertEquals(4_000, draft.healing)
        assertEquals(750, draft.mitigation)
        assertTrue(draft.isComplete)
    }

    @Test
    fun parsesMultilineLabelsInAnyOrder() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            """
                YOU
                PlayerOne
                HEALING: 9,000
                DAMAGE = 4,500
                ELIMINATI0NS 12
                DEATHS / 3
                MITIGATED | 1,500
                ASSISTS: 7
            """.trimIndent()
        )

        val draft = requireNotNull(result.draft)
        assertEquals("PlayerOne", draft.playerName)
        assertEquals(12, draft.eliminations)
        assertEquals(7, draft.assists)
        assertEquals(3, draft.deaths)
        assertEquals(4_500, draft.damage)
        assertEquals(9_000, draft.healing)
        assertEquals(1_500, draft.mitigation)
        assertFalse(PostMatchParseWarning.POSITIONAL_COLUMNS_ASSUMED in result.warnings)
    }

    @Test
    fun selectsNamedUserFromCompleteFiveVersusFiveBoard() {
        val rows = fullBoard(teamSize = 5, userRowIndex = 4, alias = "PlayerOne")
        val result = PostMatchScoreboardTextParser.parseUserRow(rows, playerAliases = listOf("PlayerOne"))

        val draft = requireNotNull(result.draft)
        assertEquals(44, draft.eliminations)
        assertEquals(14, draft.assists)
        assertEquals(4, draft.deaths)
        assertEquals(10, result.candidateRowCount)
        assertEquals(5, result.detectedTeamSize)
    }

    @Test
    fun selectsNamedUserFromCompleteSixVersusSixBoard() {
        val rows = fullBoard(teamSize = 6, userRowIndex = 11, alias = "PlayerOne")
        val result = PostMatchScoreboardTextParser.parseUserRow(rows, playerAliases = listOf("PlayerOne"))

        val draft = requireNotNull(result.draft)
        assertEquals(51, draft.eliminations)
        assertEquals(21, draft.assists)
        assertEquals(11, draft.deaths)
        assertEquals(12, result.candidateRowCount)
        assertEquals(6, result.detectedTeamSize)
    }

    @Test
    fun doesNotGuessBetweenMultipleUnmarkedRows() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            listOf(
                "PLAYER ONE | 10 | 4 | 2 | 5,000 | 500 | 0",
                "PLAYER TWO | 11 | 5 | 3 | 6,000 | 600 | 100"
            )
        )

        assertNull(result.draft)
        assertTrue(PostMatchParseWarning.AMBIGUOUS_PLAYER_ROW in result.warnings)
        assertFalse(PostMatchParseWarning.PLAYER_ROW_NOT_FOUND in result.warnings)
    }

    @Test
    fun returnsPartialEditableDraftForUnreadableCells() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            "YOU PlayerOne ELIMS: 10 ASSISTS: 4 DEATHS: 2 DAMAGE: 5,000"
        )

        val draft = requireNotNull(result.draft)
        assertEquals(10, draft.eliminations)
        assertEquals(4, draft.assists)
        assertEquals(2, draft.deaths)
        assertEquals(5_000, draft.damage)
        assertNull(draft.healing)
        assertNull(draft.mitigation)
        assertFalse(draft.isComplete)
        assertTrue(PostMatchParseWarning.MISSING_FIELDS in result.warnings)
    }

    @Test
    fun combinesVerticalOcrBlockAfterYouMarker() {
        val result = PostMatchScoreboardTextParser.parseUserRow(
            listOf("YOU\nPlayerOne\n2O\n8\n4\n12,345\n6,789\n1,234")
        )

        val draft = requireNotNull(result.draft)
        assertEquals("PlayerOne", draft.playerName)
        assertEquals(20, draft.eliminations)
        assertEquals(8, draft.assists)
        assertEquals(4, draft.deaths)
        assertEquals(12_345, draft.damage)
        assertEquals(6_789, draft.healing)
        assertEquals(1_234, draft.mitigation)
    }

    private fun fullBoard(teamSize: Int, userRowIndex: Int, alias: String): List<String> = buildList {
        add(if (teamSize == 5) "5v5 SCOREBOARD" else "6v6 SCOREBOARD")
        add("YOUR TEAM")
        repeat(teamSize * 2) { index ->
            if (index == teamSize) add("ENEMY TEAM")
            val name = if (index == userRowIndex) alias else "PLAYER_${index + 1}"
            add(
                "$name | ${40 + index} | ${10 + index} | $index | " +
                    "${10_000 + index * 100} | ${5_000 + index * 100} | ${1_000 + index * 100}"
            )
        }
    }
}
