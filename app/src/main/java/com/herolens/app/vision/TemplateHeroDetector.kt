package com.herolens.app.vision

import android.content.Context

/**
 * Experimental on-device detector. V6.3 first locates the actual blue/red scoreboard
 * panels, then compares only the portrait cells. This is substantially more robust
 * than fixed screen coordinates and supports both 5v5 and 6v6 scoreboards.
 */
class TemplateHeroDetector(context: Context) : HeroDetector, AutoCloseable {
    private val repository = HeroTemplateRepository(context.applicationContext)
    private val scoreboardDetector = OnnxScoreboardDetector(context.applicationContext)
    private val aiClassifier = OnnxHeroClassifier(context.applicationContext)
    @Volatile private var cachedTemplates: Map<String, List<ImageSignature>>? = null
    @Volatile private var cachedFailures: List<String> = emptyList()

    suspend fun warmUp(onProgress: (String) -> Unit = {}): Int {
        if (aiClassifier.isAvailable) {
            cachedTemplates = emptyMap()
            val detectorStatus = if (scoreboardDetector.isAvailable) " + full-scoreboard geometry" else ""
            onProgress("AI classifier ready$detectorStatus · ${aiClassifier.modelLabelCount} labels")
            return (aiClassifier.modelLabelCount - 1).coerceAtLeast(1)
        }
        ensureTemplates(onProgress)
        if (scoreboardDetector.isAvailable) {
            onProgress("Full-scoreboard geometry + template recognition ready")
        }
        return cachedTemplates?.size ?: 0
    }

    val aiAvailable: Boolean
        get() = aiClassifier.isAvailable

    override fun close() {
        scoreboardDetector.close()
        aiClassifier.close()
    }

