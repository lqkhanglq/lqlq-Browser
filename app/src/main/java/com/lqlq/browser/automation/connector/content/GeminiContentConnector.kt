package com.lqlq.browser.automation.connector.content

import com.lqlq.browser.automation.script.StructuredScriptParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLException

class GeminiContentConnector(
    private val transport: GeminiTransport = HttpUrlConnectionGeminiTransport()
) : ContentGenerationConnector {

    override suspend fun testConnection(
        config: ContentProviderConfig
    ): ContentGenerationResult {
        return generateContent(
            config = config,
            request = ContentGenerationRequest(
                providerId = config.providerId,
                model = config.model,
                topic = "Kiem tra ket noi Gemini cho Automation Center.",
                language = "vi",
                contentType = "connection_test",
                promptTemplate = "Tra loi dung mot dong duy nhat: KET_NOI_THANH_CONG",
                maximumOutputLength = 128
            )
        )
    }

    override suspend fun generateContent(
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ): ContentGenerationResult {
        validate(config, request)

        var lastFailure: ContentProviderException? = null
        repeat(MAX_RETRIES) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                val transportRequest = GeminiTransportRequest(
                    url = buildUrl(config),
                    body = buildRequestBody(config, request),
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                    maxResponseBytes = MAX_RESPONSE_BYTES
                )
                val response = transport.execute(transportRequest)
                return parseResponse(
                    response = response,
                    config = config,
                    request = request
                )
            } catch (error: CancellationException) {
                throw ContentProviderException(
                    ContentProviderErrorCode.CANCELLED,
                    "Da huy yeu cau tao noi dung.",
                    error
                )
            } catch (error: ContentProviderException) {
                lastFailure = error
                if (!error.code.isRetryable() || attempt == MAX_RETRIES - 1) {
                    throw error
                }
            } catch (error: Throwable) {
                val mapped = mapThrowable(error)
                lastFailure = mapped
                if (!mapped.code.isRetryable() || attempt == MAX_RETRIES - 1) {
                    throw mapped
                }
            }
        }

        throw lastFailure ?: ContentProviderException(
            ContentProviderErrorCode.PROVIDER_FAILURE,
            "Khong the tao noi dung tu Gemini luc nay."
        )
    }

    internal fun buildPrompt(request: ContentGenerationRequest): String {
        val additionalRequirements = request.promptTemplate.trim()
        val durationPolicy = request.durationPolicy
        return buildString {
            appendLine(DEFAULT_SCRIPT_SYSTEM_PROMPT)
            appendLine()
            appendLine("## Video Subject")
            appendLine(request.topic.trim())
            appendLine()
            appendLine("## Output Requirements")
            appendLine("- content_type: ${request.contentType}")
            appendLine("- language: ${request.language.trim().ifEmpty { "vi" }}")
            appendLine("- maximum_output_length: ${request.maximumOutputLength} characters")
            durationPolicy?.let { policy ->
                val targetSeconds = (policy.targetDurationMs / 1000L).coerceAtLeast(1L)
                val minimumSpokenWords = ((targetSeconds * 23L) + 9L) / 10L
                appendLine("- target_duration_ms: ${policy.targetDurationMs}")
                appendLine("- target_scene_count: ${policy.targetSceneCount}")
                appendLine("- listicle: ${policy.isListicle}")
                appendLine("- target_item_count: ${policy.targetItemCount ?: 0}")
                appendLine("- minimum_spoken_word_count: $minimumSpokenWords")
                appendLine("- The complete spoken narration must last AT LEAST $targetSeconds seconds at a normal reading speed; it may be longer, but must never be shorter.")
                appendLine("- Expand explanations, transitions, examples and scene narration naturally until the minimum duration and word count are satisfied.")
            }
            if (durationPolicy?.isListicle == true) {
                appendLine()
                appendLine("## Structured Output Contract")
                appendLine("Return valid JSON only.")
                appendLine("Schema:")
                appendLine("{")
                appendLine("  \"intro\": { \"title\": \"...\", \"voiceText\": \"...\", \"onScreenText\": \"...\", \"visualQuery\": \"...\", \"durationMs\": 4000 },")
                appendLine("  \"items\": [")
                appendLine("    { \"index\": 1, \"title\": \"...\", \"voiceText\": \"...\", \"onScreenText\": \"...\", \"visualQuery\": \"...\", \"durationMs\": 5000 }")
                appendLine("  ],")
                appendLine("  \"outro\": { \"title\": \"...\", \"voiceText\": \"...\", \"onScreenText\": \"...\", \"visualQuery\": \"...\", \"durationMs\": 4000 }")
                appendLine("}")
                appendLine("Rules:")
                appendLine("- Create exactly ${durationPolicy.targetItemCount} items.")
                appendLine("- Do not return only intro.")
                appendLine("- Each item must contain index, title, voiceText, onScreenText, visualQuery, durationMs.")
                appendLine("- For topics like \"10 cau noi\", each item should include one main sentence and one short explanation.")
                appendLine("- Include a short intro and short outro.")
                appendLine("- Total voiceText must last at least ${durationPolicy.targetDurationMs} ms when spoken at a normal speed; longer is acceptable, shorter is not.")
            }
            val repairInstruction = request.repairInstruction?.trim().orEmpty()
            if (repairInstruction.isNotEmpty()) {
                appendLine()
                appendLine("## Repair Instruction")
                appendLine(repairInstruction)
            }
            if (additionalRequirements.isNotEmpty()) {
                appendLine()
                appendLine("# Additional User Requirements:")
                appendLine(additionalRequirements)
            }
        }.trim()
    }

    private fun buildUrl(config: ContentProviderConfig): String {
        val model = config.model.trim()
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey.trim()}"
    }

    private fun buildRequestBody(
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ): String {
        val prompt = buildPrompt(request)
        val outputTokens = (request.maximumOutputLength / 2).coerceIn(512, 8_192)
        val escapedPrompt = escapeJson(prompt)
        val escapedSystemInstruction = escapeJson(
            if (request.durationPolicy?.isListicle == true) {
                "Return only valid JSON that follows the requested schema. No markdown or code fences."
            } else {
                "Return only the final script text with no markdown, JSON, HTML, or code fences."
            }
        )
        return """
            {
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    {
                      "text": "$escapedPrompt"
                    }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.5,
                "topP": 1,
                "topK": 1,
                "maxOutputTokens": $outputTokens
              },
              "systemInstruction": {
                "parts": [
                  {
                    "text": "$escapedSystemInstruction"
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private fun parseResponse(
        response: GeminiTransportResponse,
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ): ContentGenerationResult {
        if (response.statusCode !in 200..299) {
            throw mapGeminiError(response.statusCode, response.body)
        }

        val rawText = extractTextParts(response.body)
            .joinToString(separator = "\n") { it.trim() }
            .trim()

        if (rawText.isBlank()) {
            throw ContentProviderException(
                ContentProviderErrorCode.INVALID_RESPONSE,
                "Gemini khong tra ve noi dung hop le."
            )
        }

        val structuredScript = request.durationPolicy?.let { StructuredScriptParser.parse(rawText, it) }
        val generatedText = structuredScript?.fullVoiceText().takeUnless { it.isNullOrBlank() } ?: rawText

        val usageMetadata = linkedMapOf<String, Long>()
        putUsage(usageMetadata, response.body, "promptTokenCount")
        putUsage(usageMetadata, response.body, "candidatesTokenCount")
        putUsage(usageMetadata, response.body, "totalTokenCount")

        return ContentGenerationResult(
            generatedText = generatedText,
            providerId = config.providerId,
            model = request.model,
            requestId = response.requestId,
            usageMetadata = usageMetadata,
            structuredScript = structuredScript
        )
    }

    private fun extractTextParts(body: String): List<String> {
        val parts = mutableListOf<String>()
        findJsonStringValues(body, "text").forEach { text ->
            val decoded = text.trim()
            if (decoded.isNotBlank()) {
                parts += decoded
            }
        }
        return parts
    }

    private fun mapGeminiError(
        statusCode: Int,
        body: String
    ): ContentProviderException {
        val status = findJsonStringValue(body, "status").orEmpty()
        val message = findJsonStringValue(body, "message")
            .orEmpty()
            .lowercase()

        return when {
            statusCode == 401 || statusCode == 403 && message.contains("api key") ->
                ContentProviderException(
                    ContentProviderErrorCode.AUTHENTICATION,
                    "Gemini tu choi API key hoac model hien tai."
                )

            statusCode == 429 || status == "RESOURCE_EXHAUSTED" || message.contains("quota") ->
                ContentProviderException(
                    ContentProviderErrorCode.QUOTA_EXCEEDED,
                    "Dich vu Gemini da het han muc hoac dang gioi han yeu cau."
                )

            statusCode in 500..599 ->
                ContentProviderException(
                    ContentProviderErrorCode.NETWORK,
                    "Gemini tam thoi khong san sang. Hay thu lai sau."
                )

            else ->
                ContentProviderException(
                    ContentProviderErrorCode.PROVIDER_FAILURE,
                    "Gemini tra ve loi khong the xu ly luc nay."
                )
        }
    }

    private fun mapThrowable(error: Throwable): ContentProviderException {
        return when (error) {
            is ContentProviderException -> error
            is SocketTimeoutException -> ContentProviderException(
                ContentProviderErrorCode.TIMEOUT,
                "Yeu cau Gemini bi het thoi gian cho."
            )

            is SSLException,
            is ConnectException,
            is IOException -> ContentProviderException(
                ContentProviderErrorCode.NETWORK,
                "Khong the ket noi toi Gemini luc nay."
            )

            else -> ContentProviderException(
                ContentProviderErrorCode.PROVIDER_FAILURE,
                "Khong the tao noi dung tu Gemini luc nay."
            )
        }
    }

    private fun validate(
        config: ContentProviderConfig,
        request: ContentGenerationRequest
    ) {
        require(config.providerId == GEMINI_PROVIDER_ID) { "Only Gemini content generation is supported." }
        require(config.apiKey.trim().isNotEmpty()) { "Gemini API key is required." }
        require(config.model.trim().isNotEmpty()) { "Gemini model is required." }
        require(request.topic.trim().isNotEmpty()) { "Topic is required." }
        require(request.maximumOutputLength in 128..50_000) {
            "Maximum output length must stay within a safe range."
        }
    }

    private fun ContentProviderErrorCode.isRetryable(): Boolean {
        return this == ContentProviderErrorCode.NETWORK ||
            this == ContentProviderErrorCode.TIMEOUT ||
            this == ContentProviderErrorCode.INVALID_RESPONSE
    }

    private fun putUsage(
        target: MutableMap<String, Long>,
        key: String,
        body: String
    ) {
        findJsonLongValue(body, key)
            ?.let { value ->
                target[key] = value
            }
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

    private fun unescapeJson(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                val next = value[index + 1]
                when (next) {
                    '\\' -> builder.append('\\')
                    '"' -> builder.append('"')
                    '/' -> builder.append('/')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('\u000C')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'u' -> {
                        val endIndex = index + 6
                        if (endIndex <= value.length) {
                            val hex = value.substring(index + 2, endIndex)
                            builder.append(hex.toInt(16).toChar())
                            index += 4
                        }
                    }
                    else -> builder.append(next)
                }
                index += 2
                continue
            }
            builder.append(current)
            index++
        }
        return builder.toString()
    }

    private fun findJsonStringValue(body: String, key: String): String? {
        return findJsonStringValues(body, key).firstOrNull()
    }

    private fun findJsonStringValues(body: String, key: String): List<String> {
        val needle = "\"$key\""
        val values = mutableListOf<String>()
        var searchIndex = 0
        while (true) {
            val keyIndex = body.indexOf(needle, startIndex = searchIndex)
            if (keyIndex < 0) {
                return values
            }
            val colonIndex = body.indexOf(':', startIndex = keyIndex + needle.length)
            if (colonIndex < 0) {
                return values
            }
            var valueIndex = colonIndex + 1
            while (valueIndex < body.length && body[valueIndex].isWhitespace()) {
                valueIndex++
            }
            if (valueIndex >= body.length || body[valueIndex] != '"') {
                searchIndex = colonIndex + 1
                continue
            }
            valueIndex++
            val rawValue = StringBuilder()
            var escaped = false
            while (valueIndex < body.length) {
                val current = body[valueIndex]
                if (escaped) {
                    rawValue.append('\\').append(current)
                    escaped = false
                    valueIndex++
                    continue
                }
                when (current) {
                    '\\' -> {
                        escaped = true
                        valueIndex++
                    }
                    '"' -> {
                        values += unescapeJson(rawValue.toString())
                        searchIndex = valueIndex + 1
                        break
                    }
                    else -> {
                        rawValue.append(current)
                        valueIndex++
                    }
                }
            }
            if (valueIndex >= body.length) {
                return values
            }
        }
    }

    private fun findJsonLongValue(body: String, key: String): Long? {
        val needle = "\"$key\""
        val keyIndex = body.indexOf(needle)
        if (keyIndex < 0) {
            return null
        }
        val colonIndex = body.indexOf(':', startIndex = keyIndex + needle.length)
        if (colonIndex < 0) {
            return null
        }
        var valueIndex = colonIndex + 1
        while (valueIndex < body.length && body[valueIndex].isWhitespace()) {
            valueIndex++
        }
        val digits = StringBuilder()
        while (valueIndex < body.length && body[valueIndex].isDigit()) {
            digits.append(body[valueIndex])
            valueIndex++
        }
        return digits.toString().toLongOrNull()
    }

    companion object {
        const val GEMINI_PROVIDER_ID: String = "gemini"
        const val DEFAULT_MODEL: String = "gemini-2.5-flash"

        private const val MAX_RETRIES = 5
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024

        private const val DEFAULT_SCRIPT_SYSTEM_PROMPT = """
# Role: Video Script Generator

## Goals:
Generate a script for a video, depending on the subject of the video.

## Constrains:
1. the script is to be returned as a plain string.
2. do not under any circumstance reference this prompt in your response.
3. get straight to the point and avoid generic introductions.
4. do not include markdown, JSON, HTML, or code fences.
5. only return the raw content of the script.
6. do not include "voiceover", "narrator", or similar speaker labels.
7. respond in the same language as the video subject.
        """

    }
}

data class GeminiTransportRequest(
    val url: String,
    val body: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxResponseBytes: Int
)

data class GeminiTransportResponse(
    val statusCode: Int,
    val body: String,
    val requestId: String?
)

interface GeminiTransport {
    suspend fun execute(request: GeminiTransportRequest): GeminiTransportResponse
}

class HttpUrlConnectionGeminiTransport : GeminiTransport {
    override suspend fun execute(
        request: GeminiTransportRequest
    ): GeminiTransportResponse = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        val url = URL(request.url)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Gemini requests must use HTTPS."
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doInput = true
            doOutput = true
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
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
            val responseBody = responseStream?.use {
                readLimited(it.readBytes(), request.maxResponseBytes)
            }.orEmpty()
            GeminiTransportResponse(
                statusCode = statusCode,
                body = responseBody,
                requestId = connection.getHeaderField("x-request-id")
                    ?: connection.getHeaderField("x-goog-request-id")
            )
        } catch (error: SocketTimeoutException) {
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(bytes: ByteArray, maxResponseBytes: Int): String {
        if (bytes.size > maxResponseBytes) {
            throw ContentProviderException(
                ContentProviderErrorCode.INVALID_RESPONSE,
                "Phan hoi tu Gemini vuot qua gioi han an toan."
            )
        }
        return String(bytes, StandardCharsets.UTF_8)
    }
}
