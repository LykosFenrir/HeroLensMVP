package com.herolens.app.data

import java.util.Locale

/** A reviewed match outcome. UNKNOWN keeps incomplete imports honest. */
enum class MatchResult {
    WIN,
    LOSS,
    DRAW,
    UNKNOWN;

    companion object {
        fun fromPersistedValue(value: Any?): MatchResult {
            if (value is MatchResult) return value
            return when (value?.toString()?.trim()?.uppercase(Locale.ROOT)) {
                "WIN", "WON", "VICTORY", "W" -> WIN
                "LOSS", "LOST", "DEFEAT", "L" -> LOSS
                "DRAW", "TIE", "TIED", "D" -> DRAW
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Private, reviewed per-match statistics. A BattleTag is an account label, not a
 * credential; passwords, OAuth tokens and session cookies deliberately have no
 * place in this model or its persistence schema.
 */
data class MatchStatsEntry(
    val timestamp: Long,
    val battleTag: String? = null,
    val mode: String = UNKNOWN_MODE,
    val mapLabel: String = UNKNOWN_MAP_LABEL,
    val heroId: String,
    val result: MatchResult = MatchResult.UNKNOWN,
    val eliminations: Int = 0,
    val assists: Int = 0,
    val deaths: Int = 0,
    val damage: Int = 0,
    val healing: Int = 0,
    val mitigation: Int = 0,
    val source: String = MatchStatsSources.MANUAL,
    val confidence: Int = 100
) {
    /** Canonical form used by both aggregate calculations and persistence. */
    fun normalizedOrNull(): MatchStatsEntry? {
        if (timestamp <= 0L) return null
        val normalizedHeroId = heroId.normalizedHeroId() ?: return null

        return copy(
            battleTag = battleTag.normalizedOptionalLabel(MAX_BATTLE_TAG_LENGTH),
            mode = mode.normalizedCode(UNKNOWN_MODE, MAX_MODE_LENGTH),
            mapLabel = mapLabel.normalizedLabel(UNKNOWN_MAP_LABEL, MAX_MAP_LENGTH),
            heroId = normalizedHeroId,
            eliminations = eliminations.normalizedStat(),
            assists = assists.normalizedStat(),
            deaths = deaths.normalizedStat(),
            damage = damage.normalizedStat(),
            healing = healing.normalizedStat(),
            mitigation = mitigation.normalizedStat(),
            source = source.normalizedCode(MatchStatsSources.UNKNOWN, MAX_SOURCE_LENGTH),
            confidence = confidence.coerceIn(0, 100)
        )
    }
}

/** Stable source labels; unknown future labels remain supported as strings. */
object MatchStatsSources {
    const val MANUAL = "MANUAL"
    const val SCOREBOARD_SCAN = "SCOREBOARD_SCAN"
    const val ACCOUNT_IMPORT = "ACCOUNT_IMPORT"
    const val LEGACY_IMPORT = "LEGACY_IMPORT"
    const val UNKNOWN = "UNKNOWN"
}

/** A single, UI-ready aggregate of a private match list. */
data class MatchStatsAggregate(
    val matches: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val unknownResults: Int,
    /** Wins divided by decided matches (wins + losses), expressed as 0..100. */
    val winRatePercent: Double?,
    val eliminations: Long,
    val assists: Long,
    val deaths: Long,
    val damage: Long,
    val healing: Long,
    val mitigation: Long,
    /** (Eliminations + assists) / deaths; null when there are no deaths. */
    val kdaRatio: Double?,
    /** Eliminations / deaths; null when there are no deaths. */
    val eliminationDeathRatio: Double?,
    val topHeroId: String?
)

fun Iterable<MatchStatsEntry>.aggregateMatchStats(): MatchStatsAggregate {
    val entries = mapNotNull(MatchStatsEntry::normalizedOrNull)
    val wins = entries.count { it.result == MatchResult.WIN }
    val losses = entries.count { it.result == MatchResult.LOSS }
    val draws = entries.count { it.result == MatchResult.DRAW }
    val decidedMatches = wins + losses
    val eliminations = entries.sumOf { it.eliminations.toLong() }
    val assists = entries.sumOf { it.assists.toLong() }
    val deaths = entries.sumOf { it.deaths.toLong() }

    val topHeroId = entries
        .groupBy(MatchStatsEntry::heroId)
        .map { (heroId, matches) ->
            HeroUsage(
                heroId = heroId,
                matches = matches.size,
                wins = matches.count { it.result == MatchResult.WIN },
                latestTimestamp = matches.maxOf(MatchStatsEntry::timestamp)
            )
        }
        .sortedWith(
            compareByDescending<HeroUsage> { it.matches }
                .thenByDescending { it.wins }
                .thenByDescending { it.latestTimestamp }
                .thenBy { it.heroId }
        )
        .firstOrNull()
        ?.heroId

    return MatchStatsAggregate(
        matches = entries.size,
        wins = wins,
        losses = losses,
        draws = draws,
        unknownResults = entries.size - wins - losses - draws,
        winRatePercent = decidedMatches.takeIf { it > 0 }
            ?.let { wins.toDouble() * 100.0 / it },
        eliminations = eliminations,
        assists = assists,
        deaths = deaths,
        damage = entries.sumOf { it.damage.toLong() },
        healing = entries.sumOf { it.healing.toLong() },
        mitigation = entries.sumOf { it.mitigation.toLong() },
        kdaRatio = deaths.takeIf { it > 0L }
            ?.let { (eliminations + assists).toDouble() / it },
        eliminationDeathRatio = deaths.takeIf { it > 0L }
            ?.let { eliminations.toDouble() / it },
        topHeroId = topHeroId
    )
}

private data class HeroUsage(
    val heroId: String,
    val matches: Int,
    val wins: Int,
    val latestTimestamp: Long
)

private fun String.normalizedHeroId(): String? = trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\s_]+"), "-")
    .replace(Regex("-+"), "-")
    .trim('-')
    .take(MAX_HERO_ID_LENGTH)
    .takeIf(String::isNotEmpty)

private fun String?.normalizedOptionalLabel(maxLength: Int): String? = this
    ?.trim()
    ?.replace(Regex("[\\r\\n\\t]+"), " ")
    ?.replace(Regex(" {2,}"), " ")
    ?.take(maxLength)
    ?.takeIf(String::isNotEmpty)

private fun String.normalizedLabel(fallback: String, maxLength: Int): String =
    normalizedOptionalLabel(maxLength) ?: fallback

private fun String.normalizedCode(fallback: String, maxLength: Int): String {
    val canonical = trim()
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9]+"), "_")
        .trim('_')
        .take(maxLength)
    return canonical.ifEmpty { fallback }
}

private fun Int.normalizedStat(): Int = coerceIn(0, MAX_STAT_VALUE)

const val UNKNOWN_MODE = "UNKNOWN"
const val UNKNOWN_MAP_LABEL = "Unknown map"
internal const val MAX_STORED_MATCHES = 1_000
internal const val MAX_STAT_VALUE = 100_000_000
private const val MAX_BATTLE_TAG_LENGTH = 96
private const val MAX_MODE_LENGTH = 64
private const val MAX_MAP_LENGTH = 96
private const val MAX_HERO_ID_LENGTH = 80
private const val MAX_SOURCE_LENGTH = 64
