package com.herolens.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.herolens.app.core.HeroCatalog
import com.herolens.app.data.GameModeProfile
import com.herolens.app.data.MatchResult
import com.herolens.app.data.MatchStatsAggregate
import com.herolens.app.data.MatchStatsEntry
import com.herolens.app.data.ScanHistoryEntry
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Private performance history built from scoreboards the player explicitly reviews.
 * This screen owns only presentation and clear confirmation; capture, OCR and storage
 * remain with the caller.
 */
@Composable
fun PerformanceScreen(
    battleTag: String,
    onBattleTagChanged: (String) -> Unit,
    matches: List<MatchStatsEntry>,
    aggregate: MatchStatsAggregate,
    coachingHistory: List<ScanHistoryEntry>,
    onCaptureScoreboard: () -> Unit,
    onChooseScoreboard: () -> Unit,
    onOpenCoachingReview: (ScanHistoryEntry) -> Unit,
    onClearMatchStats: () -> Unit,
    onClearCoachingHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var showClearCoachingConfirmation by rememberSaveable { mutableStateOf(false) }
    val recentMatches = matches.sortedByDescending(MatchStatsEntry::timestamp).take(12)
    val recentCoaching = coachingHistory.sortedByDescending(ScanHistoryEntry::timestamp).take(6)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "PRIVATE PERFORMANCE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Your matches, not a public profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Build an honest record from final scoreboards you review and approve.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            IdentityCard(
                battleTag = battleTag,
                onBattleTagChanged = onBattleTagChanged
            )
        }

        item {
            ScoreboardCaptureCard(
                onCaptureScoreboard = onCaptureScoreboard,
                onChooseScoreboard = onChooseScoreboard
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "PERFORMANCE SNAPSHOT",
                    supportingText = if (aggregate.matches == 0) {
                        "Add a reviewed scoreboard to start your private baseline"
                    } else {
                        "Calculated only from the reviewed matches saved on this device"
                    }
                )
                AggregateGrid(aggregate)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(
                    title = "RECENT MATCHES",
                    supportingText = "Actual scoreboard stats after your review",
                    modifier = Modifier.weight(1f)
                )
                if (matches.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirmation = true }) {
                        Text("CLEAR")
                    }
                }
            }
        }

        if (recentMatches.isEmpty()) {
            item { EmptyMatchesCard(onCaptureScoreboard) }
        } else {
            items(recentMatches) { match ->
                MatchStatsRow(match)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(
                    title = "RECENT COACHING REVIEWS",
                    supportingText = "Saved matchup plans stay separate from verified match statistics",
                    modifier = Modifier.weight(1f)
                )
                if (coachingHistory.isNotEmpty()) {
                    TextButton(onClick = { showClearCoachingConfirmation = true }) {
                        Text("CLEAR")
                    }
                }
            }
        }

        if (recentCoaching.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "No coaching reviews saved yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(recentCoaching) { review ->
                CoachingReviewRow(
                    review = review,
                    onClick = { onOpenCoachingReview(review) }
                )
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear private match stats?") },
            text = {
                Text(
                    "This removes ${matches.size} reviewed ${if (matches.size == 1) "match" else "matches"} " +
                        "and resets the performance snapshot. Saved coaching reviews are not removed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmation = false
                        onClearMatchStats()
                    }
                ) {
                    Text("CLEAR STATS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
    if (showClearCoachingConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCoachingConfirmation = false },
            title = { Text("Clear coaching reviews?") },
            text = {
                Text(
                    "This removes ${coachingHistory.size} saved coaching " +
                        if (coachingHistory.size == 1) "review. Private match stats stay saved." else "reviews. Private match stats stay saved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCoachingConfirmation = false
                        onClearCoachingHistory()
                    }
                ) { Text("CLEAR REVIEWS") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCoachingConfirmation = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun IdentityCard(
    battleTag: String,
    onBattleTagChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ACCOUNT LABEL", fontWeight = FontWeight.Black)
            OutlinedTextField(
                value = battleTag,
                onValueChange = onBattleTagChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("BattleTag (optional)") },
                placeholder = { Text("Player#1234") },
                singleLine = true
            )
            Text(
                text = "BattleTag is an identity label only. Blizzard does not provide an official " +
                    "Overwatch match-history or player-stats API, so HeroLens does not claim to sync " +
                    "your account and never asks for your password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreboardCaptureCard(
    onCaptureScoreboard: () -> Unit,
    onChooseScoreboard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ADD A FINAL SCOREBOARD",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "OCR creates an editable draft on this device. Check your hero, result and " +
                    "numbers before saving; a scan is never treated as verified until you approve it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
            ) {
                Text(
                    text = "PRIVATE BY DEFAULT  •  NO AUTOMATIC UPLOAD",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCaptureScoreboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CAMERA")
                }
                OutlinedButton(
                    onClick = onChooseScoreboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CHOOSE PHOTO")
                }
            }
        }
    }
}

@Composable
private fun AggregateGrid(aggregate: MatchStatsAggregate) {
    val topHero = aggregate.topHeroId?.let(HeroCatalog.byId::get)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = "MATCHES",
                value = aggregate.matches.toString(),
                detail = "${aggregate.wins}W  ${aggregate.losses}L  ${aggregate.draws}D",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "WIN RATE",
                value = aggregate.winRatePercent?.let(::formatPercent) ?: "—",
                detail = if (aggregate.unknownResults > 0) {
                    "${aggregate.unknownResults} result unknown"
                } else {
                    "Decided matches"
                },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = "KDA",
                value = aggregate.kdaRatio?.let(::formatRatio) ?: "—",
                detail = "${formatNumber(aggregate.eliminations)} / ${formatNumber(aggregate.assists)} / ${formatNumber(aggregate.deaths)}",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "TOP HERO",
                value = topHero?.name ?: aggregate.topHeroId?.toDisplayName() ?: "—",
                detail = if (aggregate.matches > 0) "Most played" else "No matches yet",
                modifier = Modifier.weight(1f)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(17.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CAREER TOTALS ON THIS DEVICE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${formatNumber(aggregate.damage)} damage  •  " +
                        "${formatNumber(aggregate.healing)} healing  •  " +
                        "${formatNumber(aggregate.mitigation)} mitigation",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(17.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MatchStatsRow(match: MatchStatsEntry) {
    val heroName = HeroCatalog.byId[match.heroId]?.name ?: match.heroId.toDisplayName()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroPortrait(
                heroId = match.heroId,
                heroName = heroName,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = heroName,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "  ${match.eliminations} / ${match.assists} / ${match.deaths}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = listOf(
                        modeDisplayName(match.mode),
                        match.mapLabel,
                        match.timestamp.toTimeLabel()
                    ).filter(String::isNotBlank).joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatNumber(match.damage)} DMG  •  " +
                        "${formatNumber(match.healing)} HEAL  •  ${formatNumber(match.mitigation)} MIT",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${sourceDisplayName(match.source)}  •  ${match.confidence.coerceIn(0, 100)}% confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(9.dp))
            MatchResultPill(match.result)
        }
    }
}

