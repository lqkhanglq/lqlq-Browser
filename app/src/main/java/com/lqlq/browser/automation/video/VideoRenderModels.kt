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
    val videoWorkerUrl: String? = null,
    // "720p" hoac "1080p" - anh huong canh ngan cua khung hinh xuat ra (720/1080px)
    // va theo do la bitrate (selectBitrate trong AndroidNativeSlideshowVideoRenderer
    // da scale theo do phan giai thuc te).
    val videoQualityTier: String = "1080p",
    // "blurred_fill" (nen mo lap day) hoac "black_bars" (nen den don gian).
    val videoBackgroundMode: String = "blurred_fill",
    // "auto_mix" (xoay vong cac kieu Ken Burns) hoac 1 kieu co dinh:
    // "zoom_in", "zoom_out", "pan_left_to_right", "pan_right_to_left", "none".
    val videoMotionMode: String = "auto_mix",
    // Duong dan file nhac nen da luu tren may (tu AutomationBackgroundMusicStore),
    // null neu nguoi dung khong chon nhac nen - video van render binh thuong.
    val backgroundMusicFilePath: String? = null,
    // Lap lai nhac nen cho het do dai video, hay chi phat 1 lan roi im lang.
    val backgroundMusicLoop: Boolean = true,
    // Am luong nhac nen tuong doi so voi giong doc (0.0 - 1.0).
    val backgroundMusicVolume: Float = 0.35f,
    // Mau chu phu de + tieu de (hex "#RRGGBB"). Mac dinh trang.
    val videoSubtitleColor: String = "#FFFFFF",
    // Tieu de chu de video - hien o CANH DAU (ngoai tieu de tung canh). Rong neu khong co.
    val videoTitle: String = ""
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
    val subtitleText: String,
    // Tieu de cang hien to o TREN man hinh (vd "Goku"), giu co dinh suot canh -
    // khac voi subtitleText (loi doc) chay o duoi. Rong neu canh khong co tieu de.
    val titleText: String = ""
)

data class VideoRenderResult(
    val rendererId: String,
    val plan: VideoRenderPlan,
    val videoArtifact: AutomationSavedArtifact? = null,
    val waitingReason: String = "VIDEO_RENDER_PLAN_READY",
    val runtimeMessage: String? = null
)
