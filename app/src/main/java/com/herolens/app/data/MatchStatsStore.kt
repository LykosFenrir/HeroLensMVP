package com.herolens.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * App-private match-stat persistence. The payload is versioned, while the reader
 * also accepts legacy bare arrays, legacy root names and common legacy row names.
 */
class MatchStatsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    fun loadMatches(): List<MatchStatsEntry> {
        val payload = MATCH_PAYLOAD_KEYS.firstNotNullOfOrNull { key ->
            preferences.getString(key, null)?.takeIf(String::isNotBlank)
        } ?: return emptyList()
        return MatchStatsJson.decode(payload)
    }

    fun saveMatches(entries: List<MatchStatsEntry>) {
        val normalized = normalizeStoredMatches(entries)
        preferences.edit()
            .putString(KEY_MATCH_PAYLOAD, MatchStatsJson.encode(normalized))
            .apply()
    }

    /** Returns false without modifying storage when the reviewed row is invalid. */
    fun addMatch(entry: MatchStatsEntry): Boolean {
        val normalized = entry.normalizedOrNull() ?: return false
        saveMatches(listOf(normalized) + loadMatches())
        return true
    }

    fun aggregate(): MatchStatsAggregate = loadMatches().aggregateMatchStats()

    /**
     * Optional display identity used only to find the player's OCR row. This is
     * never treated as an account connection and no credential is accepted.
     */
    fun loadBattleTag(): String = preferences.getString(KEY_BATTLE_TAG, null)
        ?.trim()
        ?.replace(Regex("[\\r\\n\\t]+"), " ")
        ?.replace(Regex(" {2,}"), " ")
        ?.take(MAX_SAVED_BATTLE_TAG_LENGTH)
        .orEmpty()

    fun saveBattleTag(battleTag: String) {
        val normalized = battleTag
            .trim()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex(" {2,}"), " ")
            .take(MAX_SAVED_BATTLE_TAG_LENGTH)
        preferences.edit().putString(KEY_BATTLE_TAG, normalized).apply()
    }

    fun clear() {
        val editor = preferences.edit()
        MATCH_PAYLOAD_KEYS.forEach(editor::remove)
        editor.apply()
    }

    companion object {
        const val PREFERENCES_NAME = "herolens_private_match_stats"
        const val KEY_MATCH_PAYLOAD = "match_stats_payload"
        const val KEY_BATTLE_TAG = "battle_tag_identity"
        private val MATCH_PAYLOAD_KEYS = listOf(
            KEY_MATCH_PAYLOAD,
            "match_stats",
            "match_history"
        )
    }
}

private const val MAX_SAVED_BATTLE_TAG_LENGTH = 96

internal object MatchStatsJson {
    private const val CURRENT_SCHEMA_VERSION = 1

    fun encode(entries: List<MatchStatsEntry>): String {
        val matches = JSONArray()
        normalizeStoredMatches(entries).forEach { entry ->
            val row = JSONObject()
                .put("timestamp", entry.timestamp)
                .put("mode", entry.mode)
                .put("mapLabel", entry.mapLabel)
                .put("heroId", entry.heroId)
                .put("result", entry.result.name)
                .put("eliminations", entry.eliminations)
                .put("assists", entry.assists)
                .put("deaths", entry.deaths)
                .put("damage", entry.damage)
                .put("healing", entry.healing)
                .put("mitigation", entry.mitigation)
                .put("source", entry.source)
                .put("confidence", entry.confidence)
            entry.battleTag?.let { row.put("battleTag", it) }
            matches.put(row)
        }
        return JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("matches", matches)
            .toString()
    }

