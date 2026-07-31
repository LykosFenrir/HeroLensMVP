package com.herolens.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.util.Size as AndroidSize
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface as AndroidSurface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
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
import com.herolens.app.data.DatasetCollector
import com.herolens.app.data.DisplayType
import com.herolens.app.data.InputPlatform
import com.herolens.app.data.ScanMode
import com.herolens.app.vision.AutoDetectionResult
import com.herolens.app.vision.BurstScanSession
import com.herolens.app.vision.DetectionResult
import com.herolens.app.vision.FrameQualityEvaluator
import com.herolens.app.vision.HeroCandidate
import com.herolens.app.vision.HeroDetection
import com.herolens.app.vision.NormalizedRect
import com.herolens.app.vision.QualityHint
import com.herolens.app.vision.ScoreboardFrame
import com.herolens.app.vision.ScoreboardLayout
import com.herolens.app.vision.ScoreboardRegion
import com.herolens.app.vision.ScoreboardSearchState
import com.herolens.app.vision.ScoreboardLocator
import com.herolens.app.vision.StableDetectionSnapshot
import com.herolens.app.vision.TeamSide
import com.herolens.app.vision.TemplateHeroDetector
import com.herolens.app.vision.toScoreboardFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private enum class ScanPhase { AIMING, CAPTURING, REVIEW }

private data class CapturedFrameCandidate(
    val frame: ScoreboardFrame,
    val region: ScoreboardRegion,
    val score: Float
)

