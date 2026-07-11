package com.lqlq.browser.automation.connector.image

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageArtifactValidationTest {

    @Test
    fun htmlPayloadIsRejectedAsImage() = runBlocking {
        val htmlBytes = "<html>not an image</html>".encodeToByteArray()
        val connector = OpenAiImageConnector(
            transport = StaticTransport(successBody(java.util.Base64.getEncoder().encodeToString(htmlBytes)))
        )

        try {
            connector.generateImage(
                config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "key", OpenAiImageConnector.DEFAULT_MODEL),
                request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
            )
            throw AssertionError("Expected ImageProviderException")
        } catch (error: ImageProviderException) {
            assertEquals(ImageProviderErrorCode.INVALID_IMAGE, error.code)
        }
    }

    @Test
    fun validPngPayloadPassesValidation() = runBlocking {
        val connector = OpenAiImageConnector(
            transport = StaticTransport(successBody(PNG_BASE64))
        )

        val result = connector.generateImage(
            config = ImageProviderConfig(OpenAiImageConnector.PROVIDER_ID, "key", OpenAiImageConnector.DEFAULT_MODEL),
            request = ImageGenerationRequest("job-1", "scene-1", 1, "prompt", "1:1")
        )

        assertEquals("image/png", result.mimeType)
        assertEquals(1, result.ordinal)
    }

    private class StaticTransport(
        private val body: String
    ) : OpenAiImageTransport {
        override suspend fun execute(request: OpenAiImageTransportRequest): OpenAiImageTransportResponse {
            return OpenAiImageTransportResponse(
                statusCode = 200,
                body = body,
                requestId = "req-image"
            )
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
