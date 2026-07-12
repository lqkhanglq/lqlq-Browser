package com.lqlq.browser.automation.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer

import android.view.Surface
import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.artifact.AutomationSavedArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class AndroidNativeSlideshowVideoRenderer : NativeVideoRenderer {

    override suspend fun renderVideo(
        request: VideoRenderRequest,
        plan: VideoRenderPlan,
        artifactStore: AutomationArtifactStore
    ): NativeRenderedVideo = withContext(Dispatchers.IO) {
        require(plan.scenes.isNotEmpty()) { "Khong co scene de render video." }
        require(request.imageArtifacts.isNotEmpty()) { "Khong co image artifact de render video." }
        require(request.voiceArtifact.mimeType == "audio/wav") {
            "Android native renderer hien chi ho tro voice WAV de mux MP4 an toan."
        }

        val wavBytes = requireNotNull(artifactStore.readArtifactBytes(request.voiceArtifact)) {
            "Khong doc duoc voice artifact de render MP4."
        }
        val wavData = WavData.parse(wavBytes)
        // Nhac nen la TUY CHON - neu khong chon file (backgroundMusicFilePath null)
        // hoac decode/mix loi thi fallback ve dung giong doc goc, khong lam hong
        // ca video chi vi loi o nhac nen.
        val mixedWavData = request.backgroundMusicFilePath
            ?.let { path -> File(path).takeIf { it.exists() && it.length() > 0L } }
            ?.let { musicFile ->
                runCatching {
                    mixBackgroundMusic(wavData, musicFile, request.backgroundMusicLoop, request.backgroundMusicVolume)
                }.getOrElse { wavData }
            }
            ?: wavData
        val voiceDurationMs = extractDebugLong(request.voiceArtifact.sourceUrl, "durationMs")
            ?.takeIf { it > 0L }
            ?: estimateDurationMs(wavData)
        val fps = 30
        val candidateSizes = buildResolutionCandidates(plan.width, plan.height)
        val sceneBitmaps = loadSceneBitmaps(plan, request.imageArtifacts, artifactStore)
        val outputFile = File.createTempFile("lqlq-native-render-", ".mp4")
        try {
            var lastError: Throwable? = null
            for ((width, height) in candidateSizes) {
                runCatching {
                    renderAndValidateMp4(
                        outputFile = outputFile,
                        width = width,
                        height = height,
                        fps = fps,
                        bitrate = selectBitrate(width, height),
                        plan = plan,
                        sceneBitmaps = sceneBitmaps,
                        wavData = mixedWavData,
                        voiceDurationMs = voiceDurationMs,
                        videoBackgroundMode = request.videoBackgroundMode,
                        videoMotionMode = request.videoMotionMode,
                        subtitleColor = parseHexColor(request.videoSubtitleColor)
                    )
                }.onSuccess { stats ->
                    val bytes = outputFile.readBytes()
                    require(bytes.isNotEmpty()) { "Renderer tao MP4 rong." }
                    return@withContext NativeRenderedVideo(
                        rendererId = RENDERER_ID,
                        bytes = bytes,
                        mimeType = "video/mp4",
                        durationMs = stats.durationMs,
                        width = stats.width,
                        height = stats.height,
                        fps = fps,
                        bitrate = stats.bitrate,
                        totalFrames = stats.totalFrames,
                        sceneCount = plan.sceneCount,
                        rendererBackend = RENDERER_BACKEND,
                        hasVideoTrack = stats.hasVideoTrack,
                        hasAudioTrack = stats.hasAudioTrack,
                        firstFrameExtracted = stats.firstFrameExtracted
                    )
                }.onFailure { error ->
                    lastError = error
                    outputFile.delete()
                    outputFile.createNewFile()
                }
            }
            throw IllegalStateException(
                "Android native renderer khong tao duoc MP4 hop le. ${lastError?.message ?: "unknown_error"}",
                lastError
            )
        } finally {
            sceneBitmaps.forEach { (_, bitmap) -> bitmap.recycle() }
            outputFile.delete()
        }
    }

    private suspend fun loadSceneBitmaps(
        plan: VideoRenderPlan,
        imageArtifacts: List<AutomationSavedArtifact>,
        artifactStore: AutomationArtifactStore
    ): List<Pair<VideoRenderScenePlan, Bitmap>> {
        val artifactsById = imageArtifacts.associateBy { it.artifactId }
        return plan.scenes.map { scene ->
            val artifact = requireNotNull(artifactsById[scene.imageArtifactId]) {
                "Khong tim thay image artifact cho scene ${scene.sceneId}."
            }
            val bytes = requireNotNull(artifactStore.readArtifactBytes(artifact)) {
                "Khong doc duoc bytes cua image artifact ${artifact.artifactId}."
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("Khong decode duoc image artifact ${artifact.artifactId}.")
            scene to decoded
        }
    }

    private fun renderAndValidateMp4(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        plan: VideoRenderPlan,
        sceneBitmaps: List<Pair<VideoRenderScenePlan, Bitmap>>,
        wavData: WavData,
        voiceDurationMs: Long,
        videoBackgroundMode: String,
        videoMotionMode: String,
        subtitleColor: Int
    ): RenderStats {
        val totalFrames = max(1, ceil((voiceDurationMs / 1000.0) * fps.toDouble()).toInt())
        renderMp4(
            outputFile = outputFile,
            width = width,
            height = height,
            fps = fps,
            bitrate = bitrate,
            totalFrames = totalFrames,
            plan = plan,
            sceneBitmaps = sceneBitmaps,
            wavData = wavData,
            videoBackgroundMode = videoBackgroundMode,
            videoMotionMode = videoMotionMode,
            subtitleColor = subtitleColor
        )
        return validateRenderedVideo(
            file = outputFile,
            expectedDurationMs = voiceDurationMs,
            expectedWidth = width,
            expectedHeight = height,
            bitrate = bitrate,
            totalFrames = totalFrames
        )
    }

    private fun renderMp4(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        totalFrames: Int,
        plan: VideoRenderPlan,
        sceneBitmaps: List<Pair<VideoRenderScenePlan, Bitmap>>,
        wavData: WavData,
        videoBackgroundMode: String,
        videoMotionMode: String,
        subtitleColor: Int
    ) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val coordinator = MuxerCoordinator(muxer, fps)
        val videoCodec = createSurfaceVideoEncoder(width, height, fps, bitrate)
        val audioCodec = createAudioEncoder(wavData)
        val inputSurface = videoCodec.createInputSurface()
        videoCodec.start()
        audioCodec.start()
        try {
            encodeVideoTrack(
                codec = videoCodec,
                inputSurface = inputSurface,
                coordinator = coordinator,
                width = width,
                height = height,
                fps = fps,
                totalFrames = totalFrames,
                plan = plan,
                sceneBitmaps = sceneBitmaps,
                videoBackgroundMode = videoBackgroundMode,
                videoMotionMode = videoMotionMode,
                subtitleColor = subtitleColor
            )
            encodeAudioTrack(audioCodec, coordinator, wavData)
            coordinator.finish()
        } finally {
            runCatching { inputSurface.release() }
            runCatching { videoCodec.stop() }
            runCatching { videoCodec.release() }
            runCatching { audioCodec.stop() }
            runCatching { audioCodec.release() }
            runCatching { muxer.release() }
        }
    }

    private fun createSurfaceVideoEncoder(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    ): MediaCodec {
        val codec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE)
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            // Slideshow anh dung yen + audio, khong phai video hanh dong nhieu chuyen
            // dong. I-frame moi 1 giay (cu) buoc encoder ve lai toan bo khung hinh du
            // anh khong doi, day la nguyen nhan chinh khien file nang bat thuong.
            // Keyframe moi 5 giay la du de tua/seek, giam manh dung luong voi anh tinh.
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5)
            if (isBitrateModeSupported(codec, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }

    private fun isBitrateModeSupported(codec: MediaCodec, mode: Int): Boolean {
        return runCatching {
            codec.codecInfo
                .getCapabilitiesForType(VIDEO_MIME_TYPE)
                .encoderCapabilities
                .isBitrateModeSupported(mode)
        }.getOrDefault(false)
    }

    private fun createAudioEncoder(wavData: WavData): MediaCodec {
        val codec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
        val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, wavData.sampleRateHz, wavData.channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, if (wavData.channelCount > 1) 192_000 else 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256 * 1024)
        }
        codec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }

    private fun encodeVideoTrack(
        codec: MediaCodec,
        inputSurface: Surface,
        coordinator: MuxerCoordinator,
        width: Int,
        height: Int,
        fps: Int,
        totalFrames: Int,
        plan: VideoRenderPlan,
        sceneBitmaps: List<Pair<VideoRenderScenePlan, Bitmap>>,
        videoBackgroundMode: String,
        videoMotionMode: String,
        subtitleColor: Int
    ) {
        val timeline = buildFrameTimeline(plan, totalFrames)
        val sceneFrameBounds = computeSceneFrameBounds(timeline, sceneBitmaps.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val useBlurredBackground = !videoBackgroundMode.equals("black_bars", ignoreCase = true)
        // Nen mo tinh 1 LAN/canh (khong doi trong ca canh) thay vi tinh lai moi
        // frame - phong to/thu nho anh goc de lam nen mo rat ton kem neu lam lai
        // hang tram lan cho 1 canh dai vai giay. Khi chon "black_bars" thi khong
        // can tinh nen mo, chi ve mau den (da drawColor(BLACK) moi frame).
        val blurredBackgrounds = if (useBlurredBackground) {
            sceneBitmaps.map { (_, bitmap) -> createBlurredCoverBackground(bitmap, width, height, paint) }
        } else {
            emptyList()
        }
        // Chia phu de tung canh thanh cac cum ngan (chuan short-form caption),
        // tinh 1 LAN/canh - dong bo gan dung voi loi doc bang ty le SO TU da doc
        // qua (khong co timestamp tung tu that tu TTS mien phi nen day la uoc
        // luong hop ly nhat co the, van chuan hon nhieu so voi 1 khoi chu tinh
        // dung yen suot ca canh nhu truoc day).
        val captionCuesByScene = sceneBitmaps.map { (scene, _) -> buildCaptionCues(scene.subtitleText) }
        // Crossfade tối giản giữa 2 cảnh liền kề (chuẩn chuyên nghiệp: mờ dần đơn
        // giản, KHÔNG dùng hiệu ứng loè loẹt) - chỉ áp dụng ở vài frame cuối mỗi
        // cảnh (trừ cảnh cuối), mờ dần sang khung hình đầu của cảnh kế tiếp.
        val crossfadeFrames = (fps * CROSSFADE_DURATION_SECONDS).roundToInt().coerceAtLeast(1)
        try {
            repeat(totalFrames) { frameIndex ->
                val canvas = inputSurface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    val sceneIndex = timeline[frameIndex].coerceIn(0, sceneBitmaps.lastIndex)
                    val (scene, bitmap) = sceneBitmaps[sceneIndex]
                    val range = sceneFrameBounds[sceneIndex]
                    val progress = if (range.last > range.first) {
                        (frameIndex - range.first).toFloat() / (range.last - range.first).toFloat()
                    } else {
                        0f
                    }.coerceIn(0f, 1f)
                    if (useBlurredBackground) {
                        canvas.drawBitmap(blurredBackgrounds[sceneIndex], 0f, 0f, paint)
                    }
                    drawKenBurnsFrame(canvas, bitmap, width, height, paint, scene.ordinal, progress, videoMotionMode)

                    val framesToSceneEnd = range.last - frameIndex
                    if (sceneIndex < sceneBitmaps.lastIndex && framesToSceneEnd < crossfadeFrames) {
                        val fadeAlpha = 1f - (framesToSceneEnd.toFloat() / crossfadeFrames.toFloat()).coerceIn(0f, 1f)
                        val nextIndex = sceneIndex + 1
                        val (nextScene, nextBitmap) = sceneBitmaps[nextIndex]
                        val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val overlayCanvas = Canvas(overlay)
                        if (useBlurredBackground) {
                            overlayCanvas.drawBitmap(blurredBackgrounds[nextIndex], 0f, 0f, paint)
                        }
                        drawKenBurnsFrame(overlayCanvas, nextBitmap, width, height, paint, nextScene.ordinal, 0f, videoMotionMode)
                        val alphaPaint = Paint(paint).apply { alpha = (fadeAlpha * 255f).roundToInt().coerceIn(0, 255) }
                        canvas.drawBitmap(overlay, 0f, 0f, alphaPaint)
                        overlay.recycle()
                    }

                    // Lop 1 - tieu de canh (co dinh suot canh) o TREN, vd "Goku".
                    if (scene.titleText.isNotBlank()) {
                        drawTitle(canvas, scene.titleText, width, height, subtitleColor)
                    }
                    // Lop 2 - phu de loi doc (chay theo cum tu) o DUOI.
                    val activeCue = findActiveCaptionCue(captionCuesByScene[sceneIndex], progress)
                    if (activeCue != null) {
                        drawCaption(canvas, activeCue, width, height, subtitleColor)
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                drainEncoder(codec, coordinator, TrackKind.VIDEO, endOfStream = false)
            }
            codec.signalEndOfInputStream()
            drainEncoder(codec, coordinator, TrackKind.VIDEO, endOfStream = true)
        } finally {
            blurredBackgrounds.forEach { it.recycle() }
        }
    }

    /**
     * Tim frame dau/cuoi cua tung canh trong timeline (da duoc gan lien tuc theo
     * thu tu canh o buildFrameTimeline) - dung de tinh % tien do (0..1) cua 1
     * frame trong chinh canh cua no, phuc vu hieu ung Ken Burns tang dan theo
     * thoi gian thay vi tinh lai ty le thoi luong tu dau.
     */
    private fun computeSceneFrameBounds(timeline: IntArray, sceneCount: Int): Array<IntRange> {
        val starts = IntArray(sceneCount) { -1 }
        val ends = IntArray(sceneCount) { -1 }
        timeline.forEachIndexed { frame, sceneIdx ->
            if (sceneIdx !in 0 until sceneCount) return@forEachIndexed
            if (starts[sceneIdx] == -1) starts[sceneIdx] = frame
            ends[sceneIdx] = frame
        }
        return Array(sceneCount) { i -> if (starts[i] == -1) 0..0 else starts[i]..ends[i] }
    }

    /**
     * Nen mo (blurred-fill): phong ảnh gốc theo kiểu "cover" ở kích thước rất nhỏ
     * rồi phóng to lại lấp đầy khung hình - tạo hiệu ứng mờ mềm mại lấp đầy phần
     * viền mà ảnh gốc (hiển thị "fit" nguyên vẹn ở drawKenBurnsFrame) không che
     * tới, thay vì cắt mất ảnh gốc để lấp đầy như truoc day.
     */
    private fun createBlurredCoverBackground(bitmap: Bitmap, width: Int, height: Int, paint: Paint): Bitmap {
        // Nguon blur: cover-crop anh vao dung ty le khung hinh o kich thuoc VUA
        // (~180px canh ngan) - khong qua nho de tranh vo hat/khoi nhu truoc (48px).
        val shortSide = 180
        val smallWidth: Int
        val smallHeight: Int
        if (width <= height) {
            smallWidth = shortSide
            smallHeight = max(1, (shortSide.toFloat() * height / width).roundToInt())
        } else {
            smallHeight = shortSide
            smallWidth = max(1, (shortSide.toFloat() * width / height).roundToInt())
        }
        val small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        drawBitmapCover(Canvas(small), bitmap, smallWidth, smallHeight, paint)

        // Box blur nhieu luot (~ Gaussian) cho MEM MIN thay vi chi phong to anh nho.
        val blurred = stackBoxBlur(small, radius = 12, passes = 3)
        small.recycle()

        // Phong to len full frame (bilinear qua FILTER_BITMAP paint) roi LAM TOI nhe
        // (~28% den) de anh chinh (ve "fit" ben tren) noi bat - chuan CapCut.
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        canvas.drawBitmap(blurred, null, Rect(0, 0, width, height), paint)
        blurred.recycle()
        canvas.drawColor(Color.argb(72, 0, 0, 0))
        return full
    }

    /**
     * Box blur nhieu luot tren bitmap (xap xi Gaussian, mem min). Chay 1 LAN/canh
     * tren anh da thu nho nen rat nhe. Clamp o bien (khong hoan hao tuyet doi nhung
     * mat thuong khong thay voi nen mo).
     */
    private fun stackBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2 || radius < 1) return src.copy(Bitmap.Config.ARGB_8888, false)
        val bufA = IntArray(w * h)
        val bufB = IntArray(w * h)
        src.getPixels(bufA, 0, w, 0, 0, w, h)
        var read = bufA
        var write = bufB
        repeat(passes) {
            boxBlurHorizontal(read, write, w, h, radius.coerceAtMost(w / 2))
            boxBlurVertical(write, read, w, h, radius.coerceAtMost(h / 2))
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(read, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val rowStart = y * w
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[rowStart + i.coerceIn(0, w - 1)]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (x in 0 until w) {
                dst[rowStart + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val addC = src[rowStart + (x + radius + 1).coerceIn(0, w - 1)]
                val remC = src[rowStart + (x - radius).coerceIn(0, w - 1)]
                r += ((addC shr 16) and 0xFF) - ((remC shr 16) and 0xFF)
                g += ((addC shr 8) and 0xFF) - ((remC shr 8) and 0xFF)
                b += (addC and 0xFF) - (remC and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
        val div = radius * 2 + 1
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val addC = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                val remC = src[(y - radius).coerceIn(0, h - 1) * w + x]
                r += ((addC shr 16) and 0xFF) - ((remC shr 16) and 0xFF)
                g += ((addC shr 8) and 0xFF) - ((remC shr 8) and 0xFF)
                b += (addC and 0xFF) - (remC and 0xFF)
            }
        }
    }

    private fun drawBitmapCover(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        paint: Paint
    ) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = width.toFloat() / height.toFloat()
        val dstRect = if (srcRatio > dstRatio) {
            val scaledWidth = (height * srcRatio).roundToInt()
            val left = (width - scaledWidth) / 2
            Rect(left, 0, left + scaledWidth, height)
        } else {
            val scaledHeight = (width / srcRatio).roundToInt()
            val top = (height - scaledHeight) / 2
            Rect(0, top, width, top + scaledHeight)
        }
        canvas.drawBitmap(bitmap, null, dstRect, paint)
    }

    /**
     * Ve anh THEO KIEU "fit" (hien nguyen ven, khong cat) + hieu ung Ken Burns
     * (zoom/pan cham, luan phien kieu theo so thu tu canh de khong lap lai don
     * dieu) - day la chuan nganh cho slideshow anh tinh (dat theo ten dao dien
     * phim tai lieu Ken Burns), thay the hoan toan viec ve dung yen truoc day.
     */
    private fun drawKenBurnsFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        paint: Paint,
        sceneOrdinal: Int,
        progress: Float,
        videoMotionMode: String
    ) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val dstRatio = width.toFloat() / height.toFloat()
        val fitRect = if (srcRatio > dstRatio) {
            val fitHeight = width / srcRatio
            val top = (height - fitHeight) / 2f
            RectF(0f, top, width.toFloat(), top + fitHeight)
        } else {
            val fitWidth = height * srcRatio
            val left = (width - fitWidth) / 2f
            RectF(left, 0f, left + fitWidth, height.toFloat())
        }

        val eased = smoothstep(progress)
        val motion = resolveSceneMotion(videoMotionMode, sceneOrdinal)
        val scale = when (motion) {
            SceneMotion.ZOOM_IN -> lerp(1.00f, KEN_BURNS_MAX_ZOOM, eased)
            SceneMotion.ZOOM_OUT -> lerp(KEN_BURNS_MAX_ZOOM, 1.00f, eased)
            SceneMotion.PAN_LEFT_TO_RIGHT, SceneMotion.PAN_RIGHT_TO_LEFT -> KEN_BURNS_PAN_ZOOM
            SceneMotion.NONE -> 1.00f
        }
        val panRangeX = fitRect.width() * KEN_BURNS_PAN_FRACTION
        val offsetX = when (motion) {
            SceneMotion.PAN_LEFT_TO_RIGHT -> lerp(-panRangeX, panRangeX, eased)
            SceneMotion.PAN_RIGHT_TO_LEFT -> lerp(panRangeX, -panRangeX, eased)
            else -> 0f
        }
        val centerX = fitRect.centerX() + offsetX
        val centerY = fitRect.centerY()
        val scaledWidth = fitRect.width() * scale
        val scaledHeight = fitRect.height() * scale
        val dstRect = RectF(
            centerX - scaledWidth / 2f,
            centerY - scaledHeight / 2f,
            centerX + scaledWidth / 2f,
            centerY + scaledHeight / 2f
        )
        canvas.drawBitmap(bitmap, null, dstRect, paint)
    }

    private fun smoothstep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    private enum class SceneMotion {
        ZOOM_IN,
        PAN_LEFT_TO_RIGHT,
        ZOOM_OUT,
        PAN_RIGHT_TO_LEFT,
        NONE
    }

    /**
     * "auto_mix": xoay vong 4 kieu Ken Burns theo so thu tu canh (nhu truoc day).
     * Khac "auto_mix": dung CO DINH 1 kieu duy nhat cho toan bo video theo lua
     * chon nguoi dung trong Cai dat tu dong > Video.
     */
    private fun resolveSceneMotion(videoMotionMode: String, sceneOrdinal: Int): SceneMotion {
        val cyclingMotions = arrayOf(SceneMotion.ZOOM_IN, SceneMotion.PAN_LEFT_TO_RIGHT, SceneMotion.ZOOM_OUT, SceneMotion.PAN_RIGHT_TO_LEFT)
        return when (videoMotionMode.trim().lowercase()) {
            "zoom_in" -> SceneMotion.ZOOM_IN
            "zoom_out" -> SceneMotion.ZOOM_OUT
            "pan_left_to_right" -> SceneMotion.PAN_LEFT_TO_RIGHT
            "pan_right_to_left" -> SceneMotion.PAN_RIGHT_TO_LEFT
            "none" -> SceneMotion.NONE
            else -> cyclingMotions[(sceneOrdinal - 1).mod(cyclingMotions.size)]
        }
    }

    /**
     * 1 cum phu de ngan (vai tu) hien trong 1 khoang tien do [startProgress,
     * endProgress) cua canh - thay the hoan toan viec hien 1 khoi chu tinh suot
     * ca canh nhu truoc day. Dong bo theo TY LE SO TU da doc qua trong tong so
     * tu cua canh (khong co timestamp tung tu that tu TTS mien phi nen day la
     * xap xi hop ly nhat, nhung van chuan hon nhieu so voi khong dong bo gi ca).
     */
    private data class CaptionCue(
        val text: String,
        val startProgress: Float,
        val endProgress: Float
    )

    private fun buildCaptionCues(subtitleText: String): List<CaptionCue> {
        val words = subtitleText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (word in words) {
            current.add(word)
            val endsClause = word.endsWith(",") || word.endsWith(".") || word.endsWith("!") ||
                word.endsWith("?") || word.endsWith("...") || word.endsWith(":")
            if (current.size >= MAX_WORDS_PER_CAPTION_CUE || (endsClause && current.size >= MIN_WORDS_PER_CAPTION_CUE)) {
                chunks += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) chunks += current
        val totalWords = words.size
        var wordCursor = 0
        return chunks.map { chunk ->
            val startProgress = wordCursor.toFloat() / totalWords.toFloat()
            wordCursor += chunk.size
            val endProgress = wordCursor.toFloat() / totalWords.toFloat()
            CaptionCue(chunk.joinToString(" "), startProgress, endProgress)
        }
    }

    private fun findActiveCaptionCue(cues: List<CaptionCue>, progress: Float): String? {
        if (cues.isEmpty()) return null
        return cues.firstOrNull { progress >= it.startProgress && progress < it.endProgress }?.text
            ?: cues.last().text
    }

    /**
     * Chu dam, co vien den (doc ro tren MOI nen anh, khong can khung nen mo o
     * sau nhu truoc day) - tu xuong dong toi da 3 dong, dat trong "vung an
     * toan" (tranh 15% duoi cung man hinh, noi UI dien thoai/nut bam hay che).
     */
    private fun drawCaption(
        canvas: Canvas,
        cueText: String,
        width: Int,
        height: Int,
        subtitleColor: Int
    ) {
        if (cueText.isBlank()) return
        val textSize = (width * 0.052f).coerceAtLeast(30f)
        val maxTextWidth = width * 0.82f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtitleColor
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val strokePaint = Paint(fillPaint).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.16f
            strokeJoin = Paint.Join.ROUND
        }
        val lines = wrapCaptionText(cueText, fillPaint, maxTextWidth)
        val lineHeight = textSize * 1.28f
        val totalHeight = lineHeight * lines.size
        // Vung an toan: khong duoc thap hon 85% chieu cao (tranh 15% duoi cung),
        // uu tien vi tri lower-third (~55%) khi con du cho.
        val bottomSafeY = height * 0.85f
        var y = (bottomSafeY - totalHeight).coerceAtLeast(height * 0.55f) + textSize
        val centerX = width / 2f
        lines.forEach { line ->
            canvas.drawText(line, centerX, y, strokePaint)
            canvas.drawText(line, centerX, y, fillPaint)
            y += lineHeight
        }
    }

    /**
     * Ve TIEU DE (co dinh suot canh) o phia TREN - CUNG kieu chu nhu phu de (dam,
     * vien den, KHONG con nen den) nhung TO hon 1 chut. Tu xuong dong (toi da 2 dong)
     * neu dai. Vi tri tren tinh theo TY LE chieu cao nen tu dung cho moi khung hinh.
     */
    private fun drawTitle(canvas: Canvas, titleRaw: String, width: Int, height: Int, subtitleColor: Int) {
        val title = titleRaw.trim().replace(Regex("\\s+"), " ")
        if (title.isBlank()) return
        // To hon phu de (~0.062 vs 0.052 cua caption).
        val textSize = (width * 0.062f).coerceAtLeast(34f)
        val maxTextWidth = width * 0.86f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subtitleColor
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val strokePaint = Paint(fillPaint).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = textSize * 0.16f
            strokeJoin = Paint.Join.ROUND
        }
        val lines = wrapCaptionText(title, fillPaint, maxTextWidth).take(2)
        val lineHeight = textSize * 1.26f
        val centerX = width / 2f
        // Vung an toan tren theo ty le (tu chuan cho 9:16, 1:1, 16:9...). Baseline
        // dong dau bat dau ~8% chieu cao (tranh tai tho/notch va sat mep).
        var y = height * 0.08f + textSize
        lines.forEach { line ->
            canvas.drawText(line, centerX, y, strokePaint)
            canvas.drawText(line, centerX, y, fillPaint)
            y += lineHeight
        }
    }

    /** Chuyen chuoi hex "#RRGGBB" -> mau Int; loi/rong thi tra ve trang. */
    private fun parseHexColor(hex: String): Int {
        return runCatching { Color.parseColor(hex.trim()) }.getOrDefault(Color.WHITE)
    }

    private fun wrapCaptionText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current.toString()
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.take(MAX_CAPTION_LINES)
    }

    private fun buildFrameTimeline(
        plan: VideoRenderPlan,
        totalFrames: Int
    ): IntArray {
        val timeline = IntArray(totalFrames)
        val totalDuration = plan.scenes.sumOf { max(1L, it.durationMs) }.toDouble().coerceAtLeast(1.0)
        var frameCursor = 0
        plan.scenes.forEachIndexed { index, scene ->
            val ratio = max(1L, scene.durationMs).toDouble() / totalDuration
            val frameCount = if (index == plan.scenes.lastIndex) {
                totalFrames - frameCursor
            } else {
                max(1, (ratio * totalFrames.toDouble()).roundToInt())
            }
            repeat(frameCount.coerceAtMost(totalFrames - frameCursor)) {
                timeline[frameCursor++] = index
            }
        }
        while (frameCursor < totalFrames) {
            timeline[frameCursor] = plan.scenes.lastIndex
            frameCursor += 1
        }
        return timeline
    }

    private fun encodeAudioTrack(
        codec: MediaCodec,
        coordinator: MuxerCoordinator,
        wavData: WavData
    ) {
        val bytesPerFrame = wavData.channelCount * 2
        val chunkSize = 8_192 - (8_192 % bytesPerFrame)
        var offset = 0
        var samplesSubmitted = 0L
        while (offset < wavData.pcmBytes.size) {
            val length = minOf(chunkSize, wavData.pcmBytes.size - offset)
            val ptsUs = samplesSubmitted * 1_000_000L / wavData.sampleRateHz.toLong()
            queueAudioInput(codec, wavData.pcmBytes, ptsUs, offset, length)
            drainEncoder(codec, coordinator, TrackKind.AUDIO, endOfStream = false)
            offset += length
            samplesSubmitted += (length / bytesPerFrame).toLong()
        }
        val eosPtsUs = samplesSubmitted * 1_000_000L / wavData.sampleRateHz.toLong()
        signalAudioEndOfStream(codec, eosPtsUs)
        drainEncoder(codec, coordinator, TrackKind.AUDIO, endOfStream = true)
    }

    private fun queueAudioInput(
        codec: MediaCodec,
        data: ByteArray,
        presentationTimeUs: Long,
        offset: Int,
        length: Int
    ) {
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                inputBuffer.clear()
                inputBuffer.put(data, offset, length)
                codec.queueInputBuffer(inputIndex, 0, length, presentationTimeUs, 0)
                return
            }
        }
    }

    private fun signalAudioEndOfStream(
        codec: MediaCodec,
        presentationTimeUs: Long
    ) {
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return
            }
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        coordinator: MuxerCoordinator,
        trackKind: TrackKind,
        endOfStream: Boolean
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    coordinator.setFormat(trackKind, codec.outputFormat)
                }

                outputIndex >= 0 -> {
                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        continue
                    }
                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        coordinator.writeSample(trackKind, outputBuffer, bufferInfo)
                    }
                    val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (eos) return
                }
            }
        }
    }

    private fun validateRenderedVideo(
        file: File,
        expectedDurationMs: Long,
        expectedWidth: Int,
        expectedHeight: Int,
        bitrate: Int,
        totalFrames: Int
    ): RenderStats {
        require(file.exists() && file.length() > 0L) { "MP4 output khong ton tai hoac rong." }
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            retriever.setDataSource(file.absolutePath)
            extractor.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: 0
            require(durationMs > 0L) { "Video duration khong hop le." }
            require(width > 0 && height > 0) { "Video width/height khong hop le." }
            val firstFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val firstFrameExtracted = firstFrame != null
            firstFrame?.recycle()
            var hasVideoTrack = false
            var hasAudioTrack = false
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) hasVideoTrack = true
                if (mime.startsWith("audio/")) hasAudioTrack = true
            }
            require(hasVideoTrack) { "MP4 chua co video track hop le." }
            require(hasAudioTrack) { "MP4 chua co audio track hop le." }
            require(firstFrameExtracted) { "Khong extract duoc frame dau tien tu MP4." }
            require(width == expectedWidth && height == expectedHeight) {
                "Video output khong khop kich thuoc renderer: ${width}x${height}."
            }
            require(durationMs >= max(1L, expectedDurationMs - 2_000L)) {
                "Video duration ngan hon du kien cua voice."
            }
            require(durationMs <= expectedDurationMs + 2_000L) {
                "Video duration dai hon du kien cua voice."
            }
            return RenderStats(
                durationMs = durationMs,
                width = width,
                height = height,
                bitrate = bitrate,
                totalFrames = totalFrames,
                hasVideoTrack = hasVideoTrack,
                hasAudioTrack = hasAudioTrack,
                firstFrameExtracted = firstFrameExtracted
            )
        } finally {
            runCatching { extractor.release() }
            runCatching { retriever.release() }
        }
    }

    private fun buildResolutionCandidates(
        requestedWidth: Int,
        requestedHeight: Int
    ): List<Pair<Int, Int>> {
        val normalizedRequested = requestedWidth to requestedHeight
        val fallback = 720 to 1280
        return buildList {
            add(normalizedRequested)
            if (normalizedRequested != fallback) {
                add(fallback)
            }
        }
    }

    private fun selectBitrate(width: Int, height: Int): Int {
        // Nội dung là slideshow ảnh tĩnh + giọng đọc (không phải video quay thật nhiều
        // chuyển động), nên không cần bitrate cao như video hành động. Kèm với
        // KEY_BITRATE_MODE = VBR ở createSurfaceVideoEncoder, encoder sẽ tự giảm bit
        // cho các đoạn ảnh đứng yên thay vì ép cố định mức trần này.
        return when {
            width >= 1080 || height >= 1920 -> 3_000_000
            else -> 1_800_000
        }
    }

    private fun estimateDurationMs(wavData: WavData): Long {
        val frameSize = wavData.channelCount * 2
        val totalSamples = wavData.pcmBytes.size / frameSize
        return (totalSamples * 1000L / wavData.sampleRateHz.toLong()).coerceAtLeast(1L)
    }

    private fun extractDebugLong(source: String?, key: String): Long? {
        val normalized = source.orEmpty()
        val prefix = "$key="
        return normalized.split(';')
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
            ?.toLongOrNull()
    }

    private enum class TrackKind {
        VIDEO,
        AUDIO
    }

    private class MuxerCoordinator(
        private val muxer: MediaMuxer,
        private val fps: Int
    ) {
        private var started = false
        private var videoTrackIndex = -1
        private var audioTrackIndex = -1
        private val pendingSamples = mutableListOf<PendingSample>()
        private var videoSampleCount = 0L

        fun setFormat(trackKind: TrackKind, format: MediaFormat) {
            when (trackKind) {
                TrackKind.VIDEO -> if (videoTrackIndex < 0) videoTrackIndex = muxer.addTrack(format)
                TrackKind.AUDIO -> if (audioTrackIndex < 0) audioTrackIndex = muxer.addTrack(format)
            }
            startIfReady()
        }

        fun writeSample(trackKind: TrackKind, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (!started) {
                val bytes = ByteArray(info.size)
                buffer.get(bytes)
                pendingSamples += PendingSample(
                    trackKind = trackKind,
                    bytes = bytes,
                    presentationTimeUs = normalizedPresentationTimeUs(trackKind, info.presentationTimeUs),
                    flags = info.flags
                )
                return
            }
            val trackIndex = when (trackKind) {
                TrackKind.VIDEO -> videoTrackIndex
                TrackKind.AUDIO -> audioTrackIndex
            }
            val copyInfo = MediaCodec.BufferInfo().apply {
                set(0, info.size, normalizedPresentationTimeUs(trackKind, info.presentationTimeUs), info.flags)
            }
            muxer.writeSampleData(trackIndex, buffer, copyInfo)
        }

        fun finish() {
            if (started) {
                runCatching { muxer.stop() }
            }
        }

        private fun startIfReady() {
            if (started || videoTrackIndex < 0 || audioTrackIndex < 0) return
            muxer.start()
            started = true
            pendingSamples.forEach { sample ->
                val trackIndex = when (sample.trackKind) {
                    TrackKind.VIDEO -> videoTrackIndex
                    TrackKind.AUDIO -> audioTrackIndex
                }
                val info = MediaCodec.BufferInfo().apply {
                    set(0, sample.bytes.size, sample.presentationTimeUs, sample.flags)
                }
                muxer.writeSampleData(trackIndex, ByteBuffer.wrap(sample.bytes), info)
            }
            pendingSamples.clear()
        }

        private fun normalizedPresentationTimeUs(trackKind: TrackKind, sourcePresentationTimeUs: Long): Long {
            if (trackKind != TrackKind.VIDEO) {
                return sourcePresentationTimeUs
            }
            val ptsUs = videoSampleCount * 1_000_000L / fps.toLong().coerceAtLeast(1L)
            videoSampleCount += 1L
            return ptsUs
        }
    }

    private data class PendingSample(
        val trackKind: TrackKind,
        val bytes: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private data class RenderStats(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val bitrate: Int,
        val totalFrames: Int,
        val hasVideoTrack: Boolean,
        val hasAudioTrack: Boolean,
        val firstFrameExtracted: Boolean
    )

    private data class WavData(
        val sampleRateHz: Int,
        val channelCount: Int,
        val pcmBytes: ByteArray
    ) {
        companion object {
            fun parse(bytes: ByteArray): WavData {
                require(bytes.size > 44) { "Voice WAV qua ngan." }
                require(String(bytes, 0, 4) == "RIFF") { "Voice artifact khong phai WAV RIFF." }
                require(String(bytes, 8, 4) == "WAVE") { "Voice artifact khong phai WAV." }

                var offset = 12
                var sampleRate = 0
                var channels = 0
                var bitsPerSample = 0
                var pcmStart = -1
                var pcmLength = 0
                while (offset + 8 <= bytes.size) {
                    val chunkId = String(bytes, offset, 4)
                    val chunkSize = littleEndianInt(bytes, offset + 4)
                    val dataStart = offset + 8
                    if (dataStart + chunkSize > bytes.size) break
                    when (chunkId) {
                        "fmt " -> {
                            channels = littleEndianShort(bytes, dataStart + 2)
                            sampleRate = littleEndianInt(bytes, dataStart + 4)
                            bitsPerSample = littleEndianShort(bytes, dataStart + 14)
                        }
                        "data" -> {
                            pcmStart = dataStart
                            pcmLength = chunkSize
                        }
                    }
                    offset = dataStart + chunkSize + (chunkSize % 2)
                }

                require(sampleRate > 0) { "WAV sample rate khong hop le." }
                require(channels in 1..2) { "Renderer hien chi ho tro WAV mono/stereo." }
                require(bitsPerSample == 16) { "Renderer hien chi ho tro WAV 16-bit PCM." }
                require(pcmStart >= 0 && pcmLength > 0) { "WAV khong co data chunk hop le." }

                return WavData(
                    sampleRateHz = sampleRate,
                    channelCount = channels,
                    pcmBytes = bytes.copyOfRange(pcmStart, pcmStart + pcmLength)
                )
            }

            private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
                return (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF) shl 24)
            }

            private fun littleEndianShort(bytes: ByteArray, offset: Int): Int {
                return (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            }
        }
    }

    /**
     * Tron nhac nen (tuy chon, bat ky dinh dang audio may giai ma duoc - mp3/m4a/
     * aac/wav...) vao track giong doc: decode nhac nen ve PCM, resample ve dung
     * sample rate/so kenh cua giong doc, lap lai (loop) hoac de im lang cho khop
     * do dai giong doc, nhan am luong roi cong don gian (additive mix, co clamp)
     * vao PCM cua giong doc. Giong doc LUON la track chinh, khong bao gio bi cat
     * ngan hay keo dai theo nhac nen.
     */
    private fun mixBackgroundMusic(
        voice: WavData,
        musicFile: File,
        loop: Boolean,
        volume: Float
    ): WavData {
        val rawMusic = decodeAudioFileToPcm(musicFile)
        val resampled = resamplePcm16(
            pcm = rawMusic.pcmBytes,
            srcSampleRate = rawMusic.sampleRateHz,
            srcChannels = rawMusic.channelCount,
            dstSampleRate = voice.sampleRateHz,
            dstChannels = voice.channelCount
        )
        val fitted = fitPcmLength(resampled, voice.pcmBytes.size, loop)
        val clampedVolume = volume.coerceIn(0f, 2f)
        val mixed = ByteArray(voice.pcmBytes.size)
        var i = 0
        while (i + 1 < mixed.size) {
            val voiceSample = ((voice.pcmBytes[i].toInt() and 0xFF) or (voice.pcmBytes[i + 1].toInt() shl 8)).toShort().toInt()
            val musicSample = ((fitted[i].toInt() and 0xFF) or (fitted[i + 1].toInt() shl 8)).toShort().toInt()
            val mixedSample = (voiceSample + (musicSample * clampedVolume).roundToInt()).coerceIn(-32768, 32767)
            mixed[i] = (mixedSample and 0xFF).toByte()
            mixed[i + 1] = ((mixedSample shr 8) and 0xFF).toByte()
            i += 2
        }
        return WavData(sampleRateHz = voice.sampleRateHz, channelCount = voice.channelCount, pcmBytes = mixed)
    }

    /** Giai ma 1 file audio bat ky (MediaExtractor + MediaCodec) ve PCM 16-bit tho. */
    private fun decodeAudioFileToPcm(file: File): WavData {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    trackIndex = index
                    format = candidate
                    break
                }
            }
            require(trackIndex >= 0 && format != null) { "File nhac nen khong co audio track hop le." }
            extractor.selectTrack(trackIndex)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder.configure(format, null, null, 0)
            decoder.start()
            val bufferInfo = MediaCodec.BufferInfo()
            val pcmOutput = ByteArrayOutputStream()
            var sawInputEos = false
            var sawOutputEos = false
            var outSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = requireNotNull(decoder.getInputBuffer(inputIndex))
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                    if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (bufferInfo.size > 0 && outputBuffer != null) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.get(chunk)
                            pcmOutput.write(chunk)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = decoder.outputFormat
                        outSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally {
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
            require(outChannelCount in 1..2) { "Renderer chi ho tro nhac nen mono/stereo." }
            return WavData(
                sampleRateHz = outSampleRate,
                channelCount = outChannelCount,
                pcmBytes = pcmOutput.toByteArray()
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Resample tuyen tinh (linear interpolation) + doi so kenh (mono<->stereo) PCM 16-bit. */
    private fun resamplePcm16(
        pcm: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int,
        dstSampleRate: Int,
        dstChannels: Int
    ): ByteArray {
        val srcBytesPerFrame = srcChannels * 2
        if (srcBytesPerFrame <= 0 || pcm.size < srcBytesPerFrame) return ByteArray(0)
        val srcFrameCount = pcm.size / srcBytesPerFrame
        val channelFixed = when {
            srcChannels == dstChannels -> pcm
            srcChannels == 1 && dstChannels == 2 -> ByteArray(srcFrameCount * 4).also { out ->
                for (frame in 0 until srcFrameCount) {
                    val srcOffset = frame * 2
                    val dstOffset = frame * 4
                    out[dstOffset] = pcm[srcOffset]
                    out[dstOffset + 1] = pcm[srcOffset + 1]
                    out[dstOffset + 2] = pcm[srcOffset]
                    out[dstOffset + 3] = pcm[srcOffset + 1]
                }
            }
            srcChannels == 2 && dstChannels == 1 -> ByteArray(srcFrameCount * 2).also { out ->
                for (frame in 0 until srcFrameCount) {
                    val srcOffset = frame * 4
                    val left = ((pcm[srcOffset].toInt() and 0xFF) or (pcm[srcOffset + 1].toInt() shl 8)).toShort().toInt()
                    val right = ((pcm[srcOffset + 2].toInt() and 0xFF) or (pcm[srcOffset + 3].toInt() shl 8)).toShort().toInt()
                    val avg = ((left + right) / 2).coerceIn(-32768, 32767)
                    out[frame * 2] = (avg and 0xFF).toByte()
                    out[frame * 2 + 1] = ((avg shr 8) and 0xFF).toByte()
                }
            }
            else -> pcm
        }

        if (srcSampleRate == dstSampleRate) return channelFixed

        val bytesPerFrame = dstChannels * 2
        val frameCount = channelFixed.size / bytesPerFrame
        if (frameCount <= 0) return ByteArray(0)
        val dstFrameCount = max(1, (frameCount.toLong() * dstSampleRate / srcSampleRate).toInt())
        val out = ByteArray(dstFrameCount * bytesPerFrame)
        for (dstFrame in 0 until dstFrameCount) {
            val srcPosition = dstFrame.toDouble() * srcSampleRate / dstSampleRate
            val srcFrameLow = srcPosition.toInt().coerceIn(0, frameCount - 1)
            val srcFrameHigh = (srcFrameLow + 1).coerceAtMost(frameCount - 1)
            val frac = (srcPosition - srcFrameLow).toFloat()
            for (channel in 0 until dstChannels) {
                val lowOffset = srcFrameLow * bytesPerFrame + channel * 2
                val highOffset = srcFrameHigh * bytesPerFrame + channel * 2
                val lowSample = ((channelFixed[lowOffset].toInt() and 0xFF) or (channelFixed[lowOffset + 1].toInt() shl 8)).toShort().toInt()
                val highSample = ((channelFixed[highOffset].toInt() and 0xFF) or (channelFixed[highOffset + 1].toInt() shl 8)).toShort().toInt()
                val interpolated = (lowSample + (highSample - lowSample) * frac).roundToInt().coerceIn(-32768, 32767)
                val dstOffset = dstFrame * bytesPerFrame + channel * 2
                out[dstOffset] = (interpolated and 0xFF).toByte()
                out[dstOffset + 1] = ((interpolated shr 8) and 0xFF).toByte()
            }
        }
        return out
    }

    /** Lap lai (loop) hoac de im lang PCM cho vua khop do dai track giong doc (tinh theo byte). */
    private fun fitPcmLength(pcm: ByteArray, targetLength: Int, loop: Boolean): ByteArray {
        if (targetLength <= 0) return ByteArray(0)
        if (pcm.isEmpty()) return ByteArray(targetLength)
        if (pcm.size == targetLength) return pcm
        val out = ByteArray(targetLength)
        if (pcm.size > targetLength) {
            System.arraycopy(pcm, 0, out, 0, targetLength)
            return out
        }
        if (!loop) {
            System.arraycopy(pcm, 0, out, 0, pcm.size)
            return out
        }
        var offset = 0
        while (offset < targetLength) {
            val length = minOf(pcm.size, targetLength - offset)
            System.arraycopy(pcm, 0, out, offset, length)
            offset += length
        }
        return out
    }

    companion object {
        const val RENDERER_ID: String = "android-native-slideshow-renderer"
        private const val RENDERER_BACKEND = "surface-canvas"
        private const val VIDEO_MIME_TYPE = "video/avc"
        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
        private const val CODEC_TIMEOUT_US = 10_000L
        // Zoom rat nhe (1.0 -> 1.08) - chuan Ken Burns la cham va tinh te, zoom
        // manh/nhanh la loi thuong gap cua dan nghiep du.
        private const val KEN_BURNS_MAX_ZOOM = 1.08f
        // Pan giu scale co dinh nhe hon zoom de van con du bien di chuyen ngang.
        private const val KEN_BURNS_PAN_ZOOM = 1.06f
        // Bien do pan ngang, tinh theo % chieu rong anh da fit - nho de khong lo
        // ra ngoai bien anh goc (day la ly do can scale > 1 khi pan).
        private const val KEN_BURNS_PAN_FRACTION = 0.05f
        // Chuan short-form caption: cum ngan 3-7 tu, "pop" theo loi doc thay vi 1
        // khoi chu dai tinh suot ca canh.
        private const val MAX_WORDS_PER_CAPTION_CUE = 7
        private const val MIN_WORDS_PER_CAPTION_CUE = 3
        private const val MAX_CAPTION_LINES = 3
        // Chuyen canh mo dan don gian - chuan chuyen nghiep uu tien toi gian
        // (crossfade/cut) thay vi hieu ung loe loet.
        private const val CROSSFADE_DURATION_SECONDS = 0.35f
    }
}