@Composable
fun CameraScanScreen(
    autoScan: Boolean,
    autoOpenResults: Boolean,
    quickResponse: Boolean = false,
    showDetections: Boolean,
    hapticFeedback: Boolean,
    defaultZoom: Float,
    autoZoom: Boolean,
    scanMode: ScanMode,
    preferredLayout: ScoreboardLayout,
    collectTrainingData: Boolean,
    inputPlatform: InputPlatform,
    displayType: DisplayType,
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val originalRequestedOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val detector = remember { TemplateHeroDetector(context.applicationContext) }
    val datasetCollector = remember { DatasetCollector(context.applicationContext) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    val busy = remember { AtomicBoolean(false) }
    val phaseRef = remember { AtomicReference(ScanPhase.AIMING) }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    val zoomRatioRef = remember { AtomicReference(defaultZoom.coerceAtLeast(1f)) }
    val burstSessionRef = remember { AtomicReference<BurstScanSession?>(null) }
    val bestFrameRef = remember { AtomicReference<CapturedFrameCandidate?>(null) }
    val lastAnalysisAt = remember { AtomicLong(0L) }
    val burstDeadline = remember { AtomicLong(0L) }
    val autoStartVotes = remember { AtomicInteger(0) }
    val lastAutoZoomAt = remember { AtomicLong(0L) }

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var phase by remember { mutableStateOf(ScanPhase.AIMING) }
    var templatesReady by remember { mutableStateOf(false) }
    var templatesLoadedCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Aim at the blue and red scoreboard panels") }
    var warning by remember { mutableStateOf<String?>(null) }
    var datasetMessage by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var detections by remember { mutableStateOf(placeholderDetections(5)) }
    var selectedLayout by remember { mutableStateOf(preferredLayout) }
    var resolvedLayout by remember { mutableStateOf(preferredLayout) }
    var frameBrightness by remember { mutableStateOf(0f) }
    var frameSharpness by remember { mutableStateOf(0f) }
    var qualityHint by remember { mutableStateOf(QualityHint.GOOD) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var torchOn by remember { mutableStateOf(false) }
    var stableSlots by remember { mutableIntStateOf(0) }
    var burstFrames by remember { mutableIntStateOf(0) }
    var burstTarget by remember { mutableIntStateOf(scanMode.burstFrames) }
    var readyToImport by remember { mutableStateOf(false) }
    var correctionIndex by remember { mutableStateOf<Int?>(null) }
    var ownAllySlot by remember { mutableStateOf<Int?>(null) }
    var zoomRatio by remember { mutableStateOf(defaultZoom.coerceAtLeast(1f)) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var scoreboardRegion by remember { mutableStateOf<ScoreboardRegion?>(null) }
    var framingBounds by remember { mutableStateOf<NormalizedRect?>(null) }
    var locatorState by remember { mutableStateOf(ScoreboardSearchState.NOT_FOUND) }
    var locatorConfidence by remember { mutableStateOf(0f) }
    var frameDimensions by remember { mutableStateOf("—") }
    var overlaySlots by remember { mutableStateOf<List<Pair<TeamSide, NormalizedRect>>>(emptyList()) }
    var detectedTeamSize by remember { mutableIntStateOf(5) }
    var capturedFrame by remember { mutableStateOf<ScoreboardFrame?>(null) }
    var capturedRegion by remember { mutableStateOf<ScoreboardRegion?>(null) }
    var hapticSent by remember { mutableStateOf(false) }

    fun closeScanner() {
        activity?.requestedOrientation = originalRequestedOrientation
        onClose()
    }

    fun applyZoom(requested: Float) {
        val camera = cameraRef.get() ?: run {
            status = "Camera is still starting"
            return
        }
        val state = camera.cameraInfo.zoomState.value
        val target = requested.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: maxZoomRatio.coerceAtLeast(1f))
        zoomRatioRef.set(target)
        zoomRatio = target
        camera.cameraControl.setZoomRatio(target)
    }

    fun frameScoreboard() {
        val bounds = framingBounds ?: scoreboardRegion?.bounds
        if (bounds == null) {
            applyZoom(zoomRatioRef.get() + 0.5f)
            status = "Zooming in — keep both team panels visible"
            return
        }
        val scale = minOf(
            0.80f / bounds.width.coerceAtLeast(0.01f),
            0.76f / bounds.height.coerceAtLeast(0.01f)
        ).coerceIn(0.72f, 2.7f)
        applyZoom(zoomRatioRef.get() * scale)
        status = "Scoreboard framed"
    }

    fun averageConfidence(): Int {
        val accepted = detections.filter { it.heroId != null }
        if (accepted.isEmpty()) return 0
        return (accepted.map { it.confidence }.average() * 100).roundToInt().coerceIn(0, 99)
    }

    fun enterReview(snapshot: StableDetectionSnapshot?) {
        val best = bestFrameRef.get()
        capturedFrame = best?.frame
        capturedRegion = best?.region ?: snapshot?.scoreboardRegion ?: scoreboardRegion
        if (snapshot != null) {
            detections = snapshot.detections
            stableSlots = snapshot.stableSlots
            detectedTeamSize = snapshot.teamSize
            overlaySlots = snapshot.slotRects
            scoreboardRegion = snapshot.scoreboardRegion ?: scoreboardRegion
            readyToImport = snapshot.ready
        }
        phase = ScanPhase.REVIEW
        phaseRef.set(ScanPhase.REVIEW)
        status = when {
            readyToImport -> "Scan complete — review the lineup"
            stableSlots > 0 -> "Partial scan — correct uncertain slots"
            else -> "No confident heroes — tap each slot to select manually"
        }
        if (hapticFeedback && !hapticSent) {
            hapticSent = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun startBurst() {
        if (!templatesReady) {
            warning = "Hero references are still preparing. Keep internet enabled on first use."
            return
        }
        if (locatorState != ScoreboardSearchState.FOUND || scoreboardRegion == null) {
            warning = "Center the complete blue and red scoreboard panels, then tap Scan."
            return
        }
        warning = null
        datasetMessage = null
        hapticSent = false
        val targetFrameCount = if (quickResponse) 4 else scanMode.burstFrames
        val requiredVotes = if (quickResponse) 2 else scanMode.minimumVotes
        val requiredConfidence = if (quickResponse) minOf(scanMode.minimumConfidence, 0.46f) else scanMode.minimumConfidence
        burstTarget = targetFrameCount
        burstFrames = 0
        stableSlots = 0
        readyToImport = false
        overlaySlots = emptyList()
        detections = placeholderDetections(detectedTeamSize)
        bestFrameRef.set(null)
        val session = BurstScanSession(
            targetFrames = targetFrameCount,
            minimumVotes = requiredVotes,
            minimumAverageConfidence = requiredConfidence
        )
        burstSessionRef.set(session)
        burstDeadline.set(System.currentTimeMillis() + if (quickResponse) 4_000L else 6_000L)
        phase = ScanPhase.CAPTURING
        phaseRef.set(ScanPhase.CAPTURING)
        status = if (quickResponse) "Quick scan — hold steady for a moment" else "Scanning — hold steady"
    }

    fun scanAgain() {
        phase = ScanPhase.AIMING
        phaseRef.set(ScanPhase.AIMING)
        burstSessionRef.set(null)
        bestFrameRef.set(null)
        capturedFrame = null
        capturedRegion = null
        burstFrames = 0
        stableSlots = 0
        readyToImport = false
        overlaySlots = emptyList()
        detections = placeholderDetections(5)
        detectedTeamSize = 5
        status = "Aim at the blue and red scoreboard panels"
        warning = null
    }

    fun useReviewedTeams(allowPartial: Boolean = false) {
        val allyDetections = detections.filter { it.team == TeamSide.ALLY }.sortedBy { it.slot }
        val enemyDetections = detections.filter { it.team == TeamSide.ENEMY }.sortedBy { it.slot }
        val currentHero = ownAllySlot?.let { selected -> allyDetections.firstOrNull { it.slot == selected }?.heroId }
        val allies = allyDetections
            .filterNot { ownAllySlot != null && it.slot == ownAllySlot }
            .mapNotNull { it.heroId }
            .distinct()
            .take(if (ownAllySlot == null) detectedTeamSize else (detectedTeamSize - 1).coerceAtLeast(4))
        val enemies = enemyDetections.mapNotNull { it.heroId }.distinct().take(detectedTeamSize)
        val minimumEnemies = if (allowPartial) 3 else detectedTeamSize
        val minimumAllies = if (allowPartial) 2 else detectedTeamSize - 1
        if (enemies.size < minimumEnemies || allies.size < minimumAllies) {
            warning = if (allowPartial) {
                "Quick scan needs at least three enemies and two allies. Hold steady and scan again, or correct the empty slots."
            } else {
                "Correct the unknown slots before using the lineup. Your own ally row can remain optional."
            }
            return
        }
        val confidence = averageConfidence()
        if (collectTrainingData) {
            val frame = capturedFrame
            val region = capturedRegion
            if (frame != null && region != null) {
                status = "Saving reviewed training sample…"
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        datasetCollector.saveReviewedSample(
                            frame = frame,
                            region = region,
                            detections = detections,
                            layout = resolvedLayout,
                            platform = inputPlatform,
                            displayType = displayType,
                            scanConfidence = confidence
                        )
                    }
                    datasetMessage = if (result.saved) "Reviewed sample saved locally for future training" else result.message
                    onUseDetections(allies, enemies, currentHero, confidence)
                }
                return
            }
        }
        onUseDetections(allies, enemies, currentHero, confidence)
    }

    BackHandler(enabled = true) { closeScanner() }

    LaunchedEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        runCatching {
            templatesLoadedCount = detector.warmUp { progress ->
                scope.launch { status = progress }
            }
            templatesReady = detector.aiAvailable || templatesLoadedCount > 0
            status = when {
                detector.aiAvailable -> "Neural AI ready — aim at the scoreboard"
                templatesReady -> "Aim at the scoreboard, then tap Scan"
                else -> "Recognition references unavailable"
            }
        }.onFailure {
            warning = it.message ?: "Recognition references could not be prepared"
        }
    }

    LaunchedEffect(phase, readyToImport, quickResponse) {
        phaseRef.set(phase)
        if (phase == ScanPhase.REVIEW && autoOpenResults) {
            val alliesKnown = detections.count { it.team == TeamSide.ALLY && it.heroId != null }
            val enemiesKnown = detections.count { it.team == TeamSide.ENEMY && it.heroId != null }
            if (quickResponse && enemiesKnown >= 3 && alliesKnown >= 2) {
                delay(180)
                useReviewedTeams(allowPartial = true)
            } else if (readyToImport) {
                val allHighConfidence = detections.filter { it.heroId != null }.all { it.confidence >= 0.72f }
                if (allHighConfidence) {
                    delay(650)
                    useReviewedTeams()
                }
            }
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdownNow()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = originalRequestedOrientation
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (quickResponse) "QUICK AUTO SCAN" else "SCAN BURST", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(status, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = ::closeScanner) { Text("CLOSE") }
                }
            }

            Box(
                modifier = if (isLandscape) {
                    Modifier.fillMaxHeight().aspectRatio(16f / 9f).padding(6.dp)
                } else {
                    Modifier.fillMaxHeight(0.58f).aspectRatio(9f / 16f, matchHeightConstraintsFirst = true).padding(horizontal = 12.dp)
                }
            ) {
                if (permissionGranted) {
                    key(configuration.orientation) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PreviewView(viewContext).apply {
                                    scaleType = PreviewView.ScaleType.FIT_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    val future = ProcessCameraProvider.getInstance(viewContext)
                                    future.addListener({
                                        runCatching {
                                            val provider = future.get()
                                            val rotation = display?.rotation ?: AndroidSurface.ROTATION_0
                                            val selector = ResolutionSelector.Builder()
                                                .setResolutionStrategy(
                                                    ResolutionStrategy(
                                                        AndroidSize(1280, 720),
                                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                    )
                                                )
                                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                                .build()
                                            val preview = Preview.Builder()
                                                .setResolutionSelector(selector)
                                                .setTargetRotation(rotation)
                                                .build()
                                                .also { it.setSurfaceProvider(surfaceProvider) }
                                            val analysis = ImageAnalysis.Builder()
                                                .setResolutionSelector(selector)
                                                .setTargetRotation(rotation)
                                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()

                                            analysis.setAnalyzer(analyzerExecutor) { image ->
                                                val now = System.currentTimeMillis()
                                                val currentPhase = phaseRef.get()
                                                val interval = if (currentPhase == ScanPhase.CAPTURING) {
                                                    if (quickResponse) 110L else scanMode.intervalMs
                                                } else 180L
                                                if (now - lastAnalysisAt.get() < interval || !busy.compareAndSet(false, true)) {
                                                    image.close()
                                                    return@setAnalyzer
                                                }
                                                lastAnalysisAt.set(now)
                                                val frame = try { image.toScoreboardFrame() } finally { image.close() }
                                                if (frame == null) {
                                                    busy.set(false)
                                                    return@setAnalyzer
                                                }
                                                scope.launch(Dispatchers.Default) {
                                                    try {
                                                        val quality = FrameQualityEvaluator.evaluate(frame)
                                                        val search = ScoreboardLocator.search(frame)
                                                        withContext(Dispatchers.Main) {
                                                            frameBrightness = quality.brightness
                                                            frameSharpness = quality.sharpness
                                                            qualityHint = quality.hint
                                                            locatorState = search.state
                                                            locatorConfidence = search.confidence
                                                            framingBounds = search.framingBounds
                                                            scoreboardRegion = search.region
                                                            frameDimensions = "${frame.width}×${frame.height}"
                                                        }

                                                        if (currentPhase == ScanPhase.AIMING) {
                                                            val votes = if (search.state == ScoreboardSearchState.FOUND && search.confidence >= 0.72f) {
                                                                autoStartVotes.incrementAndGet()
                                                            } else {
                                                                autoStartVotes.set(0)
                                                                0
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                status = when (search.state) {
                                                                    ScoreboardSearchState.FOUND -> "Scoreboard ready ${(search.confidence * 100).roundToInt()}% — tap Scan"
                                                                    ScoreboardSearchState.INCOMPLETE -> "Both team panels are not fully visible"
                                                                    ScoreboardSearchState.NOT_FOUND -> "Aim at the blue and red scoreboard panels"
                                                                }
                                                                val bounds = search.framingBounds
                                                                if (autoZoom && bounds != null && bounds.width < 0.38f &&
                                                                    System.currentTimeMillis() - lastAutoZoomAt.get() > 1400L
                                                                ) {
                                                                    lastAutoZoomAt.set(System.currentTimeMillis())
                                                                    applyZoom(zoomRatioRef.get() * 1.18f)
                                                                    status = "Auto-framing scoreboard…"
                                                                }
                                                                val votesNeeded = if (quickResponse) 2 else 4
                                                                if (autoScan && votes >= votesNeeded && phase == ScanPhase.AIMING) startBurst()
                                                            }
                                                            return@launch
                                                        }

                                                        if (currentPhase != ScanPhase.CAPTURING) return@launch
                                                        if (System.currentTimeMillis() >= burstDeadline.get()) {
                                                            withContext(Dispatchers.Main) { enterReview(null) }
                                                            return@launch
                                                        }
                                                        val region = search.region
                                                        if (region == null) {
                                                            withContext(Dispatchers.Main) { status = "Scoreboard lost — hold the phone steady" }
                                                            return@launch
                                                        }
                                                        if (!quality.usable) {
                                                            withContext(Dispatchers.Main) {
                                                                status = when (quality.hint) {
                                                                    QualityHint.TOO_DARK -> "Too dark — increase screen brightness"
                                                                    QualityHint.TOO_BRIGHT -> "Too much glare — change the angle"
                                                                    QualityHint.BLURRY -> "Hold steady and tap the scoreboard to focus"
                                                                    QualityHint.GOOD -> "Scanning — hold steady"
                                                                }
                                                            }
                                                            return@launch
                                                        }

                                                        val candidateScore = search.confidence * 0.70f + (quality.sharpness * 8f).coerceIn(0f, 1f) * 0.30f
                                                        val existing = bestFrameRef.get()
                                                        if (existing == null || candidateScore > existing.score) {
                                                            bestFrameRef.set(CapturedFrameCandidate(frame, region, candidateScore))
                                                        }

                                                        val automatic = if (selectedLayout == ScoreboardLayout.AUTO) {
                                                            detector.detectAuto(frame, locatedRegion = region)
                                                        } else {
                                                            val result = detector.detectLocated(frame, region, selectedLayout)
                                                            AutoDetectionResult(
                                                                result = result,
                                                                layout = selectedLayout,
                                                                quality = detectionQuality(result),
                                                                scoreboardRegion = region,
                                                                slotRects = result.slotRects,
                                                                teamSize = result.teamSize
                                                            )
                                                        }
                                                        val progress = burstSessionRef.get()?.add(automatic) ?: return@launch
                                                        withContext(Dispatchers.Main) {
                                                            val snapshot = progress.snapshot
                                                            detections = snapshot.detections
                                                            stableSlots = snapshot.stableSlots
                                                            burstFrames = progress.framesCaptured
                                                            burstTarget = progress.targetFrames
                                                            readyToImport = snapshot.ready
                                                            detectedTeamSize = snapshot.teamSize
                                                            resolvedLayout = automatic.layout
                                                            scoreboardRegion = snapshot.scoreboardRegion ?: region
                                                            overlaySlots = snapshot.slotRects
                                                            status = "Scanning ${progress.framesCaptured}/${progress.targetFrames} — hold steady"
                                                            if (progress.complete) enterReview(snapshot)
                                                        }
                                                    } catch (throwable: Throwable) {
                                                        withContext(Dispatchers.Main) {
                                                            warning = throwable.message ?: "The scan failed"
                                                            if (phase == ScanPhase.CAPTURING) enterReview(null)
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
                                            cameraProvider = provider
                                            cameraRef.set(camera)
                                            val zoomState = camera.cameraInfo.zoomState.value
                                            maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
                                            zoomRatio = zoomRatioRef.get().coerceIn(zoomState?.minZoomRatio ?: 1f, maxZoomRatio.coerceAtLeast(1f))
                                            zoomRatioRef.set(zoomRatio)
                                            camera.cameraControl.setZoomRatio(zoomRatio)
                                            camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                                                maxZoomRatio = state.maxZoomRatio
                                                zoomRatio = state.zoomRatio
                                                zoomRatioRef.set(state.zoomRatio)
                                            }
                                            val exposureState = camera.cameraInfo.exposureState
                                            exposureMin = exposureState.exposureCompensationRange.lower
                                            exposureMax = exposureState.exposureCompensationRange.upper
                                            exposureIndex = exposureState.exposureCompensationIndex
                                            torchOn = camera.cameraInfo.torchState.value == 1

                                            val scaleDetector = ScaleGestureDetector(
                                                viewContext,
                                                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                                                        applyZoom(zoomRatioRef.get() * detector.scaleFactor)
                                                        return true
                                                    }
                                                }
                                            )
                                            val tapDetector = GestureDetector(
                                                viewContext,
                                                object : GestureDetector.SimpleOnGestureListener() {
                                                    override fun onDown(event: MotionEvent): Boolean = true
                                                    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                                                        val point = meteringPointFactory.createPoint(event.x, event.y)
                                                        val action = FocusMeteringAction.Builder(point)
                                                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                                            .build()
                                                        cameraRef.get()?.cameraControl?.startFocusAndMetering(action)
                                                        status = "Focusing…"
                                                        return true
                                                    }
                                                }
                                            )
                                            setOnTouchListener { _, event ->
                                                scaleDetector.onTouchEvent(event)
                                                tapDetector.onTouchEvent(event)
                                                true
                                            }
                                        }.onFailure {
                                            fatalError = it.message ?: "Camera failed to start"
                                        }
                                    }, ContextCompat.getMainExecutor(viewContext))
                                }
                            }
                        )
                    }
                    BurstGuideOverlay(
                        region = scoreboardRegion,
                        framingBounds = framingBounds,
                        slots = if (phase == ScanPhase.REVIEW && showDetections) overlaySlots else emptyList(),
                        phase = phase
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Camera permission is required", color = Color.White)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("ALLOW CAMERA") }
                        OutlinedButton(onClick = { context.openAppSettings() }) { Text("OPEN APP SETTINGS") }
                    }
                }

                if (isLandscape) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(10.dp).clickable { closeScanner() },
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("← EXIT", modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(status, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "${locatorState.name.lowercase()} ${(locatorConfidence * 100).roundToInt()}% · $templatesLoadedCount refs · $frameDimensions",
                                color = Color(0xFF83D8FF),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() - 0.5f) }) { Text("−") }
                            Text(String.format(Locale.US, "%.1f×", zoomRatio), color = Color.White, fontWeight = FontWeight.Black)
                            OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() + 0.5f) }) { Text("+") }
                            OutlinedButton(onClick = ::frameScoreboard) { Text("FRAME") }
                            when (phase) {
                                ScanPhase.AIMING -> Button(onClick = ::startBurst) { Text("SCAN", fontWeight = FontWeight.Black) }
                                ScanPhase.CAPTURING -> OutlinedButton(onClick = { enterReview(null) }) { Text("STOP") }
                                ScanPhase.REVIEW -> {
                                    OutlinedButton(onClick = ::scanAgain) { Text("AGAIN") }
                                    Button(onClick = { useReviewedTeams() }) { Text("USE RESULTS", fontWeight = FontWeight.Black) }
                                }
                            }
                        }
                    }
                }
            }

            if (!isLandscape) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val progress = if (phase == ScanPhase.CAPTURING) burstFrames / burstTarget.toFloat().coerceAtLeast(1f) else stableSlots / (detectedTeamSize * 2f)
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (phase) {
                                ScanPhase.AIMING -> "Locator ${(locatorConfidence * 100).roundToInt()}%"
                                ScanPhase.CAPTURING -> "$burstFrames/$burstTarget burst frames · $stableSlots stable"
                                ScanPhase.REVIEW -> "$stableSlots/${detectedTeamSize * 2} confident · tap any slot to correct"
                            },
                            color = Color.White.copy(alpha = 0.76f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(String.format(Locale.US, "%.1f×", zoomRatio), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (phase == ScanPhase.AIMING) {
                        Text("One large frame replaces the old ten-box alignment. Keep both team panels inside it.", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScoreboardLayout.entries.forEach { layout ->
                                FilterChip(
                                    selected = selectedLayout == layout,
                                    onClick = { selectedLayout = layout },
                                    label = { Text(layout.displayName) }
                                )
                            }
                        }
                    }

                    if (phase != ScanPhase.AIMING) {
                        LiveDetectionStrip(
                            detections = detections,
                            ownAllySlot = ownAllySlot,
                            onDetectionClick = { correctionIndex = it }
                        )
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF141A25))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("My row (optional)", color = Color.White, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    repeat(detectedTeamSize) { slot ->
                                        FilterChip(
                                            selected = ownAllySlot == slot,
                                            onClick = { ownAllySlot = if (ownAllySlot == slot) null else slot },
                                            label = { Text("${slot + 1}") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "${scanMode.label} burst · brightness ${(frameBrightness * 100).roundToInt()}% · detail ${(frameSharpness * 1000).roundToInt()} · ${qualityHint.name.lowercase().replaceFirstChar(Char::uppercase)}",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "Detector: ${if (detector.aiAvailable) "Neural AI" else "Template fallback"} · $templatesLoadedCount heroes · Locator: ${locatorState.name.lowercase().replaceFirstChar(Char::uppercase)} ${(locatorConfidence * 100).roundToInt()}% · Frame $frameDimensions · Camera max ${String.format(Locale.US, "%.1f", maxZoomRatio)}×",
                        color = Color(0xFF83D8FF),
                        style = MaterialTheme.typography.labelSmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() - 0.5f) }) { Text("−") }
                        OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() + 0.5f) }) { Text("+") }
                        OutlinedButton(onClick = ::frameScoreboard) { Text("FRAME") }
                        OutlinedButton(onClick = {
                            val camera = cameraRef.get()
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        }) { Text(if (torchOn) "TORCH OFF" else "TORCH") }
                    }

                    if (exposureMax > exposureMin) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Exposure", color = Color.White, modifier = Modifier.width(76.dp))
                            androidx.compose.material3.Slider(
                                value = exposureIndex.toFloat(),
                                onValueChange = { value ->
                                    val next = value.roundToInt().coerceIn(exposureMin, exposureMax)
                                    exposureIndex = next
                                    cameraRef.get()?.cameraControl?.setExposureCompensationIndex(next)
                                },
                                valueRange = exposureMin.toFloat()..exposureMax.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    when (phase) {
                        ScanPhase.AIMING -> Button(onClick = ::startBurst, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                            Text(if (quickResponse) "START QUICK SCAN" else "SCAN ${scanMode.burstFrames} FRAMES", fontWeight = FontWeight.Black)
                        }
                        ScanPhase.CAPTURING -> OutlinedButton(onClick = { enterReview(null) }, modifier = Modifier.fillMaxWidth()) { Text("STOP AND REVIEW") }
                        ScanPhase.REVIEW -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = ::scanAgain, modifier = Modifier.weight(1f)) { Text("SCAN AGAIN") }
                            Button(onClick = { useReviewedTeams() }, modifier = Modifier.weight(1f)) { Text("USE RESULTS", fontWeight = FontWeight.Black) }
                        }
                    }

                    if (collectTrainingData) {
                        Text("Training collection is ON. Only the cropped scoreboard and reviewed portrait cells are saved locally after Use Results.", color = Color(0xFFA7F3D0), style = MaterialTheme.typography.bodySmall)
                    }
                    datasetMessage?.let { Text(it, color = Color(0xFFA7F3D0), style = MaterialTheme.typography.bodySmall) }
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
    }

    correctionIndex?.let { index ->
        SingleHeroPickerDialog(
            selectedId = detections.getOrNull(index)?.heroId,
            suggested = detections.getOrNull(index)?.alternatives.orEmpty(),
            onSelect = { heroId ->
                detections = detections.mapIndexed { itemIndex, detection ->
                    if (itemIndex == index) detection.copy(heroId = heroId, confidence = 1f) else detection
                }
                correctionIndex = null
                val alliesKnown = detections.count { it.team == TeamSide.ALLY && it.heroId != null }
                val enemiesKnown = detections.count { it.team == TeamSide.ENEMY && it.heroId != null }
                readyToImport = enemiesKnown == detectedTeamSize && alliesKnown >= detectedTeamSize - 1
                stableSlots = alliesKnown + enemiesKnown
            },
            onDismiss = { correctionIndex = null }
        )
    }
}

