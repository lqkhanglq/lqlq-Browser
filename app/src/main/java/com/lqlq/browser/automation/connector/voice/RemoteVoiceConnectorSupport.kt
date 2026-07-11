package com.lqlq.browser.automation.connector.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

data class RemoteVoiceTransportRequest(
    val url: String,
    val method: String = "POST",
    val body: ByteArray,
    val contentType: String,
    val accept: String = "*/*",
    val bearerToken: String? = null,
    val additionalHeaders: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 90_000,
    val maxResponseBytes: Int = 32 * 1024 * 1024,
    val followRedirects: Boolean = false
)

data class RemoteVoiceTransportResponse(
    val statusCode: Int,
    val bytes: ByteArray,
    val body: String,
    val contentType: String?,
    val requestId: String?
)

interface RemoteVoiceTransport {
    suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse
}

class HttpUrlConnectionRemoteVoiceTransport : RemoteVoiceTransport {
    override suspend fun execute(
        request: RemoteVoiceTransportRequest
    ): RemoteVoiceTransportResponse = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Voice provider requests must use HTTPS."
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            instanceFollowRedirects = request.followRedirects
            doInput = true
            doOutput = request.body.isNotEmpty() &&
                !request.method.equals("GET", ignoreCase = true) &&
                !request.method.equals("HEAD", ignoreCase = true)
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
            setRequestProperty("Content-Type", request.contentType)
            setRequestProperty("Accept", request.accept)
            request.bearerToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            request.additionalHeaders.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }

        try {
            if (connection.doOutput) {
                connection.outputStream.use { stream ->
                    stream.write(request.body)
                }
            }
            currentCoroutineContext().ensureActive()

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseBytes = responseStream?.use { it.readBytes() } ?: ByteArray(0)
            if (responseBytes.size > request.maxResponseBytes) {
                throw VoiceProviderException(
                    VoiceProviderErrorCode.INVALID_RESPONSE,
                    "Phan hoi voice provider vuot qua gioi han an toan."
                )
            }

            RemoteVoiceTransportResponse(
                statusCode = statusCode,
                bytes = responseBytes,
                body = String(responseBytes, StandardCharsets.UTF_8),
                contentType = connection.contentType,
                requestId = connection.getHeaderField("x-request-id")
                    ?: connection.getHeaderField("request-id")
                    ?: connection.getHeaderField("x-correlation-id")
            )
        } finally {
            connection.disconnect()
        }
    }
}

data class RemoteVoiceAudioPayload(
    val bytes: ByteArray,
    val mimeType: String
)

object RemoteVoiceAudioValidator {
    private val allowedMimeTypes = setOf(
        "audio/wav",
        "audio/x-wav",
        "audio/mpeg",
        "audio/mp3",
        "audio/ogg",
        "audio/aac",
        "audio/mp4"
    )

    fun detectMimeType(contentType: String?, bytes: ByteArray): String {
        val normalized = contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
        if (normalized in allowedMimeTypes) {
            return normalized
        }
        return when {
            bytes.size >= 12 &&
                String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
                String(bytes.copyOfRange(8, 12), Charsets.US_ASCII) == "WAVE" -> "audio/wav"

            bytes.size >= 3 &&
                bytes[0] == 0x49.toByte() &&
                bytes[1] == 0x44.toByte() &&
                bytes[2] == 0x33.toByte() -> "audio/mpeg"

            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                (bytes[1].toInt() and 0xE0) == 0xE0 -> "audio/mpeg"

            bytes.size >= 4 &&
                String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "OggS" -> "audio/ogg"

            bytes.size >= 8 &&
                String(bytes.copyOfRange(4, 8), Charsets.US_ASCII) == "ftyp" -> "audio/mp4"

            else -> "application/octet-stream"
        }
    }

    fun validate(bytes: ByteArray, mimeType: String) {
        if (bytes.isEmpty()) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "Voice provider tra ve file audio rong."
            )
        }
        if (bytes.size > 32 * 1024 * 1024) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "Artifact voice vuot qua gioi han an toan."
            )
        }
        if (mimeType !in allowedMimeTypes) {
            throw VoiceProviderException(
                VoiceProviderErrorCode.INVALID_AUDIO,
                "Voice provider tra ve dinh dang audio khong duoc ho tro."
            )
        }
    }
}

