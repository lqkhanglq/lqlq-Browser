package com.lqlq.browser.automation.connector.voice

import com.lqlq.browser.automation.voice.AudioToWavDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream

/**
 * Edge Neural TTS - dung endpoint "Read Aloud" cua Microsoft Edge (mien phi, khong
 * can login/API key), CUNG dan giong Azure Neural chat luong cao. Giao thuc:
 * WebSocket + SSML, tra ve audio mp3 + moc WordBoundary (hien chua dung).
 *
 * ⚠️ CANH BAO BAO TRI: Microsoft TUNG DOI cach tinh token Sec-MS-GEC (2024). Neu 1
 * ngay endpoint tra loi loi 403/handshake fail, kha nang cao la SEC_MS_GEC_VERSION
 * hoac cach bam token da doi -> chi can cap nhat 2 hang duoi. Day la endpoint noi bo
 * cua Edge, khong duoc chinh thuc ho tro dung ngoai; da co Android TTS lam fallback.
 *
 * Tra ve WAV (giai ma mp3 -> wav qua AudioToWavDecoder) de tuong thich pipeline dong
 * bo theo canh (WavAudioAssembler) + renderer (yeu cau audio/wav).
 */
class EdgeNeuralTtsConnector(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : VoiceGenerationConnector {

    override fun listVoices(): List<VoiceDefinition> = VIETNAMESE_VOICES

    override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
        validateConfig(config)
        val mp3 = synthesizeMp3("Xin chao, day la giong doc thu nghiem.", resolveVoiceId(config), config)
        require(mp3.isNotEmpty()) { "Edge TTS khong tra ve audio." }
        return VoiceProviderConnectionResult(
            providerId = AutomationVoiceProviders.EDGE_NEURAL_TTS,
            model = config.model,
            voiceId = resolveVoiceId(config),
            locale = config.locale,
            engineName = "Microsoft Edge Neural (Read Aloud)",
            voiceCount = VIETNAMESE_VOICES.size
        )
    }

    override suspend fun synthesizeSample(config: VoiceProviderConfig, text: String): VoiceSampleResult {
        validateConfig(config)
        val mp3 = synthesizeMp3(text.ifBlank { "Xin chao." }, resolveVoiceId(config), config)
        val wav = AudioToWavDecoder.decodeToWav(mp3)
        return VoiceSampleResult(
            bytes = wav.wavBytes,
            mimeType = "audio/wav",
            providerId = AutomationVoiceProviders.EDGE_NEURAL_TTS,
            voiceId = resolveVoiceId(config),
            locale = config.locale,
            engineName = "Microsoft Edge Neural (Read Aloud)"
        )
    }

    override suspend fun generateVoice(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult {
        validateConfig(config)
        require(request.text.isNotBlank()) { "Noi dung voice rong." }
        val mp3 = synthesizeMp3(request.text, resolveVoiceId(config), config)
        val wav = AudioToWavDecoder.decodeToWav(mp3)
        val dataBytes = (wav.wavBytes.size - 44).coerceAtLeast(0)
        val byteRate = wav.sampleRateHz * wav.channelCount * 2
        val durationMs = if (byteRate > 0) (dataBytes.toLong() * 1000L / byteRate) else null
        return VoiceGenerationResult(
            bytes = wav.wavBytes,
            mimeType = "audio/wav",
            metadata = VoiceArtifactMetadata(
                providerId = AutomationVoiceProviders.EDGE_NEURAL_TTS,
                voiceId = resolveVoiceId(config),
                locale = config.locale,
                chunkCount = 1,
                durationMs = durationMs,
                checksum = sha256Hex(wav.wavBytes),
                engineName = "Microsoft Edge Neural (Read Aloud)",
                sampleRateHz = wav.sampleRateHz
            )
        )
    }

    override fun openProviderSettings() = Unit

    private fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == AutomationVoiceProviders.EDGE_NEURAL_TTS) {
            "Chi ho tro Edge Neural TTS provider."
        }
    }

    private fun resolveVoiceId(config: VoiceProviderConfig): String {
        val requested = config.voiceId?.trim().orEmpty()
        return VIETNAMESE_VOICES.firstOrNull { it.voiceId == requested }?.voiceId
            ?: DEFAULT_VOICE_ID
    }

    /** Mo WebSocket toi Edge, gui config + SSML, gom cac khung audio nhi phan -> mp3 bytes. */
    private suspend fun synthesizeMp3(
        text: String,
        voiceId: String,
        config: VoiceProviderConfig
    ): ByteArray = withContext(Dispatchers.IO) {
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val url = buildString {
            append(WSS_BASE_URL)
            append("?TrustedClientToken=").append(TRUSTED_CLIENT_TOKEN)
            append("&Sec-MS-GEC=").append(generateSecMsGec())
            append("&Sec-MS-GEC-Version=").append(SEC_MS_GEC_VERSION)
            append("&ConnectionId=").append(connectionId)
        }
        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val audioOut = ByteArrayOutputStream()
        val latch = CountDownLatch(1)
        val errorRef = arrayOfNulls<Throwable>(1)
        val failureDetail = arrayOfNulls<String>(1)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runCatching {
                    webSocket.send(buildSpeechConfigMessage())
                    webSocket.send(buildSsmlMessage(text, voiceId, config, connectionId))
                }.onFailure { error ->
                    errorRef[0] = error
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Khung van ban: metadata/turn.start/turn.end. turn.end = xong.
                if (text.contains("Path:turn.end", ignoreCase = true)) {
                    runCatching { webSocket.close(1000, null) }
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Khung nhi phan: [2 byte header-length big-endian][header][audio].
                // Chi lay phan audio khi header co "Path:audio".
                val data = bytes.toByteArray()
                if (data.size < 2) return
                val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                val headerEnd = 2 + headerLen
                if (headerEnd > data.size) return
                val header = String(data, 2, headerLen, Charsets.UTF_8)
                if (header.contains("Path:audio", ignoreCase = true)) {
                    audioOut.write(data, headerEnd, data.size - headerEnd)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                runCatching { webSocket.close(1000, null) }
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                errorRef[0] = t
                failureDetail[0] = buildString {
                    if (response != null) {
                        append("HTTP ").append(response.code)
                        response.message.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
                    }
                    t.message?.let {
                        if (isNotEmpty()) append(" | ")
                        append(it)
                    }
                }.ifBlank { t.javaClass.simpleName }
                latch.countDown()
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        try {
            val completed = latch.await(60, TimeUnit.SECONDS)
            if (!completed) {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.TIMEOUT,
                    "Edge TTS het thoi gian cho phan hoi."
                )
            }
            errorRef[0]?.let { error ->
                throw VoiceProviderException(
                    VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                    "Edge TTS loi: ${failureDetail[0] ?: error.message ?: "khong ro"}",
                    error
                )
            }
            val mp3 = audioOut.toByteArray()
            if (mp3.isEmpty()) {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.INVALID_AUDIO,
                    "Edge TTS khong tra ve du lieu audio."
                )
            }
            mp3
        } finally {
            runCatching { webSocket.cancel() }
        }
    }

    private fun buildSpeechConfigMessage(): String {
        val timestamp = utcTimestamp()
        val body = """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},"outputFormat":"$OUTPUT_FORMAT"}}}}"""
        return "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n$body"
    }

    private fun buildSsmlMessage(
        text: String,
        voiceId: String,
        config: VoiceProviderConfig,
        connectionId: String
    ): String {
        val timestamp = utcTimestamp()
        val ratePercent = ((config.speechRate - 1f) * 100f).toInt()
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='vi-VN'>" +
            "<voice name='$voiceId'><prosody pitch='+0Hz' rate='$rateStr' volume='+0%'>" +
            escapeXml(text) +
            "</prosody></voice></speak>"
        return "X-RequestId:$connectionId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n$ssml"
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun utcTimestamp(): String {
        val format = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return "${format.format(java.util.Date())} GMT+0000 (Coordinated Universal Time)"
    }

    /**
     * Sec-MS-GEC: SHA256(ticks_100ns + TrustedClientToken).uppercase(). ticks =
     * so giay tu 1601-01-01 (Windows epoch), lam tron xuong boi 300s (5 phut), doi
     * ra don vi 100-nanosecond. (Thuat toan theo edge-tts.)
     */
    private fun generateSecMsGec(): String {
        val unixSeconds = System.currentTimeMillis() / 1000L
        var ticks = unixSeconds + WINDOWS_EPOCH_OFFSET_SECONDS
        ticks -= ticks % 300L
        val ticks100ns = ticks * 10_000_000L
        return sha256Hex("$ticks100ns$TRUSTED_CLIENT_TOKEN").uppercase(Locale.US)
    }

    private fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.US_ASCII))

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_VOICE_ID = "vi-VN-HoaiMyNeural"

        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        // OkHttp newWebSocket yeu cau scheme http/https (tu nang cap len WebSocket/TLS),
        // KHONG nhan "wss://" truc tiep - dung https:// la dung, van la ket noi wss thuc su.
        private const val WSS_BASE_URL =
            "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        // ⚠️ Hang de doi khi Microsoft cap nhat Edge - cap nhat neu handshake 403.
        // Dat KHOP theo edge-tts (bo gia tri da kiem chung): version + User-Agent
        // phai cung so Chromium.
        private const val CHROMIUM_FULL_VERSION = "130.0.2849.68"
        private const val CHROMIUM_MAJOR_VERSION = "130"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
        private const val ORIGIN = "chrome-extension://jdiccldimpstbhdlkecjhgpkelgknmcad"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0"
        private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val WINDOWS_EPOCH_OFFSET_SECONDS = 11_644_473_600L

        private val VIETNAMESE_VOICES = listOf(
            VoiceDefinition(
                voiceId = "vi-VN-HoaiMyNeural",
                displayName = "Hoài My (nữ) — Edge Neural",
                locale = "vi-VN",
                engineName = "Microsoft Edge Neural",
                networkRequired = true,
                installed = true,
                isDefault = true,
                genderHint = "female"
            ),
            VoiceDefinition(
                voiceId = "vi-VN-NamMinhNeural",
                displayName = "Nam Minh (nam) — Edge Neural",
                locale = "vi-VN",
                engineName = "Microsoft Edge Neural",
                networkRequired = true,
                installed = true,
                isDefault = false,
                genderHint = "male"
            )
        )
    }
}
