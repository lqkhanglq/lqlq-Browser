package com.lqlq.browser.automation.video

import com.lqlq.browser.automation.artifact.AutomationArtifactStore

data class NativeRenderedVideo(
    val rendererId: String,
    val bytes: ByteArray,
    val mimeType: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val totalFrames: Int,
    val sceneCount: Int,
    val rendererBackend: String,
    val hasVideoTrack: Boolean,
    val hasAudioTrack: Boolean,
    val firstFrameExtracted: Boolean
)

interface NativeVideoRenderer {
    suspend fun renderVideo(
        request: VideoRenderRequest,
        plan: VideoRenderPlan,
        artifactStore: AutomationArtifactStore
    ): NativeRenderedVideo
}

object NoOpNativeVideoRenderer : NativeVideoRenderer {
    override suspend fun renderVideo(
        request: VideoRenderRequest,
        plan: VideoRenderPlan,
        artifactStore: AutomationArtifactStore
    ): NativeRenderedVideo {
        throw UnsupportedOperationException("Native Android video renderer chua duoc cau hinh.")
    }
}
