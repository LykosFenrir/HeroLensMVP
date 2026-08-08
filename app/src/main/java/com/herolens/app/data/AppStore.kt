package com.herolens.app.data

import android.content.Context
import com.herolens.app.core.MapProfile
import com.herolens.app.core.Role
import com.herolens.app.vision.ScoreboardLayout
import org.json.JSONArray
import org.json.JSONObject

enum class RankTier(val label: String) {
    UNRANKED("No rank"),
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold"),
    PLATINUM("Platinum"),
    DIAMOND("Diamond"),
    MASTER("Master"),
    GRANDMASTER("Grandmaster"),
    CHAMPION("Champion")
}

enum class InputPlatform(val label: String) {
    PC("PC"),
    CONSOLE("Console")
}

enum class DisplayType(val label: String) {
    AUTO("Auto"),
    TV("TV / console screen"),
    LAPTOP("Laptop / monitor")
}

enum class GameModeProfile(
    val label: String,
    val description: String,
    val allowsAutomaticImport: Boolean
) {
    AUTO("Auto / any mode", "Tags the exact mode as unknown. Layout and team size stay adaptive, and review always remains open.", false),
    UNRANKED("Unranked", "Tags reviewed samples as Unranked and permits automatic import only for a complete high-confidence lineup.", true),
    COMPETITIVE("Competitive + Hero Bans", "Core Competitive uses simultaneous ranked-choice Hero Ban voting, not Stadium team drafting. Enter up to five final ban results; complete scoreboard scans may auto-import.", true),
    STADIUM("Stadium", "Official Stadium is 5v5. This tag enforces the current Stadium roster and keeps review open because extra overlays remain experimental.", false),
    STADIUM_DRAFT("Stadium Competitive Draft", "Official ranked 5v5 blind-pick draft. Final-lineup scanning is experimental, the current Stadium roster is enforced, and review always stays open.", false),
    ARCADE("Arcade", "Tags Arcade samples. Two-team 3v3–6v6 is experimental; duplicate-hero and free-for-all boards are unsupported.", false),
    CUSTOM("Custom / other", "Tags Custom samples. Two-team 3v3–6v6 is experimental; duplicate-hero and free-for-all boards are unsupported.", false);

    val usesDraftAssistant: Boolean
        get() = this == COMPETITIVE || this == STADIUM_DRAFT

    val usesStadiumRoster: Boolean
        get() = this == STADIUM || this == STADIUM_DRAFT

    val fixedTeamSize: Int?
        get() = if (usesStadiumRoster) 5 else null

    val unavailableHeroLimit: Int
        get() = when (this) {
            COMPETITIVE -> 5
            STADIUM_DRAFT -> 10
            else -> 0
        }

    companion object {
        /**
         * Preferences and history outlive individual enum revisions. Keep persisted
         * values tolerant of whitespace/case changes and map anything unknown to the
         * safe, review-required AUTO profile instead of propagating an invalid name.
         */
        fun fromPersistedValue(value: String?): GameModeProfile {
            val persisted = value?.trim().orEmpty()
            return entries.firstOrNull { profile ->
                profile.name.equals(persisted, ignoreCase = true)
            } ?: AUTO
        }
    }
}

enum class TeamFormat(val label: String, val teamSize: Int?) {
    AUTO("Auto detect", null),
    THREE_V_THREE("Force 3v3", 3),
    FOUR_V_FOUR("Force 4v4", 4),
    FIVE_V_FIVE("Force 5v5", 5),
    SIX_V_SIX("Force 6v6", 6)
}

enum class ScanMode(
    val label: String,
    val description: String,
    val windowSize: Int,
    val minimumVotes: Int,
    val minimumConfidence: Float,
    val intervalMs: Long,
    val burstFrames: Int
) {
    FAST(
        label = "Fast",
        description = "Locks quickly when the phone and scoreboard are already aligned.",
        windowSize = 4,
        minimumVotes = 2,
        minimumConfidence = 0.48f,
        intervalMs = 190L,
        burstFrames = 6
    ),
    BALANCED(
        label = "Balanced",
        description = "Recommended mix of speed and protection against one-frame mistakes.",
        windowSize = 5,
        minimumVotes = 3,
        minimumConfidence = 0.54f,
        intervalMs = 270L,
        burstFrames = 9
    ),
    ACCURATE(
        label = "Accurate",
        description = "Uses more agreeing frames for glare, distant monitors and difficult angles.",
        windowSize = 7,
        minimumVotes = 4,
        minimumConfidence = 0.60f,
        intervalMs = 330L,
        burstFrames = 12
    )
}

