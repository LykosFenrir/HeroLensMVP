package com.herolens.app.vision

import android.content.Context

/**
 * Experimental on-device detector. V6.3 first locates the actual blue/red scoreboard
 * panels, then compares only the portrait cells. This is substantially more robust
 * than fixed screen coordinates and supports both 5v5 and 6v6 scoreboards.
 */
class TemplateHeroDetector(context: Context) : HeroDetector {
    private val repository = HeroTemplateRepository(context.applicationContext)
    @Volatile private var cachedTemplates: Map<String, List<ImageSignature>>? = null
    @Volatile private var cachedFailures: List<String> = emptyList()

    suspend fun warmUp(onProgress: (String) -> Unit = {}): Int {
        ensureTemplates(onProgress)
        return cachedTemplates?.size ?: 0
    }

    suspend fun detectAuto(
        frame: ScoreboardFrame,
        onProgress: (String) -> Unit = {},
        locatedRegion: ScoreboardRegion? = null
    ): AutoDetectionResult {
        ensureTemplates(onProgress)
        val region = locatedRegion ?: ScoreboardLocator.locate(frame)
        if (region == null) {
            val placeholders = placeholderDetections(teamSize = 5)
            val result = DetectionResult(
                detections = placeholders,
                templatesLoaded = cachedTemplates.orEmpty().size,
                warnings = listOf("Scoreboard panels not found. Center the blue and red team tables in the frame."),
                teamSize = 5
            )
            return AutoDetectionResult(result, ScoreboardLayout.PORTRAITS_LEFT, 0f)
        }

        val leftCandidates = listOf(5, 6).map { teamSize ->
            detectLocalized(frame, region, ScoreboardLayout.PORTRAITS_LEFT, teamSize, onProgress)
        }
        val bestLeft = leftCandidates.maxByOrNull(::resultQuality)
        val best = if (bestLeft != null && resultQuality(bestLeft) >= 0.34f &&
            bestLeft.detections.count { it.heroId != null } >= 3
        ) {
            bestLeft
        } else {
            val rightCandidates = listOf(5, 6).map { teamSize ->
                detectLocalized(frame, region, ScoreboardLayout.PORTRAITS_RIGHT, teamSize, onProgress)
            }
            (leftCandidates + rightCandidates).maxByOrNull(::resultQuality)
                ?: DetectionResult(placeholderDetections(5), cachedTemplates.orEmpty().size, teamSize = 5)
        }
        val layout = if (best.slotRects.firstOrNull()?.second?.centerX?.let { it < region.bounds.centerX } == true) {
            ScoreboardLayout.PORTRAITS_LEFT
        } else {
            ScoreboardLayout.PORTRAITS_RIGHT
        }
        val quality = (resultQuality(best) * 0.86f + region.confidence * 0.14f).coerceIn(0f, 1f)
        return AutoDetectionResult(
            result = best,
            layout = layout,
            quality = quality,
            scoreboardRegion = region,
            slotRects = best.slotRects,
            teamSize = best.teamSize
        )
    }

    override suspend fun detect(
        frame: ScoreboardFrame,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit
    ): DetectionResult {
        ensureTemplates(onProgress)
        if (layout == ScoreboardLayout.AUTO) return detectAuto(frame, onProgress).result
        val region = ScoreboardLocator.locate(frame)
        return if (region != null) {
            listOf(5, 6)
                .map { teamSize -> detectLocalized(frame, region, layout, teamSize, onProgress) }
                .maxByOrNull(::resultQuality)
                ?: DetectionResult(placeholderDetections(5), cachedTemplates.orEmpty().size, teamSize = 5)
        } else {
            detectLegacy(frame, layout, onProgress)
        }
    }

    suspend fun detectLocated(
        frame: ScoreboardFrame,
        region: ScoreboardRegion,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit = {}
    ): DetectionResult {
        ensureTemplates(onProgress)
        if (layout == ScoreboardLayout.AUTO) return detectAuto(frame, onProgress, region).result
        return listOf(5, 6)
            .map { teamSize -> detectLocalized(frame, region, layout, teamSize, onProgress) }
            .maxByOrNull(::resultQuality)
            ?: DetectionResult(
                detections = placeholderDetections(5),
                templatesLoaded = cachedTemplates.orEmpty().size,
                scoreboardRegion = region,
                teamSize = 5
            )
    }

    private suspend fun ensureTemplates(onProgress: (String) -> Unit) {
        if (cachedTemplates != null) return
        val loadResult = repository.load(onProgress)
        cachedTemplates = loadResult.signatures
        cachedFailures = loadResult.failures
    }

    private fun detectLocalized(
        frame: ScoreboardFrame,
        region: ScoreboardRegion,
        layout: ScoreboardLayout,
        teamSize: Int,
        onProgress: (String) -> Unit
    ): DetectionResult {
        val templates = cachedTemplates.orEmpty()
        if (templates.isEmpty()) return noTemplates(teamSize, region)
        val profiles = ScoreboardSlots.localizedProfiles(region, layout, teamSize)
        return profiles.mapIndexed { profileIndex, slots ->
            detectProfile(
                frame = frame,
                slots = slots,
                templates = templates,
                teamSize = teamSize,
                region = region,
                onProgress = { position ->
                    onProgress("Recognizing ${teamSize}v${teamSize} · ${profileIndex + 1}/${profiles.size} · $position/${teamSize * 2}")
                }
            )
        }.maxByOrNull(::resultQuality)
            ?: DetectionResult(placeholderDetections(teamSize), templates.size, scoreboardRegion = region, teamSize = teamSize)
    }