abstract class BaseRemoteVoiceConnector(
    private val transport: RemoteVoiceTransport = HttpUrlConnectionRemoteVoiceTransport()
) : VoiceGenerationConnector {

    final override suspend fun testConnection(config: VoiceProviderConfig): VoiceProviderConnectionResult {
        validateConfig(config)
        val sample = synthesizeInternal(config, connectionProbeText())
        return VoiceProviderConnectionResult(
            providerId = providerId(),
            model = config.model,
            voiceId = config.voiceId,
            locale = config.locale,
            engineName = displayName(),
            voiceCount = listVoices().size
        ).also {
            RemoteVoiceAudioValidator.validate(sample.bytes, sample.mimeType)
        }
    }

    final override suspend fun synthesizeSample(
        config: VoiceProviderConfig,
        text: String
    ): VoiceSampleResult {
        validateConfig(config)
        val payload = synthesizeInternal(config, text)
        return VoiceSampleResult(
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            providerId = providerId(),
            voiceId = config.voiceId,
            locale = config.locale,
            engineName = displayName()
        )
    }

    final override suspend fun generateVoice(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult {
        validateConfig(config)
        return buildGenerationResult(config, request)
    }

    final override fun openProviderSettings() = Unit

    protected suspend fun executeWithRetry(
        request: RemoteVoiceTransportRequest
    ): RemoteVoiceTransportResponse {
        var attempt = 0
        var lastError: VoiceProviderException? = null
        while (attempt <= maxTransientRetries()) {
            currentCoroutineContext().ensureActive()
            try {
                val response = transport.execute(request)
                if (response.statusCode !in 200..299) {
                    throw mapHttpError(response.statusCode, response.body)
                }
                return response
            } catch (error: CancellationException) {
                throw error
            } catch (error: VoiceProviderException) {
                lastError = error
                if (!shouldRetry(error.code) || attempt >= maxTransientRetries()) {
                    throw error
                }
            } catch (error: Throwable) {
                val mapped = mapThrowable(error)
                lastError = mapped
                if (!shouldRetry(mapped.code) || attempt >= maxTransientRetries()) {
                    throw mapped
                }
            }
            delay(retryBackoffMs()[attempt])
            attempt += 1
        }
        throw lastError ?: VoiceProviderException(
            VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
            "Khong the ket noi voice provider luc nay."
        )
    }

    protected open fun shouldRetry(code: VoiceProviderErrorCode): Boolean {
        return code == VoiceProviderErrorCode.RATE_LIMITED ||
            code == VoiceProviderErrorCode.TIMEOUT ||
            code == VoiceProviderErrorCode.PROVIDER_UNAVAILABLE
    }

    protected open fun maxTransientRetries(): Int = 2

    protected open fun retryBackoffMs(): LongArray = longArrayOf(600L, 1_200L)

    protected open fun connectionProbeText(): String = "Xin chao, day la ket noi thu cua voice provider."

    protected open suspend fun buildGenerationResult(
        config: VoiceProviderConfig,
        request: VoiceGenerationRequest
    ): VoiceGenerationResult {
        val payload = synthesizeInternal(config, request.text)
        return VoiceGenerationResult(
            bytes = payload.bytes,
            mimeType = payload.mimeType,
            metadata = VoiceArtifactMetadata(
                providerId = providerId(),
                voiceId = config.voiceId,
                locale = config.locale,
                chunkCount = 1,
                durationMs = null,
                checksum = checksum(payload.bytes),
                engineName = displayName(),
                sampleRateHz = request.sampleRateHz
            )
        )
    }

    protected suspend fun downloadAudioPayload(
        url: String,
        bearerToken: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 90_000
    ): RemoteVoiceAudioPayload {
        val response = executeWithRetry(
            RemoteVoiceTransportRequest(
                url = url,
                method = "GET",
                body = ByteArray(0),
                contentType = "application/octet-stream",
                accept = "audio/*,application/octet-stream",
                bearerToken = bearerToken,
                additionalHeaders = additionalHeaders,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                followRedirects = true
            )
        )
        val mimeType = RemoteVoiceAudioValidator.detectMimeType(response.contentType, response.bytes)
        RemoteVoiceAudioValidator.validate(response.bytes, mimeType)
        return RemoteVoiceAudioPayload(
            bytes = response.bytes,
            mimeType = mimeType
        )
    }

    protected suspend fun executeTransport(
        request: RemoteVoiceTransportRequest
    ): RemoteVoiceTransportResponse {
        return try {
            transport.execute(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw mapThrowable(error)
        }
    }

    protected open fun mapThrowable(error: Throwable): VoiceProviderException {
        return when (error) {
            is SocketTimeoutException -> VoiceProviderException(
                VoiceProviderErrorCode.TIMEOUT,
                "Yeu cau voice provider bi het thoi gian cho."
            )
            is SSLException,
            is ConnectException,
            is IOException -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Khong the ket noi toi voice provider luc nay."
            )
            else -> VoiceProviderException(
                VoiceProviderErrorCode.PROVIDER_UNAVAILABLE,
                "Voice provider tam thoi khong san sang."
            )
        }
    }

    protected abstract fun providerId(): String

    protected abstract fun displayName(): String

    protected abstract fun validateConfig(config: VoiceProviderConfig)

    protected abstract suspend fun synthesizeInternal(
        config: VoiceProviderConfig,
        text: String
    ): RemoteVoiceAudioPayload

    protected abstract fun mapHttpError(statusCode: Int, body: String): VoiceProviderException

    private fun checksum(bytes: ByteArray): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