data class ScannerSettings(
    val rank: RankTier = RankTier.UNRANKED,
    val inputPlatform: InputPlatform = InputPlatform.PC,
    val displayType: DisplayType = DisplayType.AUTO,
    val gameModeProfile: GameModeProfile = GameModeProfile.AUTO,
    val autoScan: Boolean = false,
    val autoOpenResults: Boolean = false,
    val showDetections: Boolean = true,
    val hapticFeedback: Boolean = true,
    val defaultZoom: Float = 1f,
    val autoZoom: Boolean = true,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val preferredLayout: ScoreboardLayout = ScoreboardLayout.AUTO,
    val teamFormat: TeamFormat = TeamFormat.AUTO,
    val collectTrainingData: Boolean = false,
    val onboardingComplete: Boolean = false
)

data class PlayerState(
    val role: Role = Role.DAMAGE,
    val mapProfile: MapProfile = MapProfile.MIXED,
    val currentHeroId: String? = null,
    val ultimateCharge: Int = 0,
    val heroPool: Map<String, Int> = emptyMap(),
    val allRoles: Boolean = false
)

data class ScanHistoryEntry(
    val timestamp: Long,
    val role: String,
    val currentHeroId: String?,
    val bestHeroId: String,
    val fitScore: Int,
    val scanConfidence: Int,
    val allyIds: List<String>,
    val enemyIds: List<String>,
    val gameMode: String = GameModeProfile.AUTO.name,
    val bannedHeroIds: List<String> = emptyList(),
    val stadiumRosterVersion: String? = null,
    val teamSize: Int? = null
) {
    /** Canonical form used at the persistence boundary. */
    internal fun normalizedForPersistence(): ScanHistoryEntry? {
        if (timestamp <= 0L) return null

        val normalizedBestHeroId = bestHeroId.trim().takeIf(String::isNotEmpty) ?: return null
        val normalizedMode = GameModeProfile.fromPersistedValue(gameMode)
        val normalizedRole = when {
            role.equals(ALL_ROLES_HISTORY_VALUE, ignoreCase = true) -> ALL_ROLES_HISTORY_VALUE
            else -> Role.entries.firstOrNull { it.name.equals(role.trim(), ignoreCase = true) }
                ?.name
                ?: Role.DAMAGE.name
        }

        return copy(
            role = normalizedRole,
            currentHeroId = currentHeroId.normalizedHeroId(),
            bestHeroId = normalizedBestHeroId,
            scanConfidence = scanConfidence.coerceIn(0, 100),
            allyIds = allyIds.normalizedHeroIds(),
            enemyIds = enemyIds.normalizedHeroIds(),
            gameMode = normalizedMode.name,
            bannedHeroIds = bannedHeroIds.normalizedHeroIds(),
            stadiumRosterVersion = stadiumRosterVersion?.trim()?.takeIf(String::isNotEmpty),
            teamSize = normalizedMode.fixedTeamSize ?: teamSize?.takeIf { it in MIN_TEAM_SIZE..MAX_TEAM_SIZE }
        )
    }

    /**
     * Matches two captures of the same effective draft/lineup. Team and ban order
     * is intentionally ignored because detector slot ordering is not identity.
     */
    internal fun isDuplicateOf(
        other: ScanHistoryEntry,
        withinMs: Long = HISTORY_DUPLICATE_WINDOW_MS
    ): Boolean {
        if (withinMs <= 0L) return false
        val first = normalizedForPersistence() ?: return false
        val second = other.normalizedForPersistence() ?: return false
        val elapsed = if (first.timestamp >= second.timestamp) {
            first.timestamp - second.timestamp
        } else {
            second.timestamp - first.timestamp
        }
        if (elapsed >= withinMs) return false

        return first.role == second.role &&
            first.currentHeroId == second.currentHeroId &&
            first.bestHeroId == second.bestHeroId &&
            first.gameMode == second.gameMode &&
            first.teamSize == second.teamSize &&
            first.allyIds.canonicalHeroSet() == second.allyIds.canonicalHeroSet() &&
            first.enemyIds.canonicalHeroSet() == second.enemyIds.canonicalHeroSet() &&
            first.bannedHeroIds.canonicalHeroSet() == second.bannedHeroIds.canonicalHeroSet()
    }
}

internal fun deduplicateScanHistory(entries: List<ScanHistoryEntry>): List<ScanHistoryEntry> {
    val result = mutableListOf<ScanHistoryEntry>()
    entries.forEach { rawEntry ->
        val entry = rawEntry.normalizedForPersistence() ?: return@forEach
        if (result.none { existing -> entry.isDuplicateOf(existing) }) result += entry
    }
    return result
}

