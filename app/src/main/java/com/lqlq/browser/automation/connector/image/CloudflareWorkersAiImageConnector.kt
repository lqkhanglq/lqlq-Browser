package com.lqlq.browser.automation.connector.image

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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

class CloudflareWorkersAiImageConnector(
    private val transport: CloudflareWorkersAiTransport = HttpUrlConnectionCloudflareWorkersAiTransport()
) : ImageGenerationConnector {

    override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult {
        validateConfig(config)
        val request = ImageGenerationRequest(
            jobId = "connection-test",
            sceneId = "scene-connection-test",
            ordinal = 1,
            prompt = CONNECTION_TEST_PROMPT,
            aspectRatio = "1:1"
        )
        val result = generateImage(config, request)
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
            currentCoroutineContext().ensureActive()
            val response = transport.execute(
                CloudflareWorkersAiTransportRequest(
                    url = buildEndpointUrl(config),
                    body = buildRequestBody(request),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_RESPONSE_BYTES,
                    bearerToken = config.apiKey.trim()
                )
            )

            if (response.statusCode !in 200..299) {
                throw mapCloudflareError(response.statusCode, response.body)
            }

            val bytes = extractImageBytes(response)
            validateImage(bytes)

            return ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = config.providerId,
                model = config.model,
                providerRequestId = response.requestId,
                mimeType = detectMimeType(response.contentType, bytes),
                bytes = bytes
            )
        } catch (error: CancellationException) {
            throw ImageProviderException(ImageProviderErrorCode.CANCELLED, "Da huy yeu cau tao anh.")
        } catch (error: ImageProviderException) {
            throw error
        } catch (error: Throwable) {
            throw mapThrowable(error)
        }
    }

    private fun buildEndpointUrl(config: ImageProviderConfig): String {
        val accountId = config.accountId?.trim().orEmpty()
        return "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/${config.model.trim()}"
    }

    private fun buildRequestBody(request: ImageGenerationRequest): String {
        return JSONObject()
            .put("prompt", request.prompt.trim())
            .put("steps", DEFAULT_STEPS)
            .toString()
    }

    private fun validateConfig(config: ImageProviderConfig) {
        require(config.providerId == PROVIDER_ID) { "Only Cloudflare Workers AI is supported." }
        require(config.apiKey.trim().isNotEmpty()) { "Cloudflare API token is required." }
        require(config.accountId?.trim().isNullOrEmpty().not()) { "Cloudflare Account ID is required." }
        require(isSafeModelName(config.model.trim())) { "Cloudflare model is invalid." }
    }

    private fun validateRequest(request: ImageGenerationRequest) {
        require(request.jobId.trim().isNotEmpty()) { "Job ID is required." }
        require(request.sceneId.trim().isNotEmpty()) { "Scene ID is required." }
        require(request.ordinal > 0) { "Scene ordinal must be positive." }
        require(request.prompt.trim().isNotEmpty()) { "Image prompt is required." }
    }

    private fun extractImageBytes(response: CloudflareWorkersAiTransportResponse): ByteArray {
        if (looksLikeImageResponse(response.contentType, response.bytes)) {
            return response.bytes
        }

        val root = parseResponseJson(response.body)
        val encoded = extractBase64Payload(root)
        return decodeBase64(encoded)
    }

    private fun looksLikeImageResponse(contentType: String?, bytes: ByteArray): Boolean {
        val normalized = contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        return normalized.startsWith("image/") || isPng(bytes)
    }

    private fun parseResponseJson(body: String): JSONObject {
        return try {
            JSONObject(body)
        } catch (error: Throwable) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Cloudflare Workers AI tra ve JSON khong hop le.",
                error
            )
        }
    }

    private fun extractBase64Payload(root: JSONObject): String {
        if (!root.optBoolean("success", true) && (root.optJSONArray("errors")?.length() ?: 0) > 0) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Cloudflare Workers AI tra ve ket qua that bai."
            )
        }

        val resultValue = root.opt("result")
        val encoded = when (resultValue) {
            is String -> resultValue.trim()
            is JSONObject -> resultValue.optString("image").trim()
            else -> ""
        }

        if (encoded.isEmpty()) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Cloudflare Workers AI khong tra ve anh base64 hop le."
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
                "Cloudflare Workers AI tra ve base64 khong hop le.",
                error
            )
        }
    }

    private fun validateImage(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Cloudflare tra ve file rong.")
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Artifact anh vuot qua gioi han an toan.")
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Cloudflare tra ve bytes khong decode duoc.")
        }
    }

    private fun detectMimeType(contentType: String?, bytes: ByteArray): String {
        val normalized = contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        if (normalized.startsWith("image/")) {
            return normalized
        }
        return if (isPng(bytes)) "image/png" else "application/octet-stream"
    }

    private fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
    }

    private fun mapCloudflareError(statusCode: Int, body: String): ImageProviderException {
        val normalized = body.lowercase()
        return when {
            statusCode == 401 || statusCode == 403 -> ImageProviderException(
                ImageProviderErrorCode.INVALID_API_TOKEN_OR_ACCOUNT_ACCESS,
                "Cloudflare Workers AI tu choi API token hoac Account ID hien tai."
            )
            statusCode == 404 -> ImageProviderException(
                ImageProviderErrorCode.MODEL_NOT_AVAILABLE,
                "Model Cloudflare Workers AI hien tai khong kha dung cho tai khoan nay."
            )
            statusCode == 429 && normalized.contains("\"code\":3036") -> ImageProviderException(
                ImageProviderErrorCode.FREE_ALLOCATION_EXHAUSTED,
                "Cloudflare Workers AI da het free allocation cho tai khoan nay."
            )
            statusCode == 429 -> ImageProviderException(
                ImageProviderErrorCode.RATE_LIMITED,
                "Cloudflare Workers AI dang gioi han toc do yeu cau."
            )
            statusCode in 500..599 -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Cloudflare Workers AI tam thoi khong san sang."
            )
            normalized.contains("queue") -> ImageProviderException(
                ImageProviderErrorCode.COMMUNITY_QUEUE_DELAY,
                "Cloudflare Workers AI dang ban, vui long thu lai sau."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Cloudflare Workers AI tra ve phan hoi khong hop le."
            )
        }
    }

    private fun mapThrowable(error: Throwable): ImageProviderException {
        return when (error) {
            is SocketTimeoutException -> ImageProviderException(
                ImageProviderErrorCode.TIMEOUT,
                "Yeu cau Cloudflare Workers AI bi het thoi gian cho."
            )
            is SSLException,
            is ConnectException,
            is IOException -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the ket noi toi Cloudflare Workers AI luc nay."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the tao anh voi Cloudflare Workers AI luc nay."
            )
        }
    }

    companion object {
        const val PROVIDER_ID: String = AutomationImageProviders.CLOUDFLARE_WORKERS_AI
        const val DEFAULT_MODEL: String = "@cf/black-forest-labs/flux-1-schnell"
        private const val CONNECTION_TEST_PROMPT = "A cinematic mountain sunrise poster"
        private const val DEFAULT_STEPS = 4
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 90_000
        private const val MAX_RESPONSE_BYTES = 20 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 15 * 1024 * 1024
        private val SAFE_MODEL_PATTERN = Regex("^@[A-Za-z0-9._/-]{3,127}$")

        fun isSafeModelName(model: String): Boolean {
            val normalized = model.trim()
            return SAFE_MODEL_PATTERN.matches(normalized) && !normalized.contains("://") && !normalized.contains("\\")
        }
    }
}

