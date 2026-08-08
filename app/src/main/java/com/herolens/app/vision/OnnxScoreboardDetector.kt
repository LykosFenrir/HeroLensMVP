package com.herolens.app.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Optional one-pass full-scoreboard detector.
 *
 * Model contract:
 * - input: float32 [1, 3, 192, 320], RGB in [0, 1]
 * - output: float32 [1, N, 5] or [1, N, 6]
 * - row: [centerX, centerY, width, height, confidence, optionalClassIndex]
 *
 * Coordinates are normalized to the localized scoreboard ROI. The detector is
 * deliberately optional: when the asset is missing or invalid, HeroLens keeps
 * using the existing portrait-crop classifier.
 */
class OnnxScoreboardDetector(context: Context) : AutoCloseable {
    private val environment: OrtEnvironment? = runCatching { OrtEnvironment.getEnvironment() }.getOrNull()
    private val labels: List<String> = runCatching {
        context.assets.open(LABEL_ASSET).bufferedReader().useLines { lines ->
            lines.map(String::trim).filter(String::isNotEmpty).toList()
        }
    }.getOrDefault(emptyList())
    private val sessionOptions: OrtSession.SessionOptions? = runCatching {
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) runCatching { addNnapi() }
        }
    }.getOrNull()
    private val session: OrtSession? = runCatching {
        val env = environment ?: error("ONNX Runtime unavailable")
        val options = sessionOptions ?: error("ONNX session options unavailable")
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        require(bytes.size > MINIMUM_MODEL_BYTES) { "Scoreboard detector is a placeholder" }
        env.createSession(bytes, options)
    }.getOrNull()

    val isAvailable: Boolean
        get() = session != null && labels.size >= 2

    val modelLabelCount: Int
        get() = labels.size

    fun detect(
        frame: ScoreboardFrame,
        region: ScoreboardRegion,
        preferredTeamSize: Int? = null
    ): DetectionResult? {
        val roi = region.bounds.expandForDetector()
        val rows = runRows(frame, roi) ?: return null
        return FullScoreboardPostProcessor.process(
            rows = rows,
            labels = labels,
            roi = roi,
            region = region,
            preferredTeamSize = preferredTeamSize
        )
    }

    internal fun detectPortraits(
        frame: ScoreboardFrame,
        region: ScoreboardRegion
    ): List<ScoreboardPortraitBox>? {
        val roi = region.bounds.expandForDetector()
        val rows = runRows(frame, roi) ?: return null
        return FullScoreboardBoxPostProcessor.process(rows, roi)
    }

    private fun runRows(frame: ScoreboardFrame, roi: NormalizedRect): List<FloatArray>? {
        val activeSession = session ?: return null
        val env = environment ?: return null
        val crop = SignatureMath.crop(frame, roi)
        val input = FloatArray(CHANNELS * INPUT_WIDTH * INPUT_HEIGHT)
        cropToTensor(crop, input)
        val shape = longArrayOf(1, CHANNELS.toLong(), INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong())
        val rows = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            val inputName = activeSession.inputNames.firstOrNull() ?: return null
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                extractRows(result[0].value)
            }
        }
        return rows
    }

    private fun cropToTensor(crop: CroppedImage, output: FloatArray) {
        val srcWidth = crop.width.coerceAtLeast(1)
        val srcHeight = crop.height.coerceAtLeast(1)
        val plane = INPUT_WIDTH * INPUT_HEIGHT
        for (y in 0 until INPUT_HEIGHT) {
            val sourceY = ((y + 0.5f) * srcHeight / INPUT_HEIGHT).toInt().coerceIn(0, srcHeight - 1)
            for (x in 0 until INPUT_WIDTH) {
                val sourceX = ((x + 0.5f) * srcWidth / INPUT_WIDTH).toInt().coerceIn(0, srcWidth - 1)
                val source = (sourceY * srcWidth + sourceX) * 4
                val target = y * INPUT_WIDTH + x
                output[target] = (crop.rgba[source].toInt() and 0xff) / 255f
                output[target + plane] = (crop.rgba[source + 1].toInt() and 0xff) / 255f
                output[target + plane * 2] = (crop.rgba[source + 2].toInt() and 0xff) / 255f
            }
        }
    }

    private fun extractRows(value: Any?): List<FloatArray>? = when (value) {
        is Array<*> -> {
            val first = value.firstOrNull()
            when (first) {
                is FloatArray -> value.mapNotNull { it as? FloatArray }
                is Array<*> -> first.mapNotNull { it as? FloatArray }
                else -> null
            }
        }
        else -> null
    }

    override fun close() {
        runCatching { session?.close() }
        runCatching { sessionOptions?.close() }
    }

    private fun NormalizedRect.expandForDetector(): NormalizedRect {
        val dx = width * 0.035f
        val dy = height * 0.025f
        return NormalizedRect(
            (left - dx).coerceIn(0f, 1f),
            (top - dy).coerceIn(0f, 1f),
            (right + dx).coerceIn(0f, 1f),
            (bottom + dy).coerceIn(0f, 1f)
        )
    }

    private companion object {
        const val MODEL_ASSET = "model/scoreboard_detector.onnx"
        const val LABEL_ASSET = "model/hero_labels.txt"
        const val INPUT_WIDTH = 320
        const val INPUT_HEIGHT = 192
        const val CHANNELS = 3
        const val MINIMUM_MODEL_BYTES = 32_000
    }
}

