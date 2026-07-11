package com.lqlq.browser.automation.video

import com.lqlq.browser.automation.image.ScenePrompt
import com.lqlq.browser.automation.visual.VisualAssetPlan

class LocalPlanVideoRenderer : VideoRenderer {

    override suspend fun createRenderPlan(request: VideoRenderRequest): VideoRenderResult {
        val imageByScene = request.imageArtifacts.associateBy { it.sceneId }
        val assetPlanByScene = request.assetPlans.associateBy { it.sceneId }
        val scriptSentences = splitSentences(request.generatedText)
        val voiceDurationMs = extractDebugLong(request.voiceArtifact.sourceUrl, "durationMs")

        val rawScenes = request.scenePrompts
            .sortedBy { it.ordinal }
            .mapIndexed { index, scenePrompt ->
                val imageArtifact = requireNotNull(imageByScene[scenePrompt.sceneId]) {
                    "Missing image artifact for ${scenePrompt.sceneId}."
                }
                val assetPlan = requireNotNull(assetPlanByScene[scenePrompt.sceneId]) {
                    "Missing asset plan for ${scenePrompt.sceneId}."
                }
                VideoRenderScenePlan(
                    sceneId = scenePrompt.sceneId,
                    ordinal = scenePrompt.ordinal,
                    summary = scenePrompt.summary,
                    visualPrompt = scenePrompt.visualPrompt,
                    imageArtifactUri = imageArtifact.uri,
                    imageArtifactId = imageArtifact.artifactId,
                    renderMode = assetPlan.renderMode,
                    templateId = assetPlan.templateId,
                    strategy = assetPlan.strategy,
                    durationMs = scenePrompt.plannedDurationMs.takeIf { it > 0L } ?: assetPlan.durationMs,
                    subtitleText = resolveSubtitleText(
                        index = index,
                        scenePrompt = scenePrompt,
                        assetPlan = assetPlan,
                        scriptSentences = scriptSentences
                    )
                )
            }
        val scenes = rebalanceDurations(rawScenes, voiceDurationMs)
        val plannedDurationMs = scenes.sumOf { it.durationMs }

        val plan = VideoRenderPlan(
            rendererId = RENDERER_ID,
            planVersion = 1,
            renderTarget = "REMOTE_FFMPEG_OR_PC_RENDERER",
            width = 1080,
            height = 1920,
            fps = 30,
            voiceArtifactUri = request.voiceArtifact.uri,
            voiceMimeType = request.voiceArtifact.mimeType,
            sceneCount = scenes.size,
            totalDurationMs = voiceDurationMs ?: plannedDurationMs,
            scenes = scenes,
            handoffHints = listOf(
                "android_app_only_coordinates_jobs",
                "no_ffmpeg_binary_embedded",
                "render_plan_ready_for_remote_renderer",
                "voiceDurationMs=${voiceDurationMs ?: 0}",
                "plannedDurationMs=$plannedDurationMs"
            )
        )
        return VideoRenderResult(
            rendererId = RENDERER_ID,
            plan = plan
        )
    }

    private fun resolveSubtitleText(
        index: Int,
        scenePrompt: ScenePrompt,
        assetPlan: VisualAssetPlan,
        scriptSentences: List<String>
    ): String {
        return scriptSentences.getOrNull(index)
            ?.takeIf { it.isNotBlank() }
            ?: scenePrompt.summary.ifBlank { assetPlan.rationale }
    }

    private fun splitSentences(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?…])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun rebalanceDurations(
        scenes: List<VideoRenderScenePlan>,
        voiceDurationMs: Long?
    ): List<VideoRenderScenePlan> {
        if (voiceDurationMs == null || voiceDurationMs <= 0L || scenes.isEmpty()) {
            return scenes
        }
        val plannedDurationMs = scenes.sumOf { it.durationMs }
        if (plannedDurationMs <= 0L) {
            val evenDuration = (voiceDurationMs / scenes.size).coerceAtLeast(1L)
            return scenes.map { it.copy(durationMs = evenDuration) }
        }
        val deltaRatio = kotlin.math.abs(plannedDurationMs - voiceDurationMs).toDouble() / voiceDurationMs.toDouble()
        if (deltaRatio <= 0.15) {
            return scenes
        }
        var assigned = 0L
        return scenes.mapIndexed { index, scene ->
            val duration = if (index == scenes.lastIndex) {
                (voiceDurationMs - assigned).coerceAtLeast(1L)
            } else {
                ((scene.durationMs.toDouble() / plannedDurationMs.toDouble()) * voiceDurationMs.toDouble()).toLong().coerceAtLeast(1L)
            }
            assigned += duration
            scene.copy(durationMs = duration)
        }
    }

    private fun extractDebugLong(source: String?, key: String): Long? {
        val normalized = source.orEmpty()
        val prefix = "$key="
        return normalized.split(';')
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.toLongOrNull()
    }

    companion object {
        const val RENDERER_ID: String = "local-json-video-plan"
    }
}
