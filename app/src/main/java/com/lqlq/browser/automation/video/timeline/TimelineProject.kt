package com.lqlq.browser.automation.video.timeline

import com.lqlq.browser.automation.AutomationBackgroundMusicStore
import com.lqlq.browser.automation.AutomationUiJobSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * PASS 1 (nen Timeline Editor) — mo hinh du lieu "TimelineProject" cho buoc Video.
 *
 * Day la lop SUY RA tu du lieu dang co (scenePrompts + artifacts anh/giong + nhac
 * nen), KHONG thay the gi. Bang chung/goc du lieu van la Storyboard V35. Timeline
 * dung de sau nay nguoi dung keo/cat/gian nhu CapCut roi xuat MP4; PASS 1 chi dung
 * (build-on-demand, read-only) + serialize JSON de JS/hien thi kiem tra.
 *
 * Chua dong vao AutomationFacade/renderer/nut chay-dung — chi them moi.
 */
data class TimelineCanvas(
    val aspectRatio: String,
    val width: Int,
    val height: Int
)

/** 1 clip hinh anh tren track "Hinh" (1 canh Storyboard -> 1 clip). */
data class TimelineVisualClip(
    val clipId: String,
    val sceneId: String,
    val ordinal: Int,
    val imageArtifactUri: String?,   // null = canh nay chua co anh
    val imageArtifactId: String?,
    val startMs: Long,
    val durationMs: Long,
    val motion: String,              // "auto" | "none" | "zoom_in" | "zoom_out" | ...
    val transition: String           // "none" | "crossfade"
)

/** 1 lop chu (tieu de hoac phu de) — track "Chu". */
data class TimelineCaption(
    val captionId: String,
    val sceneId: String,
    val kind: String,                // "title" | "subtitle"
    val text: String,
    val startMs: Long,
    val endMs: Long
)

/** 1 track am thanh (giong doc hoac nhac nen). */
data class TimelineAudioTrack(
    val kind: String,                // "voice" | "music"
    val sourceUri: String?,          // null neu chua co
    val startMs: Long,
    val durationMs: Long,
    val gain: Float,
    val loop: Boolean,
    val fadeInMs: Long,
    val fadeOutMs: Long
)

data class TimelineProject(
    val jobId: String,
    val canvas: TimelineCanvas,
    val clips: List<TimelineVisualClip>,
    val captions: List<TimelineCaption>,
    val voiceTrack: TimelineAudioTrack?,
    val musicTrack: TimelineAudioTrack?,
    val totalDurationMs: Long,
    // "AUTO_SYNCED" = chua ai chinh tay (sua Storyboard tu dong dung lai).
    // "MANUAL_LOCKED" = da keo/cat tay (PASS sau moi dung den).
    val editState: String
)

object TimelineProjectBuilder {

    private const val FALLBACK_CLIP_DURATION_MS = 4_000L

