package com.herolens.app.core

enum class Role(val displayName: String) {
    TANK("Tank"),
    DAMAGE("Damage"),
    SUPPORT("Support")
}

enum class MapProfile(val displayName: String) {
    MIXED("Mixed"),
    OPEN("Open sightlines"),
    VERTICAL("Vertical"),
    CLOSE("Close quarters")
}

enum class Trait {
    DIVE,
    BRAWL,
    POKE,
    RUSH,
    HITSCAN,
    PROJECTILE,
    SNIPER,
    MOBILITY,
    VERTICALITY,
    SUSTAIN,
    BURST,
    PEEL,
    CLEANSE,
    ANTI_DIVE,
    ANTI_AIR,
    SHIELD_BREAK,
    AREA_CONTROL,
    DISPLACEMENT,
    PROTECTION,
    SPEED,
    LONG_RANGE,
    CLOSE_RANGE
}

data class Hero(
    val id: String,
    val name: String,
    val role: Role,
    val traits: Set<Trait>
)

data class MatchContext(
    val role: Role,
    val mapProfile: MapProfile,
    val allyIds: Set<String>,
    val enemyIds: Set<String>,
    val preferences: Map<String, Int>,
    val currentHeroId: String? = null,
    val ultimateCharge: Int = 0,
    val rank: String = "UNRANKED"
)

sealed interface Reason {
    data class Counters(
        val enemyName: String,
        val detail: String,
        val detailAr: String
    ) : Reason

    data class WorksWith(
        val allyName: String,
        val detail: String,
        val detailAr: String
    ) : Reason
    data class MapFit(val mapName: String) : Reason
    data class Comfort(val level: Int) : Reason
    data class TeamNeed(val need: String) : Reason
    data class SwitchCost(val ultimateCharge: Int) : Reason
    data class RankFit(val rankName: String) : Reason
}

data class Recommendation(
    val hero: Hero,
    val score: Int,
    val reasons: List<Reason>
)
