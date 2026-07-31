package com.herolens.app.vision

import android.content.Context

/**
 * Experimental on-device detector. V6.3 first locates the actual blue/red scoreboard
 * panels, then compares only the portrait cells. This is substantially more robust
 * than fixed screen coordinates and supports both 5v5 and 6v6 scoreboards.
 */
class TemplateHeroDetector(context: Context) : HeroDetector {
    private val repository = HeroTemplateRepository(context.applicationContext)
    private val aiClassifier = OnnxHeroClassifier(context.applicationContext)
    @Volatile private var cachedTemplates: Map<String, List<ImageSignature>>? = null
    @Volatile private var cachedFailures: List<String> = emptyList()

    suspend fun warmUp(onProgress: (String) -> Unit = {}): Int {
        if (aiClassifier.isAvailable) {
            cachedTemplates = emptyMap()
            onProgress("AI classifier ready · ${aiClassifier.modelLabelCount} labels")
            return (aiClassifier.modelLabelCount - 1).coerceAtLeast(1)
        }
        ensureTemplates(onProgress)
        return cachedTemplates?.size ?: 0
    }

    val aiAvailable: Boolean
        get() = aiClassifier.isAvailable

    suspend fun detectAuto(
        frame: ScoreboardFrame,
        onProgress: (String) -> Unit = {},
        locatedRegion: ScoreboardRegion? = null,
        preferredTeamSize: Int? = null
    ): AutoDetectionResult {
        ensureTemplates(onProgress)
        val region = locatedRegion ?: ScoreboardLocator.locate(frame)
        val forcedTeamSize = preferredTeamSize?.takeIf { it in 5..6 }
        if (region == null) {
            val fallbackSize = forcedTeamSize ?: 5
            val placeholders = placeholderDetections(teamSize = fallbackSize)
            val result = DetectionResult(
                detections = placeholders,
                templatesLoaded = cachedTemplates.orEmpty().size,
                warnings = listOf("Scoreboard panels not found. Center the blue and red team tables in the frame."),
                teamSize = fallbackSize
            )
            return AutoDetectionResult(result, ScoreboardLayout.PORTRAITS_LEFT, 0f)
        }

        val structural = ScoreboardTeamSizeEstimator.estimate(frame, region)
        val candidateSizes = when {
            forcedTeamSize != null -> listOf(forcedTeamSize)
            structural.teamSize == 6 -> listOf(6, 5)
            structural.teamSize == 5 -> listOf(5, 6)
            else -> listOf(5, 6)
        }
        fun adjustedQuality(result: DetectionResult): Float {
            val structuralScore = if (result.teamSize == 6) structural.sixScore else structural.fiveScore
            val structuralBonus = structuralScore * 0.18f
            val selectedBonus = when {
                forcedTeamSize == result.teamSize -> 0.24f
                structural.teamSize == result.teamSize -> 0.10f * structural.confidence
                else -> 0f
            }
            return (resultQuality(result) * 0.82f + structuralBonus + selectedBonus).coerceIn(0f, 1.25f)
        }

        val leftCandidates = candidateSizes.map { teamSize ->
            detectLocalized(frame, region, ScoreboardLayout.PORTRAITS_LEFT, teamSize, onProgress)
        }
        val bestLeft = leftCandidates.maxByOrNull(::adjustedQuality)
        val best = if (bestLeft != null && adjustedQuality(bestLeft) >= 0.34f &&
            bestLeft.detections.count { it.heroId != null } >= 3
        ) {
            bestLeft
        } else {
            val rightCandidates = candidateSizes.map { teamSize ->
                detectLocalized(frame, region, ScoreboardLayout.PORTRAITS_RIGHT, teamSize, onProgress)
            }
            (leftCandidates + rightCandidates).maxByOrNull(::adjustedQuality)
                ?: DetectionResult(
                    placeholderDetections(forcedTeamSize ?: structural.teamSize ?: 5),
                    cachedTemplates.orEmpty().size,
                    teamSize = forcedTeamSize ?: structural.teamSize ?: 5
                )
        }
        val layout = if (best.slotRects.firstOrNull()?.second?.centerX?.let { it < region.bounds.centerX } == true) {
            ScoreboardLayout.PORTRAITS_LEFT
        } else {
            ScoreboardLayout.PORTRAITS_RIGHT
        }
        val structuralMessage = when {
            forcedTeamSize == 6 -> "6v6 / Open Queue mode selected."
            forcedTeamSize == 5 -> "5v5 mode selected."
            structural.teamSize == 6 -> "Six scoreboard rows detected."
            structural.teamSize == 5 -> "Five scoreboard rows detected."
            else -> "Team size inferred from hero crops."
        }
        val enriched = best.copy(warnings = best.warnings + structuralMessage)
        val quality = (adjustedQuality(best) * 0.86f + region.confidence * 0.14f).coerceIn(0f, 1f)
        return AutoDetectionResult(
            result = enriched,
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
        onProgress: (String) -> Unit = {},
        preferredTeamSize: Int? = null
    ): DetectionResult {
        ensureTemplates(onProgress)
        if (layout == ScoreboardLayout.AUTO) {
            return detectAuto(frame, onProgress, region, preferredTeamSize).result
        }
        val structural = ScoreboardTeamSizeEstimator.estimate(frame, region)
        val candidateSizes = preferredTeamSize?.takeIf { it in 5..6 }?.let { listOf(it) }
            ?: structural.teamSize?.let { listOf(it, if (it == 5) 6 else 5) }
            ?: listOf(5, 6)
        return candidateSizes
            .map { teamSize -> detectLocalized(frame, region, layout, teamSize, onProgress) }
            .maxByOrNull { result ->
                val structuralScore = if (result.teamSize == 6) structural.sixScore else structural.fiveScore
                resultQuality(result) * 0.84f + structuralScore * 0.16f
            }
            ?: DetectionResult(
                detections = placeholderDetections(preferredTeamSize ?: structural.teamSize ?: 5),
                templatesLoaded = cachedTemplates.orEmpty().size,
                scoreboardRegion = region,
                teamSize = preferredTeamSize ?: structural.teamSize ?: 5
            )
    }

    private suspend fun ensureTemplates(onProgress: (String) -> Unit) {
        if (cachedTemplates != null) return
        if (aiClassifier.isAvailable) {
            cachedTemplates = emptyMap()
            return
        }
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
        if (templates.isEmpty() && !aiClassifier.isAvailable) return noTemplates(teamSize, region)
        val profiles = ScoreboardSlots.localizedProfiles(region, layout, teamSize)
        val rankedProfiles = rankGeometryProfiles(
            frame,
            profiles,
            templates,
            limit = if (aiClassifier.isAvailable) 2 else 4
        )
        return rankedProfiles.mapIndexed { profileIndex, ranked ->
            detectProfile(
                frame = frame,
                slots = ranked.slots,
                templates = templates,
                teamSize = teamSize,
                region = region,
                profileScore = ranked.score,
                onProgress = { position ->
                    onProgress("Recognizing ${teamSize}v${teamSize} · ${profileIndex + 1}/${rankedProfiles.size} · $position/${teamSize * 2}")
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
        if (templates.isEmpty() && !aiClassifier.isAvailable) return noTemplates(5, null)
        return ScoreboardSlots.profiles(layout).mapIndexed { profileIndex, slots ->
            detectProfile(
                frame = frame,
                slots = slots,
                templates = templates,
                teamSize = 5,
                region = null,
                profileScore = 0f,
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
        profileScore: Float,
        onProgress: (Int) -> Unit
    ): DetectionResult {
        val usedByTeam = mutableMapOf<TeamSide, MutableSet<String>>()
        val detections = if (aiClassifier.isAvailable) {
            val cropGroups = slots.map { (_, rect) ->
                ScoreboardSlots.neuralVariants(rect).map { candidateRect ->
                    SignatureMath.crop(frame, candidateRect)
                }
            }
            val flatCrops = cropGroups.flatten()
            val predictions = aiClassifier.classifyBatch(flatCrops, topK = 7)
            var predictionOffset = 0
            slots.mapIndexed { index, (team, rect) ->
                onProgress(index + 1)
                val cropCount = cropGroups[index].size
                val slotPredictions = predictions
                    .subList(predictionOffset, (predictionOffset + cropCount).coerceAtMost(predictions.size))
                predictionOffset += cropCount
                val evidence = mutableMapOf<String, MutableList<Float>>()
                slotPredictions.forEach { cropPredictions ->
                    cropPredictions.forEach { candidate ->
                        evidence.getOrPut(candidate.heroId) { mutableListOf() } += candidate.confidence
                    }
                }
                val ranked = evidence.map { (heroId, values) ->
                    val ordered = values.sortedDescending()
                    val top = ordered.getOrElse(0) { 0f }
                    val secondVote = ordered.getOrElse(1) { 0f }
                    val thirdVote = ordered.getOrElse(2) { 0f }
                    val mean = values.average().toFloat()
                    val consensus = top * 0.62f + secondVote * 0.25f + thirdVote * 0.08f + mean * 0.05f
                    AggregatedCandidate(heroId, consensus, top, values.count { it >= 0.20f })
                }.sortedByDescending(AggregatedCandidate::score)
                val used = usedByTeam.getOrPut(team) { mutableSetOf() }
                val available = ranked.filterNot { it.heroId in used }.ifEmpty { ranked }
                val best = available.firstOrNull()
                val second = available.getOrNull(1)?.score ?: 0f
                val bestScore = best?.score ?: 0f
                val margin = (bestScore - second).coerceAtLeast(0f)
                val confidence = (bestScore * 0.82f + (margin / 0.24f).coerceIn(0f, 1f) * 0.18f).coerceIn(0f, 0.99f)
                val hasCropConsensus = best != null && (best.votes >= 2 || best.peak >= 0.72f)
                val accepted = best != null && hasCropConsensus && bestScore >= 0.37f && margin >= 0.030f
                if (accepted) used += best!!.heroId
                HeroDetection(
                    heroId = best?.heroId?.takeIf { accepted },
                    team = team,
                    slot = index % teamSize,
                    confidence = confidence,
                    alternatives = available.take(3).map { candidate -> HeroCandidate(candidate.heroId, candidate.score) },
                    bounds = rect
                )
            }
        } else {
            slots.mapIndexed { index, (team, rect) ->
                onProgress(index + 1)
                val crops = ScoreboardSlots.jittered(rect).map { candidateRect ->
                    SignatureMath.crop(frame, candidateRect)
                }
                val signatures = crops.map { crop ->
                    SignatureMath.signature(crop.rgba, crop.width, crop.height)
                }
                val used = usedByTeam.getOrPut(team) { mutableSetOf() }
                // Cheap hash/histogram pass first, then expensive luminance/edge
                // correlation only for the strongest candidates.
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
                val best = available.firstOrNull()
                val second = available.getOrNull(1)?.second ?: -1f
                val third = available.getOrNull(2)?.second ?: -1f
                val bestScore = best?.second?.coerceIn(-1f, 1f) ?: -1f
                val margin = (bestScore - second).coerceAtLeast(0f)
                val separation = (bestScore - third).coerceAtLeast(0f)
                val absoluteEvidence = ((bestScore - 0.08f) / 0.52f).coerceIn(0f, 1f)
                val marginEvidence = (margin / 0.16f).coerceIn(0f, 1f)
                val separationEvidence = (separation / 0.24f).coerceIn(0f, 1f)
                val confidence = (
                    absoluteEvidence * 0.68f +
                        marginEvidence * 0.24f +
                        separationEvidence * 0.08f
                    ).coerceIn(0f, 0.99f)
                val accepted = best != null && bestScore >= 0.18f && margin >= 0.018f && confidence >= 0.30f
                if (accepted) used += best!!.first
                HeroDetection(
                    heroId = best?.first?.takeIf { accepted },
                    team = team,
                    slot = index % teamSize,
                    confidence = confidence,
                    alternatives = available.take(3).map { (heroId, score) ->
                        HeroCandidate(heroId, ((score - 0.08f) / 0.52f).coerceIn(0f, 1f))
                    },
                    bounds = rect
                )
            }
        }
        val lowConfidence = detections.count { it.heroId == null || it.confidence < 0.55f }
        val warnings = buildList {
            if (aiClassifier.isAvailable) add("Neural classifier active.")
            if (cachedFailures.isNotEmpty()) add("${cachedFailures.size} portrait templates failed to load.")
            if (lowConfidence > 0) add("$lowConfidence slots are still uncertain.")
        }
        return DetectionResult(
            detections = detections,
            templatesLoaded = templates.size,
            warnings = warnings,
            scoreboardRegion = region,
            slotRects = slots,
            teamSize = teamSize,
            profileScore = profileScore
        )
    }

    private data class AggregatedCandidate(
        val heroId: String,
        val score: Float,
        val peak: Float,
        val votes: Int
    )

    private data class RankedProfile(
        val slots: List<Pair<TeamSide, NormalizedRect>>,
        val score: Float
    )

    /**
     * Scores many candidate portrait-column/row layouts with cheap hash, histogram
     * and texture checks. Only the strongest four layouts proceed to full 32×32
     * luminance/edge comparison, keeping the wider geometry search fast enough for
     * live CameraX analysis.
     */
    private fun rankGeometryProfiles(
        frame: ScoreboardFrame,
        profiles: List<List<Pair<TeamSide, NormalizedRect>>>,
        templates: Map<String, List<ImageSignature>>,
        limit: Int
    ): List<RankedProfile> {
        val representatives = templates.mapValues { (_, variants) -> variants.take(1) }
        return profiles.asSequence().map { slots ->
            val rowScores = slots.map { (_, rect) ->
                val crop = SignatureMath.crop(frame, rect)
                val signature = SignatureMath.signature(crop.rgba, crop.width, crop.height)
                val templateScore = representatives.values.maxOfOrNull { variants ->
                    variants.maxOf { template -> SignatureMath.quickSimilarity(signature, template) }
                } ?: 0f
                val texture = SignatureMath.textureScore(crop)
                templateScore * 0.70f + texture * 0.30f
            }
            val sorted = rowScores.sorted()
            val median = if (sorted.isEmpty()) 0f else sorted[sorted.size / 2]
            val mean = if (rowScores.isEmpty()) 0f else rowScores.average().toFloat()
            val weakest = sorted.firstOrNull() ?: 0f
            RankedProfile(slots, (median * 0.56f + mean * 0.29f + weakest * 0.15f).coerceIn(0f, 1f))
        }
            .sortedByDescending(RankedProfile::score)
            .take(limit.coerceAtLeast(1))
            .toList()
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
        val quality = coverage * 0.45f + confidence * 0.31f + uniqueness * 0.07f + result.profileScore * 0.17f
        return quality.coerceIn(0f, 1f)
    }
}
