package com.herolens.app.postmatch

/**
 * Editable values recovered from one post-match scoreboard row.
 *
 * OCR is not authoritative: a missing or unreadable cell remains null so the review UI can ask
 * the player to correct it before the result is saved.
 */
data class PostMatchStatsDraft(
    val playerName: String? = null,
    val eliminations: Int? = null,
    val assists: Int? = null,
    val deaths: Int? = null,
    val damage: Int? = null,
    val healing: Int? = null,
    val mitigation: Int? = null,
    val rawOcrText: String = ""
) {
    val isComplete: Boolean
        get() = eliminations != null &&
            assists != null &&
            deaths != null &&
            damage != null &&
            healing != null &&
            mitigation != null
}

enum class PostMatchParseWarning {
    /** The six values were interpreted in Overwatch scoreboard order: E, A, D, DMG, H, MIT. */
    POSITIONAL_COLUMNS_ASSUMED,

    /** At least one letter O/o inside a numeric token was interpreted as zero. */
    OCR_ZERO_CORRECTED,

    /** The selected row did not contain all six supported statistics. */
    MISSING_FIELDS,

    /** No player alias or YOU marker was found; the only plausible row was used. */
    PLAYER_MARKER_NOT_FOUND,

    /** More than one row was plausible and none could be safely identified as the user's row. */
    AMBIGUOUS_PLAYER_ROW,

    /** No row with enough scoreboard statistics could be found. */
    PLAYER_ROW_NOT_FOUND,

    /** expectedTeamSize was supplied with a value other than 5 or 6. */
    INVALID_EXPECTED_TEAM_SIZE,

    /** Text-derived team size and the caller's expected size disagree. */
    EXPECTED_TEAM_SIZE_MISMATCH
}

data class PostMatchScoreboardParseResult(
    val draft: PostMatchStatsDraft?,
    val confidence: Int,
    val detectedTeamSize: Int?,
    val candidateRowCount: Int,
    val warnings: Set<PostMatchParseWarning>
)

/**
 * Dependency-free parser for OCR text already produced by a camera or gallery OCR implementation.
 *
 * Each item in [textBlocks] may be one OCR line, one row/block, or a multiline OCR block. Passing a
 * known BattleTag or display name in [playerAliases] is the safest way to select a row. With no
 * alias, an isolated `YOU` marker is recognized. The parser never guesses between multiple
 * unmarked player rows.
 */
