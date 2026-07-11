package com.lqlq.browser.automation

import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceProviderCredentialIsolationTest {

    @Test
    fun voiceProviderRecordDoesNotLeakIntoImageProviderRecord() {
        val store = InMemoryStore()

        store.saveImageProviderConfiguration("openai-images", "openai-key", "gpt-image-2")
        store.saveVoiceProviderConfiguration(
            VoiceProviderCredentialConfiguration(
                providerId = "android-system-tts",
                locale = "vi-VN",
                voiceId = "vi-offline"
            )
        )

        assertEquals("openai-key", store.getImageProviderConfiguration("openai-images")?.apiKey)
        assertEquals("vi-offline", store.getVoiceProviderConfiguration("android-system-tts")?.voiceId)
        assertNull(store.getVoiceProviderConfiguration("openai-images"))
    }

    private class InMemoryStore : AutomationCredentialStore {
        private val imageRecords = mutableMapOf<String, ImageProviderCredentialConfiguration>()
        private val voiceRecords = mutableMapOf<String, VoiceProviderCredentialConfiguration>()

        override fun saveGeminiConfiguration(apiKey: String, model: String) = Unit
        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = null
        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("NOT_CONFIGURED")
        override fun markGeminiInvalid(message: String?) = Unit
        override fun clearGeminiConfiguration() = Unit
        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) {
            imageRecords[providerId] = ImageProviderCredentialConfiguration(providerId, apiKey, model, accountId)
        }
        override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? = imageRecords[providerId]
        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONFIG_SAVED", providerId)
        override fun markImageProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearImageProviderConfiguration(providerId: String) = Unit
        override fun setSelectedImageProviderId(providerId: String) = Unit
        override fun getSelectedImageProviderId(): String? = null
        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
            voiceRecords[configuration.providerId] = configuration
        }
        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = voiceRecords[providerId]
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("CONFIG_SAVED", providerId)
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearVoiceProviderConfiguration(providerId: String) = Unit
        override fun setSelectedVoiceProviderId(providerId: String) = Unit
        override fun getSelectedVoiceProviderId(): String? = null
    }
}
