package com.lqlq.browser.automation.connector.image

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudflareWorkersAiImageConnectorTest {

    @Test
    fun cloudflareConnectorUsesCanonicalEndpointAndRequestBody() = runBlocking {
        val transport = RecordingTransport(successBody(PNG_BASE64))
        val connector = CloudflareWorkersAiImageConnector(transport)

        val result = connector.generateImage(
            config = ImageProviderConfig(
                providerId = CloudflareWorkersAiImageConnector.PROVIDER_ID,
                apiKey = "cf-token",
                model = CloudflareWorkersAiImageConnector.DEFAULT_MODEL,
                accountId = "account-123"
            ),
            request = ImageGenerationRequest(
                jobId = "job-1",
                sceneId = "scene-1",
                ordinal = 1,
                prompt = "A bright Vietnamese street-food poster",
                aspectRatio = "9:16",
                negativePrompt = "watermark"
            )
        )

        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/account-123/ai/run/${CloudflareWorkersAiImageConnector.DEFAULT_MODEL}",
            transport.lastRequest?.url
        )
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"prompt\":\"A bright Vietnamese street-food poster\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"steps\":4"))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("negative_prompt").not())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"quality\"").not())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"output_format\"").not())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"response_format\"").not())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"n\"").not())
        assertEquals("cf-ray-1", result.providerRequestId)
        assertEquals("image/png", result.mimeType)
    }

    @Test
    fun cloudflareParserReadsWrappedBase64AndDoesNotReuseOpenAiParser() = runBlocking {
        val connector = CloudflareWorkersAiImageConnector(RecordingTransport(successBody(PNG_BASE64)))

        val result = connector.generateImage(
            config = ImageProviderConfig(
                providerId = CloudflareWorkersAiImageConnector.PROVIDER_ID,
                apiKey = "cf-token",
                model = CloudflareWorkersAiImageConnector.DEFAULT_MODEL,
                accountId = "account-123"
            ),
            request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
        )

        assertEquals("scene-1", result.sceneId)
        assertEquals(1, result.ordinal)
        assertTrue(result.bytes.isNotEmpty())
        assertMappedError(
            statusCode = 200,
            body = """{"data":[{"b64_json":"$PNG_BASE64"}]}""",
            expected = ImageProviderErrorCode.INVALID_RESPONSE
        )
    }

    @Test
    fun cloudflareErrorMappingsMatchDeviceTestReadinessMatrix() = runBlocking {
        assertMappedError(
            statusCode = 403,
            body = """{"success":false,"errors":[{"code":10000,"message":"forbidden"}]}""",
            expected = ImageProviderErrorCode.INVALID_API_TOKEN_OR_ACCOUNT_ACCESS
        )
        assertMappedError(
            statusCode = 404,
            body = """{"success":false,"errors":[{"code":7003,"message":"model not found"}]}""",
            expected = ImageProviderErrorCode.MODEL_NOT_AVAILABLE
        )
        assertMappedError(
            statusCode = 429,
            body = """{"success":false,"errors":[{"code":3036,"message":"free allocation exhausted"}]}""",
            expected = ImageProviderErrorCode.FREE_ALLOCATION_EXHAUSTED
        )
        assertMappedError(
            statusCode = 429,
            body = """{"success":false,"errors":[{"code":9999,"message":"rate limited"}]}""",
            expected = ImageProviderErrorCode.RATE_LIMITED
        )
        assertMappedError(
            statusCode = 503,
            body = """{"success":false,"errors":[{"code":5000,"message":"unavailable"}]}""",
            expected = ImageProviderErrorCode.PROVIDER_UNAVAILABLE
        )
    }

    @Test
    fun invalidModelNameRejectedBeforeRequest() = runBlocking {
        val connector = CloudflareWorkersAiImageConnector(RecordingTransport(successBody(PNG_BASE64)))

        try {
            connector.generateImage(
                config = ImageProviderConfig(
                    providerId = CloudflareWorkersAiImageConnector.PROVIDER_ID,
                    apiKey = "cf-token",
                    model = "https://evil.example/model",
                    accountId = "account-123"
                ),
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
        val connector = CloudflareWorkersAiImageConnector(RecordingTransport(body, statusCode))
        try {
            connector.generateImage(
                config = ImageProviderConfig(
                    providerId = CloudflareWorkersAiImageConnector.PROVIDER_ID,
                    apiKey = "cf-token",
                    model = CloudflareWorkersAiImageConnector.DEFAULT_MODEL,
                    accountId = "account-123"
                ),
                request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
            )
            throw AssertionError("Expected ImageProviderException")
        } catch (error: ImageProviderException) {
            assertEquals(expected, error.code)
        }
    }

    private class RecordingTransport(
        private val body: String,
        private val statusCode: Int = 200,
        private val contentType: String? = "application/json"
    ) : CloudflareWorkersAiTransport {
        var lastRequest: CloudflareWorkersAiTransportRequest? = null

        override suspend fun execute(request: CloudflareWorkersAiTransportRequest): CloudflareWorkersAiTransportResponse {
            lastRequest = request
            return CloudflareWorkersAiTransportResponse(
                statusCode = statusCode,
                body = body,
                bytes = body.toByteArray(),
                contentType = contentType,
                requestId = "cf-ray-1"
            )
        }
    }

    companion object {
        private const val PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+b9xQAAAAASUVORK5CYII="

        private fun successBody(base64: String): String {
            return """{"success":true,"result":"$base64","errors":[],"messages":[]}"""
        }
    }
}
