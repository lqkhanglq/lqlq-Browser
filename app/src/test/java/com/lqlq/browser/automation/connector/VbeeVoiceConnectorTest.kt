package com.lqlq.browser.automation.connector

import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransport
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransportRequest
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransportResponse
import com.lqlq.browser.automation.connector.voice.VbeeVoiceConnector
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class VbeeVoiceConnectorTest {

    @Test
    fun synthesizeSamplePostsJsonThenDownloadsAudioUrl() = runBlocking {
        val requests = mutableListOf<RemoteVoiceTransportRequest>()
        val audioBytes = fakeMp3Bytes()
        val connector = VbeeVoiceConnector(
            transport = object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    requests += request
                    return when (request.method) {
                        "POST" -> RemoteVoiceTransportResponse(
                            statusCode = 200,
                            bytes = """{"audio_url":"https://cdn.vbee.vn/audio/sample.mp3"}"""
                                .toByteArray(StandardCharsets.UTF_8),
                            body = """{"audio_url":"https://cdn.vbee.vn/audio/sample.mp3"}""",
                            contentType = "application/json",
                            requestId = "vbee-submit-1"
                        )

                        "GET" -> RemoteVoiceTransportResponse(
                            statusCode = 200,
                            bytes = audioBytes,
                            body = String(audioBytes, Charsets.ISO_8859_1),
                            contentType = "audio/mpeg",
                            requestId = "vbee-download-1"
                        )

                        else -> error("Unexpected method ${request.method}")
                    }
                }
            }
        )

        val result = connector.synthesizeSample(
            config = VoiceProviderConfig(
                providerId = AutomationVoiceProviders.VBEE_TTS,
                locale = "vi-VN",
                voiceId = "hn_female_minhquy_vdts_48k-hsmm",
                outputFormat = "mp3",
                apiKey = "vbee-api-key"
            ),
            text = "Xin chao VBEE"
        )

        assertEquals(2, requests.size)
        assertEquals("POST", requests[0].method)
        assertEquals("application/json; charset=utf-8", requests[0].contentType)
        assertEquals("application/json", requests[0].accept)
        assertEquals("vbee-api-key", requests[0].additionalHeaders["api-key"])
        assertTrue(String(requests[0].body, StandardCharsets.UTF_8).contains("\"voice_code\":\"hn_female_minhquy_vdts_48k-hsmm\""))
        assertTrue(String(requests[0].body, StandardCharsets.UTF_8).contains("\"audio_type\":\"mp3\""))
        assertEquals("GET", requests[1].method)
        assertEquals("audio/mpeg", result.mimeType)
        assertArrayEquals(audioBytes, result.bytes)
    }

    @Test
    fun modelFieldOverridesVoiceSelectionForCustomVoiceCode() = runBlocking {
        val requests = mutableListOf<RemoteVoiceTransportRequest>()
        val connector = VbeeVoiceConnector(
            transport = object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    requests += request
                    return if (request.method == "POST") {
                        RemoteVoiceTransportResponse(
                            statusCode = 200,
                            bytes = """{"audio_base64":"SUQzBAAAAAAAABFUQUxC"}""".toByteArray(StandardCharsets.UTF_8),
                            body = """{"audio_base64":"SUQzBAAAAAAAABFUQUxC"}""",
                            contentType = "application/json",
                            requestId = "inline-1"
                        )
                    } else {
                        error("Unexpected method ${request.method}")
                    }
                }
            }
        )

        val result = connector.synthesizeSample(
            config = VoiceProviderConfig(
                providerId = AutomationVoiceProviders.VBEE_TTS,
                locale = "vi-VN",
                voiceId = "hn_female_minhquy_vdts_48k-hsmm",
                model = "sg_male_minhhoang_vdts_48k-hsmm",
                outputFormat = "mp3",
                apiKey = "app123:vbee-secret"
            ),
            text = "Xin chao voice custom"
        )

        val submitBody = String(requests.single().body, StandardCharsets.UTF_8)
        assertTrue(submitBody.contains("\"voice_code\":\"sg_male_minhhoang_vdts_48k-hsmm\""))
        assertTrue(submitBody.contains("\"app_id\":\"app123\""))
        assertEquals("vbee-secret", requests.single().additionalHeaders["api-key"])
        assertEquals("audio/mpeg", result.mimeType)
    }

    private fun fakeMp3Bytes(): ByteArray {
        return byteArrayOf(
            0x49, 0x44, 0x33,
            0x04, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x21,
            0x54, 0x41, 0x4C, 0x42
        )
    }
}
