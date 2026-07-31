package com.herolens.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.herolens.app.R
import com.herolens.app.core.Hero
import com.herolens.app.core.HeroCatalog
import com.herolens.app.vision.FrameQualityEvaluator
import com.herolens.app.vision.HeroDetection
import com.herolens.app.vision.LiveScanStabilizer
import com.herolens.app.vision.QualityHint
import com.herolens.app.vision.ScoreboardLayout
import com.herolens.app.vision.ScoreboardSlots
import com.herolens.app.vision.TeamSide
import com.herolens.app.vision.TemplateHeroDetector
import com.herolens.app.vision.toScoreboardFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val ANALYSIS_INTERVAL_MS = 280L

@Composable
fun CameraScanScreen(
    autoScan: Boolean,
    showDetections: Boolean,
    hapticFeedback: Boolean,
    defaultZoom: Float,
    onClose: () -> Unit,
    onUseDetections: (
        allies: List<String>,
        enemies: List<String>,
        currentHeroId: String?,
        confidence: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val detector = remember { TemplateHeroDetector(context) }
    val stabilizer = remember { LiveScanStabilizer() }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val busy = remember { AtomicBoolean(false) }
    val liveEnabledRef = remember { AtomicBoolean(true) }
    val singleScanRequested = remember { AtomicBoolean(false) }
    val lastAnalysisAt = remember { AtomicLong(0L) }
    val cameraRef = remember { AtomicReference<Camera?>(null) }

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var liveEnabled by remember { mutableStateOf(true) }
    var templatesReady by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(context.getString(R.string.preparing_live_scan)) }
    var warning by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var detections by remember { mutableStateOf<List<HeroDetection>>(emptyList()) }
    var resolvedLayout by remember { mutableStateOf(ScoreboardLayout.AUTO) }
    var stableSlots by remember { mutableIntStateOf(0) }
    var framesObserved by remember { mutableIntStateOf(0) }
    var readyToImport by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    var correctionIndex by remember { mutableStateOf<Int?>(null) }
    var ownAllySlot by remember { mutableStateOf<Int?>(null) }
    var zoomRatio by remember { mutableStateOf(defaultZoom.coerceIn(1f, 3f)) }
    var hapticSent by remember { mutableStateOf(false) }

    fun averageConfidence(): Int {
        val accepted = detections.filter { it.heroId != null }
        if (accepted.isEmpty()) return 0
        return (accepted.map { it.confidence }.average() * 100).roundToInt().coerceIn(0, 99)
    }

    fun importStableTeams() {
        if (imported || !readyToImport) return
        val allyDetections = detections.filter { it.team == TeamSide.ALLY }.sortedBy { it.slot }
        val currentHero = ownAllySlot?.let { selectedSlot ->
            allyDetections.firstOrNull { it.slot == selectedSlot }?.heroId
        }
        val allies = allyDetections
            .filterNot { ownAllySlot != null && it.slot == ownAllySlot }
            .mapNotNull { it.heroId }
            .distinct()
            .take(4)
        val enemies = detections.filter { it.team == TeamSide.ENEMY }
            .sortedBy { it.slot }
            .mapNotNull { it.heroId }
            .distinct()
            .take(5)
        if (enemies.size == 5 && allies.size >= 4) {
            imported = true
            onUseDetections(allies, enemies, currentHero, averageConfidence())
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        runCatching {
            val count = detector.warmUp { progress ->
                scope.launch { status = progress }
            }
            templatesReady = count > 0
            if (templatesReady) {
                status = context.getString(R.string.open_scoreboard_live)
            } else {
                warning = "Recognition templates could not be prepared. The camera remains available, but automatic detection needs an internet connection on first use."
                status = context.getString(R.string.templates_unavailable)
            }
        }.onFailure {
            warning = it.message ?: context.getString(R.string.templates_unavailable)
        }
    }

    LaunchedEffect(liveEnabled) {
        liveEnabledRef.set(liveEnabled)
        if (liveEnabled) {
            stabilizer.reset()
            stableSlots = 0
            framesObserved = 0
            readyToImport = false
            imported = false
            hapticSent = false
            status = if (templatesReady) context.getString(R.string.open_scoreboard_live) else context.getString(R.string.preparing_live_scan)
        }
    }

    LaunchedEffect(readyToImport) {
        if (readyToImport && !hapticSent) {
            hapticSent = true
            if (hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (readyToImport && autoScan && !imported) {
            status = context.getString(R.string.lineup_locked)
            delay(650)
            importStableTeams()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            liveEnabledRef.set(false)
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdownNow()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("LIVE SCAN", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(status, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onClose) { Text("CLOSE") }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .aspectRatio(16f / 9f)
                    .background(Color.Black, RoundedCornerShape(22.dp))
            ) {
                if (permissionGranted) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PreviewView(viewContext).also { view ->
                                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                val future = ProcessCameraProvider.getInstance(viewContext)
                                future.addListener({
                                    runCatching {
                                        val provider = future.get()
                                        val preview = Preview.Builder()
                                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                            .setTargetRotation(view.display.rotation)
                                            .build()
                                            .also { it.setSurfaceProvider(view.surfaceProvider) }
                                        val analysis = ImageAnalysis.Builder()
                                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                            .setTargetRotation(view.display.rotation)
                                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        analysis.setAnalyzer(analyzerExecutor) { image ->
                                            val now = System.currentTimeMillis()
                                            val manualRequest = !autoScan && singleScanRequested.compareAndSet(true, false)
                                            val allowed = autoScan || manualRequest
                                            if (!templatesReady || !liveEnabledRef.get() || !allowed || busy.get() || now - lastAnalysisAt.get() < ANALYSIS_INTERVAL_MS) {
                                                image.close()
                                                return@setAnalyzer
                                            }
                                            if (!busy.compareAndSet(false, true)) {
                                                image.close()
                                                return@setAnalyzer
                                            }
                                            lastAnalysisAt.set(now)
                                            val frame = try {
                                                image.toScoreboardFrame()
                                            } finally {
                                                image.close()
                                            }
                                            if (frame == null) {
                                                busy.set(false)
                                                return@setAnalyzer
                                            }

                                            scope.launch(Dispatchers.Default) {
                                                try {
                                                    val quality = FrameQualityEvaluator.evaluate(frame)
                                                    if (!quality.usable) {
                                                        withContext(Dispatchers.Main) {
                                                            status = when (quality.hint) {
                                                                QualityHint.TOO_DARK -> context.getString(R.string.more_light)
                                                                QualityHint.TOO_BRIGHT -> context.getString(R.string.reduce_glare)
                                                                QualityHint.BLURRY -> context.getString(R.string.hold_steady)
                                                                QualityHint.GOOD -> context.getString(R.string.open_scoreboard_live)
                                                            }
                                                        }
                                                        return@launch
                                                    }

                                                    val automatic = detector.detectAuto(frame)
                                                    val snapshot = stabilizer.add(automatic)
                                                    withContext(Dispatchers.Main) {
                                                        detections = snapshot.detections
                                                        resolvedLayout = snapshot.layout
                                                        stableSlots = snapshot.stableSlots
                                                        framesObserved = snapshot.framesObserved
                                                        readyToImport = snapshot.ready
                                                        status = if (snapshot.ready) {
                                                            context.getString(R.string.lineup_locked)
                                                        } else {
                                                            context.getString(R.string.detecting_live, snapshot.stableSlots)
                                                        }
                                                        if (snapshot.ready) liveEnabled = false
                                                    }
                                                } catch (throwable: Throwable) {
                                                    withContext(Dispatchers.Main) {
                                                        warning = throwable.message ?: context.getString(R.string.scan_failed)
                                                    }
                                                } finally {
                                                    busy.set(false)
                                                }
                                            }
                                        }

                                        provider.unbindAll()
                                        val camera = provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            analysis
                                        )
                                        cameraRef.set(camera)
                                        cameraProvider = provider
                                        val zoomState = camera.cameraInfo.zoomState.value
                                        zoomRatio = defaultZoom.coerceIn(
                                            zoomState?.minZoomRatio ?: 1f,
                                            zoomState?.maxZoomRatio ?: 3f
                                        )
                                        camera.cameraControl.setZoomRatio(zoomRatio)

                                        view.setOnTouchListener { _, event ->
                                            if (event.action == MotionEvent.ACTION_UP) {
                                                val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                                val action = FocusMeteringAction.Builder(point)
                                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                                    .build()
                                                cameraRef.get()?.cameraControl?.startFocusAndMetering(action)
                                            }
                                            true
                                        }
                                    }.onFailure {
                                        fatalError = it.message ?: context.getString(R.string.scan_failed)
                                    }
                                }, ContextCompat.getMainExecutor(viewContext))
                            }
                        }
                    )
                    if (showDetections) ScoreboardAlignmentOverlay(resolvedLayout)
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(stringResource(R.string.camera_permission_needed), color = Color.White)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.allow_camera))
                        }
                        OutlinedButton(onClick = { context.openAppSettings() }) {
                            Text("OPEN APP SETTINGS")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { stableSlots / 10f },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$stableSlots/10 stable · $framesObserved frames", color = Color.White.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
                    Text("${String.format(java.util.Locale.US, "%.1f", zoomRatio)}×", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val camera = cameraRef.get() ?: return@OutlinedButton
                        val state = camera.cameraInfo.zoomState.value
                        zoomRatio = (zoomRatio - 0.25f).coerceAtLeast(state?.minZoomRatio ?: 1f)
                        camera.cameraControl.setZoomRatio(zoomRatio)
                    }) { Text("−") }
                    OutlinedButton(onClick = {
                        val camera = cameraRef.get() ?: return@OutlinedButton
                        val state = camera.cameraInfo.zoomState.value
                        zoomRatio = (zoomRatio + 0.25f).coerceAtMost(state?.maxZoomRatio ?: 3f)
                        camera.cameraControl.setZoomRatio(zoomRatio)
                    }) { Text("+") }

                    if (!autoScan) {
                        Button(onClick = {
                            liveEnabled = true
                            singleScanRequested.set(true)
                            status = "Scanning current frame…"
                        }, modifier = Modifier.weight(1f)) {
                            Text("SCAN NOW", fontWeight = FontWeight.Black)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                liveEnabled = !liveEnabled
                                if (liveEnabled) warning = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (liveEnabled) stringResource(R.string.pause) else stringResource(R.string.rescan))
                        }
                    }

                    if (readyToImport) {
                        Button(onClick = { importStableTeams() }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.use_now))
                        }
                    }
                }

                if (showDetections && detections.isNotEmpty()) {
                    LiveDetectionStrip(
                        detections = detections,
                        ownAllySlot = ownAllySlot,
                        onDetectionClick = { index ->
                            liveEnabled = false
                            correctionIndex = index
                        }
                    )
                }

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141A25))) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.my_row_optional), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..4).forEach { slot ->
                                FilterChip(
                                    selected = ownAllySlot == slot,
                                    onClick = { ownAllySlot = if (ownAllySlot == slot) null else slot },
                                    label = { Text("${slot + 1}") }
                                )
                            }
                        }
                    }
                }

                warning?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF392B12))) {
                        Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFFFFDFA6))
                    }
                }

                fatalError?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                            OutlinedButton(onClick = { context.openAppSettings() }) { Text("OPEN APP SETTINGS") }
                        }
                    }
                }
            }
        }
    }

    correctionIndex?.let { index ->
        SingleHeroPickerDialog(
            selectedId = detections.getOrNull(index)?.heroId,
            onSelect = { heroId ->
                detections = detections.mapIndexed { itemIndex, detection ->
                    if (itemIndex == index) detection.copy(heroId = heroId, confidence = 1f) else detection
                }
                correctionIndex = null
                readyToImport = detections.count { it.team == TeamSide.ENEMY && it.heroId != null } == 5 &&
                    detections.count { it.team == TeamSide.ALLY && it.heroId != null } >= 4
            },
            onDismiss = { correctionIndex = null }
        )
    }
}

