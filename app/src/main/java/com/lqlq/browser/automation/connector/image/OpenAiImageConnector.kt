package com.lqlq.browser.automation.connector.image

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

class OpenAiImageConnector(
    private val transport: OpenAiImageTransport = HttpUrlConnectionOpenAiImageTransport()
) : ImageGenerationConnector {

    override suspend fun testConnection(
        config: ImageProviderConfig
    ): ImageProviderConnectionResult {
        validateConfig(config)

        val probeRequest = ImageGenerationRequest(
            jobId = "connection-test",
            sceneId = "scene-connection-test",
            ordinal = 1,
            prompt = CONNECTION_TEST_PROMPT,
            aspectRatio = "1:1"
        )

        val result = generateImage(config, probeRequest)
        return ImageProviderConnectionResult(
            providerId = result.providerId,
            model = result.model,
            providerRequestId = result.providerRequestId
        )
    }

    override suspend fun generateImage(
        config: ImageProviderConfig,
        request: ImageGenerationRequest
    ): ImageGenerationResult {
        validateConfig(config)
        validateRequest(request)

        try {
            val response = executeWithRetry(config, request)

            val base64Payload = extractBase64Payload(response.body)
            val bytes = decodeBase64(base64Payload)
            validateImage(bytes)

            return ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = config.providerId,
                model = config.model,
                providerRequestId = response.requestId,
                mimeType = detectMimeType(bytes),
                bytes = bytes
            )
        } catch (error: CancellationException) {
            throw ImageProviderException(
                ImageProviderErrorCode.CANCELLED,
                "Da huy yeu cau tao anh."
            )
        } catch (error: ImageProviderException) {
            throw error
        } catch (error: Throwable) {
            throw mapThrowable(error)
        }
    }

    private suspend fun executeWithRetry(
        config: ImageProviderConfig,
        request: ImageGenerationRequest
    ): OpenAiImageTransportResponse {
        var attempt = 0
        var lastError: ImageProviderException? = null

        while (attempt <= MAX_TRANSIENT_RETRIES) {
            currentCoroutineContext().ensureActive()
            try {
                val response = transport.execute(
                    OpenAiImageTransportRequest(
                        url = API_URL,
                        method = "POST",
                        body = buildRequestBody(config, request),
                        connectTimeoutMs = CONNECT_TIMEOUT_MS,
                        readTimeoutMs = READ_TIMEOUT_MS,
                        maxResponseBytes = MAX_RESPONSE_BYTES,
                        bearerToken = config.apiKey.trim(),
                        followRedirects = false
                    )
                )
                if (response.statusCode !in 200..299) {
                    throw mapOpenAiError(response.statusCode, response.body)
                }
                return response
            } catch (error: CancellationException) {
                throw error
            } catch (error: ImageProviderException) {
                lastError = error
                if (!shouldRetry(error) || attempt >= MAX_TRANSIENT_RETRIES) {
                    throw error
                }
            } catch (error: Throwable) {
                val mapped = mapThrowable(error)
                lastError = mapped
                if (!shouldRetry(mapped) || attempt >= MAX_TRANSIENT_RETRIES) {
                    throw mapped
                }
            }

            delay(RETRY_BACKOFF_MS[attempt])
            attempt += 1
        }

        throw lastError ?: ImageProviderException(
            ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
            "Khong the tao anh that luc nay."
        )
    }

    private fun buildRequestBody(
        config: ImageProviderConfig,
        request: ImageGenerationRequest
    ): String {
        val prompt = buildPrompt(request)
        val escapedPrompt = escapeJson(prompt)
        val escapedModel = escapeJson(config.model.trim())
        val escapedSize = escapeJson(mapAspectRatioToSize(request.aspectRatio))
        return """
            {
              "model": "$escapedModel",
              "prompt": "$escapedPrompt",
              "size": "$escapedSize",
              "quality": "$DEFAULT_QUALITY",
              "output_format": "$DEFAULT_OUTPUT_FORMAT",
              "n": 1
            }
        """.trimIndent()
    }

    private fun buildPrompt(request: ImageGenerationRequest): String {
        return buildString {
            append(request.prompt.trim())
            request.negativePrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { negativePrompt ->
                    append("\n\nAvoid: ")
                    append(negativePrompt)
                }
        }
    }

    private fun mapAspectRatioToSize(aspectRatio: String): String {
        return when (aspectRatio.trim()) {
            "9:16" -> DEFAULT_SIZE_PORTRAIT
            "16:9" -> "1536x1024"
            else -> "1024x1024"
        }
    }

    private fun extractBase64Payload(body: String): String {
        val root = JSONObject(body)
        val dataArray = root.optJSONArray("data")
            ?: throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Provider anh khong tra ve mang data hop le."
            )
        val item = dataArray.optJSONObject(0)
            ?: throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Provider anh khong tra ve item anh dau tien."
            )
        val encoded = item.optString("b64_json").trim()
        if (encoded.isEmpty()) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Provider anh khong tra ve du lieu anh base64."
            )
        }
        return encoded
    }

    private fun decodeBase64(payload: String): ByteArray {
        return try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (error: IllegalArgumentException) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Provider anh tra ve base64 khong hop le.",
                error
            )
        }
    }

    private fun validateImage(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_IMAGE,
                "Provider anh tra ve file rong."
            )
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_IMAGE,
                "Artifact anh vuot qua gioi han an toan."
            )
        }
        val mimeType = detectMimeType(bytes)
        if (mimeType !in ALLOWED_MIME_TYPES) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_IMAGE,
                "Provider anh tra ve dinh dang khong duoc ho tro."
            )
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_IMAGE,
                "Provider anh tra ve bytes khong decode duoc."
            )
        }
    }

    private fun detectMimeType(bytes: ByteArray): String {
        return when {
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "image/png"

            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "image/jpeg"

            bytes.size >= 12 &&
                String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
                String(bytes.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP" -> "image/webp"

            else -> "application/octet-stream"
        }
    }

    private fun validateConfig(config: ImageProviderConfig) {
        require(config.providerId == PROVIDER_ID) { "Only OpenAI image generation is supported." }
        require(config.apiKey.trim().isNotEmpty()) { "OpenAI image API key is required." }
        require(isSafeModelName(config.model.trim())) { "OpenAI image model is invalid." }
    }

    private fun validateRequest(request: ImageGenerationRequest) {
        require(request.jobId.trim().isNotEmpty()) { "Job ID is required." }
        require(request.sceneId.trim().isNotEmpty()) { "Scene ID is required." }
        require(request.ordinal > 0) { "Scene ordinal must be positive." }
        require(request.prompt.trim().isNotEmpty()) { "Image prompt is required." }
    }

    private fun mapOpenAiError(
        statusCode: Int,
        body: String
    ): ImageProviderException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val code = error?.optString("code").orEmpty().lowercase()
        val type = error?.optString("type").orEmpty().lowercase()
        val message = error?.optString("message").orEmpty().lowercase()

        return when {
            statusCode == 401 ->
                ImageProviderException(
                    ImageProviderErrorCode.INVALID_API_KEY,
                    "OpenAI Images tu choi API key hien tai."
                )

            statusCode == 403 && (message.contains("verify") || message.contains("organization")) ->
                ImageProviderException(
                    ImageProviderErrorCode.ACCOUNT_VERIFICATION_REQUIRED,
                    "Tai khoan OpenAI Images can xac minh truoc khi tao anh."
                )

            statusCode == 403 ->
                ImageProviderException(
                    ImageProviderErrorCode.MODEL_ACCESS_REQUIRED,
                    "Tai khoan hien tai chua duoc cap quyen dung model anh nay."
                )

            statusCode == 429 && isRateLimitError(code, type, message) ->
                ImageProviderException(
                    ImageProviderErrorCode.RATE_LIMITED,
                    "OpenAI Images dang gioi han toc do yeu cau."
                )

            statusCode == 429 ->
                ImageProviderException(
                    if (message.contains("credit")) {
                        ImageProviderErrorCode.CREDIT_EXHAUSTED
                    } else {
                        ImageProviderErrorCode.BILLING_REQUIRED
                    },
                    "OpenAI Images can han muc thanh toan hoac billing hop le."
                )

            statusCode == 400 && isPromptBlocked(code, type, message) ->
                ImageProviderException(
                    ImageProviderErrorCode.USER_ACTION_REQUIRED,
                    "Prompt bi OpenAI Images chan boi chinh sach an toan."
                )

            statusCode == 400 && isPromptRevisionRequired(code, type, message) ->
                ImageProviderException(
                    ImageProviderErrorCode.USER_ACTION_REQUIRED,
                    "Prompt can duoc dieu chinh truoc khi OpenAI Images co the tao anh."
                )

            statusCode in 500..599 ->
                ImageProviderException(
                    ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenAI Images tam thoi khong san sang."
                )

            type.contains("invalid_request") || code.contains("invalid") ->
                ImageProviderException(
                    ImageProviderErrorCode.INVALID_RESPONSE,
                    "OpenAI Images tu choi request do du lieu khong hop le."
                )

            else ->
                ImageProviderException(
                    ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                    "OpenAI Images tra ve loi khong the xu ly luc nay."
                )
        }
    }

    private fun mapThrowable(error: Throwable): ImageProviderException {
        return when (error) {
            is SocketTimeoutException -> ImageProviderException(
                ImageProviderErrorCode.TIMEOUT,
                "Yeu cau OpenAI Images bi het thoi gian cho."
            )
            is SSLException,
            is ConnectException,
            is IOException -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the ket noi toi OpenAI Images luc nay."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the tao anh that luc nay."
            )
        }
    }

    private fun shouldRetry(error: ImageProviderException): Boolean {
        return when (error.code) {
            ImageProviderErrorCode.RATE_LIMITED,
            ImageProviderErrorCode.TIMEOUT,
            ImageProviderErrorCode.PROVIDER_UNAVAILABLE -> true
            else -> false
        }
    }

    private fun isRateLimitError(code: String, type: String, message: String): Boolean {
        return code.contains("rate") ||
            type.contains("rate") ||
            message.contains("rate limit") ||
            message.contains("too many requests")
    }

    private fun isPromptBlocked(code: String, type: String, message: String): Boolean {
        return code.contains("moderation_blocked") ||
            type.contains("moderation") ||
            message.contains("moderation") ||
            message.contains("safety system")
    }

    private fun isPromptRevisionRequired(code: String, type: String, message: String): Boolean {
        return code.contains("image_generation_user_error") ||
            type.contains("image_generation_user_error") ||
            message.contains("revise your prompt")
    }

    private fun escapeJson(value: String): String {
        val builder = StringBuilder(value.length + 16)
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        builder.append("\\u%04x".format(character.code))
                    } else {
                        builder.append(character)
                    }
                }
            }
        }
        return builder.toString()
    }

    companion object {
        const val PROVIDER_ID: String = "openai-images"
        const val DEFAULT_MODEL: String = "gpt-image-2"
        const val MINI_MODEL: String = "gpt-image-1-mini"
        const val LEGACY_MODEL: String = "gpt-image-1"
        const val DEFAULT_SIZE_PORTRAIT: String = "1024x1536"
        const val DEFAULT_QUALITY: String = "low"
        const val DEFAULT_OUTPUT_FORMAT: String = "png"

        private const val API_URL = "https://api.openai.com/v1/images/generations"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_RESPONSE_BYTES = 20 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val CONNECTION_TEST_PROMPT = "A green mountain landscape, vertical digital illustration"
        private val RETRY_BACKOFF_MS = longArrayOf(600L, 1_200L)
        private val SAFE_MODEL_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

        private val ALLOWED_MIME_TYPES = setOf(
            "image/png",
            "image/jpeg",
            "image/webp"
        )

        fun isSafeModelName(model: String): Boolean {
            val normalized = model.trim()
            if (normalized.isEmpty()) {
                return false
            }
            if (normalized.contains("://") ||
                normalized.contains("/") ||
                normalized.contains("\\") ||
                normalized.contains("{") ||
                normalized.contains("}") ||
                normalized.contains("\"") ||
                normalized.any { it.isWhitespace() }
            ) {
                return false
            }
            return SAFE_MODEL_PATTERN.matches(normalized)
        }
    }
}

