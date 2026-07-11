package com.lqlq.browser.automation.connector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsConnector
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsSynthAdapter
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsVoiceCatalog
import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceGenerationRequest
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
class VoiceChunkingTest {

    @Test
    fun longScriptIsSynthesizedInSequentialChunks() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val calls = mutableListOf<String>()
        val connector = AndroidSystemTtsConnector(
            context = context,
            synthAdapter = object : AndroidSystemTtsSynthAdapter {
                override fun listVoices(): AndroidSystemTtsVoiceCatalog {
                    return AndroidSystemTtsVoiceCatalog(
                        engineName = "Android System TTS",
                        voices = listOf(
                            VoiceDefinition(
                                voiceId = "vi-offline",
                                displayName = "Vietnamese Offline",
                                locale = "vi-VN",
                                engineName = "Android System TTS",
                                networkRequired = false,
                                installed = true,
                                isDefault = true
                            )
                        )
                    )
                }

                override suspend fun synthesizeToFile(text: String, config: VoiceProviderConfig, outputFile: File) {
                    calls += text
                    outputFile.writeBytes(fakeWavBytes())
                }
            }
        )

        val longText = buildString {
            repeat(30) {
                append("Day la cau thu ")
                append(it + 1)
                append(". ")
            }
        }

        val result = connector.generateVoice(
            config = VoiceProviderConfig(
                providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                locale = "vi-VN",
                voiceId = "vi-offline",
                model = null,
                speechRate = 1.0f,
                pitch = 1.0f,
                outputFormat = "wav"
            ),
            request = VoiceGenerationRequest(
                jobId = "job-voice",
                scriptArtifactId = "script-1",
                text = longText,
                providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                voiceId = "vi-offline",
                locale = "vi-VN",
                speechRate = 1.0f,
                pitch = 1.0f,
                outputFormat = "wav"
            )
        )

        assertTrue(calls.size >= 2)
        assertEquals(calls.size, result.metadata.chunkCount)
        assertTrue(result.bytes.isNotEmpty())
    }

    private fun fakeWavBytes(): ByteArray {
        val pcm = ByteArray(1600) { 0 }
        val stream = ByteArrayOutputStream()
        val temp = kotlin.io.path.createTempFile(suffix = ".wav").toFile()
        RandomAccessFile(temp, "rw").use { raf ->
            raf.setLength(0)
            val channels = 1
            val sampleRate = 16000
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            raf.writeBytes("RIFF")
            writeIntLE(raf, 36 + pcm.size)
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            writeIntLE(raf, 16)
            writeShortLE(raf, 1)
            writeShortLE(raf, channels)
            writeIntLE(raf, sampleRate)
            writeIntLE(raf, byteRate)
            writeShortLE(raf, blockAlign)
            writeShortLE(raf, bitsPerSample)
            raf.writeBytes("data")
            writeIntLE(raf, pcm.size)
            raf.write(pcm)
        }
        val bytes = temp.readBytes()
        temp.delete()
        stream.write(bytes)
        return stream.toByteArray()
    }

    private fun writeShortLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
    }

    private fun writeIntLE(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xff)
        raf.write((value shr 8) and 0xff)
        raf.write((value shr 16) and 0xff)
        raf.write((value shr 24) and 0xff)
    }
}
