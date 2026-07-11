package com.lqlq.browser.automation.connector.voice

data class VoiceProviderConfig(
    val providerId: String,
    val locale: String,
    val voiceId: String?,
    val model: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val outputFormat: String = "wav",
    val engineName: String? = null,
    val apiKey: String? = null,
    val region: String? = null,
    val credentialJson: String? = null
)

data class VoiceDefinition(
    val voiceId: String,
    val displayName: String,
    val locale: String,
    val engineName: String,
    val networkRequired: Boolean,
    val installed: Boolean,
    val isDefault: Boolean,
    val genderHint: String? = null
)

data class VoiceGenerationRequest(
    val jobId: String,
    val scriptArtifactId: String,
    val text: String,
    val providerId: String,
    val voiceId: String?,
    val locale: String,
    val speechRate: Float,
    val pitch: Float,
    val outputFormat: String,
    val sampleRateHz: Int? = null
)

data class VoiceArtifactMetadata(
    val providerId: String,
    val voiceId: String?,
    val locale: String,
    val chunkCount: Int,
    val durationMs: Long?,
    val checksum: String,
    val engineName: String? = null,
    val sampleRateHz: Int? = null
)

data class VoiceGenerationResult(
    val bytes: ByteArray,
    val mimeType: String,
    val metadata: VoiceArtifactMetadata
)

data class VoiceSampleResult(
    val bytes: ByteArray,
    val mimeType: String,
    val providerId: String,
    val voiceId: String?,
    val locale: String,
    val engineName: String?
)

data class VoiceProviderConnectionResult(
    val providerId: String,
    val model: String?,
    val voiceId: String?,
    val locale: String,
    val engineName: String?,
    val voiceCount: Int
)

enum class VoiceProviderErrorCode {
    NOT_CONFIGURED,
    VOICE_NOT_INSTALLED,
    LANGUAGE_NOT_SUPPORTED,
    INVALID_API_KEY,
    INVALID_ACCOUNT_CONFIGURATION,
    INVALID_VOICE,
    INVALID_SPEED,
    INVALID_FORMAT,
    TEXT_TOO_LONG,
    MODEL_ACCESS_REQUIRED,
    MODEL_NOT_AVAILABLE,
    QUOTA_OR_BILLING_REQUIRED,
    DAILY_REQUEST_LIMIT,
    RATE_LIMITED,
    NETWORK_TRANSIENT,
    PROVIDER_BUSY,
    PROVIDER_UNAVAILABLE,
    TIMEOUT,
    PROVIDER_TIMEOUT,
    AUDIO_URL_INVALID,
    AUDIO_DOWNLOAD_FAILED,
    USER_ACTION_REQUIRED,
    INVALID_RESPONSE,
    INVALID_AUDIO,
    CANCELLED
}

class VoiceProviderException(
    val code: VoiceProviderErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

interface VoiceGenerationConnector {
    fun listVoices(): List<VoiceDefinition>

    suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult

    suspend fun synthesizeSample(config: VoiceProviderConfig, text: String): VoiceSampleResult

    suspend fun generateVoice(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult

    fun openProviderSettings()
}
