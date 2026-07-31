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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import com.herolens.app.data.ScanMode
import com.herolens.app.vision.AutoDetectionResult
import com.herolens.app.vision.DetectionResult
import com.herolens.app.vision.FrameQualityEvaluator
import com.herolens.app.vision.HeroCandidate
import com.herolens.app.vision.HeroDetection
import com.herolens.app.vision.LiveScanStabilizer
import com.herolens.app.vision.NormalizedRect
import com.herolens.app.vision.QualityHint
import com.herolens.app.vision.ScoreboardRegion
import com.herolens.app.vision.ScoreboardLayout
import com.herolens.app.vision.ScoreboardLocator
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


@Composable
fun CameraScanScreen(
    autoScan: Boolean,
    autoOpenResults: Boolean,
    showDetections: Boolean,
    hapticFeedback: Boolean,
    defaultZoom: Float,
    autoZoom: Boolean,
    scanMode: ScanMode,
    preferredLayout: ScoreboardLayout,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val detector = remember { TemplateHeroDetector(context) }
    val stabilizer = remember(scanMode) {
        LiveScanStabilizer(
            windowSize = scanMode.windowSize,
            minimumVotes = scanMode.minimumVotes,
            minimumAverageConfidence = scanMode.minimumConfidence
        )
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val busy = remember { AtomicBoolean(false) }
    val liveEnabledRef = remember { AtomicBoolean(true) }
    val singleScanRequested = remember { AtomicBoolean(false) }
    val lastAnalysisAt = remember { AtomicLong(0L) }
    val cameraRef = remember { AtomicReference<Camera?>(null) }
    val zoomRatioRef = remember { AtomicReference(defaultZoom.coerceIn(1f, 5f)) }
    val lastAutoZoomAt = remember { AtomicLong(0L) }

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
    var selectedLayout by remember { mutableStateOf(preferredLayout) }
    var frameBrightness by remember { mutableStateOf(0f) }
    var frameSharpness by remember { mutableStateOf(0f) }
    var qualityHint by remember { mutableStateOf(QualityHint.GOOD) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var torchOn by remember { mutableStateOf(false) }
    var stableSlots by remember { mutableIntStateOf(0) }
    var framesObserved by remember { mutableIntStateOf(0) }
    var readyToImport by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    var correctionIndex by remember { mutableStateOf<Int?>(null) }
    var ownAllySlot by remember { mutableStateOf<Int?>(null) }
    var zoomRatio by remember { mutableStateOf(defaultZoom.coerceIn(1f, 5f)) }
    var hapticSent by remember { mutableStateOf(false) }
    var scoreboardRegion by remember { mutableStateOf<ScoreboardRegion?>(null) }
    var overlaySlots by remember { mutableStateOf<List<Pair<TeamSide, NormalizedRect>>>(emptyList()) }
    var detectedTeamSize by remember { mutableIntStateOf(5) }

    fun closeScanner() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onClose()
    }

    fun applyZoom(requested: Float) {
        val camera = cameraRef.get() ?: return
        val state = camera.cameraInfo.zoomState.value
        val target = requested.coerceIn(
            state?.minZoomRatio ?: 1f,
            state?.maxZoomRatio ?: 5f
        )
        zoomRatioRef.set(target)
        zoomRatio = target
        lastAutoZoomAt.set(System.currentTimeMillis())
        camera.cameraControl.setZoomRatio(target)
    }

    fun frameScoreboard() {
        val region = scoreboardRegion
        if (region == null) {
            applyZoom(zoomRatioRef.get() + 0.5f)
            status = "Zooming in — center the scoreboard"
            return
        }
        val bounds = region.bounds
        val scale = minOf(
            0.82f / bounds.width.coerceAtLeast(0.01f),
            0.76f / bounds.height.coerceAtLeast(0.01f)
        ).coerceIn(0.75f, 2.5f)
        applyZoom(zoomRatioRef.get() * scale)
        status = "Scoreboard framed"
    }

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
            .take((detectedTeamSize - 1).coerceAtLeast(4))
        val enemies = detections.filter { it.team == TeamSide.ENEMY }
            .sortedBy { it.slot }
            .mapNotNull { it.heroId }
            .distinct()
            .take(detectedTeamSize)
        if (enemies.size == detectedTeamSize && allies.size >= detectedTeamSize - 1) {
            imported = true
            onUseDetections(allies, enemies, currentHero, averageConfidence())
        }
    }

    BackHandler(enabled = true) { closeScanner() }

    LaunchedEffect(activity) {
        // FULL_SENSOR supports portrait, both landscape directions and returning to
        // portrait even when the device's normal auto-rotate setting is disabled.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
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
        if (readyToImport && autoOpenResults && !imported) {
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B12)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("LIVE SCAN", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(status, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = ::closeScanner) { Text("CLOSE") }
                }
            }

            Box(
                modifier = (if (isLandscape) {
                    Modifier.fillMaxHeight().aspectRatio(16f / 9f).padding(6.dp)
                } else {
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp).aspectRatio(16f / 9f)
                })
                    .background(Color.Black, RoundedCornerShape(22.dp))
            ) {
                if (permissionGranted) {
                    key(configuration.orientation) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PreviewView(viewContext).also { view ->
                                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                view.scaleType = PreviewView.ScaleType.FIT_CENTER
                                val future = ProcessCameraProvider.getInstance(viewContext)
                                future.addListener({
                                    runCatching {
                                        val provider = future.get()
                                        val resolutionSelector = ResolutionSelector.Builder()
                                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    AndroidSize(960, 540),
                                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                )
                                            )
                                            .build()
                                        val preview = Preview.Builder()
                                            .setResolutionSelector(resolutionSelector)
                                            .setTargetRotation(view.display.rotation)
                                            .build()
                                            .also { it.setSurfaceProvider(view.surfaceProvider) }
                                        val analysis = ImageAnalysis.Builder()
                                            .setResolutionSelector(resolutionSelector)
                                            .setTargetRotation(view.display.rotation)
                                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        view.addOnLayoutChangeListener { changedView, _, _, _, _, _, _, _, _ ->
                                            val rotation = changedView.display?.rotation ?: return@addOnLayoutChangeListener
                                            preview.targetRotation = rotation
                                            analysis.targetRotation = rotation
                                        }

                                        analysis.setAnalyzer(analyzerExecutor) { image ->
                                            val now = System.currentTimeMillis()
                                            val manualRequest = !autoScan && singleScanRequested.compareAndSet(true, false)
                                            val allowed = autoScan || manualRequest
                                            if (!templatesReady || !liveEnabledRef.get() || !allowed || busy.get() || now - lastAnalysisAt.get() < scanMode.intervalMs) {
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
                                                    withContext(Dispatchers.Main) {
                                                        frameBrightness = quality.brightness
                                                        frameSharpness = quality.sharpness
                                                        qualityHint = quality.hint
                                                    }
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

                                                    val locatedRegion = ScoreboardLocator.locate(frame)
                                                    withContext(Dispatchers.Main) {
                                                        scoreboardRegion = locatedRegion
                                                        if (locatedRegion == null) {
                                                            overlaySlots = emptyList()
                                                            status = "Aim at the blue and red scoreboard panels"
                                                        }
                                                    }
                                                    if (locatedRegion == null) return@launch

                                                    var zoomChanged = false
                                                    if (autoZoom && locatedRegion.confidence >= 0.30f && now - lastAutoZoomAt.get() >= 800L) {
                                                        val bounds = locatedRegion.bounds
                                                        val widthFraction = bounds.width.coerceAtLeast(0.01f)
                                                        val heightFraction = bounds.height.coerceAtLeast(0.01f)
                                                        if (widthFraction < 0.78f || heightFraction < 0.62f) {
                                                            val camera = cameraRef.get()
                                                            val zoomState = camera?.cameraInfo?.zoomState?.value
                                                            val current = zoomRatioRef.get()
                                                            val scale = minOf(0.82f / widthFraction, 0.76f / heightFraction)
                                                                .coerceIn(1f, 2.2f)
                                                            val target = (current * scale * 0.95f).coerceIn(
                                                                zoomState?.minZoomRatio ?: 1f,
                                                                zoomState?.maxZoomRatio ?: 5f
                                                            )
                                                            if (target > current + 0.12f) {
                                                                lastAutoZoomAt.set(now)
                                                                zoomRatioRef.set(target)
                                                                zoomChanged = true
                                                                withContext(Dispatchers.Main) {
                                                                    zoomRatio = target
                                                                    camera?.cameraControl?.setZoomRatio(target)
                                                                    status = "Auto-framing scoreboard…"
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (zoomChanged) return@launch

                                                    val automatic = if (selectedLayout == ScoreboardLayout.AUTO) {
                                                        detector.detectAuto(frame, locatedRegion = locatedRegion)
                                                    } else {
                                                        val result = detector.detect(frame, selectedLayout)
                                                        AutoDetectionResult(
                                                            result = result,
                                                            layout = selectedLayout,
                                                            quality = detectionQuality(result)
                                                        )
                                                    }
                                                    val snapshot = stabilizer.add(automatic)
                                                    withContext(Dispatchers.Main) {
                                                        detections = snapshot.detections
                                                        stableSlots = snapshot.stableSlots
                                                        framesObserved = snapshot.framesObserved
                                                        readyToImport = snapshot.ready
                                                        scoreboardRegion = snapshot.scoreboardRegion
                                                        overlaySlots = snapshot.slotRects
                                                        detectedTeamSize = snapshot.teamSize
                                                        status = when {
                                                            snapshot.ready -> context.getString(R.string.lineup_locked)
                                                            snapshot.framesObserved >= 8 && snapshot.stableSlots == 0 ->
                                                                "Move closer — make the TV scoreboard fill the frame"
                                                            else -> context.getString(R.string.detecting_live, snapshot.stableSlots)
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
                                            zoomState?.maxZoomRatio ?: 5f
                                        )
                                        zoomRatioRef.set(zoomRatio)
                                        camera.cameraControl.setZoomRatio(zoomRatio)
                                        val exposureState = camera.cameraInfo.exposureState
                                        exposureMin = exposureState.exposureCompensationRange.lower
                                        exposureMax = exposureState.exposureCompensationRange.upper
                                        exposureIndex = exposureState.exposureCompensationIndex
                                        torchOn = camera.cameraInfo.torchState.value == 1

                                        val scaleDetector = ScaleGestureDetector(
                                            viewContext,
                                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                                    val activeCamera = cameraRef.get() ?: return false
                                                    val state = activeCamera.cameraInfo.zoomState.value
                                                    val next = (zoomRatioRef.get() * detector.scaleFactor).coerceIn(
                                                        state?.minZoomRatio ?: 1f,
                                                        state?.maxZoomRatio ?: 5f
                                                    )
                                                    zoomRatioRef.set(next)
                                                    zoomRatio = next
                                                    activeCamera.cameraControl.setZoomRatio(next)
                                                    lastAutoZoomAt.set(System.currentTimeMillis())
                                                    return true
                                                }
                                            }
                                        )
                                        val tapDetector = GestureDetector(
                                            viewContext,
                                            object : GestureDetector.SimpleOnGestureListener() {
                                                override fun onDown(event: MotionEvent): Boolean = true

                                                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                                                    val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                                    val action = FocusMeteringAction.Builder(point)
                                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                                        .build()
                                                    cameraRef.get()?.cameraControl?.startFocusAndMetering(action)
                                                    status = context.getString(R.string.tap_to_focus_hint)
                                                    return true
                                                }
                                            }
                                        )
                                        view.setOnTouchListener { _, event ->
                                            scaleDetector.onTouchEvent(event)
                                            tapDetector.onTouchEvent(event)
                                            true
                                        }
                                    }.onFailure {
                                        fatalError = it.message ?: context.getString(R.string.scan_failed)
                                    }
                                }, ContextCompat.getMainExecutor(viewContext))
                            }
                        }
                    )
                    }
                    if (showDetections) DynamicScoreboardOverlay(scoreboardRegion, overlaySlots)
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

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clickable { closeScanner() },
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 6.dp
                ) {
                    Text(
                        text = "← EXIT SCAN",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }

                if (isLandscape) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "$status · $stableSlots/${detectedTeamSize * 2}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        color = Color.Black.copy(alpha = 0.76f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() - 0.35f) }) { Text("−") }
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", zoomRatio)}×",
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                            OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() + 0.35f) }) { Text("+") }
                            OutlinedButton(onClick = { frameScoreboard() }) { Text("FRAME") }
                            OutlinedButton(onClick = {
                                liveEnabled = !liveEnabled
                                if (liveEnabled) warning = null
                            }) {
                                Text(if (liveEnabled) stringResource(R.string.pause) else stringResource(R.string.rescan))
                            }
                            if (readyToImport) {
                                Button(onClick = { importStableTeams() }) {
                                    Text(stringResource(R.string.use_now), fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            if (!isLandscape) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { stableSlots / (detectedTeamSize * 2f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$stableSlots/${detectedTeamSize * 2} stable · $framesObserved frames", color = Color.White.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
                    Text("${String.format(java.util.Locale.US, "%.1f", zoomRatio)}×", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreboardLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = selectedLayout == layout,
                            onClick = {
                                selectedLayout = layout
                                liveEnabled = true
                                stabilizer.reset()
                            },
                            label = { Text(layout.displayName) }
                        )
                    }
                }
                Text(
                    "${scanMode.label} mode · brightness ${(frameBrightness * 100).roundToInt()}% · detail ${(frameSharpness * 1000).roundToInt()} · ${qualityHint.name.lowercase().replaceFirstChar(Char::uppercase)}",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() - 0.35f) }) { Text("−") }
                    OutlinedButton(onClick = { applyZoom(zoomRatioRef.get() + 0.35f) }) { Text("+") }
                    OutlinedButton(onClick = { frameScoreboard() }) { Text("FRAME") }

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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val camera = cameraRef.get() ?: return@OutlinedButton
                        // Return to continuous autofocus; tap the preview for point focus.
                        camera.cameraControl.cancelFocusAndMetering()
                        status = context.getString(R.string.tap_to_focus_hint)
                    }) { Text("FOCUS") }
                    if (cameraRef.get()?.cameraInfo?.hasFlashUnit() == true) {
                        OutlinedButton(onClick = {
                            torchOn = !torchOn
                            cameraRef.get()?.cameraControl?.enableTorch(torchOn)
                        }) { Text(if (torchOn) "TORCH OFF" else "TORCH") }
                    }
                    Text("Pinch to zoom", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.labelSmall)
                }
                if (exposureMax > exposureMin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Exposure", color = Color.White, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = { value ->
                                exposureIndex = value.roundToInt().coerceIn(exposureMin, exposureMax)
                                cameraRef.get()?.cameraControl?.setExposureCompensationIndex(exposureIndex)
                            },
                            valueRange = exposureMin.toFloat()..exposureMax.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
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
                            (0 until detectedTeamSize).forEach { slot ->
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
            suggested = detections.getOrNull(index)?.alternatives.orEmpty(),
            onSelect = { heroId ->
                detections = detections.mapIndexed { itemIndex, detection ->
                    if (itemIndex == index) detection.copy(heroId = heroId, confidence = 1f) else detection
                }
                correctionIndex = null
                readyToImport = detections.count { it.team == TeamSide.ENEMY && it.heroId != null } == detectedTeamSize &&
                    detections.count { it.team == TeamSide.ALLY && it.heroId != null } >= detectedTeamSize - 1
            },
            onDismiss = { correctionIndex = null }
        )
    }
}

