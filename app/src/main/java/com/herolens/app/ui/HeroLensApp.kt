package com.herolens.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.herolens.app.R
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.core.MapProfile
import com.herolens.app.core.MatchContext
import com.herolens.app.core.Reason
import com.herolens.app.core.Recommendation
import com.herolens.app.core.RecommendationEngine
import com.herolens.app.core.Role
import com.herolens.app.core.Trait
import com.herolens.app.data.AppStore
import com.herolens.app.data.InputPlatform
import com.herolens.app.data.PlayerState
import com.herolens.app.data.RankTier
import com.herolens.app.data.ScanHistoryEntry
import com.herolens.app.data.ScanMode
import com.herolens.app.data.ScannerSettings
import com.herolens.app.vision.ScoreboardLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class PickerTarget { ALLY, ENEMY }
private enum class MainTab(val label: String) { HISTORY("History"), SCAN("Scan"), SETTINGS("Settings") }

@Composable
fun HeroLensApp() {
    val context = LocalContext.current
    val store = remember { AppStore(context.applicationContext) }

    val initialPlayerState = remember { store.loadPlayerState() }
    var roleName by rememberSaveable { mutableStateOf(initialPlayerState.role.name) }
    var mapName by rememberSaveable { mutableStateOf(initialPlayerState.mapProfile.name) }
    var currentHeroId by rememberSaveable { mutableStateOf(initialPlayerState.currentHeroId) }
    var ultimateChargeText by rememberSaveable { mutableStateOf(initialPlayerState.ultimateCharge.toString()) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.SCAN.name) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var scanConfidence by rememberSaveable { mutableStateOf(0) }
    var settings by remember { mutableStateOf(store.loadSettings()) }

    val allyIds = remember { mutableStateListOf<String>() }
    val enemyIds = remember { mutableStateListOf<String>() }
    val preferences = remember { mutableStateMapOf<String, Int>().apply { putAll(initialPlayerState.heroPool) } }
    val history = remember {
        mutableStateListOf<ScanHistoryEntry>().apply { addAll(store.loadHistory()) }
    }

    LaunchedEffect(settings) { store.saveSettings(settings) }
    LaunchedEffect(roleName, mapName, currentHeroId, ultimateChargeText, preferences.toMap()) {
        store.savePlayerState(
            PlayerState(
                role = Role.valueOf(roleName),
                mapProfile = MapProfile.valueOf(mapName),
                currentHeroId = currentHeroId,
                ultimateCharge = ultimateChargeText.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                heroPool = preferences.toMap()
            )
        )
    }

    val role = Role.valueOf(roleName)
    val mapProfile = MapProfile.valueOf(mapName)
    val matchContext = MatchContext(
        role = role,
        mapProfile = mapProfile,
        allyIds = allyIds.toSet(),
        enemyIds = enemyIds.toSet(),
        preferences = preferences.toMap(),
        currentHeroId = currentHeroId,
        ultimateCharge = ultimateChargeText.toIntOrNull()?.coerceIn(0, 100) ?: 0,
        rank = settings.rank.name,
        inputPlatform = settings.inputPlatform.name
    )
    val recommendations = remember(matchContext) { RecommendationEngine.recommend(matchContext) }

    fun saveScanToHistory() {
        val best = recommendations.firstOrNull() ?: return
        val entry = ScanHistoryEntry(
            timestamp = System.currentTimeMillis(),
            role = role.name,
            currentHeroId = currentHeroId,
            bestHeroId = best.hero.id,
            fitScore = best.score,
            scanConfidence = scanConfidence,
            allyIds = allyIds.toList(),
            enemyIds = enemyIds.toList()
        )
        history.removeAll { existing ->
            existing.bestHeroId == entry.bestHeroId &&
                existing.enemyIds == entry.enemyIds &&
                entry.timestamp - existing.timestamp < 30_000
        }
        history.add(0, entry)
        while (history.size > 50) history.removeAt(history.lastIndex)
        store.saveHistory(history)
    }

    if (!settings.onboardingComplete) {
        OnboardingScreen(onFinish = { settings = settings.copy(onboardingComplete = true) })
        return
    }

    if (showScanner) {
        CameraScanScreen(
            autoScan = settings.autoScan,
            autoOpenResults = settings.autoOpenResults,
            showDetections = settings.showDetections,
            hapticFeedback = settings.hapticFeedback,
            defaultZoom = settings.defaultZoom,
            autoZoom = settings.autoZoom,
            scanMode = settings.scanMode,
            preferredLayout = settings.preferredLayout,
            onClose = { showScanner = false },
            onUseDetections = { detectedAllies, detectedEnemies, detectedCurrentHero, confidence ->
                allyIds.clear()
                allyIds.addAll(detectedAllies.take(5))
                enemyIds.clear()
                enemyIds.addAll(detectedEnemies.take(6))
                currentHeroId = detectedCurrentHero
                scanConfidence = confidence
                detectedCurrentHero?.let { heroId ->
                    HeroCatalog.byId[heroId]?.role?.let { roleName = it.name }
                }
                showScanner = false
                showResults = true
            }
        )
    } else if (showResults) {
        ResultsScreen(
            recommendations = recommendations,
            currentHeroId = currentHeroId,
            allyIds = allyIds,
            enemyIds = enemyIds,
            scanConfidence = scanConfidence,
            rank = settings.rank,
            ultimateCharge = matchContext.ultimateCharge,
            preferences = preferences,
            inputPlatform = settings.inputPlatform,
            onClose = {
                saveScanToHistory()
                showResults = false
            },
            onEdit = { showResults = false }
        )
    } else {
        val selectedTab = MainTab.valueOf(selectedTabName)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTabName = tab.name },
                            icon = {
                                Text(
                                    when (tab) {
                                        MainTab.HISTORY -> "◷"
                                        MainTab.SCAN -> "⌗"
                                        MainTab.SETTINGS -> "⚙"
                                    },
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    MainTab.SCAN -> ScanHomeScreen(
                        role = role,
                        onRoleChanged = {
                            roleName = it.name
                            currentHeroId = null
                        },
                        mapProfile = mapProfile,
                        onMapChanged = { mapName = it.name },
                        allyIds = allyIds,
                        enemyIds = enemyIds,
                        preferences = preferences,
                        currentHeroId = currentHeroId,
                        onCurrentHeroChanged = { currentHeroId = it },
                        ultimateChargeText = ultimateChargeText,
                        onUltimateChargeChanged = { value ->
                            ultimateChargeText = value.filter(Char::isDigit).take(3)
                        },
                        rank = settings.rank,
                        onOpenCamera = { showScanner = true },
                        onOpenPicker = { pickerTarget = it },
                        onAnalyze = {
                            scanConfidence = 0
                            showResults = true
                        }
                    )

                    MainTab.HISTORY -> HistoryScreen(
                        history = history,
                        onOpen = { entry ->
                            roleName = entry.role
                            currentHeroId = entry.currentHeroId
                            scanConfidence = entry.scanConfidence
                            allyIds.clear()
                            allyIds.addAll(entry.allyIds)
                            enemyIds.clear()
                            enemyIds.addAll(entry.enemyIds)
                            showResults = true
                        },
                        onClear = {
                            history.clear()
                            store.clearHistory()
                        }
                    )

                    MainTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { settings = it }
                    )
                }
            }
        }
    }

    pickerTarget?.let { target ->
        HeroPickerDialog(
            title = if (target == PickerTarget.ALLY) stringResource(R.string.allies) else stringResource(R.string.enemies),
            selectedIds = if (target == PickerTarget.ALLY) allyIds.toSet() else enemyIds.toSet(),
            maximum = if (target == PickerTarget.ALLY) 5 else 6,
            onToggle = { heroId ->
                val list = if (target == PickerTarget.ALLY) allyIds else enemyIds
                if (heroId in list) {
                    list.remove(heroId)
                } else if (list.size < if (target == PickerTarget.ALLY) 5 else 6) {
                    list.add(heroId)
                }
            },
            onClear = {
                if (target == PickerTarget.ALLY) allyIds.clear() else enemyIds.clear()
            },
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun ScanHomeScreen(
    role: Role,
    onRoleChanged: (Role) -> Unit,
    mapProfile: MapProfile,
    onMapChanged: (MapProfile) -> Unit,
    allyIds: List<String>,
    enemyIds: List<String>,
    preferences: MutableMap<String, Int>,
    currentHeroId: String?,
    onCurrentHeroChanged: (String?) -> Unit,
    ultimateChargeText: String,
    onUltimateChargeChanged: (String) -> Unit,
    rank: RankTier,
    onOpenCamera: () -> Unit,
    onOpenPicker: (PickerTarget) -> Unit,
    onAnalyze: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HeroLens", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("Fast scan · explainable picks", style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(rank.label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LIVE SCOREBOARD SCAN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text("Point the phone at the scoreboard. The native camera opens immediately and locks the lineup after several frames agree.")
                    Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("OPEN LIVE SCANNER", fontWeight = FontWeight.Black)
                    }
                    Text("Manual selection remains available below as a fallback.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.role))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Role.entries) { item ->
                    FilterChip(
                        selected = role == item,
                        onClick = { onRoleChanged(item) },
                        label = { Text(item.displayName) }
                    )
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.map_style))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MapProfile.entries) { item ->
                    FilterChip(
                        selected = mapProfile == item,
                        onClick = { onMapChanged(item) },
                        label = { Text(item.displayName) }
                    )
                }
            }
        }

        item {
            TeamSelectionCard(
                title = stringResource(R.string.enemies),
                selectedIds = enemyIds,
                maximum = 6,
                onOpen = { onOpenPicker(PickerTarget.ENEMY) }
            )
        }

        item {
            TeamSelectionCard(
                title = stringResource(R.string.allies),
                selectedIds = allyIds,
                maximum = 5,
                onOpen = { onOpenPicker(PickerTarget.ALLY) }
            )
        }

        item {
            SectionTitle(stringResource(R.string.hero_pool))
            Text(stringResource(R.string.cycle_hint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HeroCatalog.forRole(role), key = Hero::id) { hero ->
                    val level = preferences[hero.id] ?: 0
                    AssistChip(
                        onClick = {
                            val next = (level + 1) % 5
                            if (next == 0) preferences.remove(hero.id) else preferences[hero.id] = next
                        },
                        leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(30.dp)) },
                        label = {
                            Text(
                                text = hero.name + preferenceSuffix(level, Locale.getDefault().language == "ar"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.switch_context))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = currentHeroId == null,
                        onClick = { onCurrentHeroChanged(null) },
                        label = { Text(stringResource(R.string.not_selected)) }
                    )
                }
                items(HeroCatalog.forRole(role), key = Hero::id) { hero ->
                    FilterChip(
                        selected = currentHeroId == hero.id,
                        onClick = { onCurrentHeroChanged(hero.id) },
                        leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(26.dp)) },
                        label = { Text(hero.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ultimateChargeText,
                onValueChange = onUltimateChargeChanged,
                label = { Text(stringResource(R.string.ultimate_charge)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(210.dp)
            )
        }

        item {
            Button(
                onClick = onAnalyze,
                enabled = enemyIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(stringResource(R.string.analyze), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TeamSelectionCard(title: String, selectedIds: List<String>, maximum: Int, onOpen: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${selectedIds.size}/$maximum", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.choose_heroes)) }
            }
            if (selectedIds.isEmpty()) {
                Text(stringResource(R.string.none_selected), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedIds) { id ->
                        HeroCatalog.byId[id]?.let { hero ->
                            AssistChip(
                                onClick = onOpen,
                                leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(28.dp)) },
                                label = { Text(hero.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    recommendations: List<Recommendation>,
    currentHeroId: String?,
    allyIds: List<String>,
    enemyIds: List<String>,
    scanConfidence: Int,
    rank: RankTier,
    ultimateCharge: Int,
    preferences: Map<String, Int>,
    inputPlatform: InputPlatform,
    onClose: () -> Unit,
    onEdit: () -> Unit
) {
    var focusedHeroId by remember(recommendations) { mutableStateOf(recommendations.firstOrNull()?.hero?.id) }
    val focused = recommendations.firstOrNull { it.hero.id == focusedHeroId } ?: recommendations.firstOrNull()
    val currentCoverage = compositionCoverage(allyIds + listOfNotNull(currentHeroId))
    val suggestedCoverage = compositionCoverage(allyIds + listOfNotNull(focused?.hero?.id))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ANALYSIS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${rank.label} · ${inputPlatform.label} · ${if (scanConfidence > 0) "Scan $scanConfidence%" else "Manual lineup"}")
                }
                TextButton(onClick = onClose) { Text("CLOSE") }
            }
        }

        currentHeroId?.let { id ->
            item {
                HeroCatalog.byId[id]?.let { hero ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("PLAYING", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(12.dp))
                            HeroPortrait(hero.id, hero.name, Modifier.size(46.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(hero.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(hero.role.displayName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (focused != null) {
            item { BestPickCard(focused, pickBadge(focused, recommendations, preferences)) }
            item {
                SwitchCoachCard(
                    currentHeroId = currentHeroId,
                    ultimateCharge = ultimateCharge,
                    focused = focused,
                    recommendations = recommendations
                )
            }
            item { ScoreBreakdownCard(focused) }
            item { PlaybookCard(focused) }
        }

        if (recommendations.size > 1) {
            item {
                Text("COMPARE PICKS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                Text("Tap a hero to open the full counter and synergy explanation.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(recommendations, key = { _, item -> item.hero.id }) { index, recommendation ->
                        val selected = recommendation.hero.id == focused?.hero?.id
                        Card(
                            modifier = Modifier.width(160.dp).clickable { focusedHeroId = recommendation.hero.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                HeroPortrait(recommendation.hero.id, recommendation.hero.name, Modifier.size(72.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(recommendation.hero.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${recommendation.score}% fit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(pickBadge(recommendation, recommendations, preferences), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, maxLines = 1)
                                if (selected) Text("VIEWING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LINEUP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    TeamPortraitRow("Your team", allyIds + listOfNotNull(currentHeroId))
                    TeamPortraitRow("Enemy", enemyIds)
                    HorizontalDivider()
                    Text("Composition coverage", fontWeight = FontWeight.Bold)
                    CoverageRow("Current", currentCoverage)
                    CoverageRow("With ${focused?.hero?.name ?: "pick"}", suggestedCoverage)
                    val coverageDelta = suggestedCoverage - currentCoverage
                    Text(
                        when {
                            coverageDelta > 0 -> "Team coverage improves by $coverageDelta points."
                            coverageDelta < 0 -> "This pick trades ${-coverageDelta} coverage points for matchup value."
                            else -> "Team coverage stays balanced; the value comes from matchup and synergy."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Coverage measures role traits, not predicted win probability.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_setup))
            }
        }
    }
}

@Composable
private fun SwitchCoachCard(
    currentHeroId: String?,
    ultimateCharge: Int,
    focused: Recommendation,
    recommendations: List<Recommendation>
) {
    val current = currentHeroId?.let(HeroCatalog.byId::get)
    val currentRecommendation = recommendations.firstOrNull { it.hero.id == currentHeroId }
    val scoreGain = if (currentRecommendation != null) focused.score - currentRecommendation.score else null
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SWITCH COACH", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            val message = when {
                current == null -> "Choose your current hero and ultimate charge to get switch timing advice."
                focused.hero.id == current.id -> "Stay on ${current.name}. It is already the strongest fit among the compared picks."
                ultimateCharge >= 70 -> "You have $ultimateCharge% ultimate charge. Use it first when safe, then switch to ${focused.hero.name}; switch immediately only if the counter problem is deciding every fight."
                scoreGain != null && scoreGain <= 3 -> "The upgrade is small (+$scoreGain). Stay unless the enemy threat is directly deciding fights."
                scoreGain != null -> "${focused.hero.name} improves the fit by +$scoreGain points. Switch after a safe reset rather than during a lost fight."
                else -> "${focused.hero.name} is the stronger contextual pick. Switch after a safe reset."
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BestPickCard(recommendation: Recommendation, badge: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(badge, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroPortrait(recommendation.hero.id, recommendation.hero.name, Modifier.size(96.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(recommendation.hero.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(recommendation.hero.role.displayName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${recommendation.score}%", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("FIT SCORE", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("WHY THIS PICK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            recommendation.reasons.forEach { reason ->
                ReasonInsightCard(reason)
            }
            if (recommendation.reasons.isEmpty()) {
                Text(stringResource(R.string.balanced_fallback))
            }
        }
    }
}

@Composable
private fun ScoreBreakdownCard(recommendation: Recommendation) {
    val breakdown = recommendation.breakdown
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("WHY THE SCORE MOVED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text("Positive and negative context contributions behind the fit score.", style = MaterialTheme.typography.bodySmall)
            BreakdownRow("Enemy matchups", breakdown.matchup)
            BreakdownRow("Team synergy", breakdown.synergy)
            BreakdownRow("Map fit", breakdown.map)
            BreakdownRow("Your hero pool", breakdown.comfort)
            BreakdownRow("Composition needs", breakdown.composition)
            BreakdownRow("Rank and input", breakdown.rankAndInput)
            BreakdownRow("Switching cost", breakdown.switching)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(132.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (abs(value).coerceAtMost(20) / 20f) },
            modifier = Modifier.weight(1f).height(7.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(if (value > 0) "+$value" else value.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun PlaybookCard(recommendation: Recommendation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("FIRST-FIGHT PLAYBOOK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            recommendation.playTips.forEachIndexed { index, tip ->
                Row(verticalAlignment = Alignment.Top) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondary) {
                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(tip, modifier = Modifier.weight(1f))
                }
            }
            if (recommendation.playTips.isEmpty()) {
                Text("Play around cover, coordinate cooldowns and focus the same target as your team.")
            }
            recommendation.riskNote?.let { risk ->
                HorizontalDivider()
                Text("WATCH OUT", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                Text(risk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReasonInsightCard(reason: Reason) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                reasonTitle(reason),
                style = MaterialTheme.typography.labelLarge,
                color = when (reason) {
                    is Reason.Counters -> MaterialTheme.colorScheme.error
                    is Reason.WorksWith -> MaterialTheme.colorScheme.secondary
                    is Reason.SwitchCost -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.Black
            )
            Text(reasonDetail(reason), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TeamPortraitRow(title: String, heroIds: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(78.dp), style = MaterialTheme.typography.labelMedium)
        heroIds.distinct().take(6).forEach { id ->
            HeroCatalog.byId[id]?.let { hero ->
                HeroPortrait(hero.id, hero.name, Modifier.padding(end = 5.dp).size(38.dp))
            }
        }
    }
}

@Composable
private fun CoverageRow(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text("$value%", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryScreen(
    history: List<ScanHistoryEntry>,
    onOpen: (ScanHistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HISTORY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Recent scans are stored only on this device.")
                }
                if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("CLEAR") }
            }
        }

        if (history.isEmpty()) {
            item {
                Card {
                    Text("No scans yet. Open the Scan tab and scan a scoreboard.", modifier = Modifier.padding(18.dp))
                }
            }
        } else {
            items(history, key = { it.timestamp }) { entry ->
                val best = HeroCatalog.byId[entry.bestHeroId]
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (best != null) HeroPortrait(best.id, best.name, Modifier.size(54.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(best?.name ?: entry.bestHeroId, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(formatTimestamp(entry.timestamp), style = MaterialTheme.typography.bodySmall)
                            Text("${entry.role.lowercase().replaceFirstChar(Char::uppercase)} · Scan ${entry.scanConfidence}%")
                        }
                        Text("${entry.fitScore}%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: ScannerSettings, onSettingsChanged: (ScannerSettings) -> Unit) {
    val context = LocalContext.current
    val cameraGranted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("SETTINGS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Scanner behavior and recommendation context")
        }

        item {
            SettingsCard(
                title = "Camera permission",
                description = if (cameraGranted) "Granted. Live scanning can open immediately." else "Not granted. Android will ask when you open the scanner."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (cameraGranted) "GRANTED" else "ACTION NEEDED",
                        color = if (cameraGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }) { Text("APP SETTINGS") }
                }
            }
        }

        item {
            SettingsCard(title = "Competitive rank", description = "Saved with analysis and used by the rank-aware scoring profile.") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RankTier.entries) { rank ->
                        FilterChip(
                            selected = settings.rank == rank,
                            onClick = { onSettingsChanged(settings.copy(rank = rank)) },
                            label = { Text(rank.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Input platform", description = "Used as a small consistency adjustment, never as a replacement for matchup and comfort.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InputPlatform.entries.forEach { input ->
                        FilterChip(
                            selected = settings.inputPlatform == input,
                            onClick = { onSettingsChanged(settings.copy(inputPlatform = input)) },
                            label = { Text(input.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Scan accuracy", description = settings.scanMode.description) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanMode.entries) { mode ->
                        FilterChip(
                            selected = settings.scanMode == mode,
                            onClick = { onSettingsChanged(settings.copy(scanMode = mode)) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Scoreboard portrait side", description = "Auto checks both sides. Choose a side manually when your scoreboard layout is consistent for faster scanning.") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScoreboardLayout.entries) { layout ->
                        FilterChip(
                            selected = settings.preferredLayout == layout,
                            onClick = { onSettingsChanged(settings.copy(preferredLayout = layout)) },
                            label = { Text(layout.displayName) }
                        )
                    }
                }
            }
        }

        item {
            ToggleSetting(
                title = "Automatic scan",
                description = "Continuously analyze the newest camera frame and lock only after several frames agree.",
                checked = settings.autoScan,
                onCheckedChange = { onSettingsChanged(settings.copy(autoScan = it)) }
            )
        }

        item {
            ToggleSetting(
                title = "Open results automatically",
                description = "Jump to recommendations as soon as the lineup reaches the selected confidence mode.",
                checked = settings.autoOpenResults,
                onCheckedChange = { onSettingsChanged(settings.copy(autoOpenResults = it)) }
            )
        }

        item {
            ToggleSetting(
                title = "Show detections",
                description = "Display ally/enemy alignment boxes and recognition confidence.",
                checked = settings.showDetections,
                onCheckedChange = { onSettingsChanged(settings.copy(showDetections = it)) }
            )
        }

        item {
            ToggleSetting(
                title = "Haptic confirmation",
                description = "Vibrate when the lineup is stable and ready.",
                checked = settings.hapticFeedback,
                onCheckedChange = { onSettingsChanged(settings.copy(hapticFeedback = it)) }
            )
        }

        item {
            ToggleSetting(
                title = "Automatic framing",
                description = "Zoom toward the detected TV scoreboard while keeping manual pinch, +/− and FRAME controls available.",
                checked = settings.autoZoom,
                onCheckedChange = { onSettingsChanged(settings.copy(autoZoom = it)) }
            )
        }

        item {
            SettingsCard(title = "Default zoom", description = "Start the camera between 1.0× and 5.0×. Pinch zoom remains available during scanning.") {
                Text("${String.format(Locale.US, "%.1f", settings.defaultZoom)}×", fontWeight = FontWeight.Bold)
                Slider(
                    value = settings.defaultZoom,
                    onValueChange = { onSettingsChanged(settings.copy(defaultZoom = it)) },
                    valueRange = 1f..5f,
                    steps = 15
                )
            }
        }

        item {
            SettingsCard(title = "Data and model", description = "Versioned components make future patch and model updates replaceable without redesigning the app.") {
                Text("Hero data: ${HeroCatalog.DATA_VERSION}", fontWeight = FontWeight.Bold)
                Text("Recommendation weights: V6 Explainable", fontWeight = FontWeight.Bold)
                Text("Recognition: localized TV scoreboard · multi-crop portrait ensemble · multi-frame consensus · LiteRT model slot ready", fontWeight = FontWeight.Bold)
                Text("OTA endpoint is not configured in this MVP; updates are bundled with source releases.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy", fontWeight = FontWeight.Bold)
                    Text("Camera frames are analyzed in memory. V6 does not upload scan snapshots or require an account.")
                    Text("History remains on this phone and can be cleared from the History tab.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onSettingsChanged(settings.copy(onboardingComplete = false)) }) {
                        Text("SHOW INTRO AGAIN")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun HeroPickerDialog(
    title: String,
    selectedIds: Set<String>,
    maximum: Int,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        HeroCatalog.heroes.filter { it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(620.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${selectedIds.size}/$maximum", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = Hero::id) { hero ->
                        val selected = hero.id in selectedIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (selected || selectedIds.size < maximum) onToggle(hero.id)
                            },
                            leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(30.dp)) },
                            label = { Text("${hero.name} · ${hero.role.displayName}") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
}

private fun compositionCoverage(heroIds: List<String>): Int {
    val heroes = heroIds.distinct().mapNotNull(HeroCatalog.byId::get)
    if (heroes.isEmpty()) return 0
    val valuableTraits = setOf(
        Trait.SUSTAIN,
        Trait.PROTECTION,
        Trait.PEEL,
        Trait.MOBILITY,
        Trait.ANTI_AIR,
        Trait.ANTI_DIVE,
        Trait.SHIELD_BREAK,
        Trait.AREA_CONTROL,
        Trait.SPEED,
        Trait.LONG_RANGE,
        Trait.CLOSE_RANGE
    )
    val covered = heroes.flatMap { it.traits }.toSet().count { it in valuableTraits }
    return ((covered / valuableTraits.size.toFloat()) * 100).toInt().coerceIn(0, 100)
}

private fun pickBadge(
    recommendation: Recommendation,
    recommendations: List<Recommendation>,
    preferences: Map<String, Int>
): String {
    val index = recommendations.indexOfFirst { it.hero.id == recommendation.hero.id }
    val comfort = preferences[recommendation.hero.id] ?: 0
    return when {
        index == 0 -> "BEST OVERALL"
        comfort >= 3 -> "COMFORT PICK"
        Trait.SUSTAIN in recommendation.hero.traits || Trait.PROTECTION in recommendation.hero.traits || Trait.PEEL in recommendation.hero.traits -> "SAFER PICK"
        else -> "ALTERNATIVE ${index + 1}"
    }
}

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun preferenceSuffix(level: Int, arabic: Boolean): String = if (arabic) {
    when (level) {
        1 -> " · تجنب"
        2 -> " · أتعلّمه"
        3 -> " · متمكن"
        4 -> " · أساسي"
        else -> ""
    }
} else {
    when (level) {
        1 -> " · Avoid"
        2 -> " · Learning"
        3 -> " · Comfortable"
        4 -> " · Main"
        else -> ""
    }
}

private fun reasonTitle(reason: Reason): String {
    val arabic = Locale.getDefault().language == "ar"
    return when (reason) {
        is Reason.Counters -> if (arabic) "يواجه ${reason.enemyName}" else "COUNTERS ${reason.enemyName.uppercase()}"
        is Reason.WorksWith -> if (arabic) "ينسجم مع ${reason.allyName}" else "SYNERGY WITH ${reason.allyName.uppercase()}"
        is Reason.MapFit -> if (arabic) "مناسب للخريطة" else "MAP FIT"
        is Reason.Comfort -> if (arabic) "مستوى إتقانك" else "YOUR HERO POOL"
        is Reason.TeamNeed -> if (arabic) "حاجة الفريق" else "TEAM NEED"
        is Reason.SwitchCost -> if (arabic) "تنبيه التبديل" else "SWITCH WARNING"
        is Reason.RankFit -> if (arabic) "مناسب للرانك" else "RANK FIT"
        is Reason.InputFit -> if (arabic) "مناسب لطريقة اللعب" else "INPUT FIT"
        is Reason.ThreatFocus -> if (arabic) "التهديد الرئيسي" else "PRIMARY THREAT"
    }
}

private fun reasonDetail(reason: Reason): String {
    val arabic = Locale.getDefault().language == "ar"
    return when (reason) {
        is Reason.Counters -> if (arabic) reason.detailAr else reason.detail
        is Reason.WorksWith -> if (arabic) reason.detailAr else reason.detail
        is Reason.MapFit -> if (arabic) "قدراته مناسبة لطبيعة الخريطة: ${mapNameArabic(reason.mapName)}." else "Its range, mobility and space control fit the ${reason.mapName.lowercase()} map profile."
        is Reason.Comfort -> if (arabic) comfortArabic(reason.level) else comfortEnglish(reason.level)
        is Reason.TeamNeed -> if (arabic) "يعالج نقص الفريق في ${needArabic(reason.need)}." else "It fills the team's need for ${reason.need}."
        is Reason.SwitchCost -> if (arabic) "انتظر استخدام الألتميت إن أمكن؛ التبديل الآن يهدر ${reason.ultimateCharge}%." else "Consider using your ultimate first; switching now gives up ${reason.ultimateCharge}% charge."
        is Reason.RankFit -> if (arabic) "أسلوبه وأدواته مناسبة غالباً لفئة ${reason.rankName}." else "Its consistency and utility fit the ${reason.rankName} rank profile."
        is Reason.InputFit -> if (arabic) "أسلوبه ثابت ومناسب أكثر لإدخال ${reason.inputName}." else "Its consistency and control profile suit ${reason.inputName} input."
        is Reason.ThreatFocus -> if (arabic) "ركز على ${reason.enemyName}؛ مستوى التهديد ${reason.severity}/5." else "Prioritize ${reason.enemyName}; estimated threat level ${reason.severity}/5."
    }
}

private fun reasonText(reason: Reason): String = "${reasonTitle(reason)} — ${reasonDetail(reason)}"

private fun comfortEnglish(level: Int): String = when (level) {
    1 -> "Marked as an avoid pick"
    2 -> "You are learning this hero"
    3 -> "Inside your comfortable hero pool"
    4 -> "One of your main heroes"
    else -> "Personal comfort considered"
}

private fun comfortArabic(level: Int): String = when (level) {
    1 -> "تم تحديده كبطل يجب تجنبه"
    2 -> "أنت تتعلم هذا البطل"
    3 -> "ضمن الأبطال الذين تتقنهم"
    4 -> "أحد أبطالك الأساسيين"
    else -> "تم احتساب مستوى إتقانك"
}

private fun mapNameArabic(name: String): String = when (name) {
    "Mixed" -> "متنوعة"
    "Open sightlines" -> "مسافات مفتوحة"
    "Vertical" -> "عمودية"
    "Close quarters" -> "مساحات ضيقة"
    else -> name
}

private fun needArabic(need: String): String = when (need) {
    "peel against enemy dive" -> "حماية الخط الخلفي من اندفاع الخصم"
    "speed for the brawl composition" -> "سرعة لتشكيلة القتال القريب"
    "pressure into layered protection" -> "ضغط ضد الحماية المتعددة"
    else -> need
}
