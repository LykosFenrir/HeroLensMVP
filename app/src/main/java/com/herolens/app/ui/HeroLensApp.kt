package com.herolens.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.herolens.app.R
import com.herolens.app.BuildConfig
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.core.HeroSearch
import com.herolens.app.core.MapProfile
import com.herolens.app.core.MatchContext
import com.herolens.app.core.Reason
import com.herolens.app.core.Recommendation
import com.herolens.app.core.RecommendationEngine
import com.herolens.app.core.Role
import com.herolens.app.core.Trait
import com.herolens.app.data.AppStore
import com.herolens.app.data.DatasetCollector
import com.herolens.app.data.DisplayType
import com.herolens.app.data.GameModeProfile
import com.herolens.app.data.InputPlatform
import com.herolens.app.data.MatchStatsEntry
import com.herolens.app.data.MatchStatsStore
import com.herolens.app.data.MatchResult
import com.herolens.app.data.PlayerState
import com.herolens.app.data.RankTier
import com.herolens.app.data.ScanHistoryEntry
import com.herolens.app.data.ScanMode
import com.herolens.app.data.ScannerSettings
import com.herolens.app.data.TeamFormat
import com.herolens.app.data.aggregateMatchStats
import com.herolens.app.vision.ScoreboardLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private enum class PickerTarget { ALLY, ENEMY, BANNED }
private enum class MainTab(val label: String, val iconText: String) {
    HOME("Home", "H"),
    SCAN("Match", "+"),
    CHAMPIONS("Heroes", "C"),
    HISTORY("Stats", "P"),
    SETTINGS("More", "M")
}

