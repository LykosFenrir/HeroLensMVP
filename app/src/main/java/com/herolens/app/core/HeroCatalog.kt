package com.herolens.app.core

object HeroCatalog {
    const val DATA_VERSION = "2026-07-seed-1"

    val heroes: List<Hero> = listOf(
        Hero("dva", "D.Va", Role.TANK, setOf(Trait.DIVE, Trait.MOBILITY, Trait.VERTICALITY, Trait.PEEL, Trait.ANTI_AIR)),
        Hero("domina", "Domina", Role.TANK, setOf(Trait.POKE, Trait.PROTECTION, Trait.AREA_CONTROL, Trait.DISPLACEMENT, Trait.LONG_RANGE)),
        Hero("doomfist", "Doomfist", Role.TANK, setOf(Trait.DIVE, Trait.MOBILITY, Trait.BURST, Trait.DISPLACEMENT, Trait.CLOSE_RANGE)),
        Hero("hazard", "Hazard", Role.TANK, setOf(Trait.BRAWL, Trait.DIVE, Trait.MOBILITY, Trait.AREA_CONTROL, Trait.CLOSE_RANGE)),
        Hero("junker-queen", "Junker Queen", Role.TANK, setOf(Trait.BRAWL, Trait.RUSH, Trait.SUSTAIN, Trait.SPEED, Trait.CLOSE_RANGE)),
        Hero("mauga", "Mauga", Role.TANK, setOf(Trait.BRAWL, Trait.SUSTAIN, Trait.SHIELD_BREAK, Trait.LONG_RANGE)),
        Hero("orisa", "Orisa", Role.TANK, setOf(Trait.BRAWL, Trait.POKE, Trait.SUSTAIN, Trait.ANTI_DIVE, Trait.DISPLACEMENT)),
        Hero("ramattra", "Ramattra", Role.TANK, setOf(Trait.BRAWL, Trait.POKE, Trait.SUSTAIN, Trait.SHIELD_BREAK)),
        Hero("reinhardt", "Reinhardt", Role.TANK, setOf(Trait.BRAWL, Trait.RUSH, Trait.PROTECTION, Trait.CLOSE_RANGE)),
        Hero("roadhog", "Roadhog", Role.TANK, setOf(Trait.BRAWL, Trait.BURST, Trait.SUSTAIN, Trait.DISPLACEMENT)),
        Hero("sigma", "Sigma", Role.TANK, setOf(Trait.POKE, Trait.PROTECTION, Trait.AREA_CONTROL, Trait.LONG_RANGE)),
        Hero("winston", "Winston", Role.TANK, setOf(Trait.DIVE, Trait.MOBILITY, Trait.VERTICALITY, Trait.PROTECTION, Trait.ANTI_DIVE)),
        Hero("wrecking-ball", "Wrecking Ball", Role.TANK, setOf(Trait.DIVE, Trait.MOBILITY, Trait.VERTICALITY, Trait.DISPLACEMENT, Trait.SUSTAIN)),
        Hero("zarya", "Zarya", Role.TANK, setOf(Trait.BRAWL, Trait.SUSTAIN, Trait.PROTECTION, Trait.CLEANSE)),
        Hero("anran", "Anran", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.BURST, Trait.MOBILITY, Trait.AREA_CONTROL)),
        Hero("ashe", "Ashe", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.SNIPER, Trait.LONG_RANGE, Trait.ANTI_AIR)),
        Hero("bastion", "Bastion", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.SHIELD_BREAK, Trait.BURST, Trait.LONG_RANGE)),
        Hero("cassidy", "Cassidy", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.BURST, Trait.ANTI_DIVE, Trait.ANTI_AIR)),
        Hero("echo", "Echo", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.MOBILITY, Trait.VERTICALITY, Trait.BURST)),
        Hero("emre", "Emre", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.MOBILITY, Trait.BURST, Trait.RUSH)),
        Hero("freja", "Freja", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.SNIPER, Trait.MOBILITY, Trait.VERTICALITY, Trait.LONG_RANGE)),
        Hero("genji", "Genji", Role.DAMAGE, setOf(Trait.DIVE, Trait.MOBILITY, Trait.VERTICALITY, Trait.BURST)),
        Hero("hanzo", "Hanzo", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.SNIPER, Trait.BURST, Trait.LONG_RANGE)),
        Hero("junkrat", "Junkrat", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.BURST, Trait.AREA_CONTROL, Trait.SHIELD_BREAK)),
        Hero("mei", "Mei", Role.DAMAGE, setOf(Trait.BRAWL, Trait.AREA_CONTROL, Trait.ANTI_DIVE, Trait.SUSTAIN)),
        Hero("pharah", "Pharah", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.MOBILITY, Trait.VERTICALITY, Trait.BURST, Trait.LONG_RANGE)),
        Hero("reaper", "Reaper", Role.DAMAGE, setOf(Trait.BRAWL, Trait.CLOSE_RANGE, Trait.BURST, Trait.SUSTAIN, Trait.ANTI_DIVE)),
        Hero("shion", "Shion", Role.DAMAGE, setOf(Trait.BRAWL, Trait.BURST, Trait.MOBILITY, Trait.CLOSE_RANGE)),
        Hero("sierra", "Sierra", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.MOBILITY, Trait.VERTICALITY, Trait.LONG_RANGE)),
        Hero("sojourn", "Sojourn", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.MOBILITY, Trait.BURST, Trait.LONG_RANGE)),
        Hero("soldier-76", "Soldier: 76", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.SUSTAIN, Trait.MOBILITY, Trait.ANTI_AIR, Trait.LONG_RANGE)),
        Hero("sombra", "Sombra", Role.DAMAGE, setOf(Trait.DIVE, Trait.MOBILITY, Trait.ANTI_DIVE, Trait.BURST)),
        Hero("symmetra", "Symmetra", Role.DAMAGE, setOf(Trait.BRAWL, Trait.AREA_CONTROL, Trait.SHIELD_BREAK, Trait.CLOSE_RANGE)),
        Hero("torbjorn", "Torbjörn", Role.DAMAGE, setOf(Trait.PROJECTILE, Trait.ANTI_DIVE, Trait.AREA_CONTROL, Trait.SUSTAIN)),
        Hero("tracer", "Tracer", Role.DAMAGE, setOf(Trait.DIVE, Trait.MOBILITY, Trait.BURST, Trait.CLOSE_RANGE)),
        Hero("vendetta", "Vendetta", Role.DAMAGE, setOf(Trait.BRAWL, Trait.MOBILITY, Trait.BURST, Trait.CLOSE_RANGE)),
        Hero("venture", "Venture", Role.DAMAGE, setOf(Trait.BRAWL, Trait.MOBILITY, Trait.BURST, Trait.CLOSE_RANGE)),
        Hero("widowmaker", "Widowmaker", Role.DAMAGE, setOf(Trait.HITSCAN, Trait.SNIPER, Trait.LONG_RANGE, Trait.ANTI_AIR)),
        Hero("ana", "Ana", Role.SUPPORT, setOf(Trait.SNIPER, Trait.LONG_RANGE, Trait.ANTI_DIVE, Trait.BURST)),
        Hero("baptiste", "Baptiste", Role.SUPPORT, setOf(Trait.HITSCAN, Trait.SUSTAIN, Trait.PROTECTION, Trait.ANTI_AIR)),
        Hero("brigitte", "Brigitte", Role.SUPPORT, setOf(Trait.BRAWL, Trait.PEEL, Trait.ANTI_DIVE, Trait.PROTECTION, Trait.CLOSE_RANGE)),
        Hero("illari", "Illari", Role.SUPPORT, setOf(Trait.HITSCAN, Trait.SUSTAIN, Trait.LONG_RANGE, Trait.ANTI_AIR)),
        Hero("jetpack-cat", "Jetpack Cat", Role.SUPPORT, setOf(Trait.MOBILITY, Trait.VERTICALITY, Trait.SUSTAIN, Trait.PEEL)),
        Hero("juno", "Juno", Role.SUPPORT, setOf(Trait.MOBILITY, Trait.SPEED, Trait.SUSTAIN, Trait.LONG_RANGE)),
        Hero("kiriko", "Kiriko", Role.SUPPORT, setOf(Trait.MOBILITY, Trait.CLEANSE, Trait.BURST, Trait.SUSTAIN)),
        Hero("lifeweaver", "Lifeweaver", Role.SUPPORT, setOf(Trait.PROTECTION, Trait.PEEL, Trait.VERTICALITY, Trait.SUSTAIN)),
        Hero("lucio", "Lúcio", Role.SUPPORT, setOf(Trait.SPEED, Trait.MOBILITY, Trait.PEEL, Trait.RUSH, Trait.SUSTAIN)),
        Hero("mercy", "Mercy", Role.SUPPORT, setOf(Trait.MOBILITY, Trait.SUSTAIN, Trait.PROTECTION, Trait.VERTICALITY)),
        Hero("mizuki", "Mizuki", Role.SUPPORT, setOf(Trait.SUSTAIN, Trait.MOBILITY, Trait.AREA_CONTROL, Trait.ANTI_DIVE)),
        Hero("moira", "Moira", Role.SUPPORT, setOf(Trait.SUSTAIN, Trait.MOBILITY, Trait.BRAWL, Trait.CLOSE_RANGE)),
        Hero("wuyang", "Wuyang", Role.SUPPORT, setOf(Trait.SUSTAIN, Trait.AREA_CONTROL, Trait.PROTECTION, Trait.LONG_RANGE)),
        Hero("zenyatta", "Zenyatta", Role.SUPPORT, setOf(Trait.POKE, Trait.BURST, Trait.LONG_RANGE, Trait.SHIELD_BREAK))
    )

    val byId: Map<String, Hero> = heroes.associateBy(Hero::id)

    fun forRole(role: Role): List<Hero> = heroes.filter { it.role == role }
}
