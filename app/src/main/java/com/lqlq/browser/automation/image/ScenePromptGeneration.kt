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
    val visualDirection: String = ""
)

interface ScenePromptGenerator {
    suspend fun generateScenePrompts(
        request: ScenePromptGenerationRequest
    ): List<ScenePrompt>
}