    /**
     * Dung TimelineProject tu snapshot job hien tai (AUTO_SYNCED, read-only).
     * Thoi luong moi clip lay tu plannedDurationMs (da duoc ghi de bang thoi luong
     * audio THAT o workstream A) nen truc thoi gian khop giong doc.
     */
    fun buildFromSnapshot(
        snapshot: AutomationUiJobSnapshot,
        music: AutomationBackgroundMusicStore.BackgroundMusicSettings?
    ): TimelineProject {
        val scenes = snapshot.scenePrompts.sortedBy { it.ordinal }
        val imageBySceneId = snapshot.artifacts
            .filter { it.artifactType == "IMAGE" && !it.sceneId.isNullOrBlank() }
            .associateBy { it.sceneId!! }

        val aspect = scenes.firstOrNull()?.aspectRatio?.takeIf { it.isNotBlank() } ?: "9:16"
        val canvas = canvasFor(aspect)

        val clips = mutableListOf<TimelineVisualClip>()
        val captions = mutableListOf<TimelineCaption>()
        var cursor = 0L
        scenes.forEachIndexed { index, scene ->
            val duration = scene.plannedDurationMs.takeIf { it > 0L } ?: FALLBACK_CLIP_DURATION_MS
            val image = imageBySceneId[scene.sceneId]
            clips += TimelineVisualClip(
                clipId = "clip-${scene.sceneId}",
                sceneId = scene.sceneId,
                ordinal = scene.ordinal,
                imageArtifactUri = image?.uri,
                imageArtifactId = image?.artifactId,
                startMs = cursor,
                durationMs = duration,
                motion = "auto",
                transition = if (index == 0) "none" else "crossfade"
            )
            if (scene.onScreenText.isNotBlank()) {
                captions += TimelineCaption(
                    captionId = "cap-title-${scene.sceneId}",
                    sceneId = scene.sceneId,
                    kind = "title",
                    text = scene.onScreenText,
                    startMs = cursor,
                    endMs = cursor + duration
                )
            }
            if (scene.voiceText.isNotBlank()) {
                captions += TimelineCaption(
                    captionId = "cap-sub-${scene.sceneId}",
                    sceneId = scene.sceneId,
                    kind = "subtitle",
                    text = scene.voiceText,
                    startMs = cursor,
                    endMs = cursor + duration
                )
            }
            cursor += duration
        }
        val totalDurationMs = cursor

        val voiceArtifact = snapshot.artifacts.firstOrNull { it.artifactType == "VOICE" }
        val voiceTrack = voiceArtifact?.let {
            TimelineAudioTrack(
                kind = "voice",
                sourceUri = it.uri,
                startMs = 0L,
                durationMs = totalDurationMs,
                gain = 1.0f,
                loop = false,
                fadeInMs = 0L,
                fadeOutMs = 0L
            )
        }

        val musicTrack = music?.takeIf { it.hasMusic }?.let {
            TimelineAudioTrack(
                kind = "music",
                sourceUri = "background-music",
                startMs = 0L,
                durationMs = totalDurationMs,
                gain = it.volume,
                loop = it.loop,
                fadeInMs = 0L,
                fadeOutMs = 0L
            )
        }

        return TimelineProject(
            jobId = snapshot.jobId,
            canvas = canvas,
            clips = clips,
            captions = captions,
            voiceTrack = voiceTrack,
            musicTrack = musicTrack,
            totalDurationMs = totalDurationMs,
            editState = "AUTO_SYNCED"
        )
    }

    private fun canvasFor(aspect: String): TimelineCanvas {
        val (w, h) = when (aspect.trim()) {
            "16:9" -> 1920 to 1080
            "1:1" -> 1080 to 1080
            "4:5" -> 1080 to 1350
            "9:16" -> 1080 to 1920
            else -> 1080 to 1920
        }
        return TimelineCanvas(aspectRatio = aspect, width = w, height = h)
    }
}

object TimelineProjectJson {
    fun toJson(project: TimelineProject): JSONObject = JSONObject().apply {
        put("jobId", project.jobId)
        put("editState", project.editState)
        put("totalDurationMs", project.totalDurationMs)
        put("canvas", JSONObject()
            .put("aspectRatio", project.canvas.aspectRatio)
            .put("width", project.canvas.width)
            .put("height", project.canvas.height))
        put("clips", JSONArray().apply {
            project.clips.forEach { c ->
                put(JSONObject()
                    .put("clipId", c.clipId)
                    .put("sceneId", c.sceneId)
                    .put("ordinal", c.ordinal)
                    .put("imageArtifactUri", c.imageArtifactUri)
                    .put("imageArtifactId", c.imageArtifactId)
                    .put("startMs", c.startMs)
                    .put("durationMs", c.durationMs)
                    .put("motion", c.motion)
                    .put("transition", c.transition))
            }
        })
        put("captions", JSONArray().apply {
            project.captions.forEach { cap ->
                put(JSONObject()
                    .put("captionId", cap.captionId)
                    .put("sceneId", cap.sceneId)
                    .put("kind", cap.kind)
                    .put("text", cap.text)
                    .put("startMs", cap.startMs)
                    .put("endMs", cap.endMs))
            }
        })
        put("voiceTrack", project.voiceTrack?.let(::audioTrackJson))
        put("musicTrack", project.musicTrack?.let(::audioTrackJson))
    }

    private fun audioTrackJson(t: TimelineAudioTrack): JSONObject = JSONObject()
        .put("kind", t.kind)
        .put("sourceUri", t.sourceUri)
        .put("startMs", t.startMs)
        .put("durationMs", t.durationMs)
        .put("gain", t.gain.toDouble())
        .put("loop", t.loop)
        .put("fadeInMs", t.fadeInMs)
        .put("fadeOutMs", t.fadeOutMs)
}