data class CloudflareWorkersAiTransportRequest(
    val url: String,
    val body: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseBytes: Int,
    val bearerToken: String
)

data class CloudflareWorkersAiTransportResponse(
    val statusCode: Int,
    val body: String,
    val bytes: ByteArray,
    val contentType: String?,
    val requestId: String?
)

interface CloudflareWorkersAiTransport {
    suspend fun execute(request: CloudflareWorkersAiTransportRequest): CloudflareWorkersAiTransportResponse
}

class HttpUrlConnectionCloudflareWorkersAiTransport : CloudflareWorkersAiTransport {
    override suspend fun execute(
        request: CloudflareWorkersAiTransportRequest
    ): CloudflareWorkersAiTransportResponse = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Image provider requests must use HTTPS."
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
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
            val responseStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val responseBytes = responseStream?.use { it.readBytes() } ?: ByteArray(0)
            if (responseBytes.size > request.maxResponseBytes) {
                throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Phan hoi provider anh vuot qua gioi han an toan.")
            }

            CloudflareWorkersAiTransportResponse(
                statusCode = statusCode,
                body = String(responseBytes, StandardCharsets.UTF_8),
                bytes = responseBytes,
                contentType = connection.contentType,
                requestId = connection.getHeaderField("cf-ray")
            )
        } finally {
            connection.disconnect()
        }
    }
}