object PostMatchScoreboardTextParser {
    fun parseUserRow(
        textBlocks: List<String>,
        playerAliases: Collection<String> = emptyList(),
        expectedTeamSize: Int? = null
    ): PostMatchScoreboardParseResult {
        val normalizedBlocks = textBlocks
            .map { it.replace('\r', '\n') }
            .filter { it.isNotBlank() }
        val lines = normalizedBlocks
            .flatMap { block -> block.lineSequence().map(String::trim).filter(String::isNotBlank).toList() }

        val warnings = linkedSetOf<PostMatchParseWarning>()
        val validExpectedSize = expectedTeamSize?.takeIf { it == 5 || it == 6 }
        if (expectedTeamSize != null && validExpectedSize == null) {
            warnings += PostMatchParseWarning.INVALID_EXPECTED_TEAM_SIZE
        }

        val aliases = aliasMatchers(playerAliases)
        val directRows = lines.mapNotNull { line ->
            parseCandidate(CandidateSource(line, SourceKind.LINE, 1), aliases)
                ?.takeIf { it.draft.isComplete && it.numericTokenCount >= STAT_COUNT }
        }
        val candidateRowCount = directRows.size
        val inferredTeamSize = inferTeamSize(lines, candidateRowCount)
        if (validExpectedSize != null && inferredTeamSize != null && validExpectedSize != inferredTeamSize) {
            warnings += PostMatchParseWarning.EXPECTED_TEAM_SIZE_MISMATCH
        }
        val detectedTeamSize = inferredTeamSize ?: validExpectedSize

        val sources = candidateSources(normalizedBlocks, lines, aliases)
        val parsed = sources.mapNotNull { source -> parseCandidate(source, aliases) }
        val targeted = parsed.filter { it.target != null && it.fieldCount >= MIN_TARGETED_FIELDS }

        val selected = if (targeted.isNotEmpty()) {
            targeted.maxWithOrNull(
                compareBy<ParsedCandidate> { it.selectionScore }
                    .thenByDescending { it.source.text.length }
            )
        } else {
            val fallbackRows = directRows.distinctBy { canonicalRowKey(it.draft) }
            when (fallbackRows.size) {
                0 -> null
                1 -> fallbackRows.single().also {
                    warnings += PostMatchParseWarning.PLAYER_MARKER_NOT_FOUND
                }
                else -> {
                    warnings += PostMatchParseWarning.AMBIGUOUS_PLAYER_ROW
                    null
                }
            }
        }

        if (selected == null) {
            if (PostMatchParseWarning.AMBIGUOUS_PLAYER_ROW !in warnings) {
                warnings += PostMatchParseWarning.PLAYER_ROW_NOT_FOUND
            }
            return PostMatchScoreboardParseResult(
                draft = null,
                confidence = 0,
                detectedTeamSize = detectedTeamSize,
                candidateRowCount = candidateRowCount,
                warnings = warnings
            )
        }

        warnings += selected.warnings
        val confidence = confidenceFor(selected, fallback = selected.target == null)
        return PostMatchScoreboardParseResult(
            draft = selected.draft,
            confidence = confidence,
            detectedTeamSize = detectedTeamSize,
            candidateRowCount = candidateRowCount,
            warnings = warnings
        )
    }

    fun parseUserRow(
        ocrText: String,
        playerAliases: Collection<String> = emptyList(),
        expectedTeamSize: Int? = null
    ): PostMatchScoreboardParseResult = parseUserRow(
        textBlocks = listOf(ocrText),
        playerAliases = playerAliases,
        expectedTeamSize = expectedTeamSize
    )

    private fun candidateSources(
        blocks: List<String>,
        lines: List<String>,
        aliases: List<AliasMatcher>
    ): List<CandidateSource> {
        val sources = linkedMapOf<String, CandidateSource>()

        fun add(source: CandidateSource) {
            val key = source.text.replace(WHITESPACE, " ").trim().uppercase()
            val existing = sources[key]
            if (existing == null || source.kind.preference > existing.kind.preference) {
                sources[key] = source
            }
        }

        lines.forEach { add(CandidateSource(it, SourceKind.LINE, 1)) }
        blocks.forEach { block ->
            val blockLines = block.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            if (blockLines.size in 2..MAX_ROW_BLOCK_LINES) {
                add(CandidateSource(blockLines.joinToString(" | "), SourceKind.BLOCK, blockLines.size))
            }
        }

        lines.forEachIndexed { index, line ->
            if (findTarget(line, aliases) != null) {
                for (windowSize in 2..MAX_ROW_BLOCK_LINES) {
                    val end = (index + windowSize).coerceAtMost(lines.size)
                    if (end <= index + 1) break
                    add(
                        CandidateSource(
                            text = lines.subList(index, end).joinToString(" | "),
                            kind = SourceKind.WINDOW,
                            lineCount = end - index
                        )
                    )
                    if (end == lines.size) break
                }
            }
        }
        return sources.values.toList()
    }