private fun String?.normalizedHeroId(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun List<String>.normalizedHeroIds(): List<String> = mapNotNull(String::normalizedHeroId).distinct()

private fun List<String>.canonicalHeroSet(): List<String> = distinct().sorted()

private const val ALL_ROLES_HISTORY_VALUE = "ALL_ROLES"
internal const val HISTORY_DUPLICATE_WINDOW_MS = 30_000L
private const val MIN_TEAM_SIZE = 3
private const val MAX_TEAM_SIZE = 6

class AppStore(context: Context) {
    private val preferences = context.getSharedPreferences("herolens_v5", Context.MODE_PRIVATE)

    fun loadSettings(): ScannerSettings {
        val schema = preferences.getInt(KEY_SETTINGS_SCHEMA, 0)
        return ScannerSettings(
        rank = enumValue(KEY_RANK, RankTier.UNRANKED),
        inputPlatform = enumValue(KEY_INPUT_PLATFORM, InputPlatform.PC),
        displayType = enumValue(KEY_DISPLAY_TYPE, DisplayType.AUTO),
        gameModeProfile = GameModeProfile.fromPersistedValue(preferences.getString(KEY_GAME_MODE, null)),
        autoScan = if (schema >= 7) preferences.getBoolean(KEY_AUTO_SCAN, false) else false,
        autoOpenResults = if (schema >= 7) preferences.getBoolean(KEY_AUTO_OPEN_RESULTS, false) else false,
        showDetections = preferences.getBoolean(KEY_SHOW_DETECTIONS, true),
        hapticFeedback = preferences.getBoolean(KEY_HAPTICS, true),
        defaultZoom = preferences.getFloat(KEY_ZOOM, 1f).coerceIn(1f, 5f),
        autoZoom = preferences.getBoolean(KEY_AUTO_ZOOM, true),
        scanMode = enumValue(KEY_SCAN_MODE, ScanMode.BALANCED),
        preferredLayout = enumValue(KEY_LAYOUT, ScoreboardLayout.AUTO),
        teamFormat = enumValue(KEY_TEAM_FORMAT, TeamFormat.AUTO),
        collectTrainingData = preferences.getBoolean(KEY_COLLECT_TRAINING_DATA, false),
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING, false)
    )
    }

    fun saveSettings(settings: ScannerSettings) {
        preferences.edit()
            .putInt(KEY_SETTINGS_SCHEMA, 10)
            .putString(KEY_RANK, settings.rank.name)
            .putString(KEY_INPUT_PLATFORM, settings.inputPlatform.name)
            .putString(KEY_DISPLAY_TYPE, settings.displayType.name)
            .putString(KEY_GAME_MODE, settings.gameModeProfile.name)
            .putBoolean(KEY_AUTO_SCAN, settings.autoScan)
            .putBoolean(KEY_AUTO_OPEN_RESULTS, settings.autoOpenResults)
            .putBoolean(KEY_SHOW_DETECTIONS, settings.showDetections)
            .putBoolean(KEY_HAPTICS, settings.hapticFeedback)
            .putFloat(KEY_ZOOM, settings.defaultZoom)
            .putBoolean(KEY_AUTO_ZOOM, settings.autoZoom)
            .putString(KEY_SCAN_MODE, settings.scanMode.name)
            .putString(KEY_LAYOUT, settings.preferredLayout.name)
            .putString(KEY_TEAM_FORMAT, settings.teamFormat.name)
            .putBoolean(KEY_COLLECT_TRAINING_DATA, settings.collectTrainingData)
            .putBoolean(KEY_ONBOARDING, settings.onboardingComplete)
            .apply()
    }

    fun loadPlayerState(): PlayerState {
        val pool = mutableMapOf<String, Int>()
        val rawPool = preferences.getString(KEY_HERO_POOL, null)
        if (rawPool != null) {
            runCatching {
                val json = JSONObject(rawPool)
                json.keys().forEach { heroId ->
                    val level = json.optInt(heroId, 0).coerceIn(0, 4)
                    if (level > 0) pool[heroId] = level
                }
            }
        }
        return PlayerState(
            role = enumValue(KEY_ROLE, Role.DAMAGE),
            mapProfile = enumValue(KEY_MAP_PROFILE, MapProfile.MIXED),
            currentHeroId = preferences.getString(KEY_CURRENT_HERO, null)?.takeIf(String::isNotBlank),
            ultimateCharge = preferences.getInt(KEY_ULTIMATE, 0).coerceIn(0, 100),
            heroPool = pool,
            allRoles = preferences.getBoolean(KEY_ALL_ROLES, false)
        )
    }

    fun savePlayerState(state: PlayerState) {
        val pool = JSONObject()
        state.heroPool.forEach { (heroId, level) ->
            if (level in 1..4) pool.put(heroId, level)
        }
        preferences.edit()
            .putString(KEY_ROLE, state.role.name)
            .putString(KEY_MAP_PROFILE, state.mapProfile.name)
            .putString(KEY_CURRENT_HERO, state.currentHeroId.orEmpty())
            .putInt(KEY_ULTIMATE, state.ultimateCharge.coerceIn(0, 100))
            .putString(KEY_HERO_POOL, pool.toString())
            .putBoolean(KEY_ALL_ROLES, state.allRoles)
            .apply()
    }

    fun loadHistory(): List<ScanHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val decoded = buildList {
                for (index in 0 until array.length()) {
                    // A damaged row must not make every otherwise valid history item disappear.
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        ScanHistoryEntry(
                            timestamp = item.optLong("timestamp"),
                            role = item.optionalString("role") ?: Role.DAMAGE.name,
                            currentHeroId = item.optionalString("currentHeroId"),
                            bestHeroId = item.optionalString("bestHeroId").orEmpty(),
                            fitScore = item.optInt("fitScore"),
                            scanConfidence = item.optInt("scanConfidence"),
                            allyIds = item.optJSONArray("allies").toStringList(),
                            enemyIds = item.optJSONArray("enemies").toStringList(),
                            gameMode = item.optionalString("gameMode") ?: GameModeProfile.AUTO.name,
                            bannedHeroIds = (
                                item.optJSONArray("bannedHeroes")
                                    ?: item.optJSONArray("bannedHeroIds")
                                ).toStringList(),
                            stadiumRosterVersion = item.optionalString("stadiumRosterVersion"),
                            teamSize = item.optInt("teamSize", 0).takeIf { it in 3..6 }
                        )
                    )
                }
            }
            deduplicateScanHistory(decoded).take(MAX_HISTORY)
        }.getOrDefault(emptyList())
    }

    fun saveHistory(entries: List<ScanHistoryEntry>) {
        val array = JSONArray()
        deduplicateScanHistory(entries).take(MAX_HISTORY).forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("role", entry.role)
                    .put("currentHeroId", entry.currentHeroId ?: "")
                    .put("bestHeroId", entry.bestHeroId)
                    .put("fitScore", entry.fitScore)
                    .put("scanConfidence", entry.scanConfidence)
                    .put("allies", JSONArray(entry.allyIds))
                    .put("enemies", JSONArray(entry.enemyIds))
                    .put("gameMode", entry.gameMode)
                    .put("bannedHeroes", JSONArray(entry.bannedHeroIds))
                    .put("stadiumRosterVersion", entry.stadiumRosterVersion ?: "")
                    // JSON null preserves "unknown" while remaining readable by older
                    // builds whose optInt(..., 0) path already treats it as unset.
                    .put("teamSize", entry.teamSize ?: JSONObject.NULL)
            )
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T = runCatching {
        enumValueOf<T>(preferences.getString(key, fallback.name).orEmpty())
    }.getOrDefault(fallback)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                (opt(index) as? String)?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinct()
    }

    private fun JSONObject.optionalString(key: String): String? =
        (opt(key) as? String)?.trim()?.takeIf(String::isNotEmpty)

    private companion object {
        const val KEY_SETTINGS_SCHEMA = "settings_schema"
        const val KEY_RANK = "rank"
        const val KEY_INPUT_PLATFORM = "input_platform"
        const val KEY_DISPLAY_TYPE = "display_type"
        const val KEY_GAME_MODE = "game_mode"
        const val KEY_AUTO_SCAN = "auto_scan"
        const val KEY_AUTO_OPEN_RESULTS = "auto_open_results"
        const val KEY_SHOW_DETECTIONS = "show_detections"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ZOOM = "zoom"
        const val KEY_AUTO_ZOOM = "auto_zoom"
        const val KEY_SCAN_MODE = "scan_mode"
        const val KEY_LAYOUT = "layout"
        const val KEY_TEAM_FORMAT = "team_format"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_COLLECT_TRAINING_DATA = "collect_training_data"
        const val KEY_ROLE = "role"
        const val KEY_MAP_PROFILE = "map_profile"
        const val KEY_CURRENT_HERO = "current_hero"
        const val KEY_ULTIMATE = "ultimate"
        const val KEY_HERO_POOL = "hero_pool"
        const val KEY_ALL_ROLES = "all_roles"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 50
    }
}
