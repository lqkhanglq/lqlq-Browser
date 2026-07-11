package com.lqlq.browser.automation.video

interface VideoRenderer {
    suspend fun createRenderPlan(request: VideoRenderRequest): VideoRenderResult
}