    private fun parseCandidate(
        source: CandidateSource,
        aliases: List<AliasMatcher>
    ): ParsedCandidate? {
        val target = findTarget(source.text, aliases)
        val parseStart = target?.endExclusive ?: 0
        val statsText = source.text.substring(parseStart.coerceIn(0, source.text.length))
        val labelled = labelledValues(statsText)
        val numericTokens = NUMBER_TOKEN.findAll(statsText).map { match ->
            ParsedNumber(
                value = parseNumber(match.value),
                correctedZero = match.value.any { it == 'O' || it == 'o' }
            )
        }.filter { it.value != null }.toList()

        val positional = if (numericTokens.size >= STAT_COUNT) {
            // Scoreboard statistics are the six trailing numbers. Taking the tail also avoids
            // treating digits in a BattleTag or a leading rank marker as eliminations.
            numericTokens.takeLast(STAT_COUNT).map { requireNotNull(it.value) }
        } else {
            emptyList()
        }
        val positionalValues = STAT_ORDER.zip(positional).toMap()
        val labelledMatchesPosition = labelled.values.isNotEmpty() &&
            positional.size == STAT_COUNT &&
            labelled.values.all { (stat, value) -> positionalValues[stat] == value }
        val values = when {
            labelledMatchesPosition -> positionalValues + labelled.values
            labelled.values.isNotEmpty() -> labelled.values
            positional.size == STAT_COUNT -> positionalValues
            else -> emptyMap()
        }
        if (values.isEmpty()) return null

        val draft = PostMatchStatsDraft(
            playerName = resolvePlayerName(source.text, target, aliases),
            eliminations = values[Stat.ELIMINATIONS],
            assists = values[Stat.ASSISTS],
            deaths = values[Stat.DEATHS],
            damage = values[Stat.DAMAGE],
            healing = values[Stat.HEALING],
            mitigation = values[Stat.MITIGATION],
            rawOcrText = source.text
        )
        val parsedWarnings = linkedSetOf<PostMatchParseWarning>()
        if (positional.size == STAT_COUNT && labelled.values.size < STAT_COUNT && values.size == STAT_COUNT) {
            parsedWarnings += PostMatchParseWarning.POSITIONAL_COLUMNS_ASSUMED
        }
        if (numericTokens.any(ParsedNumber::correctedZero) || labelled.correctedZero) {
            parsedWarnings += PostMatchParseWarning.OCR_ZERO_CORRECTED
        }
        if (!draft.isComplete) parsedWarnings += PostMatchParseWarning.MISSING_FIELDS

        val fieldCount = values.size
        val extraNumbers = (numericTokens.size - STAT_COUNT).coerceAtLeast(0)
        val targetStrength = target?.strength ?: 0
        val selectionScore = targetStrength * 1_000 +
            fieldCount * 100 +
            labelled.values.size * 20 +
            source.kind.preference * 10 -
            extraNumbers * 30 -
            source.lineCount

        return ParsedCandidate(
            source = source,
            draft = draft,
            target = target,
            fieldCount = fieldCount,
            labelledFieldCount = labelled.values.size,
            numericTokenCount = numericTokens.size,
            selectionScore = selectionScore,
            warnings = parsedWarnings
        )
    }

    private fun labelledValues(text: String): LabelledValues {
        val values = linkedMapOf<Stat, Int>()
        var correctedZero = false
        LABEL.findAll(text).forEach { match ->
            val stat = canonicalStat(match.value) ?: return@forEach
            var cursor = match.range.last + 1
            while (cursor < text.length && text[cursor].isLabelValueSeparator()) cursor++
            val number = NUMBER_AT_START.find(text.substring(cursor)) ?: return@forEach
            val parsed = parseNumber(number.value) ?: return@forEach
            values.putIfAbsent(stat, parsed)
            if (number.value.any { it == 'O' || it == 'o' }) correctedZero = true
        }
        return LabelledValues(values, correctedZero)
    }

    private fun resolvePlayerName(
        text: String,
        target: TargetMatch?,
        aliases: List<AliasMatcher>
    ): String? {
        if (target?.explicit == true) return target.displayName

        if ('|' in text) {
            text.split('|').forEach { column ->
                val candidate = cleanPlayerNamePrefix(column, aliases)
                if (candidate != null) return candidate
            }
        }

        val firstNumber = NUMBER_TOKEN.find(text)?.range?.first ?: text.length
        return cleanPlayerNamePrefix(text.substring(0, firstNumber), aliases)
    }