    private fun detectLegacy(
        frame: ScoreboardFrame,
        layout: ScoreboardLayout,
        onProgress: (String) -> Unit
    ): DetectionResult {
        val templates = cachedTemplates.orEmpty()
        if (templates.isEmpty()) return noTemplates(5, null)
        return ScoreboardSlots.profiles(layout).mapIndexed { profileIndex, slots ->
            detectProfile(
                frame = frame,
                slots = slots,
                templates = templates,
                teamSize = 5,
                region = null,
                onProgress = { position -> onProgress("Fallback profile ${profileIndex + 1} · $position/10") }
            )
        }.maxByOrNull(::resultQuality)
            ?: DetectionResult(placeholderDetections(5), templates.size, teamSize = 5)
    }

    private fun detectProfile(
        frame: ScoreboardFrame,
        slots: List<Pair<TeamSide, NormalizedRect>>,
        templates: Map<String, List<ImageSignature>>,
        teamSize: Int,
        region: ScoreboardRegion?,
        onProgress: (Int) -> Unit
    ): DetectionResult {
        val usedByTeam = mutableMapOf<TeamSide, MutableSet<String>>()
        val detections = slots.mapIndexed { index, (team, rect) ->
            onProgress(index + 1)
            val signatures = ScoreboardSlots.jittered(rect).map { candidateRect ->
                val crop = SignatureMath.crop(frame, candidateRect)
                SignatureMath.signature(crop.rgba, crop.width, crop.height)
            }
            val used = usedByTeam.getOrPut(team) { mutableSetOf() }
            // Cheap hash/histogram pass first, then expensive luminance/edge
            // correlation only for the strongest candidates. This keeps live scanning
            // responsive on mid-range phones.
            val shortlist = templates.entries
                .map { (heroId, templateVariants) ->
                    heroId to signatures.maxOf { cameraSignature ->
                        templateVariants.maxOf { template ->
                            SignatureMath.quickSimilarity(cameraSignature, template)
                        }
                    }
                }
                .sortedByDescending { it.second }
                .take(12)
                .map { it.first }
                .toSet()
            val ranked = templates.entries
                .asSequence()
                .filter { it.key in shortlist }
                .map { (heroId, templateVariants) ->
                    heroId to signatures.maxOf { cameraSignature ->
                        templateVariants.maxOf { template ->
                            SignatureMath.similarity(cameraSignature, template)
                        }
                    }
                }
                .sortedByDescending { it.second }
                .toList()
            val available = ranked.filterNot { it.first in used }.ifEmpty { ranked }
            val best = available.first()
            val second = available.getOrNull(1)?.second ?: -1f
            val third = available.getOrNull(2)?.second ?: -1f
            val raw = ((best.second + 1f) / 2f).coerceIn(0f, 1f)
            val margin = (best.second - second).coerceAtLeast(0f)
            val separation = (best.second - third).coerceAtLeast(0f)
            val confidence = (raw * 0.64f + margin * 1.18f + separation * 0.36f).coerceIn(0f, 0.99f)
            // Multi-frame stabilization is the final false-positive guard. Keep the
            // per-frame gate permissive enough for camera blur, TV colour casts and
            // small laptop portraits, then require repeated agreement before import.
            val accepted = confidence >= 0.27f && raw >= 0.41f && margin >= 0.0015f
            if (accepted) used += best.first
            HeroDetection(
                heroId = best.first.takeIf { accepted },
                team = team,
                slot = index % teamSize,
                confidence = confidence,
                alternatives = available.take(3).map { (heroId, score) ->
                    HeroCandidate(heroId, ((score + 1f) / 2f).coerceIn(0f, 1f))
                },
                bounds = rect
            )
        }
        val lowConfidence = detections.count { it.heroId == null || it.confidence < 0.55f }
        val warnings = buildList {
            if (cachedFailures.isNotEmpty()) add("${cachedFailures.size} portrait templates failed to load.")
            if (lowConfidence > 0) add("$lowConfidence slots are still uncertain.")
        }
        return DetectionResult(
            detections = detections,
            templatesLoaded = templates.size,
            warnings = warnings,
            scoreboardRegion = region,
            slotRects = slots,
            teamSize = teamSize
        )
    }

    private fun noTemplates(teamSize: Int, region: ScoreboardRegion?): DetectionResult = DetectionResult(
        detections = placeholderDetections(teamSize),
        templatesLoaded = 0,
        warnings = listOf("Hero portraits could not be loaded. Check the internet connection and try again."),
        scoreboardRegion = region,
        teamSize = teamSize
    )

    private fun placeholderDetections(teamSize: Int): List<HeroDetection> = buildList {
        repeat(teamSize) { slot -> add(HeroDetection(null, TeamSide.ALLY, slot, 0f)) }
        repeat(teamSize) { slot -> add(HeroDetection(null, TeamSide.ENEMY, slot, 0f)) }
    }

    private fun resultQuality(result: DetectionResult): Float {
        if (result.detections.isEmpty()) return 0f
        val accepted = result.detections.filter { it.heroId != null }
        val total = (result.teamSize * 2).coerceAtLeast(1)
        val coverage = accepted.size / total.toFloat()
        val confidence = if (accepted.isEmpty()) 0f else accepted.map { it.confidence }.average().toFloat()
        val uniqueness = accepted.groupBy { it.team }.values
            .map { team -> team.mapNotNull { it.heroId }.distinct().size / maxOf(1f, team.size.toFloat()) }
            .average().toFloat().takeIf { !it.isNaN() } ?: 0f
        return (coverage * 0.56f + confidence * 0.36f + uniqueness * 0.08f).coerceIn(0f, 1f)
    }
}
