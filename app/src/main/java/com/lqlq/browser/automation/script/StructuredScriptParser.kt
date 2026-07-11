package com.lqlq.browser.automation.script

import org.json.JSONArray
import org.json.JSONObject

object StructuredScriptParser {
    fun parse(
        rawText: String,
        policy: ContentDurationPolicy
    ): StructuredScript? {
        parseJson(rawText, policy)?.let { return it }
        parseJsonLike(rawText, policy)?.let { return it }
        return parseFallback(rawText, policy)
    }

    private fun parseJson(rawText: String, policy: ContentDurationPolicy): StructuredScript? {
        val candidate = rawText.trim()
        if (!candidate.startsWith("{")) return null
        return runCatching {
            val root = JSONObject(candidate)
            val segments = mutableListOf<StructuredScriptSegment>()
            root.optJSONObject("intro")?.let { intro ->
                segments += segmentFromJson(intro, ScriptSegmentKind.INTRO, null)
            }
            val items = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                segments += segmentFromJson(item, ScriptSegmentKind.ITEM, item.optInt("index", i + 1))
            }
            root.optJSONObject("outro")?.let { outro ->
                segments += segmentFromJson(outro, ScriptSegmentKind.OUTRO, null)
            }
            if (segments.isEmpty()) return null
            StructuredScript(
                policy = policy,
                segments = segments,
                rawResponse = rawText
            )
        }.getOrNull()
    }

    private fun segmentFromJson(
        node: JSONObject,
        kind: ScriptSegmentKind,
        fallbackIndex: Int?
    ): StructuredScriptSegment {
        val title = node.optString("title").trim()
        val voiceText = node.optString("voiceText").trim()
        val onScreenText = node.optString("onScreenText").trim().ifBlank { title }
        val visualQuery = node.optString("visualQuery").trim().ifBlank { title.ifBlank { voiceText } }
        val durationMs = node.optLong("durationMs").takeIf { it > 0L } ?: defaultDurationMs(kind)
        return StructuredScriptSegment(
            kind = kind,
            index = fallbackIndex,
            title = title.ifBlank { kind.name.lowercase() },
            voiceText = voiceText.ifBlank { title },
            onScreenText = onScreenText,
            visualQuery = visualQuery,
            durationMs = durationMs
        )
    }

    private fun parseFallback(rawText: String, policy: ContentDurationPolicy): StructuredScript? {
        val normalizedLines = rawText
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (normalizedLines.isEmpty()) return null

        val numberedItemRegex = Regex("^\\s*(\\d{1,2})[\\).:-]?\\s+(.*)$")
        val itemLines = normalizedLines.mapNotNull { line ->
            val match = numberedItemRegex.find(line) ?: return@mapNotNull null
            val index = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            index to match.groupValues[2].trim()
        }

        if (policy.isListicle && itemLines.isNotEmpty()) {
            val segments = mutableListOf<StructuredScriptSegment>()
            if (policy.includeIntro) {
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.INTRO,
                    index = null,
                    title = "Intro",
                    voiceText = normalizedLines.first(),
                    onScreenText = normalizedLines.first().take(80),
                    visualQuery = normalizedLines.first(),
                    durationMs = 4_000L
                )
            }
            itemLines.forEach { (index, content) ->
                val title = content.substringBefore(". ").takeIf { it.length in 4..80 } ?: "Muc $index"
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.ITEM,
                    index = index,
                    title = title,
                    voiceText = content,
                    onScreenText = title,
                    visualQuery = content,
                    durationMs = defaultDurationMs(ScriptSegmentKind.ITEM)
                )
            }
            if (policy.includeOutro) {
                val outroLine = normalizedLines.last()
                segments += StructuredScriptSegment(
                    kind = ScriptSegmentKind.OUTRO,
                    index = null,
                    title = "Outro",
                    voiceText = outroLine,
                    onScreenText = outroLine.take(80),
                    visualQuery = outroLine,
                    durationMs = 4_000L
                )
            }
            return StructuredScript(
                policy = policy,
                segments = segments,
                rawResponse = rawText
            )
        }

        val singleText = normalizedLines.joinToString(" ")
        return StructuredScript(
            policy = policy,
            segments = listOf(
                StructuredScriptSegment(
                    kind = ScriptSegmentKind.ITEM,
                    index = 1,
                    title = singleText.take(80),
                    voiceText = singleText,
                    onScreenText = singleText.take(80),
                    visualQuery = singleText.take(160),
                    durationMs = policy.targetDurationMs
                )
            ),
            rawResponse = rawText
        )
    }

    private fun parseJsonLike(rawText: String, policy: ContentDurationPolicy): StructuredScript? {
        if (!rawText.contains("\"items\"")) {
            return null
        }
        val segments = mutableListOf<StructuredScriptSegment>()
        parseJsonLikeSegment(rawText, "intro", ScriptSegmentKind.INTRO)?.let(segments::add)
        ITEM_BLOCK_REGEX.findAll(rawText).forEach { match ->
            segments += StructuredScriptSegment(
                kind = ScriptSegmentKind.ITEM,
                index = match.groupValues[1].toIntOrNull(),
                title = match.groupValues[2].trim(),
                voiceText = match.groupValues[3].trim(),
                onScreenText = match.groupValues[4].trim(),
                visualQuery = match.groupValues[5].trim(),
                durationMs = match.groupValues[6].toLongOrNull() ?: defaultDurationMs(ScriptSegmentKind.ITEM)
            )
        }
        parseJsonLikeSegment(rawText, "outro", ScriptSegmentKind.OUTRO)?.let(segments::add)
        if (segments.isEmpty()) {
            return null
        }
        return StructuredScript(
            policy = policy,
            segments = segments,
            rawResponse = rawText
        )
    }

    private fun parseJsonLikeSegment(
        rawText: String,
        key: String,
        kind: ScriptSegmentKind
    ): StructuredScriptSegment? {
        val regex = Regex(
            """"$key"\s*:\s*\{\s*"title"\s*:\s*"([^"]*)"\s*,\s*"voiceText"\s*:\s*"([^"]*)"\s*,\s*"onScreenText"\s*:\s*"([^"]*)"\s*,\s*"visualQuery"\s*:\s*"([^"]*)"\s*,\s*"durationMs"\s*:\s*(\d+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(rawText) ?: return null
        return StructuredScriptSegment(
            kind = kind,
            index = null,
            title = match.groupValues[1].trim(),
            voiceText = match.groupValues[2].trim(),
            onScreenText = match.groupValues[3].trim(),
            visualQuery = match.groupValues[4].trim(),
            durationMs = match.groupValues[5].toLongOrNull() ?: defaultDurationMs(kind)
        )
    }

    private fun defaultDurationMs(kind: ScriptSegmentKind): Long {
        return when (kind) {
            ScriptSegmentKind.INTRO, ScriptSegmentKind.OUTRO -> 4_000L
            ScriptSegmentKind.ITEM -> 6_000L
        }
    }

    private val ITEM_BLOCK_REGEX = Regex(
        """\{\s*"index"\s*:\s*(\d+)\s*,\s*"title"\s*:\s*"([^"]*)"\s*,\s*"voiceText"\s*:\s*"([^"]*)"\s*,\s*"onScreenText"\s*:\s*"([^"]*)"\s*,\s*"visualQuery"\s*:\s*"([^"]*)"\s*,\s*"durationMs"\s*:\s*(\d+)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
}