    private fun cleanPlayerNamePrefix(raw: String, aliases: List<AliasMatcher>): String? {
        var prefix = raw
        aliases.forEach { matcher -> prefix = matcher.regex.replace(prefix, " ") }
        prefix = YOU_MARKER.replace(prefix, " ")
        prefix = LABEL.replace(prefix, " ")
        prefix = TEAM_HEADING.replace(prefix, " ")
        prefix = prefix
            .replace(Regex("[|:;/=()\\[\\]{}<>]+"), " ")
            .replace(WHITESPACE, " ")
            .trim(' ', '-', '_', '.')
        return prefix.takeIf { candidate ->
            candidate.length >= 2 && candidate.any(Char::isLetter)
        }
    }

    private fun findTarget(text: String, aliases: List<AliasMatcher>): TargetMatch? {
        aliases.forEach { alias ->
            alias.regex.find(text)?.let { match ->
                return TargetMatch(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    strength = if (alias.explicit) 3 else 2,
                    explicit = alias.explicit,
                    displayName = alias.displayName
                )
            }
        }
        YOU_MARKER.find(text)?.let { match ->
            return TargetMatch(
                start = match.range.first,
                endExclusive = match.range.last + 1,
                strength = 2,
                explicit = false,
                displayName = null
            )
        }
        return null
    }

    private fun aliasMatchers(playerAliases: Collection<String>): List<AliasMatcher> {
        val matchers = mutableListOf<AliasMatcher>()
        playerAliases.map(String::trim).filter(String::isNotBlank).forEach { displayName ->
            val variants = linkedSetOf(displayName)
            displayName.substringBefore('#').trim().takeIf { it.length >= 2 }?.let(variants::add)
            variants.sortedByDescending(String::length).forEach { variant ->
                val parts = variant.split(Regex("[^A-Za-z0-9]+")).filter(String::isNotBlank)
                if (parts.isEmpty()) return@forEach
                val pattern = parts.joinToString("[\\s#._-]*") { Regex.escape(it) }
                matchers += AliasMatcher(
                    regex = Regex("(?i)(?<![A-Za-z0-9])$pattern(?![A-Za-z0-9])"),
                    displayName = displayName,
                    explicit = true
                )
            }
        }
        return matchers.distinctBy { it.regex.pattern }
    }

    private fun inferTeamSize(lines: List<String>, candidateRowCount: Int): Int? {
        lines.forEach { line ->
            TEAM_SIZE.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return when (candidateRowCount) {
            10 -> 5
            12 -> 6
            else -> null
        }
    }

    private fun confidenceFor(candidate: ParsedCandidate, fallback: Boolean): Int {
        var confidence = 12
        confidence += candidate.fieldCount * 8
        confidence += candidate.labelledFieldCount * 2
        confidence += when {
            candidate.target?.explicit == true -> 25
            candidate.target != null -> 20
            fallback -> 5
            else -> 0
        }
        if (candidate.numericTokenCount == STAT_COUNT) confidence += 8
        confidence -= (candidate.numericTokenCount - STAT_COUNT).coerceAtLeast(0) * 4
        return confidence.coerceIn(0, 100)
    }

    private fun canonicalRowKey(draft: PostMatchStatsDraft): String = listOf(
        draft.playerName.orEmpty().uppercase(),
        draft.eliminations,
        draft.assists,
        draft.deaths,
        draft.damage,
        draft.healing,
        draft.mitigation
    ).joinToString("|")

    private fun canonicalStat(raw: String): Stat? {
        val label = raw.uppercase().replace('0', 'O')
        return when {
            label == "E" || label.startsWith("ELIM") -> Stat.ELIMINATIONS
            label == "A" || label.startsWith("ASSIST") || label.startsWith("AST") -> Stat.ASSISTS
            label == "D" || label.startsWith("DEATH") -> Stat.DEATHS
            label == "DMG" || label.startsWith("DAMAGE") -> Stat.DAMAGE
            label == "H" || label.startsWith("HEAL") -> Stat.HEALING
            label.startsWith("MIT") -> Stat.MITIGATION
            else -> null
        }
    }

    private fun parseNumber(raw: String): Int? {
        val normalized = buildString(raw.length) {
            raw.forEach { character ->
                when {
                    character.isDigit() -> append(character)
                    character == 'O' || character == 'o' -> append('0')
                    character == ',' || character == '.' || character == '\'' || character == '\u2019' -> Unit
                    else -> return null
                }
            }
        }
        if (normalized.isEmpty()) return null
        return normalized.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE }?.toInt()
    }

