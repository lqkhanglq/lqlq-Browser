package com.lqlq.browser.automation.connector.image

import android.graphics.BitmapFactory
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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

/**
 * Tim anh mien phi, KHONG can API key, qua Openverse (gom anh Creative Commons
 * tu Flickr/Wikimedia/... duoc WordPress van hanh). Chi lay anh co license cho
 * phep dung thuong mai + chinh sua (license_type=commercial,modification) de
 * an toan khi dang len YouTube.
 */
class OpenverseImageConnector(
    private val transport: OpenverseImageTransport = HttpUrlConnectionOpenverseImageTransport()
) : ImageGenerationConnector {

    override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult {
        validateConfig(config)
        val result = generateImage(
            config = config,
            request = ImageGenerationRequest(
                jobId = "connection-test",
                sceneId = "scene-openverse-test",
                ordinal = 1,
                prompt = CONNECTION_TEST_QUERY,
                aspectRatio = "9:16"
            )
        )
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

        return try {
            currentCoroutineContext().ensureActive()
            val query = buildQuery(request.prompt)
            val searchResponse = transport.execute(
                OpenverseImageTransportRequest(
                    url = buildSearchUrl(query),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_JSON_RESPONSE_BYTES
                )
            )
            if (searchResponse.statusCode !in 200..299) {
                throw mapOpenverseError(searchResponse.statusCode)
            }

            val selected = selectImage(searchResponse.body, request.jobId, request.ordinal)
            val imageResponse = transport.execute(
                OpenverseImageTransportRequest(
                    url = selected.imageUrl,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_IMAGE_BYTES
                )
            )
            if (imageResponse.statusCode !in 200..299) {
                throw mapOpenverseError(imageResponse.statusCode)
            }
            validateImage(imageResponse.bytes)

            ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = PROVIDER_ID,
                model = config.model,
                providerRequestId = listOf(
                    "selectedImageId=${selected.imageId.orEmpty()}",
                    "license=${selected.license.orEmpty()}",
                    "selectedIndex=${selected.selectedIndex}",
                    "resultCount=${selected.resultCount}"
                ).joinToString(";"),
                mimeType = detectMimeType(imageResponse.contentType, imageResponse.bytes),
                bytes = imageResponse.bytes
            )
        } catch (error: CancellationException) {
            throw ImageProviderException(ImageProviderErrorCode.CANCELLED, "Da huy yeu cau tim anh Openverse.")
        } catch (error: ImageProviderException) {
            throw error
        } catch (error: Throwable) {
            throw mapThrowable(error)
        }
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // license_type=commercial,modification: chi lay anh CHO PHEP dung
        // thuong mai VA chinh sua (loai bo NC/ND) — an toan de dang YouTube.
        return "https://api.openverse.org/v1/images/?q=$encoded&license_type=commercial,modification&page_size=20&mature=false"
    }

    private fun buildQuery(prompt: String): String {
        val cleaned = prompt
            .replace(Regex("(?i)create scene \\d+ for a short-form video about"), " ")
            .replace(Regex("(?i)narrative beat:"), " ")
            .replace(Regex("(?i)visual style:.*"), " ")
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = cleaned.split(' ')
            .filter { it.length >= 3 }
            .take(MAX_QUERY_WORDS)
            .joinToString(" ")
        return words.ifBlank { CONNECTION_TEST_QUERY }
    }

    private fun selectImage(body: String, jobId: String, ordinal: Int): SelectedOpenverseImage {
        val root = try {
            JSONObject(body)
        } catch (error: Throwable) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Openverse tra ve JSON khong hop le.",
                error
            )
        }
        val results = root.optJSONArray("results")
            ?: throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Openverse khong tra ve danh sach ket qua.")
        if (results.length() == 0) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Openverse khong tim thay anh phu hop.")
        }
        val candidates = buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val imageUrl = item.optString("url").trim()
                if (!imageUrl.startsWith("https://") && !imageUrl.startsWith("http://")) continue
                add(
                    SelectedOpenverseImage(
                        imageId = item.optString("id").trim().ifBlank { null },
                        imageUrl = imageUrl,
                        license = item.optString("license").trim().ifBlank { null },
                        selectedIndex = index,
                        resultCount = results.length()
                    )
                )
            }
        }
        if (candidates.isEmpty()) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Openverse tra ve ket qua thieu URL anh hop le.")
        }
        val usedImageIds = usedImageIdsByJob.getOrPut(jobId) { linkedSetOf() }
        val preferredIndex = (ordinal - 1).mod(candidates.size)
        for (offset in candidates.indices) {
            val candidate = candidates[(preferredIndex + offset) % candidates.size]
            val imageId = candidate.imageId
            if (imageId.isNullOrBlank() || !usedImageIds.contains(imageId)) {
                imageId?.let(usedImageIds::add)
                return candidate
            }
        }
        return candidates[preferredIndex]
    }

    private fun validateConfig(config: ImageProviderConfig) {
        require(config.providerId == PROVIDER_ID) { "Only Openverse image search is supported." }
    }

    private fun validateRequest(request: ImageGenerationRequest) {
        require(request.jobId.trim().isNotEmpty()) { "Job ID is required." }
        require(request.sceneId.trim().isNotEmpty()) { "Scene ID is required." }
        require(request.ordinal > 0) { "Scene ordinal must be positive." }
        require(request.prompt.trim().isNotEmpty()) { "Search query is required." }
    }

    private fun validateImage(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Openverse tra ve file rong.")
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Anh Openverse vuot qua gioi han an toan.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Openverse tra ve bytes khong decode duoc.")
        }
    }

    private fun detectMimeType(contentType: String?, bytes: ByteArray): String {
        val normalized = contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        if (normalized.startsWith("image/")) return normalized
        return when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            else -> "application/octet-stream"
        }
    }

    private fun mapOpenverseError(statusCode: Int): ImageProviderException {
        return when {
            statusCode == 429 -> ImageProviderException(
                ImageProviderErrorCode.RATE_LIMITED,
                "Openverse dang gioi han toc do yeu cau (khong dung API key nen gioi han thap hon)."
            )
            statusCode in 500..599 -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Openverse tam thoi khong san sang."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Openverse tra ve phan hoi khong hop le (HTTP $statusCode)."
            )
        }
    }

    private fun mapThrowable(error: Throwable): ImageProviderException {
        return when (error) {
            is SocketTimeoutException -> ImageProviderException(
                ImageProviderErrorCode.TIMEOUT,
                "Yeu cau Openverse bi het thoi gian cho."
            )
            is SSLException,
            is ConnectException,
            is IOException -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the ket noi toi Openverse luc nay."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the tim anh Openverse luc nay."
            )
        }
    }

    private data class SelectedOpenverseImage(
        val imageId: String?,
        val imageUrl: String,
        val license: String?,
        val selectedIndex: Int,
        val resultCount: Int
    )

    companion object {
        const val PROVIDER_ID: String = AutomationImageProviders.OPENVERSE
        const val DEFAULT_MODEL: String = "openverse-search-v1"
        private const val CONNECTION_TEST_QUERY = "nature landscape"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_JSON_RESPONSE_BYTES = 1024 * 1024
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MAX_QUERY_WORDS = 8
        private val usedImageIdsByJob = ConcurrentHashMap<String, LinkedHashSet<String>>()
    }
}

data class OpenverseImageTransportRequest(
    val url: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseBytes: Int
)

data class OpenverseImageTransportResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: String,
    val bytes: ByteArray
)

interface OpenverseImageTransport {
    suspend fun execute(request: OpenverseImageTransportRequest): OpenverseImageTransportResponse
}

class HttpUrlConnectionOpenverseImageTransport : OpenverseImageTransport {
    override suspend fun execute(request: OpenverseImageTransportRequest): OpenverseImageTransportResponse {
        return withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = request.connectTimeoutMs
                readTimeout = request.readTimeoutMs
                setRequestProperty("Accept", "application/json,image/*;q=0.9,*/*;q=0.5")
                setRequestProperty("User-Agent", "lqlq-browser-android/1.0 (automation image search)")
            }
            try {
                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > request.maxResponseBytes) {
                            throw IOException("Openverse response is too large.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: ByteArray(0)
                OpenverseImageTransportResponse(
                    statusCode = statusCode,
                    contentType = connection.contentType,
                    body = String(bytes, Charsets.UTF_8),
                    bytes = bytes
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
