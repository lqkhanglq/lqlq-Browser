package com.lqlq.browser.automation.video

import com.lqlq.browser.automation.artifact.AutomationSavedArtifact
import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.visual.VisualAssetPlan

data class VideoRenderRequest(
    val jobId: String,
    val generatedText: String,
    val scenePrompts: List<ScenePrompt>,
    val assetPlans: List<VisualAssetPlan>,
    val imageArtifacts: List<AutomationSavedArtifact>,
    val voiceArtifact: AutomationSavedArtifact,
    val videoRendererMode: String = "local_plan_only",
    val videoWorkerUrl: String? = null
)

data class VideoRenderPlan(
    val rendererId: String,
    val planVersion: Int,
    val renderTarget: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val voiceArtifactUri: String,
    val voiceMimeType: String,
    val sceneCount: Int,
    val totalDurationMs: Long,
    val scenes: List<VideoRenderScenePlan>,
    val handoffHints: List<String>
)

data class VideoRenderScenePlan(
    val sceneId: String,
    val ordinal: Int,
    val summary: String,
    val visualPrompt: String,
    val imageArtifactUri: String,
    val imageArtifactId: String,
    val renderMode: String,
    val templateId: String,
    val strategy: String,
    val durationMs: Long,
    val subtitleText: String
)

data class VideoRenderResult(
    val rendererId: String,
    val plan: VideoRenderPlan,
    val videoArtifact: AutomationSavedArtifact? = null,
    val waitingReason: String = "VIDEO_RENDER_PLAN_READY",
    val runtimeMessage: String? = null
)
