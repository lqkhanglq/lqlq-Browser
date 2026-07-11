package com.lqlq.browser.automation.connector

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsConnector
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsSynthAdapter
import com.lqlq.browser.automation.connector.voice.AndroidSystemTtsVoiceCatalog
import com.lqlq.browser.automation.connector.voice.VoiceDefinition
import com.lqlq.browser.automation.connector.voice.VoiceProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VietnameseVoiceFilteringTest {

    @Test
    fun listVoicesReturnsOnlyVietnameseAndPrefersOfflineViVn() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val connector = AndroidSystemTtsConnector(
            context = context,
            synthAdapter = object : AndroidSystemTtsSynthAdapter {
                override fun listVoices(): AndroidSystemTtsVoiceCatalog {
                    return AndroidSystemTtsVoiceCatalog(
                        engineName = "Android System TTS",
                        voices = listOf(
                            VoiceDefinition("en-us", "English", "en-US", "Android System TTS", false, true, false),
                            VoiceDefinition("vi-net", "Vietnamese Network", "vi-VN", "Android System TTS", true, true, false),
                            VoiceDefinition("vi-offline", "Vietnamese Offline", "vi-VN", "Android System TTS", false, true, true),
                            VoiceDefinition("vi-generic", "Vietnamese Generic", "vi", "Android System TTS", false, true, false)
                        )
                    )
                }

                override suspend fun synthesizeToFile(text: String, config: VoiceProviderConfig, outputFile: File) {
                    error("Not needed for this test.")
                }
            }
        )

        val voices = connector.listVoices()

        assertEquals("vi-offline", voices.first().voiceId)
        assertEquals(3, voices.size)
        assertFalse(voices.any { it.locale.startsWith("en", ignoreCase = true) })
    }
}