data class OpenAiImageTransportRequest(
    val url: String,
    val method: String,
    val body: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseBytes: Int,
    val bearerToken: String,
    val followRedirects: Boolean
)

data class OpenAiImageTransportResponse(
    val statusCode: Int,
    val body: String,
    val requestId: String?
)

interface OpenAiImageTransport {
    suspend fun execute(request: OpenAiImageTransportRequest): OpenAiImageTransportResponse
}

class HttpUrlConnectionOpenAiImageTransport : OpenAiImageTransport {
    override suspend fun execute(
        request: OpenAiImageTransportRequest
    ): OpenAiImageTransportResponse = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Image provider requests must use HTTPS."
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            instanceFollowRedirects = request.followRedirects
            doInput = true
            doOutput = true
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
            setRequestProperty("Authorization", "Bearer ${request.bearerToken}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(request.body.toByteArray(StandardCharsets.UTF_8))
            }
            currentCoroutineContext().ensureActive()

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseBytes = responseStream?.use { stream ->
                stream.readBytes()
            } ?: ByteArray(0)
            if (responseBytes.size > request.maxResponseBytes) {
                throw ImageProviderException(
                    ImageProviderErrorCode.INVALID_RESPONSE,
                    "Phan hoi provider anh vuot qua gioi han an toan."
                )
            }

            OpenAiImageTransportResponse(
                statusCode = statusCode,
                body = String(responseBytes, StandardCharsets.UTF_8),
                requestId = connection.getHeaderField("x-request-id")
            )
        } finally {
            connection.disconnect()
        }
    }
}
