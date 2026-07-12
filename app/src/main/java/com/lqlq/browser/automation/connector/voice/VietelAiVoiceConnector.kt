package com.lqlq.browser.automation.connector.voice

import com.lqlq.browser.automation.voice.AudioToWavDecoder
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Viettel AI Text-to-Speech (viettelai.vn) - REST API dong bo, tra ve audio truc
 * tiep. Nguoi dung dang ky tai khoan tren viettelai.vn de lay token (API key), dan
 * vao o "Credential / API key".
 *
 * Tra WAV cho pipeline: neu API tra mp3 thi giai ma -> WAV (AudioToWavDecoder) de
 * khop dong bo theo canh (WavAudioAssembler) + renderer (audio/wav).
 *
 * ⚠️ Endpoint/format theo tai lieu viettelai.vn - neu Viettel doi API thi chinh
 * API_URL / cac field body ben duoi.
 */
open class VietelAiVoiceConnector(
    transport: RemoteVoiceTransport = HttpUrlConnectionRemoteVoiceTransport()
) : BaseRemoteVoiceConnector(transport) {

    override fun listVoices(): List<VoiceDefinition> = VOICES

    override fun providerId(): String = AutomationVoiceProviders.VIETTEL_AI_TTS

    override fun displayName(): String = DISPLAY_NAME

    override fun connectionProbeText(): String = SAMPLE_TEXT

    override fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == providerId()) { "Only Viettel AI voice provider is supported." }
        require(!config.apiKey.isNullOrBlank()) { "Viettel AI can token (API key)." }
        require(isVietnameseLocale(config.locale)) { "Viettel AI hien dung locale vi-VN." }
    }

    override suspend fun synthesizeInternal(
        config: VoiceProviderConfig,
        text: String
    ): RemoteVoiceAudioPayload {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Viettel AI yeu cau noi dung khong duoc rong."
            )
        }
        val body = JSONObject().apply {
            put("text", normalized)
            put("voice", resolveVoiceId(config))
            put("speed", config.speechRate.coerceIn(0.5f, 2.0f).toDouble())
            put("tts_return_option", 3) // audio bytes
            put("token", config.apiKey.orEmpty().trim())
            put("without_filter", false)
        }
        // Endpoint co the KHAC nhau theo tai khoan Viettel (viettelai.vn / vtcc.ai /
        // viettelgroup.ai). Cho phep nguoi dung dan endpoint dung vao o "Model /
        // profile" (config.model) de tu sua khi mac dinh bi 404.
        val endpoint = config.model?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) } ?: API_URL
        val response = executeWithRetry(
            RemoteVoiceTransportRequest(
                url = endpoint,
                method = "POST",
                body = body.toString().toByteArray(StandardCharsets.UTF_8),
                contentType = "application/json; charset=utf-8",
                accept = "audio/*,application/json,application/octet-stream",
                // Nen tang Viettel Open Platform nhan token o header (ngoai body).
                additionalHeaders = mapOf("token" to config.apiKey.orEmpty().trim()),
                // Timeout ngan de neu endpoint sai/khong phan hoi thi that bai NHANH,
                // tranh dong bo chan giao dien lau (nghe thu goi mang dong bo).
                connectTimeoutMs = 8_000,
                readTimeoutMs = 12_000
            )
        )
        val rawMime = RemoteVoiceAudioValidator.detectMimeType(response.contentType, response.bytes)
        // Neu server tra JSON (loi) thay vi audio -> bao loi ro rang.
        if (rawMime == "application/octet-stream" && looksLikeJson(response.bytes)) {
            throw mapJsonError(String(response.bytes, StandardCharsets.UTF_8))
        }
        // Dam bao WAV cho pipeline dong bo theo canh.
        return if (rawMime == "audio/wav" || rawMime == "audio/x-wav") {
            RemoteVoiceAudioPayload(bytes = response.bytes, mimeType = "audio/wav")
        } else {
            val decoded = runCatching { AudioToWavDecoder.decodeToWav(response.bytes) }.getOrElse {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.INVALID_AUDIO,
                    "Khong giai ma duoc audio Viettel AI tra ve."
                )
            }
            RemoteVoiceAudioPayload(bytes = decoded.wavBytes, mimeType = "audio/wav")
        }
    }

    override fun mapHttpError(statusCode: Int, body: String): VoiceProviderException {
        return when {
            statusCode == 401 || statusCode == 403 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_API_KEY,
                "Viettel AI tu choi token. Kiem tra lai API key tren viettelai.vn."
            )
            statusCode == 429 -> VoiceProviderException(
                VoiceProviderErrorCode.RATE_LIMITED,
                "Viettel AI dang gioi han toc do (hoac het han muc)."
            )
            statusCode in 500..599 -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Viettel AI tam thoi khong san sang."
            )
            else -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Viettel AI tra ve phan hoi khong hop le (HTTP $statusCode)."
            )
        }
    }

    override fun shouldRetry(code: VoiceProviderErrorCode): Boolean {
        return code == VoiceProviderErrorCode.RATE_LIMITED ||
            code == VoiceProviderErrorCode.PROVIDER_UNAVAILABLE ||
            code == VoiceProviderErrorCode.NETWORK_TRANSIENT
    }

    override fun retryBackoffMs(): LongArray = longArrayOf(1_500L)

    override fun maxTransientRetries(): Int = 1

    private fun mapJsonError(body: String): VoiceProviderException {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        return VoiceProviderException(
            VoiceProviderErrorCode.INVALID_RESPONSE,
            "Viettel AI bao loi: ${message.ifBlank { body.take(200) }}"
        )
    }

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val head = bytes.take(1).firstOrNull()?.toInt()?.toChar()
        return head == '{' || head == '['
    }

    private fun resolveVoiceId(config: VoiceProviderConfig): String {
        val requested = config.voiceId?.trim().orEmpty()
        return VOICES.firstOrNull { it.voiceId == requested }?.voiceId ?: DEFAULT_VOICE_ID
    }

    private fun isVietnameseLocale(locale: String): Boolean {
        val normalized = locale.trim().lowercase()
        return normalized == "vi-vn" || normalized == "vi"
    }

    companion object {
        const val DISPLAY_NAME: String = "Viettel AI Text to Speech"
        const val DEFAULT_VOICE_ID: String = "hn-quynhanh"
        const val SAMPLE_TEXT: String = "Xin chào, đây là giọng đọc tiếng Việt của Viettel AI cho lqlq Browser."
        // ⚠️ Endpoint Viettel AI Open Platform (viettelgroup.ai). Neu tai khoan cua ban
        // dung domain khac (vtcc.ai...) thi dan endpoint dung vao o "Model / profile".
        const val API_URL: String = "https://viettelgroup.ai/voice/api/tts/v1/rest/syn"

        // Ma giong (voice code) theo dashboard Viettel - hn-quynhanh la mac dinh chac
        // chan; cac code khac la phong doan theo ten, neu sai chi rieng giong do loi.
        private val VOICES = listOf(
            voice("hn-quynhanh", "Quỳnh Anh (nữ, Bắc)", isDefault = true, gender = "female"),
            voice("hn-phuongtrang", "Phương Trang (nữ)", gender = "female"),
            voice("hn-thaochi", "Thảo Chi (nữ)", gender = "female"),
            voice("hn-thanhha", "Thanh Hà (nữ)", gender = "female"),
            voice("hn-thanhtung", "Thanh Tùng (nam)", gender = "male"),
            voice("hn-thanhphuong", "Thanh Phương (nữ)", gender = "female"),
            voice("hn-namkhanh", "Nam Khánh (nam)", gender = "male"),
            voice("hn-tienquan", "Tiến Quân (nam)", gender = "male")
        )

        private fun voice(id: String, name: String, isDefault: Boolean = false, gender: String): VoiceDefinition {
            return VoiceDefinition(
                voiceId = id,
                displayName = name,
                locale = "vi-VN",
                engineName = DISPLAY_NAME,
                networkRequired = true,
                installed = true,
                isDefault = isDefault,
                genderHint = gender
            )
        }
    }
}
