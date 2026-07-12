package com.lqlq.browser.automation.video

import kotlin.math.roundToInt

class LocalPlanVideoRenderer : VideoRenderer {

    override suspend fun createRenderPlan(request: VideoRenderRequest): VideoRenderResult {
        val imageByScene = request.imageArtifacts.associateBy { it.sceneId }
        val assetPlanByScene = request.assetPlans.associateBy { it.sceneId }
        val voiceDurationMs = extractDebugLong(request.voiceArtifact.sourceUrl, "durationMs")

        val orderedScenes = request.scenePrompts.sortedBy { it.ordinal }
        val firstSceneId = orderedScenes.firstOrNull()?.sceneId
        val rawScenes = orderedScenes
            .map { scenePrompt ->
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
                    // Dung DUNG voiceText that cua chinh canh nay (Gemini da tach san
                    // theo tung canh) lam nguon phu de - truoc day doan bang cach tach
                    // cau tu TOAN BO script roi lay theo vi tri index, hay bi lech vi
                    // so cau khong khop so canh.
                    subtitleText = scenePrompt.voiceText.trim().ifBlank { scenePrompt.summary.ifBlank { assetPlan.rationale } },
                    // Canh DAU: hien TIEU DE CHU DE video (vd "Top 10 nhan vat anime").
                    // Cac canh sau: dung onScreenText (nhan Gemini, vd "Goku") neu la
                    // nhan ngan (<= 42 ky tu), dai qua thi de trong.
                    titleText = if (scenePrompt.sceneId == firstSceneId && request.videoTitle.isNotBlank()) {
                        request.videoTitle.trim()
                    } else {
                        scenePrompt.onScreenText.trim().takeIf { it.isNotEmpty() && it.length <= 42 }.orEmpty()
                    }
                )
            }
        val scenes = rebalanceDurations(rawScenes, voiceDurationMs)
        val plannedDurationMs = scenes.sumOf { it.durationMs }
        val (renderWidth, renderHeight) = resolveDimensions(
            request.scenePrompts.firstOrNull()?.aspectRatio,
            request.videoQualityTier
        )

        val plan = VideoRenderPlan(
            rendererId = RENDERER_ID,
            planVersion = 1,
            renderTarget = "REMOTE_FFMPEG_OR_PC_RENDERER",
            width = renderWidth,
            height = renderHeight,
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

    /**
     * Quy đổi tỉ lệ khung hình ("9:16", "16:9", "1:1", "3:4", "4:3", "21:9") do người
     * dùng chọn ở bước tạo nội dung thành kích thước pixel thật cho video xuất ra —
     * trước đây bị hardcode cứng 1080x1920 (luôn 9:16) bất kể lựa chọn nào.
     * Cạnh ngắn cố định theo qualityTier (720/1080px), cạnh dài tính theo tỉ lệ và
     * làm tròn số chẵn (bắt buộc với encoder H.264/MediaCodec).
     */
    private fun resolveDimensions(aspectRatio: String?, qualityTier: String): Pair<Int, Int> {
        val shortSide = if (qualityTier.trim().equals("720p", ignoreCase = true)) 720 else 1080
        val parts = aspectRatio.orEmpty().trim().split(":")
        val ratioW = parts.getOrNull(0)?.toDoubleOrNull()
        val ratioH = parts.getOrNull(1)?.toDoubleOrNull()
        if (ratioW == null || ratioH == null || ratioW <= 0.0 || ratioH <= 0.0) {
            return shortSide to (shortSide * 16 / 9).let { if (it % 2 == 0) it else it + 1 }
        }
        return if (ratioW <= ratioH) {
            val longSide = (shortSide * (ratioH / ratioW)).roundToEven()
            shortSide to longSide
        } else {
            val longSide = (shortSide * (ratioW / ratioH)).roundToEven()
            longSide to shortSide
        }
    }

    private fun Double.roundToEven(): Int {
        val rounded = this.roundToInt()
        return if (rounded % 2 == 0) rounded else rounded + 1
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