@Composable
fun HeroLensApp() {
    val context = LocalContext.current
    val store = remember { AppStore(context.applicationContext) }
    val matchStatsStore = remember { MatchStatsStore(context.applicationContext) }

    val initialPlayerState = remember { store.loadPlayerState() }
    var roleName by rememberSaveable { mutableStateOf(initialPlayerState.role.name) }
    var allRoles by rememberSaveable { mutableStateOf(initialPlayerState.allRoles) }
    var mapName by rememberSaveable { mutableStateOf(initialPlayerState.mapProfile.name) }
    var currentHeroId by rememberSaveable { mutableStateOf(initialPlayerState.currentHeroId) }
    var ultimateChargeText by rememberSaveable { mutableStateOf(initialPlayerState.ultimateCharge.toString()) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var quickAutoScan by rememberSaveable { mutableStateOf(false) }
    var pictureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var statsPictureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingStatsCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    var selectedChampionId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var scanConfidence by rememberSaveable { mutableStateOf(0) }
    var settings by remember { mutableStateOf(store.loadSettings()) }
    var savedAllyHeroIds by rememberSaveable { mutableStateOf("") }
    var savedEnemyHeroIds by rememberSaveable { mutableStateOf("") }
    var savedBannedHeroIds by rememberSaveable { mutableStateOf("") }
    var resolvedTeamSize by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeDraftModeName by rememberSaveable { mutableStateOf(settings.gameModeProfile.name) }
    var activeTeamFormatName by rememberSaveable { mutableStateOf(settings.teamFormat.name) }
    var battleTag by rememberSaveable { mutableStateOf(matchStatsStore.loadBattleTag()) }

    val allyIds = remember {
        mutableStateListOf<String>().apply {
            addAll(savedAllyHeroIds.split(',').filter(String::isNotBlank))
        }
    }
    val enemyIds = remember {
        mutableStateListOf<String>().apply {
            addAll(savedEnemyHeroIds.split(',').filter(String::isNotBlank))
        }
    }
    val bannedHeroIds = remember {
        mutableStateListOf<String>().apply {
            addAll(savedBannedHeroIds.split(',').filter(String::isNotBlank))
        }
    }
    val preferences = remember { mutableStateMapOf<String, Int>().apply { putAll(initialPlayerState.heroPool) } }
    val history = remember {
        mutableStateListOf<ScanHistoryEntry>().apply { addAll(store.loadHistory()) }
    }
    val matchStats = remember {
        mutableStateListOf<MatchStatsEntry>().apply { addAll(matchStatsStore.loadMatches()) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pictureUri = uri
    }
    val statsGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) statsPictureUri = uri
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pictureUri = pendingCaptureUri
        pendingCaptureUri = null
    }
    val takeStatsPictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            statsPictureUri = pendingStatsCaptureUri
        } else {
            File(context.cacheDir, "post_match_capture").listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        }
        pendingStatsCaptureUri = null
    }
    val launchPictureCapture = {
        val directory = File(context.cacheDir, "picture_scan").apply { mkdirs() }
        val file = File(directory, "scoreboard_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        takePictureLauncher.launch(uri)
    }
    val pictureCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchPictureCapture() else pendingCaptureUri = null
    }
    val launchStatsPictureCapture = {
        val directory = File(context.cacheDir, "post_match_capture").apply { mkdirs() }
        val file = File(directory, "scoreboard_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingStatsCaptureUri = uri
        takeStatsPictureLauncher.launch(uri)
    }
    val statsCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchStatsPictureCapture() else pendingStatsCaptureUri = null
    }

    fun takeScoreboardPicture() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchPictureCapture()
        } else {
            pictureCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun takePostMatchPicture() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchStatsPictureCapture()
        } else {
            statsCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun closePostMatchPicture() {
        statsPictureUri = null
        pendingStatsCaptureUri = null
        File(context.cacheDir, "post_match_capture").listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    LaunchedEffect(settings) { store.saveSettings(settings) }
    LaunchedEffect(battleTag) { matchStatsStore.saveBattleTag(battleTag) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            File(context.cacheDir, "post_match_capture").listFiles()?.forEach { file ->
                if (file.isFile) file.delete()
            }
        }
    }
    LaunchedEffect(roleName, allRoles, mapName, currentHeroId, ultimateChargeText, preferences.toMap()) {
        store.savePlayerState(
            PlayerState(
                role = Role.valueOf(roleName),
                mapProfile = MapProfile.valueOf(mapName),
                currentHeroId = currentHeroId,
                ultimateCharge = ultimateChargeText.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                heroPool = preferences.toMap(),
                allRoles = allRoles
            )
        )
    }

    val role = Role.valueOf(roleName)
    val mapProfile = MapProfile.valueOf(mapName)
    val effectiveTeamSize = settings.gameModeProfile.fixedTeamSize ?: settings.teamFormat.teamSize
    val teamCapacity = effectiveTeamSize ?: resolvedTeamSize ?: 6
    val draftUnavailableHeroIds = if (settings.gameModeProfile.usesDraftAssistant) bannedHeroIds.toSet() else emptySet()
    val modeAvailableHeroIds = if (settings.gameModeProfile.usesStadiumRoster) {
        HeroCatalog.stadiumHeroIds
    } else {
        HeroCatalog.byId.keys
    }
    val allowedScanHeroIds = modeAvailableHeroIds - draftUnavailableHeroIds
    val validCurrentHeroId = currentHeroId?.takeIf { it in allowedScanHeroIds }
    val allyCapacity = (teamCapacity - 1).coerceAtLeast(0)
    val recommendationUnavailableHeroIds = draftUnavailableHeroIds + allyIds.filter { it in allowedScanHeroIds }
    val recommendableHeroIds = allowedScanHeroIds - recommendationUnavailableHeroIds
    val banMaximum = settings.gameModeProfile.unavailableHeroLimit

    LaunchedEffect(allyIds.toList()) {
        savedAllyHeroIds = allyIds.joinToString(",")
    }
    LaunchedEffect(enemyIds.toList()) {
        savedEnemyHeroIds = enemyIds.joinToString(",")
    }
    LaunchedEffect(bannedHeroIds.toList()) {
        savedBannedHeroIds = bannedHeroIds.joinToString(",")
    }
    LaunchedEffect(settings.gameModeProfile, settings.teamFormat) {
        if (activeDraftModeName != settings.gameModeProfile.name) {
            bannedHeroIds.clear()
            resolvedTeamSize = null
            activeDraftModeName = settings.gameModeProfile.name
        }
        if (activeTeamFormatName != settings.teamFormat.name) {
            resolvedTeamSize = null
            activeTeamFormatName = settings.teamFormat.name
        }
    }
    LaunchedEffect(settings.gameModeProfile, allRoles) {
        if (settings.gameModeProfile.usesStadiumRoster && allRoles) allRoles = false
    }
    LaunchedEffect(allowedScanHeroIds, currentHeroId, allyIds.toList(), enemyIds.toList(), teamCapacity) {
        if (currentHeroId != validCurrentHeroId) currentHeroId = validCurrentHeroId
        allyIds.removeAll { it !in allowedScanHeroIds || it == validCurrentHeroId }
        enemyIds.removeAll { it !in allowedScanHeroIds }
        val uniqueAllies = allyIds.distinct()
        if (uniqueAllies.size != allyIds.size) {
            allyIds.clear()
            allyIds.addAll(uniqueAllies)
        }
        val uniqueEnemies = enemyIds.distinct()
        if (uniqueEnemies.size != enemyIds.size) {
            enemyIds.clear()
            enemyIds.addAll(uniqueEnemies)
        }
        while (allyIds.size > allyCapacity) allyIds.removeAt(allyIds.lastIndex)
        while (enemyIds.size > teamCapacity) enemyIds.removeAt(enemyIds.lastIndex)
    }

    val matchContext = MatchContext(
        role = role,
        mapProfile = mapProfile,
        allyIds = allyIds.filter { it in allowedScanHeroIds && it != currentHeroId }.toSet(),
        enemyIds = enemyIds.filter { it in allowedScanHeroIds }.toSet(),
        preferences = preferences.toMap(),
        currentHeroId = validCurrentHeroId,
        ultimateCharge = ultimateChargeText.toIntOrNull()?.coerceIn(0, 100) ?: 0,
        rank = settings.rank.name,
        inputPlatform = settings.inputPlatform.name,
        allRoles = allRoles && !settings.gameModeProfile.usesStadiumRoster,
        unavailableHeroIds = recommendationUnavailableHeroIds,
        availableHeroIds = HeroCatalog.stadiumHeroIds.takeIf { settings.gameModeProfile.usesStadiumRoster }
    )
    val recommendations = remember(matchContext) { RecommendationEngine.recommend(matchContext) }

    fun saveScanToHistory() {
        val best = recommendations.firstOrNull() ?: return
        val entry = ScanHistoryEntry(
            timestamp = System.currentTimeMillis(),
            role = if (allRoles) "ALL_ROLES" else role.name,
            currentHeroId = currentHeroId,
            bestHeroId = best.hero.id,
            fitScore = best.score,
            scanConfidence = scanConfidence,
            allyIds = allyIds.toList(),
            enemyIds = enemyIds.toList(),
            gameMode = settings.gameModeProfile.name,
            bannedHeroIds = bannedHeroIds.toList(),
            stadiumRosterVersion = HeroCatalog.STADIUM_DATA_VERSION.takeIf { settings.gameModeProfile.usesStadiumRoster },
            teamSize = teamCapacity
        )
        history.removeAll { existing -> existing.isDuplicateOf(entry) }
        history.add(0, entry)
        while (history.size > 50) history.removeAt(history.lastIndex)
        store.saveHistory(history)
    }

    fun applyDetectedResults(
        detectedAllies: List<String>,
        detectedEnemies: List<String>,
        detectedCurrentHero: String?,
        confidence: Int,
        detectedTeamSize: Int
    ) {
        val importedTeamSize = settings.gameModeProfile.fixedTeamSize ?: detectedTeamSize.coerceIn(3, 6)
        resolvedTeamSize = importedTeamSize
        val importedCurrentHero = detectedCurrentHero?.takeIf { it in allowedScanHeroIds }
        val importedAllyCapacity = (importedTeamSize - 1).coerceAtLeast(0)
        allyIds.clear()
        allyIds.addAll(
            detectedAllies
                .filter { it in allowedScanHeroIds && it != importedCurrentHero }
                .distinct()
                .take(importedAllyCapacity)
        )
        enemyIds.clear()
        enemyIds.addAll(detectedEnemies.filter { it in allowedScanHeroIds }.distinct().take(importedTeamSize))
        currentHeroId = importedCurrentHero
        scanConfidence = confidence
        importedCurrentHero?.let { heroId ->
            HeroCatalog.byId[heroId]?.role?.let { roleName = it.name }
        }
        showScanner = false
        quickAutoScan = false
        pictureUri = null
        showResults = true
    }

    fun restoreHistoryEntry(entry: ScanHistoryEntry) {
        val restoredMode = runCatching { GameModeProfile.valueOf(entry.gameMode) }
            .getOrDefault(GameModeProfile.AUTO)
        val restoredModeHeroIds = if (restoredMode.usesStadiumRoster) {
            HeroCatalog.stadiumHeroIds
        } else {
            HeroCatalog.byId.keys
        }
        val restoredBans = entry.bannedHeroIds
            .filter { restoredMode.usesDraftAssistant && it in restoredModeHeroIds }
            .distinct()
            .take(restoredMode.unavailableHeroLimit)
        val restoredAllowedIds = restoredModeHeroIds - restoredBans.toSet()
        val restoredCurrentHero = entry.currentHeroId?.takeIf { it in restoredAllowedIds }
        val restoredTeamCapacity = restoredMode.fixedTeamSize ?: entry.teamSize
            ?: settings.teamFormat.teamSize ?: 6
        val restoredAllyCapacity = (restoredTeamCapacity - 1).coerceAtLeast(0)

        activeDraftModeName = restoredMode.name
        activeTeamFormatName = settings.teamFormat.name
        settings = settings.copy(gameModeProfile = restoredMode)
        resolvedTeamSize = restoredTeamCapacity.takeIf {
            restoredMode.fixedTeamSize == null && settings.teamFormat.teamSize == null
        }
        bannedHeroIds.clear()
        bannedHeroIds.addAll(restoredBans)
        if (entry.role == "ALL_ROLES") {
            allRoles = true
        } else {
            allRoles = false
            roleName = runCatching { Role.valueOf(entry.role).name }.getOrDefault(Role.DAMAGE.name)
        }
        currentHeroId = restoredCurrentHero
        scanConfidence = entry.scanConfidence
        allyIds.clear()
        allyIds.addAll(
            entry.allyIds
                .filter { it in restoredAllowedIds && it != restoredCurrentHero }
                .distinct()
                .take(restoredAllyCapacity)
        )
        enemyIds.clear()
        enemyIds.addAll(
            entry.enemyIds
                .filter { it in restoredAllowedIds }
                .distinct()
                .take(restoredTeamCapacity)
        )
        showResults = true
    }

    if (!settings.onboardingComplete) {
        OnboardingScreen(onFinish = { settings = settings.copy(onboardingComplete = true) })
        return
    }

    if (statsPictureUri != null) {
        PostMatchCaptureScreen(
            imageUri = requireNotNull(statsPictureUri),
            battleTag = battleTag,
            defaultMode = settings.gameModeProfile,
            expectedTeamSize = teamCapacity.takeIf { it == 5 || it == 6 },
            initialHeroId = validCurrentHeroId,
            onClose = ::closePostMatchPicture,
            onSave = { entry ->
                if (matchStatsStore.addMatch(entry)) {
                    matchStats.clear()
                    matchStats.addAll(matchStatsStore.loadMatches())
                }
                closePostMatchPicture()
                selectedTabName = MainTab.HISTORY.name
            }
        )
    } else if (showScanner) {
        CameraScanScreen(
            autoScan = if (quickAutoScan) true else settings.autoScan,
            autoOpenResults = settings.autoOpenResults && settings.gameModeProfile.allowsAutomaticImport,
            quickResponse = quickAutoScan,
            showDetections = settings.showDetections,
            hapticFeedback = settings.hapticFeedback,
            defaultZoom = settings.defaultZoom,
            autoZoom = settings.autoZoom,
            scanMode = settings.scanMode,
            preferredLayout = settings.preferredLayout,
            preferredTeamSize = effectiveTeamSize,
            collectTrainingData = settings.collectTrainingData,
            inputPlatform = settings.inputPlatform,
            displayType = settings.displayType,
            gameModeProfile = settings.gameModeProfile,
            allowedHeroIds = allowedScanHeroIds,
            onClose = {
                showScanner = false
                quickAutoScan = false
            },
            onUseDetections = ::applyDetectedResults
        )
    } else if (pictureUri != null) {
        PictureScanScreen(
            imageUri = requireNotNull(pictureUri),
            collectTrainingData = settings.collectTrainingData,
            inputPlatform = settings.inputPlatform,
            displayType = settings.displayType,
            gameModeProfile = settings.gameModeProfile,
            allowedHeroIds = allowedScanHeroIds,
            preferredTeamSize = effectiveTeamSize,
            onClose = { pictureUri = null },
            onUseDetections = ::applyDetectedResults
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
            gameModeProfile = settings.gameModeProfile,
            onClose = {
                saveScanToHistory()
                showResults = false
            },
            onEdit = { showResults = false }
        )
    } else {
        val selectedTab = runCatching { MainTab.valueOf(selectedTabName) }.getOrDefault(MainTab.HOME)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTabName = tab.name },
                            icon = {
                                Text(tab.iconText, style = MaterialTheme.typography.titleLarge)
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (selectedTab) {
                    MainTab.HOME -> {
                        val recentMatches = if (matchStats.isNotEmpty()) {
                            matchStats.take(3).map { entry ->
                                val hero = HeroCatalog.byId[entry.heroId]
                                CompanionRecentMatchSummary(
                                    id = "stats:${entry.timestamp}",
                                    heroId = hero?.id,
                                    heroName = hero?.name ?: entry.heroId,
                                    result = when (entry.result) {
                                        MatchResult.WIN -> CompanionMatchResult.WIN
                                        MatchResult.LOSS -> CompanionMatchResult.LOSS
                                        MatchResult.DRAW -> CompanionMatchResult.DRAW
                                        MatchResult.UNKNOWN -> CompanionMatchResult.REVIEWED
                                    },
                                    modeLabel = GameModeProfile.fromPersistedValue(entry.mode).label,
                                    mapLabel = entry.mapLabel,
                                    playedAtLabel = formatTimestamp(entry.timestamp),
                                    headlineMetric = "${entry.eliminations} / ${entry.assists} / ${entry.deaths}",
                                    coachingNote = "${entry.damage} damage · ${entry.healing} healing"
                                )
                            }
                        } else {
                            history.take(3).map { entry ->
                                val best = HeroCatalog.byId[entry.bestHeroId]
                                CompanionRecentMatchSummary(
                                    id = entry.timestamp.toString(),
                                    heroId = best?.id,
                                    heroName = best?.name ?: entry.bestHeroId,
                                    result = CompanionMatchResult.REVIEWED,
                                    modeLabel = GameModeProfile.fromPersistedValue(entry.gameMode).label,
                                    playedAtLabel = formatTimestamp(entry.timestamp),
                                    headlineMetric = "${entry.fitScore}% fit",
                                    coachingNote = "Saved coaching review"
                                )
                            }
                        }
                        val activeRecommendation = recommendations.firstOrNull()
                        val canOpenCoach = activeRecommendation != null &&
                            (enemyIds.isNotEmpty() || settings.gameModeProfile.usesDraftAssistant)
                        CompanionDashboardScreen(
                            recentMatches = recentMatches,
                            playerLabel = if (allRoles) "Flex player" else "${role.displayName} player",
                            rankLabel = settings.rank.label,
                            activeSession = activeRecommendation?.takeIf { canOpenCoach }?.let { recommendation ->
                                CompanionSessionSummary(
                                    modeLabel = settings.gameModeProfile.label,
                                    mapLabel = mapProfile.displayName,
                                    heroId = recommendation.hero.id,
                                    heroName = recommendation.hero.name,
                                    statusLabel = "COACHING PLAN READY",
                                    focus = recommendation.playTips.firstOrNull()
                                )
                            },
                            onDraftClick = { selectedTabName = MainTab.SCAN.name },
                            onLiveCoachClick = {
                                if (canOpenCoach) {
                                    scanConfidence = 0
                                    showResults = true
                                } else {
                                    selectedTabName = MainTab.SCAN.name
                                }
                            },
                            onPostMatchClick = { selectedTabName = MainTab.HISTORY.name },
                            onHeroesMetaClick = { selectedTabName = MainTab.CHAMPIONS.name },
                            onRecentMatchClick = { summary ->
                                if (summary.id.startsWith("stats:")) {
                                    selectedTabName = MainTab.HISTORY.name
                                } else {
                                    history.firstOrNull { it.timestamp.toString() == summary.id }
                                        ?.let(::restoreHistoryEntry)
                                }
                            },
                            onResumeSession = {
                                scanConfidence = 0
                                showResults = true
                            },
                            onViewAllMatches = { selectedTabName = MainTab.HISTORY.name }
                        )
                    }

                    MainTab.SCAN -> ScanHomeScreen(
                        role = role,
                        allRoles = allRoles,
                        onAllRolesChanged = { enabled ->
                            allRoles = enabled
                            if (!enabled && currentHeroId?.let { HeroCatalog.byId[it]?.role } != role) {
                                currentHeroId = null
                            }
                        },
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
                        gameModeProfile = settings.gameModeProfile,
                        onGameModeChanged = {
                            resolvedTeamSize = null
                            settings = settings.copy(gameModeProfile = it)
                        },
                        bannedHeroIds = bannedHeroIds,
                        allowedHeroIds = allowedScanHeroIds,
                        recommendableHeroIds = recommendableHeroIds,
                        allyMaximum = allyCapacity,
                        enemyMaximum = teamCapacity,
                        banMaximum = banMaximum,
                        onOpenAutoScan = {
                            quickAutoScan = true
                            showScanner = true
                        },
                        onOpenReviewScan = {
                            quickAutoScan = false
                            showScanner = true
                        },
                        onTakePicture = ::takeScoreboardPicture,
                        onChoosePicture = { galleryLauncher.launch("image/*") },
                        onStartManual = { pickerTarget = PickerTarget.ENEMY },
                        onOpenPicker = { pickerTarget = it },
                        onAnalyze = {
                            scanConfidence = 0
                            showResults = true
                        }
                    )

                    MainTab.HISTORY -> PerformanceScreen(
                        battleTag = battleTag,
                        onBattleTagChanged = { battleTag = it.take(96) },
                        matches = matchStats,
                        aggregate = matchStats.aggregateMatchStats(),
                        coachingHistory = history,
                        onCaptureScoreboard = ::takePostMatchPicture,
                        onChooseScoreboard = { statsGalleryLauncher.launch("image/*") },
                        onOpenCoachingReview = ::restoreHistoryEntry,
                        onClearMatchStats = {
                            matchStats.clear()
                            matchStatsStore.clear()
                        },
                        onClearCoachingHistory = {
                            history.clear()
                            store.clearHistory()
                        }
                    )

                    MainTab.CHAMPIONS -> ChampionsScreen(
                        selectedHeroId = selectedChampionId,
                        onHeroSelected = { selectedChampionId = it },
                        onBackToCatalog = { selectedChampionId = null }
                    )

                    MainTab.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { updated ->
                            if (
                                updated.gameModeProfile != settings.gameModeProfile ||
                                updated.teamFormat != settings.teamFormat
                            ) {
                                resolvedTeamSize = null
                            }
                            settings = updated
                        }
                    )
                }
            }
        }
    }

    pickerTarget?.let { target ->
        val targetList = when (target) {
            PickerTarget.ALLY -> allyIds
            PickerTarget.ENEMY -> enemyIds
            PickerTarget.BANNED -> bannedHeroIds
        }
        val maximum = when (target) {
            PickerTarget.ALLY -> allyCapacity
            PickerTarget.ENEMY -> teamCapacity
            PickerTarget.BANNED -> banMaximum
        }
        val pickerAllowedHeroIds = when (target) {
            PickerTarget.ALLY -> allowedScanHeroIds - listOfNotNull(validCurrentHeroId).toSet()
            PickerTarget.ENEMY -> allowedScanHeroIds
            PickerTarget.BANNED -> modeAvailableHeroIds
        }
        HeroPickerDialog(
            title = when (target) {
                PickerTarget.ALLY -> stringResource(R.string.allies)
                PickerTarget.ENEMY -> stringResource(R.string.enemies)
                PickerTarget.BANNED -> "Banned / unavailable heroes"
            },
            selectedIds = targetList.toSet(),
            maximum = maximum,
            allowedHeroIds = pickerAllowedHeroIds,
            onToggle = { heroId ->
                if (heroId in targetList) {
                    targetList.remove(heroId)
                } else if (targetList.size < maximum) {
                    targetList.add(heroId)
                }
            },
            onClear = targetList::clear,
            onDismiss = { pickerTarget = null }
        )
    }
}

