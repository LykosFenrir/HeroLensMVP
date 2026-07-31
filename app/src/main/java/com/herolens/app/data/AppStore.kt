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

enum class ScanMode(
    val label: String,
    val description: String,
    val windowSize: Int,
    val minimumVotes: Int,
    val minimumConfidence: Float,
    val intervalMs: Long
) {
    FAST(
        label = "Fast",
        description = "Locks quickly when the phone and scoreboard are already aligned.",
        windowSize = 4,
        minimumVotes = 2,
        minimumConfidence = 0.47f,
        intervalMs = 190L
    ),
    BALANCED(
        label = "Balanced",
        description = "Recommended mix of speed and protection against one-frame mistakes.",
        windowSize = 5,
        minimumVotes = 3,
        minimumConfidence = 0.52f,
        intervalMs = 270L
    ),
    ACCURATE(
        label = "Accurate",
        description = "Uses more agreeing frames for glare, distant monitors and difficult angles.",
        windowSize = 7,
        minimumVotes = 4,
        minimumConfidence = 0.57f,
        intervalMs = 330L
    )
}

data class ScannerSettings(
    val rank: RankTier = RankTier.UNRANKED,
    val inputPlatform: InputPlatform = InputPlatform.PC,
    val autoScan: Boolean = true,
    val autoOpenResults: Boolean = true,
    val showDetections: Boolean = true,
    val hapticFeedback: Boolean = true,
    val defaultZoom: Float = 1f,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val preferredLayout: ScoreboardLayout = ScoreboardLayout.AUTO,
    val onboardingComplete: Boolean = false
)

data class PlayerState(
    val role: Role = Role.DAMAGE,
    val mapProfile: MapProfile = MapProfile.MIXED,
    val currentHeroId: String? = null,
    val ultimateCharge: Int = 0,
    val heroPool: Map<String, Int> = emptyMap()
)

data class ScanHistoryEntry(
    val timestamp: Long,
    val role: String,
    val currentHeroId: String?,
    val bestHeroId: String,
    val fitScore: Int,
    val scanConfidence: Int,
    val allyIds: List<String>,
    val enemyIds: List<String>
)

class AppStore(context: Context) {
    private val preferences = context.getSharedPreferences("herolens_v5", Context.MODE_PRIVATE)

    fun loadSettings(): ScannerSettings = ScannerSettings(
        rank = enumValue(KEY_RANK, RankTier.UNRANKED),
        inputPlatform = enumValue(KEY_INPUT_PLATFORM, InputPlatform.PC),
        autoScan = preferences.getBoolean(KEY_AUTO_SCAN, true),
        autoOpenResults = preferences.getBoolean(KEY_AUTO_OPEN_RESULTS, true),
        showDetections = preferences.getBoolean(KEY_SHOW_DETECTIONS, true),
        hapticFeedback = preferences.getBoolean(KEY_HAPTICS, true),
        defaultZoom = preferences.getFloat(KEY_ZOOM, 1f).coerceIn(1f, 5f),
        scanMode = enumValue(KEY_SCAN_MODE, ScanMode.BALANCED),
        preferredLayout = enumValue(KEY_LAYOUT, ScoreboardLayout.AUTO),
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING, false)
    )

    fun saveSettings(settings: ScannerSettings) {
        preferences.edit()
            .putString(KEY_RANK, settings.rank.name)
            .putString(KEY_INPUT_PLATFORM, settings.inputPlatform.name)
            .putBoolean(KEY_AUTO_SCAN, settings.autoScan)
            .putBoolean(KEY_AUTO_OPEN_RESULTS, settings.autoOpenResults)
            .putBoolean(KEY_SHOW_DETECTIONS, settings.showDetections)
            .putBoolean(KEY_HAPTICS, settings.hapticFeedback)
            .putFloat(KEY_ZOOM, settings.defaultZoom)
            .putString(KEY_SCAN_MODE, settings.scanMode.name)
            .putString(KEY_LAYOUT, settings.preferredLayout.name)
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
            heroPool = pool
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
            .apply()
    }

    fun loadHistory(): List<ScanHistoryEntry> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ScanHistoryEntry(
                            timestamp = item.optLong("timestamp"),
                            role = item.optString("role"),
                            currentHeroId = item.optString("currentHeroId").takeIf(String::isNotBlank),
                            bestHeroId = item.optString("bestHeroId"),
                            fitScore = item.optInt("fitScore"),
                            scanConfidence = item.optInt("scanConfidence"),
                            allyIds = item.optJSONArray("allies").toStringList(),
                            enemyIds = item.optJSONArray("enemies").toStringList()
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveHistory(entries: List<ScanHistoryEntry>) {
        val array = JSONArray()
        entries.take(MAX_HISTORY).forEach { entry ->
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
                optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private companion object {
        const val KEY_RANK = "rank"
        const val KEY_INPUT_PLATFORM = "input_platform"
        const val KEY_AUTO_SCAN = "auto_scan"
        const val KEY_AUTO_OPEN_RESULTS = "auto_open_results"
        const val KEY_SHOW_DETECTIONS = "show_detections"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ZOOM = "zoom"
        const val KEY_SCAN_MODE = "scan_mode"
        const val KEY_LAYOUT = "layout"
        const val KEY_ONBOARDING = "onboarding_complete"
        const val KEY_ROLE = "role"
        const val KEY_MAP_PROFILE = "map_profile"
        const val KEY_CURRENT_HERO = "current_hero"
        const val KEY_ULTIMATE = "ultimate"
        const val KEY_HERO_POOL = "hero_pool"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 50
    }
}
