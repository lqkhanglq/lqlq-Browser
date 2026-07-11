package com.lqlq.browser.automation.connector

import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.FptAiVoiceConnector
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransport
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransportRequest
import com.lqlq.browser.automation.connector.voice.RemoteVoiceTransportResponse
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import com.lqlq.browser.automation.connector.voice.VoiceProviderErrorCode
import com.lqlq.browser.automation.connector.voice.VoiceProviderException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
class FptAiVoiceConnectorTest {

    @Test
    fun synthesizeSampleUsesOfficialHeadersAndRawUtf8Body() = runBlocking {
        val requests = mutableListOf<RemoteVoiceTransportRequest>()
        val audioBytes = fakeWaveBytes(1)
        val connector = fastConnector(
            object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    requests += request
                    return when (request.method) {
                        "POST" -> jsonResponse("""{"error":0,"async":"https://voice.fpt.ai/audio-1.wav","request_id":"req-1"}""")
                        "GET" -> audioResponse(audioBytes, "audio/wav")
                        else -> error("Unexpected method ${request.method}")
                    }
                }
            }
        )

        val result = connector.synthesizeSample(
            config = VoiceProviderConfig(
                providerId = AutomationVoiceProviders.FPT_AI_TTS,
                locale = "vi-VN",
                voiceId = "banmai",
                outputFormat = "wav",
                apiKey = "fpt-key"
            ),
            text = "Xin chào FPT.AI"
        )

        assertEquals(2, requests.size)
        assertEquals("POST", requests[0].method)
        assertEquals("fpt-key", requests[0].additionalHeaders["api_key"])
        assertEquals("banmai", requests[0].additionalHeaders["voice"])
        assertEquals("0", requests[0].additionalHeaders["speed"])
        assertEquals("wav", requests[0].additionalHeaders["format"])
        assertEquals("no-cache", requests[0].additionalHeaders["Cache-Control"])
        assertEquals("Xin chào FPT.AI", String(requests[0].body, StandardCharsets.UTF_8))
        assertEquals("GET", requests[1].method)
        assertEquals("audio/wav", result.mimeType)
        assertArrayEquals(audioBytes, result.bytes)
    }

    @Test
    fun generateVoiceSplitsLongTextSequentiallyAndMergesWavChunks() = runBlocking {
        val requests = mutableListOf<RemoteVoiceTransportRequest>()
        val chunkOneAudio = fakeWaveBytes(2)
        val chunkTwoAudio = fakeWaveBytes(3)
        val connector = fastConnector(
            object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    requests += request
                    return when (request.method) {
                        "POST" -> {
                            val chunkIndex = requests.count { it.method == "POST" }
                            jsonResponse("""{"error":0,"async":"https://voice.fpt.ai/audio-$chunkIndex.wav","request_id":"req-$chunkIndex"}""")
                        }

                        "GET" -> {
                            if (request.url.endsWith("audio-1.wav")) {
                                audioResponse(chunkOneAudio, "audio/wav")
                            } else {
                                audioResponse(chunkTwoAudio, "audio/wav")
                            }
                        }

                        else -> error("Unexpected method ${request.method}")
                    }
                }
            }
        )

        val longText = buildString {
            append("Đoạn một. ")
            append("a".repeat(4_490))
            append("\n\n")
            append("Đoạn hai. ")
            append("b".repeat(4_490))
        }

        val result = connector.generateVoice(
            config = VoiceProviderConfig(
                providerId = AutomationVoiceProviders.FPT_AI_TTS,
                locale = "vi-VN",
                voiceId = "banmai",
                outputFormat = "wav",
                apiKey = "fpt-key"
            ),
            request = VoiceGenerationRequest(
                jobId = "job-1",
                scriptArtifactId = "script-1",
                text = longText,
                providerId = AutomationVoiceProviders.FPT_AI_TTS,
                voiceId = "banmai",
                locale = "vi-VN",
                speechRate = 1.0f,
                pitch = 1.0f,
                outputFormat = "wav"
            )
        )

        assertEquals(4, requests.size)
        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
        assertEquals(2, result.metadata.chunkCount)
        assertEquals("audio/wav", result.mimeType)
        assertTrue(result.bytes.size > chunkOneAudio.size)
    }

    @Test
    fun authFailureOnFirstChunkStopsImmediately() = runBlocking {
        val requests = mutableListOf<RemoteVoiceTransportRequest>()
        val connector = fastConnector(
            object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    requests += request
                    return RemoteVoiceTransportResponse(
                        statusCode = 403,
                        bytes = """{"message":"forbidden"}""".toByteArray(StandardCharsets.UTF_8),
                        body = """{"message":"forbidden"}""",
                        contentType = "application/json",
                        requestId = "req-auth"
                    )
                }
            }
        )

        val error = try {
            connector.generateVoice(
                config = VoiceProviderConfig(
                    providerId = AutomationVoiceProviders.FPT_AI_TTS,
                    locale = "vi-VN",
                    voiceId = "banmai",
                    outputFormat = "wav",
                    apiKey = "bad-key"
                ),
                request = VoiceGenerationRequest(
                    jobId = "job-2",
                    scriptArtifactId = "script-2",
                    text = "x".repeat(4_600),
                    providerId = AutomationVoiceProviders.FPT_AI_TTS,
                    voiceId = "banmai",
                    locale = "vi-VN",
                    speechRate = 1.0f,
                    pitch = 1.0f,
                    outputFormat = "wav"
                )
            )
            null
        } catch (failure: VoiceProviderException) {
            failure
        }

        requireNotNull(error)
        assertEquals(VoiceProviderErrorCode.INVALID_API_KEY, error.code)
        assertEquals(1, requests.size)
    }

    @Test
    fun mp3MultiChunkReturnsUserActionRequiredInsteadOfFakeMerge() = runBlocking {
        val connector = fastConnector(
            object : RemoteVoiceTransport {
                override suspend fun execute(request: RemoteVoiceTransportRequest): RemoteVoiceTransportResponse {
                    error("Transport must not run for rejected multi-chunk mp3 request")
                }
            }
        )

        val error = try {
            connector.generateVoice(
                config = VoiceProviderConfig(
                    providerId = AutomationVoiceProviders.FPT_AI_TTS,
                    locale = "vi-VN",
                    voiceId = "banmai",
                    outputFormat = "mp3",
                    apiKey = "fpt-key"
                ),
                request = VoiceGenerationRequest(
                    jobId = "job-3",
                    scriptArtifactId = "script-3",
                    text = "x".repeat(4_800),
                    providerId = AutomationVoiceProviders.FPT_AI_TTS,
                    voiceId = "banmai",
                    locale = "vi-VN",
                    speechRate = 1.0f,
                    pitch = 1.0f,
                    outputFormat = "mp3"
                )
            )
            null
        } catch (failure: VoiceProviderException) {
            failure
        }

        requireNotNull(error)
        assertEquals(VoiceProviderErrorCode.USER_ACTION_REQUIRED, error.code)
    }

    private fun fastConnector(transport: RemoteVoiceTransport): FptAiVoiceConnector {
        return object : FptAiVoiceConnector(transport) {
            override fun initialPollDelayMs(): Long = 0L
            override fun pollScheduleMs(): LongArray = longArrayOf(0L)
            override fun maxPollWindowMs(): Long = 50L
        }
    }

    private fun jsonResponse(body: String): RemoteVoiceTransportResponse {
        return RemoteVoiceTransportResponse(
            statusCode = 200,
            bytes = body.toByteArray(StandardCharsets.UTF_8),
            body = body,
            contentType = "application/json",
            requestId = "json"
        )
    }

    private fun audioResponse(bytes: ByteArray, contentType: String): RemoteVoiceTransportResponse {
        return RemoteVoiceTransportResponse(
            statusCode = 200,
            bytes = bytes,
            body = "",
            contentType = contentType,
            requestId = "audio"
        )
    }

    private fun fakeWaveBytes(seed: Int): ByteArray {
        val data = byteArrayOf(seed.toByte(), (seed + 1).toByte(), (seed + 2).toByte(), (seed + 3).toByte())
        val totalSize = 36 + data.size
        val buffer = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
        buffer.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(16_000)
        buffer.putInt(16_000)
        buffer.putShort(1.toShort())
        buffer.putShort(8.toShort())
        buffer.put("data".toByteArray(StandardCharsets.US_ASCII))
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }
}