@Composable
private fun DynamicScoreboardOverlay(
    region: ScoreboardRegion?,
    slots: List<Pair<TeamSide, NormalizedRect>>
) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 2.dp.toPx())
        if (region == null) {
            // A single framing guide is less misleading than fixed hero boxes. The
            // actual boxes appear only after the scoreboard panels are localized.
            val guideWidth = size.width * 0.72f
            val guideHeight = size.height * 0.68f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset((size.width - guideWidth) / 2f, (size.height - guideHeight) / 2f),
                size = Size(guideWidth, guideHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            return@Canvas
        }

        listOf(TeamSide.ALLY to region.allyPanel, TeamSide.ENEMY to region.enemyPanel).forEach { (team, rect) ->
            val color = if (team == TeamSide.ALLY) Color(0xFF22D3EE) else Color(0xFFFB7185)
            drawRoundRect(
                color = color.copy(alpha = 0.72f),
                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                size = Size(rect.width * size.width, rect.height * size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        slots.forEach { (team, rect) ->
            val color = if (team == TeamSide.ALLY) Color(0xFF67E8F9) else Color(0xFFFB7185)
            drawRect(
                color = color,
                topLeft = Offset(rect.left * size.width, rect.top * size.height),
                size = Size(rect.width * size.width, rect.height * size.height),
                style = stroke
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
    suggested: List<HeroCandidate>,
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