@Composable
private fun ScanHomeScreen(
    role: Role,
    allRoles: Boolean,
    onAllRolesChanged: (Boolean) -> Unit,
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
    gameModeProfile: GameModeProfile,
    onGameModeChanged: (GameModeProfile) -> Unit,
    bannedHeroIds: List<String>,
    allowedHeroIds: Set<String>,
    recommendableHeroIds: Set<String>,
    allyMaximum: Int,
    enemyMaximum: Int,
    banMaximum: Int,
    onOpenAutoScan: () -> Unit,
    onOpenReviewScan: () -> Unit,
    onTakePicture: () -> Unit,
    onChoosePicture: () -> Unit,
    onStartManual: () -> Unit,
    onOpenPicker: (PickerTarget) -> Unit,
    onAnalyze: () -> Unit
) {
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val isStadiumDraft = gameModeProfile == GameModeProfile.STADIUM_DRAFT
    val roleFlexible = !gameModeProfile.usesStadiumRoster
    val effectiveAllRoles = allRoles && roleFlexible
    val poolHeroes = (if (effectiveAllRoles) HeroCatalog.heroes else HeroCatalog.forRole(role))
        .filter { it.id in allowedHeroIds }
    val selectableHeroes = poolHeroes.filter { it.id in recommendableHeroIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MATCH SETUP", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("Draft advice · matchup plan · live coach", style = MaterialTheme.typography.bodyMedium)
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
            SectionTitle("Match mode")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GameModeProfile.entries) { mode ->
                    FilterChip(
                        selected = gameModeProfile == mode,
                        onClick = { onGameModeChanged(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            Text(gameModeProfile.description, style = MaterialTheme.typography.bodySmall)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isStadiumDraft) "FINAL SCOREBOARD SCAN · EXPERIMENTAL" else "AUTO SCAN · FASTEST",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (isStadiumDraft) {
                            "Live camera scanning recognizes stacked scoreboards, not the side-by-side Draft grid yet. Use Picture Scan below for a reviewed partial Draft image."
                        } else {
                            "Aim at the scoreboard. HeroLens builds a short four-frame consensus and keeps partial or uncertain lineups in review."
                        }
                    )
                    Button(onClick = onOpenAutoScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(if (isStadiumDraft) "SCAN FINAL SCOREBOARD" else "START AUTO SCAN", fontWeight = FontWeight.Black)
                    }
                    OutlinedButton(onClick = onOpenReviewScan, modifier = Modifier.fillMaxWidth()) {
                        Text("OPEN FULL REVIEW SCAN", fontWeight = FontWeight.Bold)
                    }
                    Text("Full review scan honors the scan-burst settings below; Auto Scan always begins immediately.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PICTURE SCAN · MOST STABLE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(
                        if (isStadiumDraft) {
                            "Take or choose the current Draft screen. Revealed picks can be corrected and hidden slots stay unknown; a geometry miss opens ten manual review slots instead of failing."
                        } else {
                            "Take one clear picture or choose an existing screenshot. Review uncertain slots before using the result."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onTakePicture, modifier = Modifier.weight(1f)) { Text("TAKE PICTURE", fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = onChoosePicture, modifier = Modifier.weight(1f)) { Text("CHOOSE IMAGE", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MANUAL SELECTION · ALWAYS WORKS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text("Select enemy and ally heroes directly when you need an instant fallback without using the camera.")
                    OutlinedButton(onClick = onStartManual, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("SELECT HEROES MANUALLY", fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item {
            SectionTitle("Recommendation pool")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = !effectiveAllRoles,
                        onClick = { onAllRolesChanged(false) },
                        label = { Text("Selected role") }
                    )
                }
                item {
                    FilterChip(
                        selected = effectiveAllRoles,
                        onClick = { onAllRolesChanged(true) },
                        enabled = roleFlexible,
                        label = { Text("All roles / mixed") }
                    )
                }
            }
            Text(
                when {
                    !roleFlexible -> "Stadium uses a fixed 1 Tank / 2 Damage / 2 Support lineup. Choose your assigned role; the Tank is placed in the fifth draft slot."
                    allRoles -> "Use this for modes where roles are flexible or mixed. HeroLens will compare heroes from every role."
                    else -> "Use this when your current mode locks you to one role."
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (!effectiveAllRoles) {
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
                title = if (gameModeProfile == GameModeProfile.STADIUM_DRAFT) {
                    "Revealed enemy picks"
                } else {
                    stringResource(R.string.enemies)
                },
                selectedIds = enemyIds,
                maximum = enemyMaximum,
                onOpen = { onOpenPicker(PickerTarget.ENEMY) }
            )
        }

        item {
            TeamSelectionCard(
                title = if (gameModeProfile == GameModeProfile.STADIUM_DRAFT) {
                    "Revealed ally picks (not you)"
                } else {
                    stringResource(R.string.allies)
                },
                selectedIds = allyIds,
                maximum = allyMaximum,
                onOpen = { onOpenPicker(PickerTarget.ALLY) }
            )
        }

        if (gameModeProfile.usesDraftAssistant) {
            item {
                TeamSelectionCard(
                    title = if (gameModeProfile == GameModeProfile.STADIUM_DRAFT) {
                        "Stadium Draft unavailable heroes"
                    } else {
                        "Competitive Hero Bans"
                    },
                    selectedIds = bannedHeroIds,
                    maximum = banMaximum,
                    onOpen = { onOpenPicker(PickerTarget.BANNED) }
                )
                Text(
                    if (gameModeProfile == GameModeProfile.STADIUM_DRAFT) {
                        "Stadium Competitive uses blind simultaneous pair picks and reveal, assigns Tanks to the fifth slot, and permits mirror picks across teams. Add revealed picks after each round, mark unavailable heroes here, then analyze. Recommendations enforce the current ${HeroCatalog.stadiumHeroIds.size}-hero roster (${HeroCatalog.STADIUM_DATA_VERSION}). Final-lineup scanning is supported with review; draft-grid recognition is experimental."
                    } else {
                        "Core Competitive uses simultaneous ranked-choice Hero Ban voting, not team-pick drafting. Enter up to five final ban results so HeroLens never recommends an unavailable hero."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (gameModeProfile == GameModeProfile.STADIUM_DRAFT) {
                    Text(
                        "Draft progress: ${allyIds.size}/$allyMaximum other ally picks revealed · ${enemyIds.size}/$enemyMaximum enemy picks revealed. HeroLens always reserves your fifth team slot for the recommendation.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.hero_pool))
            Text(stringResource(R.string.cycle_hint), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(poolHeroes, key = Hero::id) { hero ->
                    val level = preferences[hero.id] ?: 0
                    AssistChip(
                        onClick = {
                            val next = (level + 1) % 5
                            if (next == 0) preferences.remove(hero.id) else preferences[hero.id] = next
                        },
                        leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(30.dp)) },
                        label = {
                            Text(
                                text = hero.name + preferenceSuffix(level, isArabic),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        item {
            SectionTitle(
                if (gameModeProfile.usesStadiumRoster) "Your draft candidate (optional)"
                else stringResource(R.string.switch_context)
            )
            if (gameModeProfile.usesStadiumRoster) {
                Text(
                    "Choose a hero you are considering so HeroLens can compare it with legal alternatives. Stadium heroes are locked for the match once the draft ends.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = currentHeroId == null,
                        onClick = { onCurrentHeroChanged(null) },
                        label = { Text(stringResource(R.string.not_selected)) }
                    )
                }
                items(selectableHeroes, key = Hero::id) { hero ->
                    FilterChip(
                        selected = currentHeroId == hero.id,
                        onClick = { onCurrentHeroChanged(hero.id) },
                        leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(26.dp)) },
                        label = { Text(hero.name) }
                    )
                }
            }
            if (!gameModeProfile.usesStadiumRoster) {
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
        }

        item {
            if (selectableHeroes.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "No legal recommendation remains for this role. Remove an unavailable hero, choose another role, or use All roles / mixed.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = onAnalyze,
                enabled = selectableHeroes.isNotEmpty() && (enemyIds.isNotEmpty() || gameModeProfile.usesDraftAssistant),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    when (gameModeProfile) {
                        GameModeProfile.COMPETITIVE -> "ANALYZE WITH HERO BANS"
                        GameModeProfile.STADIUM_DRAFT -> "ANALYZE STADIUM PICK"
                        else -> stringResource(R.string.analyze)
                    },
                    fontWeight = FontWeight.Black
                )
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
    gameModeProfile: GameModeProfile,
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
                    Text("LIVE COACH", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${rank.label} · ${inputPlatform.label} · ${if (scanConfidence > 0) "Scan $scanConfidence%" else "Manual lineup"}")
                }
                TextButton(onClick = onClose) { Text("SAVE & CLOSE") }
            }
        }

        currentHeroId?.let { id ->
            item {
                HeroCatalog.byId[id]?.let { hero ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (gameModeProfile.usesStadiumRoster) "DRAFT CANDIDATE" else "PLAYING",
                                style = MaterialTheme.typography.labelMedium
                            )
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
            if (gameModeProfile.usesStadiumRoster) {
                item { StadiumHeroLockCard() }
            } else {
                item {
                    SwitchCoachCard(
                        currentHeroId = currentHeroId,
                        ultimateCharge = ultimateCharge,
                        focused = focused,
                        recommendations = recommendations
                    )
                }
            }
            item { ScoreBreakdownCard(focused) }
            item { PlaybookCard(focused) }
        }

        if (focused == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("NO LEGAL PICK", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Every hero in the selected recommendation pool is already picked, banned, or unavailable in this mode. Return to the setup and adjust the draft state.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
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
private fun StadiumHeroLockCard() {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("STADIUM HERO LOCK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
            Text(
                "Use pick recommendations before locking your hero. After the draft, Stadium keeps that hero for the match, so use the matchup reasons and playbook as post-draft coaching—not as switch advice.",
                style = MaterialTheme.typography.bodyMedium
            )
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
                    Text("MATCH REVIEW", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Saved coaching sessions stay private on this device.")
                }
                if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("CLEAR") }
            }
        }

        if (history.isEmpty()) {
            item {
                Card {
                    Text("No saved sessions yet. Start a matchup from Home or Match.", modifier = Modifier.padding(18.dp))
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
                            val modeLabel = runCatching { GameModeProfile.valueOf(entry.gameMode).label }
                                .getOrDefault("Auto / any mode")
                            val sourceLabel = if (entry.scanConfidence > 0) "Scan ${entry.scanConfidence}%" else "Manual"
                            Text("$modeLabel · ${if (entry.role == "ALL_ROLES") "All roles" else entry.role.lowercase().replaceFirstChar(Char::uppercase)} · $sourceLabel")
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
    val datasetCollector = remember { DatasetCollector(context.applicationContext) }
    val settingsScope = rememberCoroutineScope()
    var datasetBusy by remember { mutableStateOf(false) }
    var datasetRevision by remember { mutableStateOf(0) }
    val datasetCount = remember(datasetRevision) { datasetCollector.sampleCount() }
    val datasetSizeMb = remember(datasetRevision) { datasetCollector.sizeBytes() / (1024f * 1024f) }
    val cameraGranted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("PROFILE & SETTINGS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Your competitive context, device and scanner preferences")
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
            SettingsCard(title = "Screen being scanned", description = "Saved with opt-in training samples and used to tune future TV and laptop models separately.") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DisplayType.entries) { type ->
                        FilterChip(
                            selected = settings.displayType == type,
                            onClick = { onSettingsChanged(settings.copy(displayType = type)) },
                            label = { Text(type.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(
                title = "Game mode tag & review policy",
                description = settings.gameModeProfile.description
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GameModeProfile.entries) { mode ->
                        FilterChip(
                            selected = settings.gameModeProfile == mode,
                            onClick = { onSettingsChanged(settings.copy(gameModeProfile = mode)) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(
                title = "Scoreboard team size",
                description = if (settings.gameModeProfile.fixedTeamSize != null) {
                    "Stadium modes are fixed to their official 5v5 team format."
                } else {
                    "Auto detects standard five-row and six-row scoreboards. Arcade/custom 3v3 and 4v4 are supported through an explicit size when automatic row counting is uncertain."
                }
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TeamFormat.entries) { format ->
                        FilterChip(
                            selected = if (settings.gameModeProfile.fixedTeamSize != null) {
                                format == TeamFormat.FIVE_V_FIVE
                            } else {
                                settings.teamFormat == format
                            },
                            onClick = { onSettingsChanged(settings.copy(teamFormat = format)) },
                            enabled = settings.gameModeProfile.fixedTeamSize == null,
                            label = { Text(format.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Scan burst", description = "${settings.scanMode.description} Captures ${settings.scanMode.burstFrames} analyzed frames per scan.") {
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
                title = "Auto-start scan burst",
                description = "Start the full review scan automatically after the scoreboard is centered. Quick Auto Scan always starts automatically.",
                checked = settings.autoScan,
                onCheckedChange = { onSettingsChanged(settings.copy(autoScan = it)) }
            )
        }

        item {
            ToggleSetting(
                title = "Skip review when perfect",
                description = "For explicit Unranked or Competitive profiles, open recommendations only when the complete lineup is high-confidence. Auto, Stadium, Arcade and Custom always keep review open.",
                checked = settings.autoOpenResults,
                onCheckedChange = { onSettingsChanged(settings.copy(autoOpenResults = it)) },
                enabled = settings.gameModeProfile.allowsAutomaticImport
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
            ToggleSetting(
                title = "Help train accurate detection",
                description = "After you review and correct a scan, save only the cropped scoreboard and hero cells locally. Nothing is uploaded automatically.",
                checked = settings.collectTrainingData,
                onCheckedChange = { onSettingsChanged(settings.copy(collectTrainingData = it)) }
            )
        }

        item {
            SettingsCard(
                title = "Local training samples",
                description = "$datasetCount reviewed scans · ${String.format(Locale.US, "%.1f", datasetSizeMb)} MB. Export them when you are ready to use them for model training."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = datasetCount > 0 && !datasetBusy,
                        onClick = {
                            settingsScope.launch {
                                datasetBusy = true
                                val share = withContext(Dispatchers.IO) { datasetCollector.createShareIntent() }
                                datasetBusy = false
                                share?.let { context.startActivity(Intent.createChooser(it, "Share HeroLens training samples")) }
                            }
                        }
                    ) { Text(if (datasetBusy) "PREPARING…" else "EXPORT ZIP") }
                    OutlinedButton(
                        enabled = datasetCount > 0,
                        onClick = {
                            datasetCollector.clear()
                            datasetRevision++
                        }
                    ) { Text("DELETE") }
                }
            }
        }

        item {
            SettingsCard(title = "Data and model", description = "Versioned components make future patch and model updates replaceable without redesigning the app.") {
                Text("App: ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                Text("Hero data: ${HeroCatalog.DATA_VERSION}", fontWeight = FontWeight.Bold)
                Text("Recommendation engine: V10 explainable coach + Champions guides", fontWeight = FontWeight.Bold)
                Text("Recognition: ONNX portrait classifier + scoreboard geometry detector + template fallback", fontWeight = FontWeight.Bold)
                Text("OTA endpoint is not configured in this MVP; updates are bundled with source releases.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy", fontWeight = FontWeight.Bold)
                    Text("Camera frames are analyzed in memory. Training samples are saved only when the opt-in setting is enabled and only after you review a scan.")
                    Text("No training sample is uploaded automatically. You control export and deletion from this screen.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onSettingsChanged(settings.copy(onboardingComplete = false)) }) {
                        Text("SHOW INTRO AGAIN")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    allowedHeroIds: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, allowedHeroIds) {
        HeroCatalog.heroes.filter {
            it.id in allowedHeroIds && HeroSearch.matches(it, query, null)
        }
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
