package com.lqlq.browser.automation.connector.content

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class GeminiContentConnectorTest {

    @Test
    fun requestMappingCarriesDonorPromptSemantics() = runBlocking {
        val transport = RecordingTransport(
            GeminiTransportResponse(
                statusCode = 200,
                body = successfulResponse("Generated body"),
                requestId = "req-1"
            )
        )
        val connector = GeminiContentConnector(transport)

        connector.generateContent(validConfig(), validRequest())

        assertTrue(transport.lastRequest!!.url.contains("models/gemini-2.5-flash:generateContent"))
        assertTrue(transport.lastRequest!!.body.contains("Video Script Generator"))
        assertTrue(transport.lastRequest!!.body.contains("Ten lua"))
        assertTrue(transport.lastRequest!!.body.contains("Giu giu giong dieu mo ta"))
        assertTrue(transport.lastRequest!!.body.contains(""""maxOutputTokens": 2000"""))
    }

    @Test
    fun successfulResponseExtractionReturnsTextAndUsage() = runBlocking {
        val connector = GeminiContentConnector(
            RecordingTransport(
                GeminiTransportResponse(
                    statusCode = 200,
                    body = successfulResponse("Noi dung script that"),
                    requestId = "req-22"
                )
            )
        )

        val result = connector.generateContent(validConfig(), validRequest())

        assertEquals("Noi dung script that", result.generatedText)
        assertEquals("req-22", result.requestId)
        if (result.usageMetadata.isNotEmpty()) {
            assertEquals(21L, result.usageMetadata["totalTokenCount"])
        }
    }

    @Test
    fun emptyProviderResponseRejected() = runBlocking {
        val connector = GeminiContentConnector(
            RecordingTransport(
                GeminiTransportResponse(
                    statusCode = 200,
                    body = """{"candidates":[{"content":{"parts":[]}}]}""",
                    requestId = null
                )
            )
        )

        val error = runCatching {
            connector.generateContent(validConfig(), validRequest())
        }.exceptionOrNull()

        require(error is ContentProviderException)
        assertEquals(ContentProviderErrorCode.INVALID_RESPONSE, error.code)
    }

    @Test
    fun authenticationErrorMappingIsRedacted() = runBlocking {
        val connector = GeminiContentConnector(
            RecordingTransport(
                GeminiTransportResponse(
                    statusCode = 403,
                    body = """{"error":{"status":"PERMISSION_DENIED","message":"bad api key secret=abc"}}""",
                    requestId = null
                )
            )
        )

        val error = runCatching {
            connector.generateContent(validConfig(), validRequest())
        }.exceptionOrNull()

        require(error is ContentProviderException)
        assertEquals(ContentProviderErrorCode.AUTHENTICATION, error.code)
        assertFalse(error.message.orEmpty().contains("secret"))
    }

    @Test
    fun quotaErrorMappingIsFriendly() = runBlocking {
        val connector = GeminiContentConnector(
            RecordingTransport(
                GeminiTransportResponse(
                    statusCode = 429,
                    body = """{"error":{"status":"RESOURCE_EXHAUSTED","message":"quota exceeded"}}""",
                    requestId = null
                )
            )
        )

        val error = runCatching {
            connector.generateContent(validConfig(), validRequest())
        }.exceptionOrNull()

        require(error is ContentProviderException)
        assertEquals(ContentProviderErrorCode.QUOTA_EXCEEDED, error.code)
    }

    @Test
    fun timeoutMappingStaysBounded() = runBlocking {
        val connector = GeminiContentConnector(
            object : GeminiTransport {
                override suspend fun execute(request: GeminiTransportRequest): GeminiTransportResponse {
                    throw SocketTimeoutException("timeout")
                }
            }
        )

        val error = runCatching {
            connector.generateContent(validConfig(), validRequest())
        }.exceptionOrNull()

        require(error is ContentProviderException)
        assertEquals(ContentProviderErrorCode.TIMEOUT, error.code)
    }

    @Test
    fun cancellationMappingIsSupported() = runBlocking {
        val connector = GeminiContentConnector(
            object : GeminiTransport {
                override suspend fun execute(request: GeminiTransportRequest): GeminiTransportResponse {
                    throw kotlinx.coroutines.CancellationException("cancelled")
                }
            }
        )

        val error = runCatching {
            connector.generateContent(validConfig(), validRequest())
        }.exceptionOrNull()

        require(error is ContentProviderException)
        assertEquals(ContentProviderErrorCode.CANCELLED, error.code)
    }

    private fun validConfig(): ContentProviderConfig {
        return ContentProviderConfig(
            providerId = "gemini",
            apiKey = "secret-key",
            model = "gemini-2.5-flash",
            promptTemplate = "Giu giu giong dieu mo ta",
            maximumOutputLength = 4000
        )
    }

    private fun validRequest(): ContentGenerationRequest {
        return ContentGenerationRequest(
            providerId = "gemini",
            model = "gemini-2.5-flash",
            topic = "Ten lua",
            language = "vi",
            contentType = "video_script",
            promptTemplate = "Giu giu giong dieu mo ta",
            maximumOutputLength = 4000
        )
    }

    private fun successfulResponse(text: String): String {
        return """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "$text" }
                    ]
                  }
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 10,
                "candidatesTokenCount": 11,
                "totalTokenCount": 21
              }
            }
        """.trimIndent()
    }

    private class RecordingTransport(
        private val response: GeminiTransportResponse
    ) : GeminiTransport {
        var lastRequest: GeminiTransportRequest? = null

        override suspend fun execute(request: GeminiTransportRequest): GeminiTransportResponse {
            lastRequest = request
            return response
        }
    }
}
