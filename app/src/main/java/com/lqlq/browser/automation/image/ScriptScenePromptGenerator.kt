package com.lqlq.browser.automation.image

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class ScriptScenePromptGenerator : ScenePromptGenerator {

    override suspend fun generateScenePrompts(
        request: ScenePromptGenerationRequest
    ): List<ScenePrompt> {
        validate(request)

        val normalizedLanguage = request.language.trim().ifBlank { "vi" }
        val normalizedStyle = request.visualStyle.trim().ifBlank { DEFAULT_VISUAL_STYLE }
        val normalizedAspectRatio = request.targetAspectRatio.trim().ifBlank { DEFAULT_ASPECT_RATIO }
        val canonicalTopic = extractCanonicalTopic(request.topic)
        request.structuredScript?.let { structured ->
            return structured.segments.mapIndexed { index, segment ->
                val ordinal = index + 1
                val summary = segment.title.ifBlank { summarizeSection(segment.voiceText) }
                val stockSearchQuery = segment.visualQuery.trim()
                val visualDirection = segment.voiceText.trim()
                ScenePrompt(
                    sceneId = "scene-${UUID.randomUUID().toString().substring(0, 8)}",
                    ordinal = ordinal,
                    summary = summary,
                    visualPrompt = buildVisualPrompt(
                        topic = canonicalTopic,
                        summary = summary,
                        keywordText = stockSearchQuery.ifBlank { segment.voiceText },
                        visualDescription = visualDirection,
                        language = normalizedLanguage,
                        visualStyle = normalizedStyle,
                        aspectRatio = normalizedAspectRatio,
                        ordinal = ordinal
                    ),
                    negativePrompt = DEFAULT_NEGATIVE_PROMPT,
                    aspectRatio = normalizedAspectRatio,
                    voiceText = segment.voiceText,
                    onScreenText = segment.onScreenText,
                    plannedDurationMs = segment.durationMs,
                    stockSearchQuery = stockSearchQuery,
                    visualDirection = visualDirection
                )
            }
        }

        parseSceneBlocks(request.generatedScript)
            .takeIf { it.size >= 2 }
            ?.let { sceneBlocks ->
                return sceneBlocks.mapIndexed { index, block ->
                    val ordinal = index + 1
                    val voiceText = block.voiceText.ifBlank { block.rawText }
                    val onScreenText = block.onScreenText.ifBlank { summarizeSection(voiceText.ifBlank { block.headingLine }) }
                    val summary = summarizeSection(onScreenText.ifBlank { voiceText.ifBlank { block.headingLine } })
                    val stockSearchQuery = block.keywordText.trim()
                    val visualDirection = block.visualDescription.trim()
                    ScenePrompt(
                        sceneId = "scene-${UUID.randomUUID().toString().substring(0, 8)}",
                        ordinal = ordinal,
                        summary = summary,
                        visualPrompt = buildVisualPrompt(
                            topic = canonicalTopic,
                            summary = summary,
                            keywordText = block.keywordText.ifBlank { block.headingLine.ifBlank { voiceText } },
                            visualDescription = block.visualDescription.ifBlank { block.rawText },
                            language = normalizedLanguage,
                            visualStyle = normalizedStyle,
                            aspectRatio = normalizedAspectRatio,
                            ordinal = ordinal
                        ),
                        negativePrompt = DEFAULT_NEGATIVE_PROMPT,
                        aspectRatio = normalizedAspectRatio,
                        voiceText = voiceText,
                        onScreenText = onScreenText,
                        plannedDurationMs = block.suggestedDurationMs ?: DEFAULT_FALLBACK_SCENE_DURATION_MS,
                        stockSearchQuery = stockSearchQuery,
                        visualDirection = visualDirection
                    )
                }
            }

        val sceneCount = request.requestedSceneCount.coerceIn(MIN_SCENE_COUNT, MAX_SCENE_COUNT)
        val scriptSections = splitIntoNarrativeSections(request.generatedScript, sceneCount)

        return scriptSections.mapIndexed { index, section ->
            val ordinal = index + 1
            val summary = summarizeSection(section)
            ScenePrompt(
                sceneId = "scene-${UUID.randomUUID().toString().substring(0, 8)}",
                ordinal = ordinal,
                summary = summary,
                visualPrompt = buildVisualPrompt(
                    topic = canonicalTopic,
                    summary = summary,
                    keywordText = summary,
                    visualDescription = section,
                    language = normalizedLanguage,
                    visualStyle = normalizedStyle,
                    aspectRatio = normalizedAspectRatio,
                    ordinal = ordinal
                ),
                negativePrompt = DEFAULT_NEGATIVE_PROMPT,
                aspectRatio = normalizedAspectRatio,
                voiceText = section,
                onScreenText = summary,
                plannedDurationMs = DEFAULT_FALLBACK_SCENE_DURATION_MS,
                stockSearchQuery = summary,
                visualDirection = section
            )
        }
    }

    private fun parseSceneBlocks(script: String): List<SceneBlock> {
        val lines = script
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }

        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    current += ""
                }
                return@forEach
            }
            if (isSceneHeading(line)) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            }
            current += line
        }
        if (current.isNotEmpty()) {
            blocks += current
        }

        return blocks.mapNotNull { blockLines ->
            val headingLine = blockLines.firstOrNull { it.isNotBlank() }.orEmpty()
            if (!isSceneHeading(headingLine)) {
                return@mapNotNull null
            }
            val voiceText = extractField(blockLines, "loi doc")
            val onScreenText = extractField(blockLines, "chu tren man hinh")
            val keywordText = extractField(blockLines, "tu khoa tim anh/video")
            val visualDescription = extractField(blockLines, "mo ta visual")
            val suggestedDurationMs = extractDurationMs(blockLines)
            SceneBlock(
                headingLine = headingLine,
                voiceText = voiceText,
                onScreenText = onScreenText,
                keywordText = keywordText,
                visualDescription = visualDescription,
                suggestedDurationMs = suggestedDurationMs,
                rawText = blockLines.joinToString("\n").trim()
            )
        }
    }

    private fun splitIntoNarrativeSections(
        script: String,
        targetCount: Int
    ): List<String> {
        val normalizedScript = script
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val sentences = normalizedScript
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) {
            return listOf(normalizedScript.take(MAX_SECTION_CHARS))
        }

        val actualCount = min(targetCount, max(1, sentences.size))
        val sections = MutableList(actualCount) { StringBuilder() }
        sentences.forEachIndexed { index, sentence ->
            val bucket = min(actualCount - 1, index * actualCount / max(1, sentences.size))
            val target = sections[bucket]
            if (target.isNotEmpty()) {
                target.append(' ')
            }
            target.append(sentence)
        }

        return sections
            .map { it.toString().trim() }
            .filter { it.isNotBlank() }
    }

    private fun summarizeSection(section: String): String {
        val cleaned = section.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= SUMMARY_LIMIT) {
            return cleaned
        }
        val cutAt = cleaned.lastIndexOf(' ', SUMMARY_LIMIT).takeIf { it > 0 } ?: SUMMARY_LIMIT
        return cleaned.substring(0, cutAt).trim()
    }

    private fun buildVisualPrompt(
        topic: String,
        summary: String,
        keywordText: String,
        visualDescription: String,
        language: String,
        visualStyle: String,
        aspectRatio: String,
        ordinal: Int
    ): String {
        val keywords = keywordText.replace(Regex("\\s+"), " ").trim().take(MAX_SECTION_CHARS)
        val visual = visualDescription.replace(Regex("\\s+"), " ").trim().take(MAX_SECTION_CHARS)
        return buildString {
            append("Vertical short scene ")
            append(ordinal)
            append(" about ")
            append(topic.trim())
            append(". ")
            append("Scene summary: ")
            append(summary)
            append(". ")
            append("Search keywords: ")
            append(keywords.ifBlank { summary })
            append(". ")
            append("Visual direction: ")
            append(visual.ifBlank { summary })
            append(". ")
            append("Show one clear moment with readable composition, strong focal subject, and no text overlay. ")
            append("Visual style: ")
            append(visualStyle)
            append(". ")
            append("Aspect ratio: ")
            append(aspectRatio)
            append(". ")
            append("Language context: ")
            append(language)
        }
    }

    private fun extractCanonicalTopic(userInput: String): String {
        userInput
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .firstOrNull { normalize(it).startsWith("chu de:") }
            ?.substringAfter(':', "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return userInput.replace(Regex("\\s+"), " ").trim()
    }

    private fun extractField(lines: List<String>, fieldName: String): String {
        val normalizedField = normalize(fieldName)
        val prefixMatches = listOf(
            "$normalizedField:",
            "$normalizedField -",
            "$normalizedField "
        )
        val rawLine = lines.firstOrNull { line ->
            val normalizedLine = normalize(line)
            prefixMatches.any { normalizedLine.startsWith(it) }
        }.orEmpty()
        if (rawLine.isBlank()) {
            return ""
        }
        return rawLine.substringAfter(':', "")
            .ifBlank { rawLine.substringAfter('-', "") }
            .trim()
    }

    private fun extractDurationMs(lines: List<String>): Long? {
        val durationLine = lines.firstOrNull { normalize(it).startsWith("thoi luong de xuat") } ?: return null
        val normalized = normalize(durationLine)
        Regex("(\\d{1,3})\\s*(giay|s)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return it * 1000L
        }
        Regex("(\\d{1,2})\\s*(phut|min)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
            return it * 60_000L
        }
        return null
    }

    private fun isSceneHeading(line: String): Boolean {
        val normalized = normalize(line)
        return SCENE_HEADING_REGEX.matches(normalized)
    }

    private fun normalize(value: String): String {
        return buildString(value.length) {
            value.lowercase().forEach { character ->
                append(
                    when (character) {
                        'à', 'á', 'ạ', 'ả', 'ã', 'â', 'ầ', 'ấ', 'ậ', 'ẩ', 'ẫ', 'ă', 'ằ', 'ắ', 'ặ', 'ẳ', 'ẵ' -> 'a'
                        'è', 'é', 'ẹ', 'ẻ', 'ẽ', 'ê', 'ề', 'ế', 'ệ', 'ể', 'ễ' -> 'e'
                        'ì', 'í', 'ị', 'ỉ', 'ĩ' -> 'i'
                        'ò', 'ó', 'ọ', 'ỏ', 'õ', 'ô', 'ồ', 'ố', 'ộ', 'ổ', 'ỗ', 'ơ', 'ờ', 'ớ', 'ợ', 'ở', 'ỡ' -> 'o'
                        'ù', 'ú', 'ụ', 'ủ', 'ũ', 'ư', 'ừ', 'ứ', 'ự', 'ử', 'ữ' -> 'u'
                        'ỳ', 'ý', 'ỵ', 'ỷ', 'ỹ' -> 'y'
                        'đ' -> 'd'
                        else -> character
                    }
                )
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun validate(request: ScenePromptGenerationRequest) {
        require(request.topic.trim().isNotEmpty()) { "Topic is required." }
        require(request.generatedScript.trim().isNotEmpty()) { "Generated script is required." }
        require(request.requestedSceneCount in MIN_SCENE_COUNT..MAX_SCENE_COUNT) {
            "Requested scene count is outside the safe range."
        }
    }

    private data class SceneBlock(
        val headingLine: String,
        val voiceText: String,
        val onScreenText: String,
        val keywordText: String,
        val visualDescription: String,
        val suggestedDurationMs: Long?,
        val rawText: String
    )

    companion object {
        const val DEFAULT_VISUAL_STYLE: String = "cinematic mobile-friendly digital illustration"
        const val DEFAULT_ASPECT_RATIO: String = "9:16"
        const val DEFAULT_NEGATIVE_PROMPT: String =
            "blurry, distorted anatomy, unreadable text, watermark, extra limbs, duplicate subjects"

        private val SCENE_HEADING_REGEX = Regex("^(scene|canh)\\s*(so)?\\s*:?\\s*\\d{1,2}.*$")

        private const val MIN_SCENE_COUNT = 1
        private const val MAX_SCENE_COUNT = 20
        private const val SUMMARY_LIMIT = 140
        private const val MAX_SECTION_CHARS = 420
        private const val DEFAULT_FALLBACK_SCENE_DURATION_MS = 6_000L
    }
}
