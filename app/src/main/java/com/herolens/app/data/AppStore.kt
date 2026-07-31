package com.herolens.app.data

import android.content.Context
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

data class ScannerSettings(
    val rank: RankTier = RankTier.UNRANKED,
    val autoScan: Boolean = true,
    val showDetections: Boolean = true,
    val hapticFeedback: Boolean = true,
    val defaultZoom: Float = 1f
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
        rank = runCatching {
            RankTier.valueOf(preferences.getString(KEY_RANK, RankTier.UNRANKED.name).orEmpty())
        }.getOrDefault(RankTier.UNRANKED),
        autoScan = preferences.getBoolean(KEY_AUTO_SCAN, true),
        showDetections = preferences.getBoolean(KEY_SHOW_DETECTIONS, true),
        hapticFeedback = preferences.getBoolean(KEY_HAPTICS, true),
        defaultZoom = preferences.getFloat(KEY_ZOOM, 1f).coerceIn(1f, 3f)
    )

    fun saveSettings(settings: ScannerSettings) {
        preferences.edit()
            .putString(KEY_RANK, settings.rank.name)
            .putBoolean(KEY_AUTO_SCAN, settings.autoScan)
            .putBoolean(KEY_SHOW_DETECTIONS, settings.showDetections)
            .putBoolean(KEY_HAPTICS, settings.hapticFeedback)
            .putFloat(KEY_ZOOM, settings.defaultZoom)
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
        const val KEY_AUTO_SCAN = "auto_scan"
        const val KEY_SHOW_DETECTIONS = "show_detections"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ZOOM = "zoom"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 30
    }
}