    suspend fun detectAuto(
        frame: ScoreboardFrame,
        onProgress: (String) -> Unit = {},
        locatedRegion: ScoreboardRegion? = null,
        preferredTeamSize: Int? = null
    ): AutoDetectionResult {
        ensureTemplates(onProgress)
        val region = locatedRegion ?: ScoreboardLocator.locate(frame)
        val forcedTeamSize = preferredTeamSize?.takeIf { it in 3..6 }
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
        val fullGeometryResult = detectWithFullScoreboardGeometry(
            frame = frame,
            region = region,
            forcedTeamSize = forcedTeamSize,
            estimatedTeamSize = structural.teamSize,
            onProgress = onProgress
        )
        if (fullGeometryResult != null && fullGeometryResult.isNearCompleteFullDetection()) {
            val layout = detectorLayout(fullGeometryResult, region)
            return AutoDetectionResult(
                result = fullGeometryResult,
                layout = layout,
                quality = resultQuality(fullGeometryResult),
                scoreboardRegion = region,
                slotRects = fullGeometryResult.slotRects,
                teamSize = fullGeometryResult.teamSize
            )
        }
        val detectorResult = scoreboardDetector.detect(
            frame = frame,
            region = region,
            preferredTeamSize = forcedTeamSize ?: structural.teamSize
        )
        if (detectorResult != null && detectorResult.isNearCompleteFullDetection()) {
            val layout = detectorLayout(detectorResult, region)
            return AutoDetectionResult(
                result = detectorResult,
                layout = layout,
                quality = resultQuality(detectorResult),
                scoreboardRegion = region,
                slotRects = detectorResult.slotRects,
                teamSize = detectorResult.teamSize
            )
        }
        val candidateSizes = when {
            forcedTeamSize != null -> listOf(forcedTeamSize)
            structural.teamSize == 6 -> listOf(6, 5)
            structural.teamSize == 5 -> listOf(5, 6)
            else -> listOf(5, 6)
        }
        fun adjustedQuality(result: DetectionResult): Float {
            val structuralScore = when (result.teamSize) {
                6 -> structural.sixScore
                5 -> structural.fiveScore
                else -> 0f
            }
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
        // Always compare both portrait sides. A weak left-side texture match used
        // to short-circuit before the correct right-side profile was evaluated.
        val rightCandidates = candidateSizes.map { teamSize ->
            detectLocalized(frame, region, ScoreboardLayout.PORTRAITS_RIGHT, teamSize, onProgress)
        }
        val fullCandidates = listOfNotNull(
            fullGeometryResult?.takeIf { it.isUsefulFullDetection() },
            detectorResult?.takeIf { it.isUsefulFullDetection() }
        )
        val best = (leftCandidates + rightCandidates + fullCandidates).maxByOrNull(::adjustedQuality)
            ?: DetectionResult(
                placeholderDetections(forcedTeamSize ?: structural.teamSize ?: 5),
                cachedTemplates.orEmpty().size,
                teamSize = forcedTeamSize ?: structural.teamSize ?: 5
            )
        val layout = detectorLayout(best, region)
        val structuralMessage = when {
            forcedTeamSize == 6 -> "Six-row scoreboard selected."
            forcedTeamSize == 5 -> "Five-row scoreboard selected."
            forcedTeamSize == 4 -> "Four-row Arcade scoreboard selected."
            forcedTeamSize == 3 -> "Three-row Arcade scoreboard selected."
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
            val structural = ScoreboardTeamSizeEstimator.estimate(frame, region)
            detectWithFullScoreboardGeometry(
                frame,
                region,
                forcedTeamSize = null,
                estimatedTeamSize = structural.teamSize,
                onProgress = onProgress
            )
                ?.takeIf { it.isNearCompleteFullDetection() }
                ?.let { return it }
            scoreboardDetector.detect(frame, region)?.takeIf { it.isNearCompleteFullDetection() }?.let {
                return it
            }
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
        detectWithFullScoreboardGeometry(
            frame,
            region,
            forcedTeamSize = preferredTeamSize,
            estimatedTeamSize = ScoreboardTeamSizeEstimator.estimate(frame, region).teamSize,
            onProgress = onProgress
        )?.takeIf { it.isNearCompleteFullDetection() }?.let { return it }
        scoreboardDetector.detect(frame, region, preferredTeamSize)
            ?.takeIf { it.isNearCompleteFullDetection() }
            ?.let { return it }
        if (layout == ScoreboardLayout.AUTO) {
            return detectAuto(frame, onProgress, region, preferredTeamSize).result
        }
        val structural = ScoreboardTeamSizeEstimator.estimate(frame, region)
        val candidateSizes = preferredTeamSize?.takeIf { it in 3..6 }?.let { listOf(it) }
            ?: structural.teamSize?.let { listOf(it, if (it == 5) 6 else 5) }
            ?: listOf(5, 6)
        return candidateSizes
            .map { teamSize -> detectLocalized(frame, region, layout, teamSize, onProgress) }
            .maxByOrNull { result ->
                val structuralScore = when (result.teamSize) {
                    6 -> structural.sixScore
                    5 -> structural.fiveScore
                    else -> 0f
                }
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
            limit = 4
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
            val independent = slots.mapIndexed { index, (team, rect) ->
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
                val best = ranked.firstOrNull()
                val second = ranked.getOrNull(1)?.score ?: 0f
                val bestScore = best?.score ?: 0f
                val margin = (bestScore - second).coerceAtLeast(0f)
                val confidence = (bestScore * 0.82f + (margin / 0.24f).coerceIn(0f, 1f) * 0.18f).coerceIn(0f, 0.99f)
                val hasCropConsensus = best != null && (best.votes >= 2 || best.peak >= 0.72f)
                val accepted = best != null && hasCropConsensus && bestScore >= 0.37f && margin >= 0.030f
                HeroDetection(
                    heroId = best?.heroId?.takeIf { accepted },
                    team = team,
                    slot = index % teamSize,
                    confidence = confidence,
                    alternatives = ranked.take(7).map { candidate -> HeroCandidate(candidate.heroId, candidate.score) },
                    bounds = rect
                )
            }
            assignUniqueNeuralHeroes(independent)
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
                if (accepted) used += best.first
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

    private fun detectWithFullScoreboardGeometry(
        frame: ScoreboardFrame,
        region: ScoreboardRegion,
        forcedTeamSize: Int?,
        estimatedTeamSize: Int?,
        onProgress: (String) -> Unit
    ): DetectionResult? {
        if (!scoreboardDetector.isAvailable || !aiClassifier.isAvailable) return null
        val boxes = scoreboardDetector.detectPortraits(frame, region).orEmpty()
        if (boxes.size < 3) return null
        fun teamFor(box: ScoreboardPortraitBox): TeamSide {
            val allyDistance = kotlin.math.abs(box.bounds.centerY - region.allyPanel.centerY)
            val enemyDistance = kotlin.math.abs(box.bounds.centerY - region.enemyPanel.centerY)
            return if (allyDistance <= enemyDistance) TeamSide.ALLY else TeamSide.ENEMY
        }
        val teamCounts = boxes.groupingBy(::teamFor).eachCount()
        val normalizedTeamSize = ScoreboardTeamSizeResolver.resolve(
            forcedTeamSize = forcedTeamSize,
            estimatedTeamSize = estimatedTeamSize,
            allyBoxCount = teamCounts[TeamSide.ALLY] ?: 0,
            enemyBoxCount = teamCounts[TeamSide.ENEMY] ?: 0
        )
        val assigned = mutableMapOf<Pair<TeamSide, Int>, ScoreboardPortraitBox>()
        boxes.sortedByDescending(ScoreboardPortraitBox::confidence).forEach { box ->
            val team = teamFor(box)
            val panel = if (team == TeamSide.ALLY) region.allyPanel else region.enemyPanel
            val relativeY = ((box.bounds.centerY - panel.top) / panel.height).coerceIn(0f, 0.9999f)
            val slot = (relativeY * normalizedTeamSize).toInt().coerceIn(0, normalizedTeamSize - 1)
            assigned.putIfAbsent(team to slot, box)
        }
        if (assigned.size < 3) return null
        val ordered = assigned.entries.sortedWith(
            compareBy<Map.Entry<Pair<TeamSide, Int>, ScoreboardPortraitBox>>(
                { it.key.first.ordinal }, { it.key.second }
            )
        )
        val cropGroups = ordered.map { (_, box) ->
            ScoreboardSlots.neuralVariants(box.bounds).map { SignatureMath.crop(frame, it) }
        }
        val predictions = aiClassifier.classifyBatch(cropGroups.flatten(), topK = 7)
        var offset = 0
        val detectedBySlot = mutableMapOf<Pair<TeamSide, Int>, HeroDetection>()
        ordered.forEachIndexed { index, entry ->
            onProgress("Full-board detection · ${index + 1}/${ordered.size}")
            val count = cropGroups[index].size
            val slotPredictions = predictions.subList(offset, (offset + count).coerceAtMost(predictions.size))
            offset += count
            val evidence = mutableMapOf<String, MutableList<Float>>()
            slotPredictions.forEach { cropPredictions ->
                cropPredictions.forEach { candidate ->
                    evidence.getOrPut(candidate.heroId) { mutableListOf() } += candidate.confidence
                }
            }
            val ranked = evidence.map { (heroId, values) ->
                val sorted = values.sortedDescending()
                val top = sorted.getOrElse(0) { 0f }
                val second = sorted.getOrElse(1) { 0f }
                val third = sorted.getOrElse(2) { 0f }
                val score = top * 0.62f + second * 0.25f + third * 0.08f + values.average().toFloat() * 0.05f
                AggregatedCandidate(heroId, score, top, values.count { it >= 0.20f })
            }.sortedByDescending(AggregatedCandidate::score)
            val best = ranked.firstOrNull()
            val margin = ((best?.score ?: 0f) - (ranked.getOrNull(1)?.score ?: 0f)).coerceAtLeast(0f)
            val classifierConfidence = ((best?.score ?: 0f) * 0.82f +
                (margin / 0.24f).coerceIn(0f, 1f) * 0.18f).coerceIn(0f, 0.99f)
            val confidence = (classifierConfidence * 0.88f + entry.value.confidence * 0.12f).coerceIn(0f, 0.99f)
            val accepted = best != null && (best.votes >= 2 || best.peak >= 0.72f) &&
                best.score >= 0.37f && margin >= 0.030f
            detectedBySlot[entry.key] = HeroDetection(
                heroId = best?.heroId?.takeIf { accepted },
                team = entry.key.first,
                slot = entry.key.second,
                confidence = confidence,
                alternatives = ranked.take(7).map { HeroCandidate(it.heroId, it.score) },
                bounds = entry.value.bounds
            )
        }
        val complete = buildList {
            TeamSide.entries.forEach { team ->
                repeat(normalizedTeamSize) { slot ->
                    add(detectedBySlot[team to slot] ?: HeroDetection(null, team, slot, 0f))
                }
            }
        }
        val resolved = assignUniqueNeuralHeroes(complete)
        val accepted = resolved.count { it.heroId != null }
        return DetectionResult(
            detections = resolved,
            templatesLoaded = 0,
            warnings = buildList {
                add("Full-scoreboard detector active.")
                if (accepted < normalizedTeamSize * 2) add("${normalizedTeamSize * 2 - accepted} slots require review.")
            },
            scoreboardRegion = region,
            slotRects = resolved.mapNotNull { detection -> detection.bounds?.let { detection.team to it } },
            teamSize = normalizedTeamSize,
            profileScore = (assigned.size / (normalizedTeamSize * 2f)).coerceIn(0f, 1f)
        )
    }

    /**
     * Resolve a team together instead of letting early weak slots greedily consume
     * a hero needed by a later strong slot. Score-sorted assignment gives the most
     * certain slot first choice, then uses each losing slot's next-best candidate.
     */
    private fun assignUniqueNeuralHeroes(detections: List<HeroDetection>): List<HeroDetection> {
        val output = detections.toMutableList()
        TeamSide.entries.forEach { team ->
            val teamIndices = detections.indices.filter { index ->
                detections[index].team == team && detections[index].heroId != null
            }
            val edges = teamIndices.flatMap { index ->
                detections[index].alternatives.map { candidate ->
                    NeuralAssignmentEdge(index, candidate)
                }
            }.sortedByDescending { edge -> edge.candidate.confidence }
            val assignedSlots = mutableSetOf<Int>()
            val assignedHeroes = mutableSetOf<String>()
            edges.forEach { edge ->
                if (edge.slotIndex !in assignedSlots &&
                    edge.candidate.heroId !in assignedHeroes &&
                    edge.candidate.confidence >= 0.18f
                ) {
                    val original = detections[edge.slotIndex]
                    val confidence = if (original.heroId == edge.candidate.heroId) {
                        original.confidence
                    } else {
                        (edge.candidate.confidence * 0.90f).coerceIn(0f, original.confidence)
                    }
                    output[edge.slotIndex] = original.copy(
                        heroId = edge.candidate.heroId,
                        confidence = confidence
                    )
                    assignedSlots += edge.slotIndex
                    assignedHeroes += edge.candidate.heroId
                }
            }
            teamIndices.filterNot { it in assignedSlots }.forEach { index ->
                output[index] = output[index].copy(heroId = null, confidence = 0f)
            }
        }
        return output
    }

    private data class NeuralAssignmentEdge(
        val slotIndex: Int,
        val candidate: HeroCandidate
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
        val ranked = profiles.asSequence().map { slots ->
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
        }.toList()

        /*
         * With ONNX active, portrait templates are intentionally not loaded and
         * templateScore is therefore zero. A global texture-only sort used to
         * return several trim/width variants of the same X offset, commonly the
         * bright role-icon or progression-badge column. Keep the strongest
         * vertical/width candidate for every portrait-column offset so the real
         * portrait column always reaches neural classification.
         */
        val candidates = if (templates.isEmpty()) {
            ranked.groupBy { profile ->
                (profile.slots.firstOrNull()?.second?.centerX.orZero() * 10_000f).toInt()
            }.values.mapNotNull { group -> group.maxByOrNull(RankedProfile::score) }
        } else {
            ranked
        }
        return candidates.sortedByDescending(RankedProfile::score)
            .take(limit.coerceAtLeast(1))
    }

    private fun Float?.orZero(): Float = this ?: 0f

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

    private fun DetectionResult.isUsefulFullDetection(): Boolean =
        warnings.any { it == "Full-scoreboard detector active." } &&
            detections.count { it.heroId != null } >= 3

    private fun DetectionResult.isNearCompleteFullDetection(): Boolean =
        isUsefulFullDetection() && detections.count { it.heroId != null } >= (teamSize * 2 - 1).coerceAtLeast(3)

    private fun detectorLayout(result: DetectionResult, region: ScoreboardRegion): ScoreboardLayout {
        val votes = result.detections.mapNotNull { detection ->
            val bounds = detection.bounds ?: return@mapNotNull null
            val panel = if (detection.team == TeamSide.ALLY) region.allyPanel else region.enemyPanel
            if (bounds.centerX <= panel.centerX) -1 else 1
        }
        return if (votes.sum() <= 0) {
            ScoreboardLayout.PORTRAITS_LEFT
        } else {
            ScoreboardLayout.PORTRAITS_RIGHT
        }
    }
}
