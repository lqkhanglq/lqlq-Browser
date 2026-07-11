package com.lqlq.browser.automation.script

enum class ScriptSegmentKind {
    INTRO,
    ITEM,
    OUTRO
}

data class StructuredScriptSegment(
    val kind: ScriptSegmentKind,
    val index: Int?,
    val title: String,
    val voiceText: String,
    val onScreenText: String,
    val visualQuery: String,
    val durationMs: Long
)

data class StructuredScript(
    val policy: ContentDurationPolicy,
    val segments: List<StructuredScriptSegment>,
    val rawResponse: String
) {
    val itemCount: Int
        get() = segments.count { it.kind == ScriptSegmentKind.ITEM }

    val sceneCount: Int
        get() = segments.size

    val totalDurationMs: Long
        get() = segments.sumOf { it.durationMs }

    fun fullVoiceText(): String {
        return segments.joinToString("\n\n") { it.voiceText.trim() }.trim()
    }
}
