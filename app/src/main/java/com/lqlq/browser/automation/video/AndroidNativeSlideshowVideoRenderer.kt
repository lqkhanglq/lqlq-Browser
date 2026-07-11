package com.lqlq.browser.automation.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
                        wavData = wavData,
                        voiceDurationMs = voiceDurationMs
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
        voiceDurationMs: Long
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
            wavData = wavData
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
        wavData: WavData
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
                sceneBitmaps = sceneBitmaps
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
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
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
        sceneBitmaps: List<Pair<VideoRenderScenePlan, Bitmap>>
    ) {
        val timeline = buildFrameTimeline(plan, totalFrames)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        repeat(totalFrames) { frameIndex ->
            val canvas = inputSurface.lockCanvas(null)
            try {
                canvas.drawColor(Color.BLACK)
                val sceneIndex = timeline[frameIndex].coerceIn(0, sceneBitmaps.lastIndex)
                val (scene, bitmap) = sceneBitmaps[sceneIndex]
                drawBitmapCover(canvas, bitmap, width, height, paint)
                drawOverlayText(canvas, scene, width, height)
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }
            drainEncoder(codec, coordinator, TrackKind.VIDEO, endOfStream = false)
        }
        codec.signalEndOfInputStream()
        drainEncoder(codec, coordinator, TrackKind.VIDEO, endOfStream = true)
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

    private fun drawOverlayText(
        canvas: Canvas,
        scene: VideoRenderScenePlan,
        width: Int,
        height: Int
    ) {
        val subtitle = scene.subtitleText.trim()
        if (subtitle.isBlank()) {
            return
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88000000.toInt()
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.040f).coerceAtLeast(28f)
            textAlign = Paint.Align.CENTER
        }
        val maxText = subtitle.take(140)
        val y = height - (height * 0.09f)
        canvas.drawRect(
            width * 0.08f,
            y - textPaint.textSize * 1.3f,
            width * 0.92f,
            y + textPaint.textSize * 0.4f,
            backgroundPaint
        )
        canvas.drawText(maxText, width / 2f, y, textPaint)
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
        return when {
            width >= 1080 || height >= 1920 -> 8_000_000
            else -> 4_000_000
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

    companion object {
        const val RENDERER_ID: String = "android-native-slideshow-renderer"
        private const val RENDERER_BACKEND = "surface-canvas"
        private const val VIDEO_MIME_TYPE = "video/avc"
        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}
