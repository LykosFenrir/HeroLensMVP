package com.herolens.app.vision

/** Resolves row count without allowing uncertain box counts to override an explicit user choice. */
internal object ScoreboardTeamSizeResolver {
    fun resolve(
        forcedTeamSize: Int?,
        estimatedTeamSize: Int?,
        allyBoxCount: Int,
        enemyBoxCount: Int
    ): Int {
        forcedTeamSize?.takeIf { it in 3..6 }?.let { return it }
        // A single six-row panel is enough evidence for 6v6. Requiring both panels
        // dropped a row whenever glare hid one portrait on only one team.
        if (maxOf(allyBoxCount, enemyBoxCount) >= 6) return 6
        return estimatedTeamSize?.takeIf { it in 5..6 } ?: 5
    }
}
