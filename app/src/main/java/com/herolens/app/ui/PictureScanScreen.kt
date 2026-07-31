package com.herolens.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.data.DatasetCollector
import com.herolens.app.data.DisplayType
import com.herolens.app.data.InputPlatform
import com.herolens.app.vision.FrameQualityEvaluator
import com.herolens.app.vision.HeroDetection
import com.herolens.app.vision.NormalizedRect
import com.herolens.app.vision.ScoreboardFrame
import com.herolens.app.vision.ScoreboardLayout
import com.herolens.app.vision.ScoreboardLocator
import com.herolens.app.vision.ScoreboardRegion
import com.herolens.app.vision.TeamSide
import com.herolens.app.vision.TemplateHeroDetector
import com.herolens.app.vision.toBitmap
import com.herolens.app.vision.toScoreboardFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun PictureScanScreen(
    imageUri: Uri,
    collectTrainingData: Boolean,
    inputPlatform: InputPlatform,
    displayType: DisplayType,
    preferredTeamSize: Int?,
    onClose: () -> Unit,
    onUseDetections: (allies: List<String>, enemies: List<String>, currentHeroId: String?, confidence: Int) -> Unit
) {
    val context = LocalContext.current
    val detector = remember { TemplateHeroDetector(context.applicationContext) }
    val collector = remember { DatasetCollector(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Loading picture…") }
    var error by remember { mutableStateOf<String?>(null) }
    var frame by remember { mutableStateOf<ScoreboardFrame?>(null) }
    var region by remember { mutableStateOf<ScoreboardRegion?>(null) }
    var layout by remember { mutableStateOf(ScoreboardLayout.AUTO) }
    var detections by remember { mutableStateOf<List<HeroDetection>>(emptyList()) }
    var teamSize by remember { mutableIntStateOf(5) }
    var correctionIndex by remember { mutableStateOf<Int?>(null) }
    var ownAllySlot by remember { mutableStateOf<Int?>(null) }
    var processing by remember { mutableStateOf(true) }

    BackHandler { onClose() }

    LaunchedEffect(imageUri) {
        processing = true
        error = null
        runCatching {
            val loaded = withContext(Dispatchers.IO) { loadPictureFrame(context, imageUri) }
            frame = loaded
            val quality = FrameQualityEvaluator.evaluate(loaded)
            status = "Finding scoreboard…"
            val search = withContext(Dispatchers.Default) { ScoreboardLocator.search(loaded) }
            val located = search.region ?: error(search.message)
            region = located
            status = "Preparing ${HeroCatalog.heroes.size} hero references…"
            detector.warmUp { progress -> status = progress }
            status = "Recognizing heroes…"
            val result = withContext(Dispatchers.Default) { detector.detectAuto(loaded, locatedRegion = located, preferredTeamSize = preferredTeamSize) }
            layout = result.layout
            teamSize = result.teamSize
            detections = result.result.detections
            val known = detections.count { it.heroId != null }
            status = buildString {
                append(if (known == teamSize * 2) "Picture scan complete" else "Partial picture scan")
                append(" · $known/${teamSize * 2}")
                append(if (detector.aiAvailable) " · neural AI" else " · template fallback")
                append(" · ${quality.hint.name.lowercase().replace('_', ' ')}")
            }
        }.onFailure { throwable ->
            error = throwable.message ?: "The picture could not be scanned"
            status = "Picture scan failed"
        }
        processing = false
    }

    fun useResults() {
        val alliesDetected = detections.filter { it.team == TeamSide.ALLY }.sortedBy { it.slot }
        val enemiesDetected = detections.filter { it.team == TeamSide.ENEMY }.sortedBy { it.slot }
        val currentHero = ownAllySlot?.let { slot -> alliesDetected.firstOrNull { it.slot == slot }?.heroId }
        val allies = alliesDetected
            .filterNot { ownAllySlot != null && it.slot == ownAllySlot }
            .mapNotNull { it.heroId }
            .distinct()
        val enemies = enemiesDetected.mapNotNull { it.heroId }.distinct()
        if (enemies.size < 3) {
            error = "Select at least three enemy heroes before using the result. Tap an empty slot to correct it."
            return
        }
        val confidence = detections.filter { it.heroId != null }
            .map { it.confidence }
            .average()
            .takeIf { !it.isNaN() }
            ?.times(100)
            ?.roundToInt()
            ?.coerceIn(0, 99) ?: 0
        val sourceFrame = frame
        val sourceRegion = region
        if (collectTrainingData && sourceFrame != null && sourceRegion != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    collector.saveReviewedSample(
                        frame = sourceFrame,
                        region = sourceRegion,
                        detections = detections,
                        layout = layout,
                        platform = inputPlatform,
                        displayType = displayType,
                        scanConfidence = confidence
                    )
                }
                onUseDetections(allies, enemies, currentHero, confidence)
            }
        } else {
            onUseDetections(allies, enemies, currentHero, confidence)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PICTURE SCAN", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text(status, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClose) { Text("CLOSE") }
                }
            }

            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    val previewRatio = frame?.let { (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.56f, 1.78f) } ?: (16f / 10f)
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(previewRatio).background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val bitmap = frame?.toBitmap()
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Selected scoreboard picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            PictureOverlay(
                                frame = frame,
                                region = region,
                                detections = detections,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (processing) CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (detections.isNotEmpty()) {
                item {
                    DetectionTeamCard(
                        title = "Allies",
                        team = TeamSide.ALLY,
                        detections = detections,
                        ownAllySlot = ownAllySlot,
                        onOwnSlot = { slot -> ownAllySlot = if (ownAllySlot == slot) null else slot },
                        onCorrect = { correctionIndex = detections.indexOf(it) }
                    )
                }
                item {
                    DetectionTeamCard(
                        title = "Enemies",
                        team = TeamSide.ENEMY,
                        detections = detections,
                        ownAllySlot = null,
                        onOwnSlot = {},
                        onCorrect = { correctionIndex = detections.indexOf(it) }
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("SCAN ANOTHER") }
                    Button(onClick = ::useResults, enabled = detections.isNotEmpty() && !processing, modifier = Modifier.weight(1f)) {
                        Text("USE RESULTS", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }

    correctionIndex?.let { index ->
        val current = detections.getOrNull(index)
        if (current != null) {
            PictureHeroPickerDialog(
                selectedId = current.heroId,
                onSelected = { heroId ->
                    detections = detections.toMutableList().also { list ->
                        list[index] = current.copy(heroId = heroId, confidence = 1f)
                    }
                    correctionIndex = null
                },
                onDismiss = { correctionIndex = null }
            )
        }
    }
}

@Composable
private fun PictureOverlay(
    frame: ScoreboardFrame?,
    region: ScoreboardRegion?,
    detections: List<HeroDetection>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val sourceFrame = frame ?: return@Canvas
        val sourceWidth = sourceFrame.width.toFloat()
        val sourceHeight = sourceFrame.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(size.width / sourceWidth, size.height / sourceHeight)
        val contentWidth = sourceWidth * scale
        val contentHeight = sourceHeight * scale
        val offsetX = (size.width - contentWidth) / 2f
        val offsetY = (size.height - contentHeight) / 2f
        fun drawRect(rect: NormalizedRect, color: Color, width: Float) {
            drawRoundRect(
                color = color,
                topLeft = Offset(offsetX + rect.left * contentWidth, offsetY + rect.top * contentHeight),
                size = Size(rect.width * contentWidth, rect.height * contentHeight),
                cornerRadius = CornerRadius(9f, 9f),
                style = Stroke(width = width)
            )
        }
        region?.let {
            drawRect(it.allyPanel, Color(0xFF4DE6FF), 4f)
            drawRect(it.enemyPanel, Color(0xFFFF5D7D), 4f)
        }
        detections.forEach { detection ->
            val bounds = detection.bounds ?: return@forEach
            val color = if (detection.team == TeamSide.ALLY) Color(0xFF4DE6FF) else Color(0xFFFF5D7D)
            drawRect(bounds, color.copy(alpha = if (detection.heroId == null) 0.55f else 1f), if (detection.heroId == null) 2f else 4f)
        }
    }
}

@Composable
private fun DetectionTeamCard(
    title: String,
    team: TeamSide,
    detections: List<HeroDetection>,
    ownAllySlot: Int?,
    onOwnSlot: (Int) -> Unit,
    onCorrect: (HeroDetection) -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            detections.filter { it.team == team }.sortedBy { it.slot }.forEach { detection ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onCorrect(detection) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${detection.slot + 1}", modifier = Modifier.size(30.dp), fontWeight = FontWeight.Bold)
                    val hero = detection.heroId?.let(HeroCatalog.byId::get)
                    HeroPortrait(hero?.id ?: "__unknown__", hero?.name ?: "Unknown", Modifier.size(44.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hero?.name ?: "Tap to select", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(detection.confidence * 100).roundToInt()}% confidence", style = MaterialTheme.typography.bodySmall)
                    }
                    if (team == TeamSide.ALLY) {
                        OutlinedButton(onClick = { onOwnSlot(detection.slot) }) {
                            Text(if (ownAllySlot == detection.slot) "MY ROW" else "ME")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PictureHeroPickerDialog(
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val heroes = remember(query) {
        HeroCatalog.heroes.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Correct hero", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search heroes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onSelected(null) }, modifier = Modifier.fillMaxWidth()) { Text("Unknown / empty") }
                LazyColumn(modifier = Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(heroes, key = Hero::id) { hero ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(hero.id) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroPortrait(hero.id, hero.name, Modifier.size(42.dp))
                            Spacer(Modifier.size(10.dp))
                            Text(hero.name, modifier = Modifier.weight(1f), fontWeight = if (hero.id == selectedId) FontWeight.Black else FontWeight.Medium)
                            Text(hero.role.displayName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun loadPictureFrame(context: Context, uri: Uri): ScoreboardFrame {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDimension = maxOf(info.size.width, info.size.height)
            if (maxDimension > 1920) decoder.setTargetSampleSize((maxDimension / 1920f).roundToInt().coerceAtLeast(1))
        }
    } else {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected picture" }
            BitmapFactory.decodeStream(input)
        }
    }
    val resized = resizeForAnalysis(bitmap, 1920)
    return resized.toScoreboardFrame()
}

private fun resizeForAnalysis(bitmap: Bitmap, maximum: Int): Bitmap {
    val largest = maxOf(bitmap.width, bitmap.height)
    if (largest <= maximum) return bitmap
    val scale = maximum / largest.toFloat()
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
}
