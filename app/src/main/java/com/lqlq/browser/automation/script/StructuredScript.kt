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
    val rawResponse: String,
    // Chu the chinh lap lai xuyen video (vd "Thach Hao") do Gemini quyet dinh.
    // Rong = video dang danh sach nhieu chu the khac nhau (vd "top 10 nhan vat"),
    // khi do KHONG chen chu the chung vao tu khoa tung canh.
    val mainSubject: String = ""
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
