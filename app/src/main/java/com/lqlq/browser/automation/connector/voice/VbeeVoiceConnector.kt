package com.lqlq.browser.automation.connector.voice

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

class VbeeVoiceConnector(
    transport: RemoteVoiceTransport = HttpUrlConnectionRemoteVoiceTransport()
) : BaseRemoteVoiceConnector(transport) {

    override fun listVoices(): List<VoiceDefinition> {
        return listOf(
            voice("hn_female_minhquy_vdts_48k-hsmm", "Minh Quy", true, "female"),
            voice("hn_male_xuantin_vdts_48k-hsmm", "Xuan Tin", false, "male"),
            voice("sg_female_thaotrinh_vdts_48k-hsmm", "Thao Trinh", false, "female"),
            voice("sg_male_minhhoang_vdts_48k-hsmm", "Minh Hoang", false, "male")
        )
    }

    override fun providerId(): String = AutomationVoiceProviders.VBEE_TTS

    override fun displayName(): String = "VBEE"

    override fun validateConfig(config: VoiceProviderConfig) {
        require(config.providerId == providerId()) { "Only VBEE connector is supported." }
        require(!config.apiKey.isNullOrBlank()) { "VBEE API key is required." }
        require(isVietnameseLocale(config.locale)) { "VBEE hien chi duoc cau hinh cho vi-VN." }
        require(isSafeVoiceCode(resolveVoiceCode(config))) { "VBEE voice code khong hop le." }
        require(isSupportedOutputFormat(config.outputFormat)) { "VBEE hien chi ho tro mp3 hoac wav." }
        config.model?.takeIf { it.isNotBlank() }?.let { requestedModel ->
            require(isSafeVoiceCode(requestedModel)) {
                "VBEE model/profile phai la voice code hop le."
            }
        }
    }

    override suspend fun synthesizeInternal(
        config: VoiceProviderConfig,
        text: String
    ): RemoteVoiceAudioPayload {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "VBEE yeu cau noi dung text khong duoc rong."
            )
        }

        val auth = parseAuth(config.apiKey.orEmpty())
        val requestJson = JSONObject()
            .put("text", normalizedText)
            .put("voice_code", resolveVoiceCode(config))
            .put("audio_type", normalizeOutputFormat(config.outputFormat))
            .put("speed_rate", normalizeSpeechRate(config.speechRate))
            .apply {
                auth.appId?.let { put("app_id", it) }
            }

        val submitResponse = executeWithRetry(
            RemoteVoiceTransportRequest(
                url = API_URL,
                method = "POST",
                body = requestJson.toString().toByteArray(StandardCharsets.UTF_8),
                contentType = "application/json; charset=utf-8",
                accept = "application/json",
                bearerToken = auth.token,
                additionalHeaders = buildHeaders(auth.token)
            )
        )

        val submitJson = parseJson(submitResponse.body)
        extractInlineAudio(submitJson)?.let { return it }

        val audioUrl = extractAudioUrl(submitJson)
        if (audioUrl.isNullOrBlank()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "VBEE khong tra ve audio URL hoac audio base64 hop le."
            )
        }
        requireHttpsUrl(audioUrl)
        return downloadAudioPayload(
            url = audioUrl,
            bearerToken = auth.token,
            additionalHeaders = buildHeaders(auth.token)
        )
    }

    override fun mapHttpError(statusCode: Int, body: String): VoiceProviderException {
        val normalizedBody = body.lowercase()
        return when {
            statusCode == 400 && normalizedBody.contains("voice") -> VoiceProviderException(
                VoiceProviderErrorCode.MODEL_NOT_AVAILABLE,
                "VBEE tu choi voice code hien tai."
            )

            statusCode == 400 && (normalizedBody.contains("language") || normalizedBody.contains("locale")) -> VoiceProviderException(
                VoiceProviderErrorCode.LANGUAGE_NOT_SUPPORTED,
                "VBEE hien chua chap nhan locale dang chon."
            )

            statusCode == 401 || statusCode == 403 -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_API_KEY,
                "VBEE tu choi API key hien tai."
            )

            statusCode == 404 -> VoiceProviderException(
                VoiceProviderErrorCode.MODEL_NOT_AVAILABLE,
                "VBEE khong tim thay voice code hoac endpoint duoc yeu cau."
            )

            statusCode == 429 && (normalizedBody.contains("quota") || normalizedBody.contains("credit") || normalizedBody.contains("billing")) -> VoiceProviderException(
                VoiceProviderErrorCode.QUOTA_OR_BILLING_REQUIRED,
                "VBEE can han muc hop le truoc khi tao giong doc."
            )

            statusCode == 429 -> VoiceProviderException(
                VoiceProviderErrorCode.RATE_LIMITED,
                "VBEE dang gioi han toc do yeu cau."
            )

            statusCode in 500..599 -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "VBEE tam thoi khong san sang."
            )

            else -> VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "VBEE tra ve phan hoi khong hop le."
            )
        }
    }

    private fun parseAuth(rawApiKey: String): VbeeAuth {
        val normalized = rawApiKey.trim()
        val parts = normalized.split(':', limit = 2)
        return if (parts.size == 2 &&
            SAFE_APP_ID.matches(parts[0].trim()) &&
            parts[1].trim().isNotEmpty()
        ) {
            VbeeAuth(
                token = parts[1].trim(),
                appId = parts[0].trim()
            )
        } else {
            VbeeAuth(token = normalized, appId = null)
        }
    }

    private fun buildHeaders(token: String): Map<String, String> {
        return mapOf(
            "api-key" to token,
            "x-api-key" to token
        )
    }

    private fun resolveVoiceCode(config: VoiceProviderConfig): String {
        return config.model?.trim()?.ifBlank { null }
            ?: config.voiceId?.trim()?.ifBlank { null }
            ?: DEFAULT_VOICE_CODE
    }

    private fun normalizeOutputFormat(format: String): String {
        return format.trim().lowercase().ifBlank { DEFAULT_OUTPUT_FORMAT }
    }

    private fun normalizeSpeechRate(rate: Float): Double {
        return rate.coerceIn(0.5f, 2.0f).toDouble()
    }

    private fun parseJson(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (error: Throwable) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "VBEE tra ve JSON khong hop le.",
                error
            )
        }
    }

    private fun extractAudioUrl(json: JSONObject): String? {
        val candidates = listOf(
            json.optString("audio_url"),
            json.optString("audioUrl"),
            json.optString("url"),
            json.optString("link"),
            json.optString("async"),
            json.optJSONObject("result")?.optString("audio_url"),
            json.optJSONObject("result")?.optString("audioUrl"),
            json.optJSONObject("result")?.optString("url"),
            json.optJSONObject("data")?.optString("audio_url"),
            json.optJSONObject("data")?.optString("audioUrl"),
            json.optJSONObject("data")?.optString("url")
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun extractInlineAudio(json: JSONObject): RemoteVoiceAudioPayload? {
        val base64Audio = listOf(
            json.optString("audio_base64"),
            json.optString("audioBase64"),
            json.optString("audio"),
            json.optJSONObject("result")?.optString("audio_base64"),
            json.optJSONObject("result")?.optString("audioBase64"),
            json.optJSONObject("data")?.optString("audio_base64"),
            json.optJSONObject("data")?.optString("audioBase64")
        ).firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null

        val bytes = try {
            Base64.getDecoder().decode(base64Audio)
        } catch (error: IllegalArgumentException) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "VBEE tra ve audio base64 khong hop le.",
                error
            )
        }
        val mimeType = RemoteVoiceAudioValidator.detectMimeType(null, bytes)
        RemoteVoiceAudioValidator.validate(bytes, mimeType)
        return RemoteVoiceAudioPayload(bytes = bytes, mimeType = mimeType)
    }

    private fun requireHttpsUrl(url: String) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_RESPONSE,
                "VBEE tra ve audio URL khong an toan."
            )
        }
    }

    private fun voice(
        voiceId: String,
        displayName: String,
        isDefault: Boolean,
        genderHint: String
    ): VoiceDefinition {
        return VoiceDefinition(
            voiceId = voiceId,
            displayName = displayName,
            locale = "vi-VN",
            engineName = displayName(),
            networkRequired = true,
            installed = true,
            isDefault = isDefault,
            genderHint = genderHint
        )
    }

    private fun isVietnameseLocale(locale: String): Boolean {
        val normalized = locale.trim().lowercase()
        return normalized == "vi-vn" || normalized == "vi"
    }

    private fun isSafeVoiceCode(value: String): Boolean {
        return SAFE_VOICE_CODE.matches(value.trim())
    }

    private fun isSupportedOutputFormat(format: String): Boolean {
        return format.trim().lowercase() in SUPPORTED_OUTPUT_FORMATS
    }

    private data class VbeeAuth(
        val token: String,
        val appId: String?
    )

    companion object {
        const val API_URL: String = "https://api.vbee.vn/api/v1/tts"
        const val DEFAULT_VOICE_CODE: String = "hn_female_minhquy_vdts_48k-hsmm"
        const val DEFAULT_OUTPUT_FORMAT: String = "mp3"

        private val SAFE_APP_ID = Regex("^[A-Za-z0-9._-]{2,64}$")
        private val SAFE_VOICE_CODE = Regex("^[A-Za-z0-9._-]{2,128}$")
        private val SUPPORTED_OUTPUT_FORMATS = setOf("mp3", "wav")
    }
}