@Composable
private fun MatchResultPill(result: MatchResult) {
    val containerColor = when (result) {
        MatchResult.WIN -> MaterialTheme.colorScheme.secondaryContainer
        MatchResult.LOSS -> MaterialTheme.colorScheme.errorContainer
        MatchResult.DRAW,
        MatchResult.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (result) {
        MatchResult.WIN -> MaterialTheme.colorScheme.onSecondaryContainer
        MatchResult.LOSS -> MaterialTheme.colorScheme.onErrorContainer
        MatchResult.DRAW,
        MatchResult.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor) {
        Text(
            text = when (result) {
                MatchResult.WIN -> "WIN"
                MatchResult.LOSS -> "LOSS"
                MatchResult.DRAW -> "DRAW"
                MatchResult.UNKNOWN -> "SAVED"
            },
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EmptyMatchesCard(onCaptureScoreboard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NO VERIFIED MATCHES YET", fontWeight = FontWeight.Black)
                Text(
                    text = "Photograph a final scoreboard, review the OCR draft, then save it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCaptureScoreboard) { Text("CAPTURE") }
        }
    }
}

@Composable
private fun CoachingReviewRow(
    review: ScanHistoryEntry,
    onClick: () -> Unit
) {
    val heroName = HeroCatalog.byId[review.bestHeroId]?.name ?: review.bestHeroId.toDisplayName()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroPortrait(
                heroId = review.bestHeroId,
                heroName = heroName,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = heroName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${modeDisplayName(review.gameMode)}  •  ${review.timestamp.toTimeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("Fit ${review.fitScore}")
                        append("  •  scan ${review.scanConfidence.coerceIn(0, 100)}%")
                        review.teamSize?.let { append("  •  ${it}v$it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "OPEN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun modeDisplayName(rawMode: String): String {
    val canonical = rawMode.trim()
    val knownProfile = GameModeProfile.entries.firstOrNull {
        it.name.equals(canonical, ignoreCase = true)
    }
    return knownProfile?.label ?: canonical.toDisplayName().ifBlank { GameModeProfile.AUTO.label }
}

private fun sourceDisplayName(source: String): String = when (source.trim().uppercase(Locale.ROOT)) {
    "SCOREBOARD_SCAN" -> "Reviewed OCR"
    "MANUAL" -> "Manual entry"
    "ACCOUNT_IMPORT" -> "Account import"
    "LEGACY_IMPORT" -> "Legacy import"
    else -> source.toDisplayName().ifBlank { "Saved entry" }
}

private fun String.toDisplayName(): String = trim()
    .replace('-', ' ')
    .replace('_', ' ')
    .lowercase(Locale.ROOT)
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }

private fun Long.toTimeLabel(): String {
    if (this <= 0L) return "Unknown time"
    return SimpleDateFormat("MMM d  •  h:mm a", Locale.getDefault()).format(Date(this))
}

private fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance().format(value)

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun formatPercent(value: Double): String = String.format(Locale.getDefault(), "%.0f%%", value)

private fun formatRatio(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
