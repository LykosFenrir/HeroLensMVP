package com.herolens.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.core.HeroGuide
import com.herolens.app.core.HeroRelationship
import com.herolens.app.core.HeroSearch
import com.herolens.app.core.RecommendationEngine
import com.herolens.app.core.Role

@Composable
fun ChampionsScreen(
    selectedHeroId: String?,
    onHeroSelected: (String) -> Unit,
    onBackToCatalog: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedRoleName by rememberSaveable { mutableStateOf("ALL") }
    BackHandler(enabled = selectedHeroId != null, onBack = onBackToCatalog)
    val guide = selectedHeroId?.let { id -> remember(id) { RecommendationEngine.guideFor(id) } }
    if (guide == null) {
        ChampionCatalog(
            query = query,
            onQueryChanged = { query = it },
            selectedRoleName = selectedRoleName,
            onRoleChanged = { selectedRoleName = it },
            onHeroSelected = onHeroSelected
        )
    } else {
        key(guide.hero.id) {
            ChampionGuideDetail(
                guide = guide,
                onBack = onBackToCatalog,
                onHeroSelected = onHeroSelected
            )
        }
    }
}

@Composable
private fun ChampionCatalog(
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedRoleName: String,
    onRoleChanged: (String) -> Unit,
    onHeroSelected: (String) -> Unit
) {
    val filtered = remember(query, selectedRoleName) {
        val role = selectedRoleName.takeUnless { it == "ALL" }?.let(Role::valueOf)
        HeroCatalog.heroes.filter { hero -> HeroSearch.matches(hero, query, role) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("HEROES", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Offline play guides, counters, threats and team synergies for every hero.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Search heroes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedRoleName == "ALL",
                        onClick = { onRoleChanged("ALL") },
                        label = { Text("All") }
                    )
                }
                items(Role.entries, key = Role::name) { role ->
                    FilterChip(
                        selected = selectedRoleName == role.name,
                        onClick = { onRoleChanged(role.name) },
                        label = { Text(role.displayName) }
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} heroes", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "Data ${HeroCatalog.DATA_VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(filtered, key = Hero::id) { hero ->
            val guide = remember(hero.id) { RecommendationEngine.guideFor(hero.id) }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onHeroSelected(hero.id) },
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroPortrait(hero.id, hero.name, Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(hero.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(
                            "${hero.role.displayName} · ${guide?.archetype ?: "Hero guide"}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            guide?.strengths?.take(3)?.joinToString(" · ").orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("OPEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text(
                        "No heroes match that search and role filter.",
                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChampionGuideDetail(
    guide: HeroGuide,
    onBack: () -> Unit,
    onHeroSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("BACK TO HEROES") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroPortrait(guide.hero.id, guide.hero.name, Modifier.size(92.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(guide.hero.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(
                        "${guide.hero.role.displayName} · ${guide.archetype}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Strategy data ${guide.dataVersion}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("HOW THIS HERO WINS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(guide.overview)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(guide.strengths, key = { it }) { strength ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    strength,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("HOW TO PLAY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    guide.gamePlan.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.secondary
                            ) {
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(step, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            RelationshipSection(
                title = "STRONG INTO",
                description = "Heroes this kit can pressure or deny. Use the matchup plan instead of treating a counter as automatic.",
                relationships = guide.counters,
                actionLabel = "How to punish",
                onHeroSelected = onHeroSelected
            )
        }

        item {
            RelationshipSection(
                title = "WATCH OUT FOR",
                description = "Heroes that can disrupt this game plan. Track them before committing.",
                relationships = guide.threats,
                actionLabel = "How to adapt",
                onHeroSelected = onHeroSelected
            )
        }

        item {
            RelationshipSection(
                title = "BEST SYNERGIES",
                description = "Strong partners and the exact coordination that makes each pairing valuable.",
                relationships = guide.synergies,
                actionLabel = "How to coordinate",
                onHeroSelected = onHeroSelected
            )
        }

        item {
            Text(
                "Matchups depend on map, player skill and balance updates. These guides explain a fight plan; they do not promise a win.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelationshipSection(
    title: String,
    description: String,
    relationships: List<HeroRelationship>,
    actionLabel: String,
    onHeroSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(description, style = MaterialTheme.typography.bodySmall)
        relationships.forEach { relationship ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onHeroSelected(relationship.hero.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeroPortrait(relationship.hero.id, relationship.hero.name, Modifier.size(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(relationship.hero.name, fontWeight = FontWeight.Black)
                            Text(
                                "${relationship.hero.role.displayName} · ${relationship.evidence.label}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text("VIEW GUIDE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(relationship.explanation)
                    HorizontalDivider()
                    Text(actionLabel.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    Text(relationship.howToPlay, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (relationships.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text(
                    "No sufficiently reliable relationships are available in this strategy-data version.",
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