internal data class ScoreboardPortraitBox(val bounds: NormalizedRect, val confidence: Float)

internal object FullScoreboardBoxPostProcessor {
    private const val MIN_CONFIDENCE = 0.30f
    private const val NMS_IOU = 0.42f

    fun process(rows: List<FloatArray>, roi: NormalizedRect): List<ScoreboardPortraitBox> =
        rows.mapNotNull { row -> decode(row, roi) }
            .sortedByDescending(ScoreboardPortraitBox::confidence)
            .fold(mutableListOf<ScoreboardPortraitBox>()) { kept, candidate ->
                if (kept.none { iou(it.bounds, candidate.bounds) >= NMS_IOU }) kept += candidate
                kept
            }
            // Keep enough candidates for both panels before team-aware slot
            // assignment; early global truncation let one noisy panel crowd out the other.
            .take(24)

    private fun decode(row: FloatArray, roi: NormalizedRect): ScoreboardPortraitBox? {
        if (row.size < 5 || !row[4].isFinite() || row[4] < MIN_CONFIDENCE) return null
        val width = row[2].coerceIn(0f, 1f)
        val height = row[3].coerceIn(0f, 1f)
        if (width <= 0f || height <= 0f) return null
        val left = (row[0] - width / 2f).coerceIn(0f, 1f)
        val top = (row[1] - height / 2f).coerceIn(0f, 1f)
        val right = (row[0] + width / 2f).coerceIn(0f, 1f)
        val bottom = (row[1] + height / 2f).coerceIn(0f, 1f)
        return ScoreboardPortraitBox(
            bounds = NormalizedRect(
                roi.left + left * roi.width,
                roi.top + top * roi.height,
                roi.left + right * roi.width,
                roi.top + bottom * roi.height
            ),
            confidence = row[4].coerceIn(0f, 0.99f)
        )
    }

    private fun iou(a: NormalizedRect, b: NormalizedRect): Float {
        val intersectionWidth = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
        val intersectionHeight = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}

/** Pure post-processing kept separate so geometry and duplicate handling are unit-testable. */
internal object FullScoreboardPostProcessor {
    private const val MIN_CONFIDENCE = 0.32f
    private const val NMS_IOU = 0.45f

