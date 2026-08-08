package com.herolens.app.core

/** A practical, offline guide assembled from the same knowledge used by recommendations. */
data class HeroGuide(
    val hero: Hero,
    val archetype: String,
    val overview: String,
    val strengths: List<String>,
    val gamePlan: List<String>,
    val counters: List<HeroRelationship>,
    val threats: List<HeroRelationship>,
    val synergies: List<HeroRelationship>,
    val dataVersion: String = HeroCatalog.DATA_VERSION
)

data class HeroRelationship(
    val hero: Hero,
    val explanation: String,
    val howToPlay: String,
    val evidence: GuideEvidence
)

enum class GuideEvidence(val label: String) {
    CURATED("Curated matchup data"),
    INFERRED("Trait-inferred")
}
