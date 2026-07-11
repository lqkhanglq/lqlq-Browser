package com.lqlq.browser.automation.video

import org.json.JSONArray
import org.json.JSONObject

object VideoRenderPlanJson {
    fun encode(plan: VideoRenderPlan): String {
        return JSONObject()
            .put("rendererId", plan.rendererId)
            .put("planVersion", plan.planVersion)
            .put("renderTarget", plan.renderTarget)
            .put("width", plan.width)
            .put("height", plan.height)
            .put("fps", plan.fps)
            .put("voiceArtifactUri", plan.voiceArtifactUri)
            .put("voiceMimeType", plan.voiceMimeType)
            .put("sceneCount", plan.sceneCount)
            .put("totalDurationMs", plan.totalDurationMs)
            .put("handoffHints", JSONArray(plan.handoffHints))
            .put(
                "scenes",
                JSONArray().apply {
                    plan.scenes.forEach { scene ->
                        put(
                            JSONObject()
                                .put("sceneId", scene.sceneId)
                                .put("ordinal", scene.ordinal)
                                .put("summary", scene.summary)
                                .put("visualPrompt", scene.visualPrompt)
                                .put("imageArtifactUri", scene.imageArtifactUri)
                                .put("imageArtifactId", scene.imageArtifactId)
                                .put("renderMode", scene.renderMode)
                                .put("templateId", scene.templateId)
                                .put("strategy", scene.strategy)
                                .put("durationMs", scene.durationMs)
                                .put("subtitleText", scene.subtitleText)
                        )
                    }
                }
            )
            .toString()
    }
}
