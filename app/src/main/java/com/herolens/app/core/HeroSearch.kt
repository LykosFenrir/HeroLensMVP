package com.herolens.app.core

import java.text.Normalizer

object HeroSearch {
    fun matches(hero: Hero, query: String, role: Role?): Boolean {
        if (role != null && hero.role != role) return false
        val needle = normalize(query)
        return needle.isEmpty() || normalize(hero.name).contains(needle) || normalize(hero.id).contains(needle)
    }

    internal fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .filter(Char::isLetterOrDigit)
}
