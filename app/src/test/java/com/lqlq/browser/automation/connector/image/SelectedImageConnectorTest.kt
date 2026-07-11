package com.lqlq.browser.automation.connector.image

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectedImageConnectorTest {

    @Test
    fun openAiConnectorMapsRequestToOfficialImagesApiContract() = runBlocking {
        val transport = RecordingTransport(successBody(PNG_BASE64))
        val connector = OpenAiImageConnector(transport)

        val result = connector.generateImage(
            config = ImageProviderConfig(
                providerId = OpenAiImageConnector.PROVIDER_ID,
                apiKey = "openai-key",
                model = OpenAiImageConnector.DEFAULT_MODEL
            ),
            request = ImageGenerationRequest(
                jobId = "job-1",
                sceneId = "scene-1",
                ordinal = 1,
                prompt = "A clean product hero shot",
                aspectRatio = "9:16",
                negativePrompt = "watermark"
            )
        )

        assertEquals("https://api.openai.com/v1/images/generations", transport.lastRequest?.url)
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"model\": \"${OpenAiImageConnector.DEFAULT_MODEL}\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"size\": \"1024x1536\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"quality\": \"low\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"output_format\": \"png\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"n\": 1"))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"b64_json\"").not())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("Avoid: watermark"))
        assertEquals("scene-1", result.sceneId)
        assertEquals(1, result.ordinal)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun providerAuthenticationErrorIsMapped() = runBlocking {
        val transport = RecordingTransport(
            """{"error":{"code":"invalid_api_key","type":"invalid_request_error","message":"bad key"}}""",
            statusCode = 401
        )
        val connector = OpenAiImageConnector(transport)

        try {
            connector.generateImage(
                config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "bad-key", OpenAiImageConnector.DEFAULT_MODEL),
                request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
            )
            throw AssertionError("Expected ImageProviderException")
        } catch (error: ImageProviderException) {
            assertEquals(ImageProviderErrorCode.INVALID_API_KEY, error.code)
        }
    }

    @Test
    fun quotaModerationAndServerErrorsAreMapped() = runBlocking {
        assertMappedError(
            statusCode = 429,
            body = """{"error":{"code":"insufficient_quota","message":"billing required"}}""",
            expected = ImageProviderErrorCode.BILLING_REQUIRED
        )
        assertMappedError(
            statusCode = 400,
            body = """{"error":{"code":"moderation_blocked","message":"blocked by moderation"}}""",
            expected = ImageProviderErrorCode.USER_ACTION_REQUIRED
        )
        assertMappedError(
            statusCode = 400,
            body = """{"error":{"code":"image_generation_user_error","message":"revise your prompt"}}""",
            expected = ImageProviderErrorCode.USER_ACTION_REQUIRED
        )
        assertMappedError(
            statusCode = 500,
            body = """{"error":{"message":"server error"}}""",
            expected = ImageProviderErrorCode.PROVIDER_UNAVAILABLE
        )
    }

    @Test
    fun retriesTransientErrorsButStopsAfterLimit() = runBlocking {
        val transport = SequencedTransport(
            listOf(
                OpenAiImageTransportResponse(429, """{"error":{"code":"rate_limit_exceeded","message":"rate limit"}}""", "req-1"),
                OpenAiImageTransportResponse(429, """{"error":{"code":"rate_limit_exceeded","message":"rate limit"}}""", "req-2"),
                OpenAiImageTransportResponse(200, successBody(PNG_BASE64), "req-3")
            )
        )
        val connector = OpenAiImageConnector(transport)

        val result = connector.generateImage(
            config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "openai-key", OpenAiImageConnector.DEFAULT_MODEL),
            request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "9:16")
        )

        assertEquals(3, transport.callCount)
        assertEquals("req-3", result.providerRequestId)
    }

    @Test
    fun invalidModelNameRejectedBeforeRequest() = runBlocking {
        val connector = OpenAiImageConnector(RecordingTransport(successBody(PNG_BASE64)))

        try {
            connector.generateImage(
                config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "openai-key", "https://evil/model"),
                request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
            )
            throw AssertionError("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("invalid"))
        }
    }

    private suspend fun assertMappedError(
        statusCode: Int,
        body: String,
        expected: ImageProviderErrorCode
    ) {
        val connector = OpenAiImageConnector(RecordingTransport(body, statusCode))
        try {
            connector.generateImage(
                config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "bad-key", OpenAiImageConnector.DEFAULT_MODEL),
                request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
            )
            throw AssertionError("Expected ImageProviderException")
        } catch (error: ImageProviderException) {
            assertEquals(expected, error.code)
        }
    }

    private class RecordingTransport(
        private val body: String,
        private val statusCode: Int = 200
    ) : OpenAiImageTransport {
        var lastRequest: OpenAiImageTransportRequest? = null

        override suspend fun execute(request: OpenAiImageTransportRequest): OpenAiImageTransportResponse {
            lastRequest = request
            return OpenAiImageTransportResponse(
                statusCode = statusCode,
                body = body,
                requestId = "req-openai-image"
            )
        }
    }

    private class SequencedTransport(
        private val responses: List<OpenAiImageTransportResponse>
    ) : OpenAiImageTransport {
        var callCount: Int = 0

        override suspend fun execute(request: OpenAiImageTransportRequest): OpenAiImageTransportResponse {
            val index = callCount.coerceAtMost(responses.lastIndex)
            callCount += 1
            return responses[index]
        }
    }

    companion object {
        private const val PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+b9xQAAAAASUVORK5CYII="

        private fun successBody(base64: String): String {
            return """{"data":[{"b64_json":"$base64"}]}"""
        }
    }
}
