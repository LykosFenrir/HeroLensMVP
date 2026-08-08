package com.herolens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A compact, UI-ready snapshot of a HeroLens coaching session. */
@Immutable
data class CompanionSessionSummary(
    val modeLabel: String,
    val mapLabel: String? = null,
    val heroId: String? = null,
    val heroName: String? = null,
    val statusLabel: String = "MATCH IN PROGRESS",
    val focus: String? = null
)

enum class CompanionMatchResult(val label: String) {
    WIN("WIN"),
    LOSS("LOSS"),
    DRAW("DRAW"),
    REVIEWED("SAVED")
}

/** Data displayed by one recent-match row. Labels are supplied already localized. */
@Immutable
data class CompanionRecentMatchSummary(
    val id: String,
    val heroId: String? = null,
    val heroName: String,
    val result: CompanionMatchResult,
    val modeLabel: String,
    val mapLabel: String? = null,
    val playedAtLabel: String,
    val headlineMetric: String? = null,
    val coachingNote: String? = null
)

/**
 * Mobile-first HeroLens home screen. It owns no navigation or session state; callers provide
 * dashboard data and route each action through the callbacks below.
 */
@Composable
fun CompanionDashboardScreen(
    recentMatches: List<CompanionRecentMatchSummary>,
    onDraftClick: () -> Unit,
    onLiveCoachClick: () -> Unit,
    onPostMatchClick: () -> Unit,
    onHeroesMetaClick: () -> Unit,
    onRecentMatchClick: (CompanionRecentMatchSummary) -> Unit,
    modifier: Modifier = Modifier,
    playerLabel: String = "PLAYER",
    rankLabel: String? = null,
    activeSession: CompanionSessionSummary? = null,
    onResumeSession: (() -> Unit)? = null,
    onViewAllMatches: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            DashboardHeader(playerLabel = playerLabel, rankLabel = rankLabel)
        }

        activeSession?.let { session ->
            item {
                ActiveSessionCard(
                    session = session,
                    onResume = onResumeSession ?: onLiveCoachClick
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "MATCH COMPANION",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Plan the matchup, stay focused during the fight, then turn the result into your next improvement.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PhaseActionRow(
                    first = DashboardAction(
                        number = "01",
                        title = "DRAFT / PRE-MATCH",
                        description = "Scan teams and bans, then get picks, counters and a fight plan.",
                        accent = DashboardAccent.PRIMARY,
                        onClick = onDraftClick
                    ),
                    second = DashboardAction(
                        number = "02",
                        title = "LIVE COACH",
                        description = "Open a glanceable plan for threats, positioning and ultimate timing.",
                        accent = DashboardAccent.SECONDARY,
                        onClick = onLiveCoachClick
                    )
                )
                PhaseActionRow(
                    first = DashboardAction(
                        number = "03",
                        title = "POST-MATCH REVIEW",
                        description = "Reopen saved plans and turn each matchup into your next improvement.",
                        accent = DashboardAccent.SECONDARY,
                        onClick = onPostMatchClick
                    ),
                    second = DashboardAction(
                        number = "04",
                        title = "HERO INTEL",
                        description = "Explore hero matchups, synergies, threats and practical game plans.",
                        accent = DashboardAccent.PRIMARY,
                        onClick = onHeroesMetaClick
                    )
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "RECENT COACHING",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Your latest saved recommendations and matchup notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                onViewAllMatches?.let { callback ->
                    TextButton(onClick = callback) { Text("VIEW ALL") }
                }
            }
        }

        if (recentMatches.isEmpty()) {
            item {
                EmptyRecentMatchesCard(onStartMatch = onDraftClick)
            }
        } else {
            items(recentMatches, key = CompanionRecentMatchSummary::id) { match ->
                RecentMatchCard(match = match, onClick = { onRecentMatchClick(match) })
            }
        }
    }
}

@Composable
private fun DashboardHeader(playerLabel: String, rankLabel: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "HEROLENS // OVERWATCH",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "READY, ${playerLabel.uppercase()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        rankLabel?.takeIf(String::isNotBlank)?.let { rank ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = rank.uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionCard(session: CompanionSessionSummary, onResume: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = session.statusLabel.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = listOfNotNull(session.modeLabel, session.mapLabel).joinToString(" / "),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                session.heroId?.let { id ->
                    HeroPortrait(
                        heroId = id,
                        heroName = session.heroName ?: id,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.heroName ?: "Live match plan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    session.focus?.takeIf(String::isNotBlank)?.let { focus ->
                        Text(
                            text = focus,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(onClick = onResume) { Text("RESUME") }
            }
        }
    }
}

private enum class DashboardAccent { PRIMARY, SECONDARY }

private data class DashboardAction(
    val number: String,
    val title: String,
    val description: String,
    val accent: DashboardAccent,
    val onClick: () -> Unit
)

@Composable
private fun PhaseActionRow(first: DashboardAction, second: DashboardAction) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PhaseActionCard(action = first, modifier = Modifier.weight(1f))
        PhaseActionCard(action = second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhaseActionCard(action: DashboardAction, modifier: Modifier = Modifier) {
    val accent = when (action.accent) {
        DashboardAccent.PRIMARY -> MaterialTheme.colorScheme.primary
        DashboardAccent.SECONDARY -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = modifier
            .heightIn(min = 170.dp)
            .semantics { role = Role.Button }
            .clickable(onClick = action.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(shape = CircleShape, color = accent) {
                Text(
                    text = action.number,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColorForAccent(action.accent),
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun contentColorForAccent(accent: DashboardAccent): Color = when (accent) {
    DashboardAccent.PRIMARY -> MaterialTheme.colorScheme.onPrimary
    DashboardAccent.SECONDARY -> MaterialTheme.colorScheme.onSecondary
}

@Composable
private fun RecentMatchCard(match: CompanionRecentMatchSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (match.heroId != null) {
                HeroPortrait(match.heroId, match.heroName, Modifier.size(54.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = match.heroName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = match.heroName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    match.headlineMetric?.takeIf(String::isNotBlank)?.let { metric ->
                        Text(
                            text = "  $metric",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = listOfNotNull(match.modeLabel, match.mapLabel, match.playedAtLabel)
                        .joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                match.coachingNote?.takeIf(String::isNotBlank)?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            MatchResultBadge(match.result)
        }
    }
}

@Composable
private fun MatchResultBadge(result: CompanionMatchResult) {
    val containerColor = when (result) {
        CompanionMatchResult.WIN -> MaterialTheme.colorScheme.secondaryContainer
        CompanionMatchResult.LOSS -> MaterialTheme.colorScheme.errorContainer
        CompanionMatchResult.DRAW,
        CompanionMatchResult.REVIEWED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (result) {
        CompanionMatchResult.WIN -> MaterialTheme.colorScheme.onSecondaryContainer
        CompanionMatchResult.LOSS -> MaterialTheme.colorScheme.onErrorContainer
        CompanionMatchResult.DRAW,
        CompanionMatchResult.REVIEWED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor) {
        Text(
            text = result.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EmptyRecentMatchesCard(onStartMatch: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NO SAVED COACHING YET", fontWeight = FontWeight.Black)
                Text(
                    "Start a matchup, open Live Coach, then save the plan for later review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onStartMatch) { Text("START") }
        }
    }
}
