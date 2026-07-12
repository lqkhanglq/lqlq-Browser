package com.lqlq.browser.automation.image

import com.lqlq.browser.automation.script.StructuredScript

data class ScenePromptGenerationRequest(
    val topic: String,
    val generatedScript: String,
    val language: String,
    val visualStyle: String,
    val targetAspectRatio: String,
    val requestedSceneCount: Int,
    val structuredScript: StructuredScript? = null
)

data class ScenePrompt(
    val sceneId: String,
    val ordinal: Int,
    val summary: String,
    val visualPrompt: String,
    val negativePrompt: String?,
    val aspectRatio: String,
    val voiceText: String = "",
    val onScreenText: String = "",
    val plannedDurationMs: Long = 0L,
    val stockSearchQuery: String = "",
    val visualDirection: String = "",
    // Chu ky (hash) cua dau vao anh (cau tim/prompt) tai lan gan-anh gan nhat.
    // Rong = chua tung gan anh. Neu khac voi hash hien tai -> anh da CU (STALE)
    // vi nguoi dung sua tu khoa/prompt sau khi da co anh. Xem AutomationFacade.
    val imageSignature: String = ""
)

interface ScenePromptGenerator {
    suspend fun generateScenePrompts(
        request: ScenePromptGenerationRequest
    ): List<ScenePrompt>
}
