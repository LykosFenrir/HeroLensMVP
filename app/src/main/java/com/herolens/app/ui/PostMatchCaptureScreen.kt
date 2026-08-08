package com.herolens.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text as VisionText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.data.GameModeProfile
import com.herolens.app.data.MatchResult
import com.herolens.app.data.MatchStatsEntry
import com.herolens.app.data.MatchStatsSources
import com.herolens.app.data.MAX_STAT_VALUE
import com.herolens.app.postmatch.PostMatchParseWarning
import com.herolens.app.postmatch.PostMatchScoreboardParseResult
import com.herolens.app.postmatch.PostMatchScoreboardTextParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reviews one already-selected scoreboard image with bundled, on-device OCR.
 * Neither the image nor raw OCR text is copied into match history or uploaded.
 * A camera capture may exist briefly in app cache and the caller deletes it when
 * this review closes.
 */
@Composable
fun PostMatchCaptureScreen(
    imageUri: Uri,
    battleTag: String,
    defaultMode: GameModeProfile,
    expectedTeamSize: Int?,
    initialHeroId: String?,
    onClose: () -> Unit,
    onSave: (MatchStatsEntry) -> Unit
) {
    val context = LocalContext.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var ocrAttempt by remember(imageUri) { mutableIntStateOf(0) }
    var processing by remember(imageUri) { mutableStateOf(true) }
    var scanError by remember(imageUri) { mutableStateOf<String?>(null) }
    var parseResult by remember(imageUri) { mutableStateOf<PostMatchScoreboardParseResult?>(null) }

    var selectedResult by remember(imageUri) { mutableStateOf(MatchResult.UNKNOWN) }
    var selectedMode by remember(imageUri, defaultMode) { mutableStateOf(defaultMode) }
    var mapLabel by remember(imageUri) { mutableStateOf("") }
    var selectedHeroId by remember(imageUri, initialHeroId) {
        mutableStateOf(initialHeroId?.takeIf(HeroCatalog.byId::containsKey))
    }
    var showHeroPicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

    var eliminations by remember(imageUri) { mutableStateOf("") }
    var assists by remember(imageUri) { mutableStateOf("") }
    var deaths by remember(imageUri) { mutableStateOf("") }
    var damage by remember(imageUri) { mutableStateOf("") }
    var healing by remember(imageUri) { mutableStateOf("") }
    var mitigation by remember(imageUri) { mutableStateOf("") }

    BackHandler(onBack = onClose)

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    LaunchedEffect(imageUri, battleTag, expectedTeamSize, ocrAttempt) {
        processing = true
        scanError = null
        try {
            val inputImage = withContext(Dispatchers.IO) {
                InputImage.fromFilePath(context, imageUri)
            }
            val recognized = recognizer.processAwait(inputImage)
            val textBlocks = recognized.textBlocks.map { it.text }
                .ifEmpty { listOf(recognized.text) }
            val alias = battleTag.trim().takeIf(String::isNotEmpty)
            val parsed = withContext(Dispatchers.Default) {
                PostMatchScoreboardTextParser.parseUserRow(
                    textBlocks = textBlocks,
                    playerAliases = listOfNotNull(alias),
                    expectedTeamSize = expectedTeamSize
                )
            }
            parseResult = parsed
            parsed.draft?.let { draft ->
                if (eliminations.isBlank()) eliminations = draft.eliminations?.toString().orEmpty()
                if (assists.isBlank()) assists = draft.assists?.toString().orEmpty()
                if (deaths.isBlank()) deaths = draft.deaths?.toString().orEmpty()
                if (damage.isBlank()) damage = draft.damage?.toString().orEmpty()
                if (healing.isBlank()) healing = draft.healing?.toString().orEmpty()
                if (mitigation.isBlank()) mitigation = draft.mitigation?.toString().orEmpty()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            parseResult = null
            scanError = "HeroLens could not read this image. Check image access and try a clearer scoreboard screenshot."
        } finally {
            processing = false
        }
    }

    val reviewedStats = remember(eliminations, assists, deaths, damage, healing, mitigation) {
        ReviewedStats.parse(
            eliminations = eliminations,
            assists = assists,
            deaths = deaths,
            damage = damage,
            healing = healing,
            mitigation = mitigation
        )
    }
    val saveProblem = when {
        selectedResult == MatchResult.UNKNOWN -> "Select the match result."
        selectedHeroId == null -> "Select the hero you played."
        reviewedStats == null -> "Review all six statistics. Use non-negative whole numbers."
        else -> null
    }

    if (showHeroPicker) {
        HeroPickerDialog(
            selectedHeroId = selectedHeroId,
            onSelected = { heroId ->
                selectedHeroId = heroId
                showHeroPicker = false
            },
            onDismiss = { showHeroPicker = false }
        )
    }
    if (showModePicker) {
        ModePickerDialog(
            selectedMode = selectedMode,
            onSelected = { mode ->
                selectedMode = mode
                showModePicker = false
            },
            onDismiss = { showModePicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "POST-MATCH CAPTURE",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        battleTag.trim().takeIf(String::isNotEmpty)?.let { "Private match for $it" }
                            ?: "Private local match",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onClose) { Text("CLOSE") }
            }
        }

        item {
            OcrReviewStatus(
                processing = processing,
                error = scanError,
                result = parseResult,
                onRetry = { ocrAttempt += 1 }
            )
        }

        item {
            ReviewSection(title = "MATCH") {
                Text(
                    "Result",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(MatchResult.WIN, MatchResult.LOSS, MatchResult.DRAW).forEach { result ->
                        FilterChip(
                            selected = selectedResult == result,
                            onClick = { selectedResult = result },
                            label = { Text(result.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showModePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("MODE", style = MaterialTheme.typography.labelSmall)
                        Text(selectedMode.label, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = mapLabel,
                    onValueChange = { mapLabel = it.take(MAX_LABEL_INPUT) },
                    label = { Text("Map (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val hero = selectedHeroId?.let(HeroCatalog.byId::get)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHeroPicker = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hero != null) {
                            HeroPortrait(hero.id, hero.name, Modifier.size(54.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("HERO PLAYED", style = MaterialTheme.typography.labelSmall)
                            Text(
                                hero?.name ?: "Choose hero",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text("CHANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item {
            ReviewSection(title = "SCOREBOARD STATISTICS") {
                StatFieldRow(
                    firstLabel = "Eliminations",
                    firstValue = eliminations,
                    onFirstChanged = { eliminations = sanitizeStatInput(it) },
                    secondLabel = "Assists",
                    secondValue = assists,
                    onSecondChanged = { assists = sanitizeStatInput(it) }
                )
                StatFieldRow(
                    firstLabel = "Deaths",
                    firstValue = deaths,
                    onFirstChanged = { deaths = sanitizeStatInput(it) },
                    secondLabel = "Damage",
                    secondValue = damage,
                    onSecondChanged = { damage = sanitizeStatInput(it) }
                )
                StatFieldRow(
                    firstLabel = "Healing",
                    firstValue = healing,
                    onFirstChanged = { healing = sanitizeStatInput(it) },
                    secondLabel = "Mitigation",
                    secondValue = mitigation,
                    onSecondChanged = { mitigation = sanitizeStatInput(it) }
                )
            }
        }

        item {
            saveProblem?.let { problem ->
                Text(
                    problem,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                enabled = saveProblem == null && !processing,
                onClick = {
                    val stats = reviewedStats ?: return@Button
                    val heroId = selectedHeroId ?: return@Button
                    val entry = MatchStatsEntry(
                        timestamp = System.currentTimeMillis(),
                        battleTag = battleTag.trim().takeIf(String::isNotEmpty),
                        mode = selectedMode.name,
                        mapLabel = mapLabel,
                        heroId = heroId,
                        result = selectedResult,
                        eliminations = stats.eliminations,
                        assists = stats.assists,
                        deaths = stats.deaths,
                        damage = stats.damage,
                        healing = stats.healing,
                        mitigation = stats.mitigation,
                        source = MatchStatsSources.SCOREBOARD_SCAN,
                        confidence = parseResult?.confidence ?: 0
                    ).normalizedOrNull() ?: return@Button
                    onSave(entry)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SAVE PRIVATE MATCH", fontWeight = FontWeight.Black)
            }
            Text(
                "OCR runs on this device. Match history saves only the reviewed fields above—not the image or raw OCR text. Temporary camera captures are deleted when review closes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun OcrReviewStatus(
    processing: Boolean,
    error: String?,
    result: PostMatchScoreboardParseResult?,
    onRetry: () -> Unit
) {
    val containerColor = when {
        error != null -> MaterialTheme.colorScheme.errorContainer
        result != null && result.confidence < 65 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                processing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Reading scoreboard on device", fontWeight = FontWeight.Black)
                            Text("Finding your player row and six statistics…")
                        }
                    }
                }
                error != null -> {
                    Text("OCR COULD NOT FINISH", fontWeight = FontWeight.Black)
                    Text(error)
                    OutlinedButton(onClick = onRetry) { Text("TRY AGAIN") }
                }
                result != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (result.draft != null) "OCR READY FOR REVIEW" else "MANUAL REVIEW NEEDED",
                                fontWeight = FontWeight.Black
                            )
                            val detectedSize = result.detectedTeamSize?.let { " · ${it}v$it" }.orEmpty()
                            Text("${result.confidence}% confidence$detectedSize · ${result.candidateRowCount} candidate rows")
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "${result.confidence}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (result.warnings.isEmpty()) {
                        Text("No parser warnings. Verify the values before saving.")
                    } else {
                        HorizontalDivider()
                        result.warnings.forEach { warning ->
                            Text("• ${warning.reviewMessage()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                else -> Text("Preparing OCR…")
            }
        }
    }
}

@Composable
private fun ReviewSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun StatFieldRow(
    firstLabel: String,
    firstValue: String,
    onFirstChanged: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChanged: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatField(
            label = firstLabel,
            value = firstValue,
            onValueChanged = onFirstChanged,
            modifier = Modifier.weight(1f)
        )
        StatField(
            label = secondLabel,
            value = secondValue,
            onValueChanged = onSecondChanged,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = value.isNotBlank() && parseReviewedStat(value) == null,
        modifier = modifier
    )
}

@Composable
private fun HeroPickerDialog(
    selectedHeroId: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val heroes = remember(query) {
        HeroCatalog.heroes.filter { hero ->
            query.isBlank() ||
                hero.name.contains(query, ignoreCase = true) ||
                hero.id.contains(query, ignoreCase = true)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SELECT HERO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_LABEL_INPUT) },
                    label = { Text("Search heroes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(heroes, key = Hero::id) { hero ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(hero.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroPortrait(hero.id, hero.name, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(hero.name, fontWeight = FontWeight.Bold)
                                Text(hero.role.displayName, style = MaterialTheme.typography.bodySmall)
                            }
                            if (hero.id == selectedHeroId) {
                                Text("SELECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CANCEL")
                }
            }
        }
    }
}

@Composable
private fun ModePickerDialog(
    selectedMode: GameModeProfile,
    onSelected: (GameModeProfile) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SELECT MODE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                    items(GameModeProfile.entries, key = GameModeProfile::name) { mode ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(mode) }
                                .padding(vertical = 11.dp)
                        ) {
                            Text(mode.label, fontWeight = FontWeight.Bold)
                            if (mode == selectedMode) {
                                Text(
                                    "CURRENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("CANCEL")
                }
            }
        }
    }
}

private data class ReviewedStats(
    val eliminations: Int,
    val assists: Int,
    val deaths: Int,
    val damage: Int,
    val healing: Int,
    val mitigation: Int
) {
    companion object {
        fun parse(
            eliminations: String,
            assists: String,
            deaths: String,
            damage: String,
            healing: String,
            mitigation: String
        ): ReviewedStats? = ReviewedStats(
            eliminations = parseReviewedStat(eliminations) ?: return null,
            assists = parseReviewedStat(assists) ?: return null,
            deaths = parseReviewedStat(deaths) ?: return null,
            damage = parseReviewedStat(damage) ?: return null,
            healing = parseReviewedStat(healing) ?: return null,
            mitigation = parseReviewedStat(mitigation) ?: return null
        )
    }
}

private fun sanitizeStatInput(value: String): String = value
    .filter { it.isDigit() || it == ',' }
    .take(MAX_STAT_INPUT)

private fun parseReviewedStat(value: String): Int? = value
    .trim()
    .replace(",", "")
    .takeIf(String::isNotEmpty)
    ?.toLongOrNull()
    ?.takeIf { it in 0L..MAX_STAT_VALUE.toLong() }
    ?.toInt()

private fun PostMatchParseWarning.reviewMessage(): String = when (this) {
    PostMatchParseWarning.POSITIONAL_COLUMNS_ASSUMED ->
        "Column labels were unclear; values use standard E / A / D / DMG / H / MIT order."
    PostMatchParseWarning.OCR_ZERO_CORRECTED ->
        "At least one letter O was interpreted as the number zero."
    PostMatchParseWarning.MISSING_FIELDS ->
        "One or more statistics could not be read. Complete them manually."
    PostMatchParseWarning.PLAYER_MARKER_NOT_FOUND ->
        "Your BattleTag or YOU marker was not found; the only plausible row was used."
    PostMatchParseWarning.AMBIGUOUS_PLAYER_ROW ->
        "Multiple player rows matched. Enter your statistics manually."
    PostMatchParseWarning.PLAYER_ROW_NOT_FOUND ->
        "No complete player row was found. Enter your statistics manually."
    PostMatchParseWarning.INVALID_EXPECTED_TEAM_SIZE ->
        "The expected team size was invalid; only 5v5 and 6v6 are recognized here."
    PostMatchParseWarning.EXPECTED_TEAM_SIZE_MISMATCH ->
        "The scoreboard format differs from the expected team size. Verify this match."
}

private suspend fun TextRecognizer.processAwait(image: InputImage): VisionText =
    suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { text ->
                if (continuation.isActive) continuation.resume(text)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCanceledListener {
                continuation.cancel()
            }
    }

private const val MAX_LABEL_INPUT = 96
private const val MAX_STAT_INPUT = 12
