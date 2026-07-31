import com.herolens.app.core.MapProfile
import com.herolens.app.core.MatchContext
import com.herolens.app.core.RecommendationEngine
import com.herolens.app.core.Role

fun main() {
    val picks = RecommendationEngine.recommend(
        MatchContext(
            role = Role.DAMAGE,
            mapProfile = MapProfile.OPEN,
            allyIds = setOf("mercy", "sigma"),
            enemyIds = setOf("pharah", "echo", "winston"),
            preferences = mapOf("soldier-76" to 4, "cassidy" to 3)
        )
    )
    check(picks.size == 3)
    check(picks.all { it.hero.role == Role.DAMAGE })
    println(picks.joinToString("\n") { "${it.hero.name}: ${it.score}" })
}
