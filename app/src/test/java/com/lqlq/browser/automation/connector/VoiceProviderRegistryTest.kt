package com.lqlq.browser.automation.connector

import com.lqlq.browser.automation.connector.voice.AutomationVoiceProviders
import com.lqlq.browser.automation.connector.voice.DefaultVoiceProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProviderRegistryTest {

    @Test
    fun vietnameseCommercialProvidersAppearBeforeLocalTtsFallback() {
        val registry = DefaultVoiceProviderRegistry(
            implementedProviderIds = setOf(
                AutomationVoiceProviders.VBEE_TTS,
                AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
                AutomationVoiceProviders.FPT_AI_TTS
            )
        )

        val providers = registry.allDefinitions()

        assertEquals(AutomationVoiceProviders.ANDROID_SYSTEM_TTS, providers.first().providerId)
        assertEquals("vi-VN", providers.first().capabilities.defaultLocale)
        assertEquals(AutomationVoiceProviders.FPT_AI_TTS, providers[1].providerId)
        assertEquals("FPT.AI Text to Speech", providers[1].displayName)
        assertEquals("FREE_LIMITED", providers[1].costType.name)
        assertTrue(providers.any { it.providerId == AutomationVoiceProviders.OPENAI_TTS })
        assertTrue(providers.any { it.providerId == AutomationVoiceProviders.GOOGLE_CLOUD_TTS })
        assertTrue(providers.any { it.providerId == AutomationVoiceProviders.ELEVENLABS })
        assertTrue(providers.any { it.providerId == AutomationVoiceProviders.CLOUDFLARE_WORKERS_AI_TTS })
        assertTrue(providers.any { it.providerId == AutomationVoiceProviders.VBEE_TTS && it.health.name == "AVAILABLE" })
    }
}