    fun decode(payload: String?): List<MatchStatsEntry> {
        val source = payload?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val rows = runCatching {
            when {
                source.startsWith("[") -> JSONArray(source)
                source.startsWith("{") -> {
                    val root = JSONObject(source)
                    root.optJSONArray("matches")
                        ?: root.optJSONArray("entries")
                        ?: root.optJSONArray("history")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }
        }.getOrNull() ?: return emptyList()

        val decoded = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val fields = buildMap<String, Any?> {
                    val keys = row.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, row.opt(key).takeUnless { it === JSONObject.NULL })
                    }
                }
                decodeMatchStatsFields(fields)?.let(::add)
            }
        }
        return normalizeStoredMatches(decoded)
    }
}

/** Pure row decoder, separated from Android JSON so corrupt/legacy cases are unit-testable. */
internal fun decodeMatchStatsFields(fields: Map<String, Any?>): MatchStatsEntry? {
    val reader = LegacyFieldReader(fields)
    val timestamp = reader.long("timestamp", "playedAt", "createdAt") ?: return null
    val heroId = reader.string("heroId", "hero", "hero_id").orEmpty()
    val rawConfidence = reader.value("confidence", "scanConfidence", "ocrConfidence")

    return MatchStatsEntry(
        timestamp = timestamp,
        battleTag = reader.string("battleTag", "battle_tag", "battletag"),
        mode = reader.string("mode", "gameMode", "game_mode") ?: UNKNOWN_MODE,
        mapLabel = reader.string("mapLabel", "map", "mapName") ?: UNKNOWN_MAP_LABEL,
        heroId = heroId,
        result = MatchResult.fromPersistedValue(reader.value("result", "outcome")),
        eliminations = reader.stat("eliminations", "elims", "kills"),
        assists = reader.stat("assists"),
        deaths = reader.stat("deaths"),
        damage = reader.stat("damage", "heroDamage", "damageDone"),
        healing = reader.stat("healing", "healingDone"),
        mitigation = reader.stat("mitigation", "mitigated", "damageMitigated"),
        source = reader.string("source", "captureSource") ?: MatchStatsSources.LEGACY_IMPORT,
        confidence = parseConfidence(rawConfidence)
    ).normalizedOrNull()
}

internal fun normalizeStoredMatches(entries: Iterable<MatchStatsEntry>): List<MatchStatsEntry> =
    entries.mapNotNull(MatchStatsEntry::normalizedOrNull)
        .sortedByDescending(MatchStatsEntry::timestamp)
        .distinct()
        .take(MAX_STORED_MATCHES)

private class LegacyFieldReader(fields: Map<String, Any?>) {
    private val values = fields.entries.associate { it.key.lowercase(Locale.ROOT) to it.value }

    fun value(vararg names: String): Any? = names.firstNotNullOfOrNull {
        values[it.lowercase(Locale.ROOT)]
    }

    fun string(vararg names: String): String? = (value(*names) as? String)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    fun long(vararg names: String): Long? = value(*names).asLong()

    fun stat(vararg names: String): Int = value(*names)
        .asLong()
        ?.coerceIn(0L, MAX_STAT_VALUE.toLong())
        ?.toInt()
        ?: 0
}

private fun Any?.asLong(): Long? = when (this) {
    is Byte -> toLong()
    is Short -> toLong()
    is Int -> toLong()
    is Long -> this
    is Float -> takeIf(Float::isFinite)?.toLong()
    is Double -> takeIf(Double::isFinite)?.toLong()
    is String -> trim().replace(",", "").toLongOrNull()
    else -> null
}

private fun parseConfidence(value: Any?): Int {
    if (value == null) return 0
    val stringValue = value as? String
    val raw = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim()
            .removeSuffix("%")
            .replace(",", "")
            .toDoubleOrNull()
            ?: return 0
        else -> return 0
    }
    if (!raw.isFinite()) return 0
    val isFraction = (value is Float || value is Double) ||
        (stringValue != null && !stringValue.endsWith("%") && stringValue.contains('.'))
    val percentage = if (isFraction && raw in 0.0..1.0) raw * 100.0 else raw
    return percentage.toInt().coerceIn(0, 100)
}