    fun process(
        rows: List<FloatArray>,
        labels: List<String>,
        roi: NormalizedRect,
        region: ScoreboardRegion,
        preferredTeamSize: Int? = null
    ): DetectionResult {
        val candidates = rows.mapNotNull { row -> decode(row, labels, roi) }
            .sortedByDescending(Candidate::confidence)
            .fold(mutableListOf<Candidate>()) { kept, candidate ->
                if (kept.none { iou(it.bounds, candidate.bounds) >= NMS_IOU }) {
                    kept += candidate
                }
                kept
            }
        val byTeam = candidates.groupBy { candidate ->
            val allyDistance = panelDistance(candidate.bounds, region.allyPanel)
            val enemyDistance = panelDistance(candidate.bounds, region.enemyPanel)
            if (allyDistance <= enemyDistance) TeamSide.ALLY else TeamSide.ENEMY
        }
        val inferredSize = preferredTeamSize?.takeIf { it in 3..6 } ?: run {
            val strongestCount = byTeam.values.maxOfOrNull { it.size } ?: 5
            if (strongestCount >= 6) 6 else 5
        }
        val detections = buildList {
            TeamSide.entries.forEach { team ->
                val unique = mutableSetOf<String>()
                val panel = if (team == TeamSide.ALLY) region.allyPanel else region.enemyPanel
                val selected = byTeam[team].orEmpty()
                    .filter { unique.add(it.heroId) }
                    .sortedByDescending(Candidate::confidence)
                    .take(inferredSize)
                val bySlot = mutableMapOf<Int, Candidate>()
                selected.forEach { candidate ->
                    val relativeY = ((candidate.bounds.centerY - panel.top) / panel.height)
                        .coerceIn(0f, 0.9999f)
                    val slot = (relativeY * inferredSize).toInt().coerceIn(0, inferredSize - 1)
                    if (slot !in bySlot) bySlot[slot] = candidate
                }
                repeat(inferredSize) { slot ->
                    val candidate = bySlot[slot]
                    add(
                        HeroDetection(
                            heroId = candidate?.heroId,
                            team = team,
                            slot = slot,
                            confidence = candidate?.confidence ?: 0f,
                            alternatives = candidate?.let {
                                listOf(HeroCandidate(it.heroId, it.confidence))
                            }.orEmpty(),
                            bounds = candidate?.bounds
                        )
                    )
                }
            }
        }
        val accepted = detections.count { it.heroId != null }
        return DetectionResult(
            detections = detections,
            templatesLoaded = 0,
            warnings = buildList {
                add("Full-scoreboard detector active.")
                if (accepted < inferredSize * 2) add("${inferredSize * 2 - accepted} slots require review.")
            },
            scoreboardRegion = region,
            slotRects = detections.mapNotNull { detection -> detection.bounds?.let { detection.team to it } },
            teamSize = inferredSize,
            profileScore = (accepted / (inferredSize * 2f)).coerceIn(0f, 1f)
        )
    }

    private fun decode(row: FloatArray, labels: List<String>, roi: NormalizedRect): Candidate? {
        if (row.size < 6) return null
        val confidence = row[4]
        val classIndex = row[5].toInt()
        if (!confidence.isFinite() || confidence < MIN_CONFIDENCE || classIndex !in labels.indices) return null
        val heroId = labels[classIndex].takeIf { it != "__unknown__" } ?: return null
        val width = row[2].coerceIn(0f, 1f)
        val height = row[3].coerceIn(0f, 1f)
        if (width <= 0f || height <= 0f) return null
        val left = (row[0] - width / 2f).coerceIn(0f, 1f)
        val top = (row[1] - height / 2f).coerceIn(0f, 1f)
        val right = (row[0] + width / 2f).coerceIn(0f, 1f)
        val bottom = (row[1] + height / 2f).coerceIn(0f, 1f)
        return Candidate(
            heroId = heroId,
            confidence = confidence.coerceIn(0f, 0.99f),
            bounds = NormalizedRect(
                roi.left + left * roi.width,
                roi.top + top * roi.height,
                roi.left + right * roi.width,
                roi.top + bottom * roi.height
            )
        )
    }

    private fun panelDistance(box: NormalizedRect, panel: NormalizedRect): Float {
        val vertical = when {
            box.centerY < panel.top -> panel.top - box.centerY
            box.centerY > panel.bottom -> box.centerY - panel.bottom
            else -> 0f
        }
        val horizontal = when {
            box.centerX < panel.left -> panel.left - box.centerX
            box.centerX > panel.right -> box.centerX - panel.right
            else -> 0f
        }
        return vertical * 1.8f + horizontal
    }

    private fun iou(a: NormalizedRect, b: NormalizedRect): Float {
        val intersectionWidth = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
        val intersectionHeight = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private data class Candidate(
        val heroId: String,
        val confidence: Float,
        val bounds: NormalizedRect
    )
}
