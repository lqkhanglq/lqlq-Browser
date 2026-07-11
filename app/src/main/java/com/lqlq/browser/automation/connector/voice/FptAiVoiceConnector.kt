package com.lqlq.browser.automation.connector.voice

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext

open class FptAiVoiceConnector(
    transport: RemoteVoiceTransport = HttpUrlConnectionRemoteVoiceTransport()
) : BaseRemoteVoiceConnector(transport) {

    override fun listVoices(): List<VoiceDefinition> {
        return listOf(
            voice("banmai", "banmai", "Nu mien Bac", isDefault = true),
            voice("lannhi", "lannhi", "Nu mien Nam"),
            voice("leminh", "leminh", "Nam mien Bac"),
            voice("myan", "myan", "Nu mien Trung"),
            voice("thuminh", "thuminh", "Nu mien Bac"),
            voice("giahuy", "giahuy", "Nam mien Trung"),
            voice("linhsan", "linhsan", "Nu mien Nam")
        )
    }

    override fun providerId(): String = AutomationVoiceProviders.FPT_AI_TTS

    override fun displayName(): String = DISPLAY_NAME

    override fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == providerId()) { "Only FPT.AI voice provider is supported." }
        require(!config.apiKey.isNullOrBlank()) { "FPT.AI API key is required." }
        require(isVietnameseLocale(config.locale)) { "FPT.AI currently requires locale vi-VN." }
        require(resolveVoiceId(config) in SUPPORTED_VOICE_IDS) { "FPT.AI voice is invalid." }
        require(resolveOutputFormat(config) in SUPPORTED_OUTPUT_FORMATS) { "FPT.AI output format is invalid." }
        resolveSpeedHeader(config)
    }

    override fun connectionProbeText(): String = SAMPLE_TEXT

    override suspend fun synthesizeInternal(
        config: VoiceProviderConfig,
        text: String
    ): RemoteVoiceAudioPayload {
        return synthesizeChunks(config, text).payload
    }

    override suspend fun buildGenerationResult(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult {
        val synthesis = synthesizeChunks(config, request.text)
        return VoiceGenerationResult(
            bytes = synthesis.payload.bytes,
            mimeType = synthesis.payload.mimeType,
            metadata = VoiceArtifactMetadata(
                providerId = providerId(),
                voiceId = resolveVoiceId(config),
                locale = "vi-VN",
                chunkCount = synthesis.chunkCount,
                durationMs = null,
                checksum = checksum(synthesis.payload.bytes),
                engineName = DISPLAY_NAME,
                sampleRateHz = request.sampleRateHz
            )
        )
    }

    override fun mapHttpError(statusCode: Int, body: String): VoiceProviderException {
        val normalizedBody = body.lowercase()
        return when {
            statusCode == 401 || statusCode == 403 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_API_KEY,
                "FPT.AI tu choi API key hoac quyen truy cap hien tai."
            )

            statusCode == 404 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI endpoint khong ton tai hoac response khong dung contract."
            )

            statusCode == 429 && normalizedBody.contains("daily") -> VoiceProviderException(
                VoiceProviderErrorCode.DAILY_REQUEST_LIMIT,
                "FPT.AI da dat gioi han request trong ngay."
            )

            statusCode == 429 && normalizedBody.contains("quota") -> VoiceProviderException(
                VoiceProviderErrorCode.QUOTA_OR_BILLING_REQUIRED,
                "FPT.AI yeu cau han muc hop le truoc khi tao giong doc."
            )

            statusCode == 429 -> VoiceProviderException(
                VoiceProviderErrorCode.RATE_LIMITED,
                "FPT.AI dang gioi han toc do yeu cau."
            )

            statusCode in 500..599 -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "FPT.AI tam thoi khong san sang."
            )

            normalizedBody.contains("voice") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_VOICE,
                "FPT.AI tu choi voice dang chon."
            )

            normalizedBody.contains("speed") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_SPEED,
                "FPT.AI tu choi toc do giong doc dang chon."
            )

            normalizedBody.contains("format") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_FORMAT,
                "FPT.AI tu choi output format dang chon."
            )

            normalizedBody.contains("too long") || normalizedBody.contains("5000") -> VoiceProviderException(
                VoiceProviderErrorCode.TEXT_TOO_LONG,
                "Noi dung vuot qua gioi han FPT.AI cho mot request."
            )

            else -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI tra ve phan hoi khong hop le."
            )
        }
    }

    override fun shouldRetry(code: VoiceProviderErrorCode): Boolean {
        return code == VoiceProviderErrorCode.RATE_LIMITED ||
            code == VoiceProviderErrorCode.PROVIDER_UNAVAILABLE ||
            code == VoiceProviderErrorCode.NETWORK_TRANSIENT
    }

    override fun retryBackoffMs(): LongArray = longArrayOf(1_000L, 2_000L)

    protected override fun mapThrowable(error: Throwable): VoiceProviderException {
        val mapped = super.mapThrowable(error)
        return when (mapped.code) {
            VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
            VoiceProviderErrorCode.TIMEOUT -> VoiceProviderException(
                VoiceProviderErrorCode.NETWORK_TRANSIENT,
                mapped.message
            )

            else -> mapped
        }
    }

    private suspend fun synthesizeChunks(
        config: VoiceProviderConfig,
        text: String
    ): ChunkSynthesisResult {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI yeu cau noi dung text khong duoc rong."
            )
        }
        val chunks = splitIntoChunks(normalizedText)
        val outputFormat = resolveOutputFormat(config)
        if (chunks.size > 1 && outputFormat != "wav") {
            throw VoiceProviderException(
                VoiceProviderErrorCode.USER_ACTION_REQUIRED,
                "Noi dung dai hon gioi han mot request. Hay chon wav de FPT.AI xu ly nhieu chunk an toan."
            )
        }
        val payloads = mutableListOf<RemoteVoiceAudioPayload>()
        chunks.forEachIndexed { index, chunk ->
            currentCoroutineContext().ensureActive()
            val submitted = submitChunk(config, chunk)
            try {
                payloads += waitForAudioPayload(submitted.audioUrl)
            } catch (error: VoiceProviderException) {
                if (index == 0 && error.code in FAIL_FAST_CODES) {
                    throw error
                }
                throw error
            }
        }
        val merged = if (payloads.size == 1) {
            payloads.first()
        } else {
            mergeWavePayloads(payloads)
        }
        return ChunkSynthesisResult(
            payload = merged,
            chunkCount = chunks.size
        )
    }

    private suspend fun submitChunk(
        config: VoiceProviderConfig,
        text: String
    ): SubmittedChunk {
        val response = executeWithRetry(
            RemoteVoiceTransportRequest(
                url = API_URL,
                method = "POST",
                body = text.toByteArray(StandardCharsets.UTF_8),
                contentType = "text/plain; charset=utf-8",
                accept = "application/json",
                additionalHeaders = mapOf(
                    "api_key" to config.apiKey.orEmpty().trim(),
                    "voice" to resolveVoiceId(config),
                    "speed" to resolveSpeedHeader(config),
                    "format" to resolveOutputFormat(config),
                    "Cache-Control" to "no-cache"
                )
            )
        )
        val submitJson = parseJson(response.body)
        val errorCode = submitJson.optInt("error", -1)
        if (errorCode != 0) {
            throw mapSubmitError(errorCode, submitJson.optString("message"))
        }
        val audioUrl = submitJson.optString("async").trim()
        val requestId = submitJson.optString("request_id").trim()
        if (audioUrl.isBlank()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.AUDIO_URL_INVALID,
                "FPT.AI khong tra ve async URL hop le."
            )
        }
        requireHttpsUrl(audioUrl)
        return SubmittedChunk(
            audioUrl = audioUrl,
            requestId = requestId.ifBlank { null }
        )
    }

    private suspend fun waitForAudioPayload(audioUrl: String): RemoteVoiceAudioPayload {
        val startedAt = System.currentTimeMillis()
        delay(initialPollDelayMs())
        var attempt = 0
        while (System.currentTimeMillis() - startedAt < maxPollWindowMs()) {
            currentCoroutineContext().ensureActive()
            val response = executeTransport(
                RemoteVoiceTransportRequest(
                    url = audioUrl,
                    method = "GET",
                    body = ByteArray(0),
                    contentType = "application/octet-stream",
                    accept = "audio/*,application/octet-stream,application/json",
                    followRedirects = true,
                    connectTimeoutMs = 15_000,
                    readTimeoutMs = 90_000
                )
            )
            when {
                response.statusCode == 200 -> {
                    val mimeType = RemoteVoiceAudioValidator.detectMimeType(response.contentType, response.bytes)
                    if (mimeType.startsWith("audio/")) {
                        RemoteVoiceAudioValidator.validate(response.bytes, mimeType)
                        return RemoteVoiceAudioPayload(
                            bytes = response.bytes,
                            mimeType = mimeType
                        )
                    }
                }

                response.statusCode == 401 || response.statusCode == 403 || response.statusCode == 404 -> {
                    // FPT async URL co the chua san sang ngay.
                }

                response.statusCode == 429 -> throw VoiceProviderException(
                    VoiceProviderErrorCode.RATE_LIMITED,
                    "FPT.AI dang gioi han polling tai thoi diem nay."
                )

                response.statusCode in 500..599 -> throw VoiceProviderException(
                    VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                    "FPT.AI tam thoi chua cung cap duoc audio artifact."
                )

                else -> throw VoiceProviderException(
                    VoiceProviderErrorCode.AUDIO_DOWNLOAD_FAILED,
                    "Khong the tai audio artifact tu FPT.AI."
                )
            }
            val schedule = pollScheduleMs()
            delay(schedule[attempt.coerceAtMost(schedule.lastIndex)])
            attempt += 1
        }
        throw VoiceProviderException(
            VoiceProviderErrorCode.PROVIDER_TIMEOUT,
            "FPT.AI chua san sang audio artifact trong cua so cho phep."
        )
    }

    protected open fun initialPollDelayMs(): Long = INITIAL_POLL_DELAY_MS

    protected open fun pollScheduleMs(): LongArray = POLL_SCHEDULE_MS

    protected open fun maxPollWindowMs(): Long = MAX_POLL_WINDOW_MS

    private fun mapSubmitError(errorCode: Int, message: String?): VoiceProviderException {
        val normalized = message.orEmpty().lowercase()
        return when {
            normalized.contains("voice") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_VOICE,
                "FPT.AI tu choi voice dang chon."
            )

            normalized.contains("speed") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_SPEED,
                "FPT.AI tu choi toc do dang chon."
            )

            normalized.contains("format") -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_FORMAT,
                "FPT.AI tu choi output format dang chon."
            )

            normalized.contains("too long") || normalized.contains("5000") -> VoiceProviderException(
                VoiceProviderErrorCode.TEXT_TOO_LONG,
                "Noi dung vuot qua gioi han FPT.AI cho mot request."
            )

            normalized.contains("busy") -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_BUSY,
                "FPT.AI dang ban, can thu lai sau."
            )

            normalized.contains("quota") || normalized.contains("billing") -> VoiceProviderException(
                VoiceProviderErrorCode.QUOTA_OR_BILLING_REQUIRED,
                "FPT.AI can han muc hop le truoc khi tao giong doc."
            )

            errorCode != 0 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI tra ve loi submit khong hop le."
            )

            else -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI tra ve submit response khong hop le."
            )
        }
    }

    private fun splitIntoChunks(text: String): List<String> {
        if (text.length <= CHUNK_CHAR_LIMIT) {
            return listOf(text)
        }
        val normalizedParagraphs = text
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        normalizedParagraphs.forEach { paragraph ->
            val paragraphSegments = splitParagraph(paragraph)
            paragraphSegments.forEach { segment ->
                if (segment.length > MAX_PROVIDER_CHARS) {
                    throw VoiceProviderException(
                        VoiceProviderErrorCode.TEXT_TOO_LONG,
                        "Khong the tach noi dung ve dung gioi han FPT.AI."
                    )
                }
                if (current.isEmpty()) {
                    current.append(segment)
                } else if (current.length + 2 + segment.length <= CHUNK_CHAR_LIMIT) {
                    current.append("\n\n").append(segment)
                } else {
                    chunks += current.toString()
                    current = StringBuilder(segment)
                }
            }
        }
        if (current.isNotEmpty()) {
            chunks += current.toString()
        }
        return chunks
    }

    private fun splitParagraph(paragraph: String): List<String> {
        if (paragraph.length <= CHUNK_CHAR_LIMIT) {
            return listOf(paragraph)
        }
        val sentences = paragraph
            .split(Regex("(?<=[.!?;:])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sentences.size == 1) {
            return forceSplit(paragraph)
        }
        val segments = mutableListOf<String>()
        var current = StringBuilder()
        sentences.forEach { sentence ->
            if (sentence.length > CHUNK_CHAR_LIMIT) {
                if (current.isNotEmpty()) {
                    segments += current.toString()
                    current = StringBuilder()
                }
                segments += forceSplit(sentence)
            } else if (current.isEmpty()) {
                current.append(sentence)
            } else if (current.length + 1 + sentence.length <= CHUNK_CHAR_LIMIT) {
                current.append(' ').append(sentence)
            } else {
                segments += current.toString()
                current = StringBuilder(sentence)
            }
        }
        if (current.isNotEmpty()) {
            segments += current.toString()
        }
        return segments
    }

    private fun forceSplit(text: String): List<String> {
        val segments = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + CHUNK_CHAR_LIMIT, text.length)
            segments += text.substring(start, end).trim()
            start = end
        }
        return segments.filter { it.isNotEmpty() }
    }

    private fun mergeWavePayloads(payloads: List<RemoteVoiceAudioPayload>): RemoteVoiceAudioPayload {
        val parsed = payloads.map { payload ->
            require(payload.mimeType == "audio/wav" || payload.mimeType == "audio/x-wav") {
                "Only wav multi-chunk merge is supported."
            }
            parseWave(payload.bytes)
        }
        val first = parsed.first()
        parsed.drop(1).forEach { next ->
            if (!first.formatChunk.contentEquals(next.formatChunk)) {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.INVALID_AUDIO,
                    "FPT.AI tra ve cac chunk WAV khong cung dinh dang nen khong the ghep an toan."
                )
            }
        }
        val combinedDataSize = parsed.sumOf { it.audioData.size }
        val totalSize = 4 + (8 + first.formatChunk.size) + (8 + combinedDataSize)
        val buffer = ByteBuffer.allocate(8 + totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buffer.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(first.formatChunk.size)
        buffer.put(first.formatChunk)
        buffer.put("data".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(combinedDataSize)
        parsed.forEach { buffer.put(it.audioData) }
        val merged = buffer.array()
        return RemoteVoiceAudioPayload(
            bytes = merged,
            mimeType = "audio/wav"
        )
    }

    private fun parseWave(bytes: ByteArray): ParsedWave {
        if (bytes.size < 44) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "FPT.AI tra ve WAV khong hop le."
            )
        }
        if (ascii(bytes, 0, 4) != "RIFF" || ascii(bytes, 8, 4) != "WAVE") {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "FPT.AI tra ve audio khong phai RIFF/WAVE."
            )
        }
        var offset = 12
        var formatChunk: ByteArray? = null
        var audioData: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val chunkId = ascii(bytes, offset, 4)
            val chunkSize = littleEndianInt(bytes, offset + 4)
            val dataStart = offset + 8
            val dataEnd = dataStart + chunkSize
            if (dataEnd > bytes.size) {
                break
            }
            when (chunkId) {
                "fmt " -> formatChunk = bytes.copyOfRange(dataStart, dataEnd)
                "data" -> audioData = bytes.copyOfRange(dataStart, dataEnd)
            }
            offset = dataEnd + (chunkSize % 2)
        }
        if (formatChunk == null || audioData == null || audioData.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "FPT.AI tra ve WAV thieu fmt/data chunk."
            )
        }
        return ParsedWave(formatChunk, audioData)
    }

    private fun parseJson(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (error: Throwable) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "FPT.AI tra ve JSON khong hop le.",
                error
            )
        }
    }

    private fun requireHttpsUrl(url: String) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.AUDIO_URL_INVALID,
                "FPT.AI tra ve async URL khong an toan."
            )
        }
    }

    private fun resolveVoiceId(config: VoiceProviderConfig): String {
        return config.voiceId?.trim()?.lowercase().orEmpty().ifBlank { DEFAULT_VOICE_ID }
    }

    private fun resolveOutputFormat(config: VoiceProviderConfig): String {
        return config.outputFormat.trim().lowercase().ifBlank { DEFAULT_OUTPUT_FORMAT }
    }

    private fun resolveSpeedHeader(config: VoiceProviderConfig): String {
        val speed = ((config.speechRate - 1f) * 3f).roundToInt().coerceIn(-3, 3)
        val normalized = speed.toString()
        require(normalized in SUPPORTED_SPEED_VALUES) { "FPT.AI speed is invalid." }
        return normalized
    }

    private fun isVietnameseLocale(locale: String): Boolean {
        val normalized = locale.trim().lowercase()
        return normalized == "vi-vn" || normalized == "vi"
    }

    private fun voice(
        voiceId: String,
        displayName: String,
        genderHint: String,
        isDefault: Boolean = false
    ): VoiceDefinition {
        return VoiceDefinition(
            voiceId = voiceId,
            displayName = displayName,
            locale = "vi-VN",
            engineName = DISPLAY_NAME,
            networkRequired = true,
            installed = true,
            isDefault = isDefault,
            genderHint = genderHint
        )
    }

    private fun checksum(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String {
        return String(bytes, offset, length, StandardCharsets.US_ASCII)
    }

    private data class SubmittedChunk(
        val audioUrl: String,
        val requestId: String?
    )

    private data class ChunkSynthesisResult(
        val payload: RemoteVoiceAudioPayload,
        val chunkCount: Int
    )

    private data class ParsedWave(
        val formatChunk: ByteArray,
        val audioData: ByteArray
    )

    companion object {
        const val API_URL: String = "https://api.fpt.ai/hmi/tts/v5"
        const val DISPLAY_NAME: String = "FPT.AI Text to Speech"
        const val DEFAULT_VOICE_ID: String = "banmai"
        const val DEFAULT_OUTPUT_FORMAT: String = "wav"
        const val SAMPLE_TEXT: String = "Xin chào, đây là giọng đọc tiếng Việt được tạo bởi FPT.AI cho lqlq Browser."

        private const val MAX_PROVIDER_CHARS = 5_000
        private const val CHUNK_CHAR_LIMIT = 4_500
        private const val INITIAL_POLL_DELAY_MS = 5_000L
        private const val MAX_POLL_WINDOW_MS = 120_000L
        private val POLL_SCHEDULE_MS = longArrayOf(5_000L, 10_000L, 15_000L, 20_000L)
        private val SUPPORTED_OUTPUT_FORMATS = setOf("mp3", "wav")
        private val SUPPORTED_SPEED_VALUES = setOf("-3", "-2", "-1", "0", "1", "2", "3")
        private val SUPPORTED_VOICE_IDS = setOf(
            "banmai",
            "lannhi",
            "leminh",
            "myan",
            "thuminh",
            "giahuy",
            "linhsan"
        )
        private val FAIL_FAST_CODES = setOf(
            VoiceProviderErrorCode.INVALID_API_KEY,
            VoiceProviderErrorCode.QUOTA_OR_BILLING_REQUIRED,
            VoiceProviderErrorCode.DAILY_REQUEST_LIMIT,
            VoiceProviderErrorCode.INVALID_VOICE,
            VoiceProviderErrorCode.INVALID_SPEED,
            VoiceProviderErrorCode.INVALID_FORMAT,
            VoiceProviderErrorCode.TEXT_TOO_LONG
        )
    }
}