    private fun Char.isLabelValueSeparator(): Boolean =
        isWhitespace() || this in charArrayOf(':', '=', '|', '-', '/', '\\', '.', '_', '•', '·', '—', '–')

    private enum class Stat { ELIMINATIONS, ASSISTS, DEATHS, DAMAGE, HEALING, MITIGATION }

    private enum class SourceKind(val preference: Int) {
        WINDOW(1),
        BLOCK(2),
        LINE(3)
    }

    private data class CandidateSource(
        val text: String,
        val kind: SourceKind,
        val lineCount: Int
    )

    private data class ParsedCandidate(
        val source: CandidateSource,
        val draft: PostMatchStatsDraft,
        val target: TargetMatch?,
        val fieldCount: Int,
        val labelledFieldCount: Int,
        val numericTokenCount: Int,
        val selectionScore: Int,
        val warnings: Set<PostMatchParseWarning>
    )

    private data class ParsedNumber(val value: Int?, val correctedZero: Boolean)

    private data class LabelledValues(
        val values: Map<Stat, Int>,
        val correctedZero: Boolean
    )

    private data class TargetMatch(
        val start: Int,
        val endExclusive: Int,
        val strength: Int,
        val explicit: Boolean,
        val displayName: String?
    )

    private data class AliasMatcher(
        val regex: Regex,
        val displayName: String,
        val explicit: Boolean
    )

    private val STAT_ORDER = listOf(
        Stat.ELIMINATIONS,
        Stat.ASSISTS,
        Stat.DEATHS,
        Stat.DAMAGE,
        Stat.HEALING,
        Stat.MITIGATION
    )
    private val NUMBER_TOKEN = Regex("(?i)(?<![A-Z0-9])[0-9O](?:[0-9O,.\\u2019']*)(?![A-Z0-9])")
    private val NUMBER_AT_START = Regex("(?i)^[0-9O](?:[0-9O,.\\u2019']*)")
    private val LABEL = Regex(
        "(?i)(?<![A-Z0-9])(?:" +
            "ELIMINATI[O0]NS?|ELIMS?|ELIM|" +
            "ASSISTS?|ASTS?|AST|" +
            "DEATHS?|DEATH|" +
            "DAMAGE|DMG|" +
            "HEALING|HEALS?|HEAL|" +
            "MITIGATI[O0]N|MITIGATED|MIT|" +
            "E|A|D|H" +
            ")(?![A-Z0-9])"
    )
    private val YOU_MARKER = Regex("(?i)(?<![A-Z0-9])YOU(?![A-Z0-9])")
    private val TEAM_HEADING = Regex("(?i)\\b(?:YOUR|ALLY|ENEMY)\\s+TEAM\\b")
    private val TEAM_SIZE = Regex("(?i)(?<![0-9])([56])\\s*[VX]\\s*\\1(?![0-9])")
    private val WHITESPACE = Regex("\\s+")

    private const val STAT_COUNT = 6
    private const val MIN_TARGETED_FIELDS = 2
    private const val MAX_ROW_BLOCK_LINES = 9
}
