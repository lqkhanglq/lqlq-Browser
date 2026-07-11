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

class PexelsStockImageConnector(
    private val transport: PexelsStockImageTransport = HttpUrlConnectionPexelsStockImageTransport()
) : ImageGenerationConnector {

    override suspend fun testConnection(config: ImageProviderConfig): ImageProviderConnectionResult {
        validateConfig(config)
        val result = generateImage(
            config = config,
            request = ImageGenerationRequest(
                jobId = "connection-test",
                sceneId = "scene-pexels-test",
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
                PexelsStockImageTransportRequest(
                    url = buildSearchUrl(query, request.aspectRatio),
                    apiKey = config.apiKey.trim(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_JSON_RESPONSE_BYTES
                )
            )
            if (searchResponse.statusCode !in 200..299) {
                throw mapPexelsError(searchResponse.statusCode, searchResponse.body)
            }

            val selected = selectPhoto(searchResponse.body, request.jobId, request.ordinal)
            val imageResponse = transport.execute(
                PexelsStockImageTransportRequest(
                    url = selected.downloadUrl,
                    apiKey = config.apiKey.trim(),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_IMAGE_BYTES
                )
            )
            if (imageResponse.statusCode !in 200..299) {
                throw mapPexelsError(imageResponse.statusCode, imageResponse.body)
            }
            validateImage(imageResponse.bytes)

            ImageGenerationResult(
                sceneId = request.sceneId,
                ordinal = request.ordinal,
                providerId = PROVIDER_ID,
                model = config.model,
                providerRequestId = listOf(
                    "selectedPhotoId=${selected.photoId.orEmpty()}",
                    "selectedIndex=${selected.selectedIndex}",
                    "resultCount=${selected.resultCount}"
                ).joinToString(";"),
                mimeType = detectMimeType(imageResponse.contentType, imageResponse.bytes),
                bytes = imageResponse.bytes
            )
        } catch (error: CancellationException) {
            throw ImageProviderException(ImageProviderErrorCode.CANCELLED, "Da huy yeu cau lay stock image Pexels.")
        } catch (error: ImageProviderException) {
            throw error
        } catch (error: Throwable) {
            throw mapThrowable(error)
        }
    }

    private fun buildSearchUrl(query: String, aspectRatio: String): String {
        val orientation = when (aspectRatio.trim()) {
            "16:9" -> "landscape"
            "1:1" -> "square"
            else -> "portrait"
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://api.pexels.com/v1/search?query=$encoded&per_page=30&orientation=$orientation"
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

    private fun selectPhoto(body: String, jobId: String, ordinal: Int): SelectedPexelsPhoto {
        val root = try {
            JSONObject(body)
        } catch (error: Throwable) {
            throw ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Pexels tra ve JSON khong hop le.",
                error
            )
        }
        val photos = root.optJSONArray("photos")
            ?: throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Pexels khong tra ve danh sach photo.")
        if (photos.length() == 0) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Pexels khong tim thay stock photo phu hop.")
        }
        val candidates = buildList {
            for (index in 0 until photos.length()) {
                val photo = photos.optJSONObject(index) ?: continue
                val src = photo.optJSONObject("src") ?: continue
                val downloadUrl = listOf("large2x", "portrait", "large", "original")
                    .asSequence()
                    .map { src.optString(it).trim() }
                    .firstOrNull { it.startsWith("https://") }
                    ?: continue
                add(
                    SelectedPexelsPhoto(
                        photoId = photo.optLong("id", 0L).takeIf { it > 0L }?.toString(),
                        downloadUrl = downloadUrl,
                        selectedIndex = index,
                        resultCount = photos.length()
                    )
                )
            }
        }
        if (candidates.isEmpty()) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_RESPONSE, "Pexels photo thieu URL tai anh hop le.")
        }
        val usedPhotoIds = usedPhotoIdsByJob.getOrPut(jobId) { linkedSetOf() }
        val preferredIndex = (ordinal - 1).mod(candidates.size)
        for (offset in candidates.indices) {
            val candidate = candidates[(preferredIndex + offset) % candidates.size]
            val photoId = candidate.photoId
            if (photoId.isNullOrBlank() || !usedPhotoIds.contains(photoId)) {
                photoId?.let(usedPhotoIds::add)
                return candidate
            }
        }
        return candidates[preferredIndex]
    }

    private fun validateConfig(config: ImageProviderConfig) {
        require(config.providerId == PROVIDER_ID) { "Only Pexels stock image search is supported." }
        require(config.apiKey.trim().isNotEmpty()) { "Pexels API key is required." }
        require(config.model.trim() == DEFAULT_MODEL) { "Pexels model must be $DEFAULT_MODEL." }
    }

    private fun validateRequest(request: ImageGenerationRequest) {
        require(request.jobId.trim().isNotEmpty()) { "Job ID is required." }
        require(request.sceneId.trim().isNotEmpty()) { "Scene ID is required." }
        require(request.ordinal > 0) { "Scene ordinal must be positive." }
        require(request.prompt.trim().isNotEmpty()) { "Stock media query is required." }
    }

    private fun validateImage(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Pexels tra ve file rong.")
        }
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Stock image vuot qua gioi han an toan.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageProviderException(ImageProviderErrorCode.INVALID_IMAGE, "Pexels tra ve bytes khong decode duoc.")
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

    private fun mapPexelsError(statusCode: Int, body: String): ImageProviderException {
        val normalized = body.lowercase()
        return when {
            statusCode == 401 || statusCode == 403 -> ImageProviderException(
                ImageProviderErrorCode.INVALID_API_KEY,
                "Pexels tu choi API key hien tai."
            )
            statusCode == 429 -> ImageProviderException(
                ImageProviderErrorCode.RATE_LIMITED,
                "Pexels dang gioi han toc do yeu cau."
            )
            statusCode in 500..599 -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Pexels tam thoi khong san sang."
            )
            normalized.contains("not found") -> ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Pexels khong tim thay stock image phu hop."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.INVALID_RESPONSE,
                "Pexels tra ve phan hoi khong hop le."
            )
        }
    }

    private fun mapThrowable(error: Throwable): ImageProviderException {
        return when (error) {
            is SocketTimeoutException -> ImageProviderException(
                ImageProviderErrorCode.TIMEOUT,
                "Yeu cau Pexels bi het thoi gian cho."
            )
            is SSLException,
            is ConnectException,
            is IOException -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the ket noi toi Pexels luc nay."
            )
            else -> ImageProviderException(
                ImageProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the lay stock image Pexels luc nay."
            )
        }
    }

    private data class SelectedPexelsPhoto(
        val photoId: String?,
        val downloadUrl: String,
        val selectedIndex: Int,
        val resultCount: Int
    )

    companion object {
        const val PROVIDER_ID: String = AutomationImageProviders.PEXELS
        const val DEFAULT_MODEL: String = "stock-photo-search-v1"
        private const val CONNECTION_TEST_QUERY = "communication confidence people conversation office"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_JSON_RESPONSE_BYTES = 512 * 1024
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MAX_QUERY_WORDS = 12
        private val usedPhotoIdsByJob = ConcurrentHashMap<String, LinkedHashSet<String>>()
    }
}

data class PexelsStockImageTransportRequest(
    val url: String,
    val apiKey: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseBytes: Int
)

data class PexelsStockImageTransportResponse(
    val statusCode: Int,
    val contentType: String?,
    val body: String,
    val bytes: ByteArray
)

interface PexelsStockImageTransport {
    suspend fun execute(request: PexelsStockImageTransportRequest): PexelsStockImageTransportResponse
}

class HttpUrlConnectionPexelsStockImageTransport : PexelsStockImageTransport {
    override suspend fun execute(request: PexelsStockImageTransportRequest): PexelsStockImageTransportResponse {
        return withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = request.connectTimeoutMs
                readTimeout = request.readTimeoutMs
                setRequestProperty("Authorization", request.apiKey)
                setRequestProperty("Accept", "application/json,image/*;q=0.9,*/*;q=0.5")
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
                            throw IOException("Pexels response is too large.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: ByteArray(0)
                PexelsStockImageTransportResponse(
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
