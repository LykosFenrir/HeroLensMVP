package com.herolens.app.vision

import android.content.Context

/**
 * Experimental detector that compares aligned scoreboard slots with cached hero portraits.
 * Live scanning adds blur rejection and multi-frame consensus around this detector.
 * A trained LiteRT classifier can replace it without changing the camera UI contract.
 */
class TemplateHeroDetector(context: Context) : HeroDetector {
    private val repository = HeroTemplateRepository(context.applicationContext)
    @Volatile private var cachedTemplates: Map<String, ImageSignature>? = null
    @Volatile private var cachedFailures: List<String> = emptyList()

    suspend fun warmUp(onProgress: (String) -> Unit = {}): Int {
        ensureTemplates(onProgress)
        return cachedTemplates?.size ?: 0
    }

    suspend fun detectAuto(
        frame: ScoreboardFrame,
        onProgress: (String) -> Unit = {}
    ): AutoDetectionResult {
        ensureTemplates(onProgress)
        val left = detectWithTemplates(frame, ScoreboardLayout.PORTRAITS_LEFT, onProgress)
        val right = detectWithTemplates(frame, ScoreboardLayout.PORTRAITS_RIGHT, onProgress)
        val leftQuality = resultQuality(left)
        val rightQuality = resultQuality(right)
        return if (rightQuality > leftQuality) {
            AutoDetectionResult(right, ScoreboardLayout.PORTRAITS_RIGHT, rightQuality)
        } else {
            AutoDetectionResult(left, ScoreboardLayout.PORTRAITS_LEFT, leftQuality)
        }
    }

    override suspend fun detect(
        frame: ScoreboardFrame,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit
    ): DetectionResult {
        ensureTemplates(onProgress)
        return when (layout) {
            ScoreboardLayout.AUTO -> detectAuto(frame, onProgress).result
            else -> detectWithTemplates(frame, layout, onProgress)
        }
    }

    private suspend fun ensureTemplates(onProgress: (String) -> Unit) {
        if (cachedTemplates != null) return
        val loadResult = repository.load(onProgress)
        cachedTemplates = loadResult.signatures
        cachedFailures = loadResult.failures
    }

    private fun detectWithTemplates(
        frame: ScoreboardFrame,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit
    ): DetectionResult {
        val templates = cachedTemplates.orEmpty()
        if (templates.isEmpty()) {
            return DetectionResult(
                detections = emptyList(),
                templatesLoaded = 0,
                warnings = listOf("Hero portraits could not be loaded. Check the internet connection and try again.")
            )
        }

        val usedByTeam = mutableMapOf<TeamSide, MutableSet<String>>()
        val detections = ScoreboardSlots.slots(layout).mapIndexed { index, (team, rect) ->
            onProgress("Recognizing ${index + 1}/10")
            val crop = SignatureMath.crop(frame, rect)
            val signature = SignatureMath.signature(crop.rgba, crop.width, crop.height)
            val used = usedByTeam.getOrPut(team) { mutableSetOf() }
            val ranked = templates.entries
                .map { (heroId, template) -> heroId to SignatureMath.similarity(signature, template) }
                .sortedByDescending { it.second }
            val available = ranked.filterNot { it.first in used }.ifEmpty { ranked }
            val best = available.first()
            val second = available.getOrNull(1)?.second ?: -1f
            val raw = ((best.second + 1f) / 2f).coerceIn(0f, 1f)
            val margin = (best.second - second).coerceAtLeast(0f)
            val confidence = (raw * 0.68f + margin * 1.05f).coerceIn(0f, 0.99f)
            val accepted = confidence >= 0.45f
            if (accepted) used += best.first
            HeroDetection(
                heroId = best.first.takeIf { accepted },
                team = team,
                slot = index % 5,
                confidence = confidence,
                alternatives = available.take(3).map { (heroId, score) ->
                    HeroCandidate(heroId, ((score + 1f) / 2f).coerceIn(0f, 1f))
                }
            )
        }
        val lowConfidence = detections.count { it.heroId == null || it.confidence < 0.58f }
        val warnings = buildList {
            if (cachedFailures.isNotEmpty()) add("${cachedFailures.size} portrait templates failed to load.")
            if (lowConfidence > 0) add("$lowConfidence slots are still uncertain.")
        }
        return DetectionResult(detections, templates.size, warnings)
    }

    private fun resultQuality(result: DetectionResult): Float {
        if (result.detections.isEmpty()) return 0f
        val accepted = result.detections.filter { it.heroId != null }
        val coverage = accepted.size / 10f
        val confidence = if (accepted.isEmpty()) 0f else accepted.map { it.confidence }.average().toFloat()
        return coverage * 0.58f + confidence * 0.42f
    }
}