@Composable
private fun ScoreboardAlignmentOverlay(layout: ScoreboardLayout) {
    Canvas(Modifier.fillMaxSize()) {
        val layouts = if (layout == ScoreboardLayout.AUTO) {
            listOf(ScoreboardLayout.PORTRAITS_LEFT, ScoreboardLayout.PORTRAITS_RIGHT)
        } else listOf(layout)
        layouts.forEach { activeLayout ->
            val alpha = if (layout == ScoreboardLayout.AUTO) 0.45f else 0.95f
            val stroke = Stroke(width = 2.dp.toPx())
            ScoreboardSlots.slots(activeLayout).forEach { (team, rect) ->
                val base = if (team == TeamSide.ALLY) Color(0xFF67E8F9) else Color(0xFFFB7185)
                drawRect(
                    color = base.copy(alpha = alpha),
                    topLeft = Offset(rect.left * size.width, rect.top * size.height),
                    size = Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height),
                    style = stroke
                )
            }
        }
    }
}

@Composable
private fun LiveDetectionStrip(
    detections: List<HeroDetection>,
    ownAllySlot: Int?,
    onDetectionClick: (Int) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141A25))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamSide.entries.forEach { team ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (team == TeamSide.ALLY) stringResource(R.string.allies) else stringResource(R.string.enemies),
                        color = Color.White,
                        modifier = Modifier.width(58.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                    detections.withIndex().filter { it.value.team == team }.sortedBy { it.value.slot }.forEach { indexed ->
                        val detection = indexed.value
                        val hero = detection.heroId?.let(HeroCatalog.byId::get)
                        Column(
                            modifier = Modifier
                                .clickable { onDetectionClick(indexed.index) }
                                .background(
                                    if (team == TeamSide.ALLY && ownAllySlot == detection.slot) Color.White.copy(alpha = 0.25f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (hero != null) {
                                HeroPortrait(hero.id, hero.name, Modifier.size(34.dp))
                            } else {
                                Box(Modifier.size(34.dp).background(Color.DarkGray, RoundedCornerShape(6.dp)))
                            }
                            Text(
                                "${(detection.confidence * 100).roundToInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleHeroPickerDialog(
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        HeroCatalog.heroes.filter { it.name.contains(query, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(520.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.correct_hero), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = Hero::id) { hero ->
                        FilterChip(
                            selected = hero.id == selectedId,
                            onClick = { onSelect(hero.id) },
                            leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(30.dp)) },
                            label = { Text("${hero.name} · ${hero.role.displayName}") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
