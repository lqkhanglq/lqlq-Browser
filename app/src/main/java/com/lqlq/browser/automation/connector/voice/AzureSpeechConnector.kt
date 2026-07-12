package com.lqlq.browser.automation.connector.voice

import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * Azure Speech (Cognitive Services) Text-to-Speech - endpoint CHINH THUC cua
 * Microsoft, bac mien phi ~500.000 ky tu/thang. Dung CHINH bo giong Neural
 * vi-VN-HoaiMyNeural / vi-VN-NamMinhNeural (giong ma Edge "Read Aloud" xai) nhung
 * qua API co key/region hop phap -> KHONG bi chan 403 nhu Edge.
 *
 * Yeu cau outputFormat "riff-24khz-16bit-mono-pcm" -> Azure tra ve WAV 16-bit mono
 * thang, khong can giai ma mp3, khop luon pipeline dong bo theo canh (WavAudioAssembler)
 * + renderer (audio/wav). Dong bo (response chinh la audio) nen don gian hon FPT (async).
 */
open class AzureSpeechConnector(
    transport: RemoteVoiceTransport = HttpUrlConnectionRemoteVoiceTransport()
) : BaseRemoteVoiceConnector(transport) {

    override fun listVoices(): List<VoiceDefinition> = VOICES

    override fun providerId(): String = AutomationVoiceProviders.AZURE_SPEECH

    override fun displayName(): String = DISPLAY_NAME

    override fun connectionProbeText(): String = SAMPLE_TEXT

    override fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == providerId()) { "Only Azure Speech provider is supported." }
        require(!config.apiKey.isNullOrBlank()) { "Azure Speech can API key (Subscription Key)." }
        require(!config.region.isNullOrBlank()) { "Azure Speech can Region (vd southeastasia, eastus)." }
        require(isVietnameseLocale(config.locale)) { "Azure Speech hien dung locale vi-VN." }
    }

    override suspend fun synthesizeInternal(
        config: VoiceProviderConfig,
        text: String
    ): RemoteVoiceAudioPayload {
        val normalized = text.trim()
        if (normalized.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Azure Speech yeu cau noi dung khong duoc rong."
            )
        }
        val region = config.region.orEmpty().trim().lowercase()
        val url = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
        val ssml = buildSsml(normalized, resolveVoiceId(config), config)
        val response = executeWithRetry(
            RemoteVoiceTransportRequest(
                url = url,
                method = "POST",
                body = ssml.toByteArray(StandardCharsets.UTF_8),
                contentType = "application/ssml+xml; charset=utf-8",
                accept = "audio/*",
                additionalHeaders = mapOf(
                    "Ocp-Apim-Subscription-Key" to config.apiKey.orEmpty().trim(),
                    "X-Microsoft-OutputFormat" to OUTPUT_FORMAT,
                    "User-Agent" to "lqlq-browser"
                ),
                readTimeoutMs = 90_000
            )
        )
        val mimeType = RemoteVoiceAudioValidator.detectMimeType(response.contentType, response.bytes)
        RemoteVoiceAudioValidator.validate(response.bytes, mimeType)
        return RemoteVoiceAudioPayload(bytes = response.bytes, mimeType = mimeType)
    }

    override fun mapHttpError(statusCode: Int, body: String): VoiceProviderException {
        return when {
            statusCode == 401 || statusCode == 403 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_API_KEY,
                "Azure tu choi Subscription Key hoac Region. Kiem tra lai key + region tren Azure Portal."
            )
            statusCode == 400 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Azure tu choi yeu cau (SSML/giong/format khong hop le)."
            )
            statusCode == 404 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Azure endpoint khong dung - kiem tra lai Region."
            )
            statusCode == 429 -> VoiceProviderException(
                VoiceProviderErrorCode.RATE_LIMITED,
                "Azure dang gioi han toc do yeu cau (hoac het han muc thang)."
            )
            statusCode in 500..599 -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Azure Speech tam thoi khong san sang."
            )
            else -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "Azure Speech tra ve phan hoi khong hop le (HTTP $statusCode)."
            )
        }
    }

    override fun maxTransientRetries(): Int = 3

    override fun retryBackoffMs(): LongArray = longArrayOf(1_000L, 2_000L, 4_000L)

    private fun buildSsml(text: String, voiceId: String, config: VoiceProviderConfig): String {
        val ratePercent = ((config.speechRate - 1f) * 100f).roundToInt().coerceIn(-50, 100)
        val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"
        val pitchPercent = ((config.pitch - 1f) * 50f).roundToInt().coerceIn(-50, 50)
        val pitchStr = if (pitchPercent >= 0) "+$pitchPercent%" else "$pitchPercent%"
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='vi-VN'>" +
            "<voice name='$voiceId'><prosody rate='$rateStr' pitch='$pitchStr'>" +
            escapeXml(text) +
            "</prosody></voice></speak>"
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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
        const val DISPLAY_NAME: String = "Azure Speech (Neural)"
        const val DEFAULT_VOICE_ID: String = "vi-VN-HoaiMyNeural"
        const val SAMPLE_TEXT: String = "Xin chào, đây là giọng đọc Azure Neural tiếng Việt cho lqlq Browser."
        private const val OUTPUT_FORMAT = "riff-24khz-16bit-mono-pcm"

        private val VOICES = listOf(
            VoiceDefinition(
                voiceId = "vi-VN-HoaiMyNeural",
                displayName = "Hoài My (nữ) — Azure Neural",
                locale = "vi-VN",
                engineName = "Azure Speech",
                networkRequired = true,
                installed = true,
                isDefault = true,
                genderHint = "female"
            ),
            VoiceDefinition(
                voiceId = "vi-VN-NamMinhNeural",
                displayName = "Nam Minh (nam) — Azure Neural",
                locale = "vi-VN",
                engineName = "Azure Speech",
                networkRequired = true,
                installed = true,
                isDefault = false,
                genderHint = "male"
            )
        )
    }
}
