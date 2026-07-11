package com.lqlq.browser.automation.connector.image

data class ImageProviderConfig(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val accountId: String? = null
)

data class ImageGenerationRequest(
    val jobId: String,
    val sceneId: String,
    val ordinal: Int,
    val prompt: String,
    val aspectRatio: String,
    val negativePrompt: String? = null
)

data class ImageGenerationResult(
    val sceneId: String,
    val ordinal: Int,
    val providerId: String,
    val model: String,
    val providerRequestId: String?,
    val mimeType: String,
    val bytes: ByteArray
)

data class ImageProviderConnectionResult(
    val providerId: String,
    val model: String,
    val providerRequestId: String?
)

enum class ImageProviderErrorCode {
    NOT_CONFIGURED,
    INVALID_API_KEY,
    INVALID_API_TOKEN_OR_ACCOUNT_ACCESS,
    BILLING_REQUIRED,
    CREDIT_EXHAUSTED,
    FREE_ALLOCATION_EXHAUSTED,
    MODEL_ACCESS_REQUIRED,
    MODEL_NOT_AVAILABLE,
    ACCOUNT_VERIFICATION_REQUIRED,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    COMMUNITY_QUEUE_DELAY,
    TIMEOUT,
    USER_ACTION_REQUIRED,
    INVALID_RESPONSE,
    INVALID_IMAGE,
    CANCELLED
}

class ImageProviderException(
    val code: ImageProviderErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

interface ImageGenerationConnector {
    suspend fun testConnection(
        config: ImageProviderConfig
    ): ImageProviderConnectionResult

    suspend fun generateImage(
        config: ImageProviderConfig,
        request: ImageGenerationRequest
    ): ImageGenerationResult
}