@Composable
private fun BurstGuideOverlay(
    region: ScoreboardRegion?,
    framingBounds: NormalizedRect?,
    slots: List<Pair<TeamSide, NormalizedRect>>,
    phase: ScanPhase
) {
    Canvas(Modifier.fillMaxSize()) {
        val guide = framingBounds ?: region?.bounds
        if (guide != null) {
            val color = when (phase) {
                ScanPhase.AIMING -> Color(0xFF60A5FA)
                ScanPhase.CAPTURING -> Color(0xFFFFB84D)
                ScanPhase.REVIEW -> Color(0xFF34D399)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(guide.left * size.width, guide.top * size.height),
                size = Size(guide.width * size.width, guide.height * size.height),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        } else {
            val width = size.width * 0.76f
            val height = size.height * 0.62f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.72f),
                topLeft = Offset((size.width - width) / 2f, (size.height - height) / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        slots.forEach { (team, rect) ->
            val color = if (team == TeamSide.ALLY) Color(0xFF67E8F9) else Color(0xFFFB7185)
            drawRect(
                color = color,
                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                size = Size(rect.width * size.width, rect.height * size.height),
                style = Stroke(width = 2.dp.toPx())
            )
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TeamSide.entries.forEach { team ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                    if (team == TeamSide.ALLY && ownAllySlot == detection.slot) Color.White.copy(alpha = 0.24f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (hero != null) HeroPortrait(hero.id, hero.name, Modifier.size(36.dp))
                            else Box(Modifier.size(36.dp).background(Color.DarkGray, RoundedCornerShape(7.dp)))
                            Text("${(detection.confidence * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
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
    suggested: List<HeroCandidate>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { HeroCatalog.heroes.filter { it.name.contains(query, ignoreCase = true) } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth().height(520.dp), shape = RoundedCornerShape(22.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.correct_hero), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (suggested.isNotEmpty()) {
                    Text("TOP MATCHES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggested.take(3).forEach { candidate ->
                            val hero = HeroCatalog.byId[candidate.heroId] ?: return@forEach
                            FilterChip(
                                selected = hero.id == selectedId,
                                onClick = { onSelect(hero.id) },
                                leadingIcon = { HeroPortrait(hero.id, hero.name, Modifier.size(28.dp)) },
                                label = { Text("${hero.name} ${(candidate.confidence * 100).roundToInt()}%") }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
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

private fun placeholderDetections(teamSize: Int): List<HeroDetection> = buildList {
    repeat(teamSize) { add(HeroDetection(null, TeamSide.ALLY, it, 0f)) }
    repeat(teamSize) { add(HeroDetection(null, TeamSide.ENEMY, it, 0f)) }
}

private fun detectionQuality(result: DetectionResult): Float {
    val accepted = result.detections.filter { it.heroId != null }
    if (accepted.isEmpty()) return 0f
    val coverage = accepted.size / (result.teamSize * 2f)
    val confidence = accepted.map { it.confidence }.average().toFloat()
    return coverage * 0.58f + confidence * 0.42f
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
