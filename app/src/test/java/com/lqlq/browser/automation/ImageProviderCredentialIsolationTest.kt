package com.lqlq.browser.automation

import com.lqlq.browser.automation.credential.AutomationCredentialStatusSnapshot
import com.lqlq.browser.automation.credential.AutomationCredentialStore
import com.lqlq.browser.automation.credential.GeminiCredentialConfiguration
import com.lqlq.browser.automation.credential.ImageProviderCredentialConfiguration
import com.lqlq.browser.automation.credential.VoiceProviderCredentialConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageProviderCredentialIsolationTest {

    @Test
    fun providerACannotReadProviderBCredentialRecord() {
        val store = IsolatedCredentialStore()

        store.saveImageProviderConfiguration("openai-images", "openai-key", "gpt-image-2")
        store.saveImageProviderConfiguration("cloudflare-workers-ai", "cf-token", "@cf/black-forest-labs/flux-1-schnell", "account-1")

        assertEquals("openai-key", store.getImageProviderConfiguration("openai-images")?.apiKey)
        assertEquals("cf-token", store.getImageProviderConfiguration("cloudflare-workers-ai")?.apiKey)
        assertNull(store.getImageProviderConfiguration("huggingface-inference"))
    }

    private class IsolatedCredentialStore : AutomationCredentialStore {
        private val providerRecords = mutableMapOf<String, ImageProviderCredentialConfiguration>()

        override fun saveGeminiConfiguration(apiKey: String, model: String) = Unit
        override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = null
        override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("NOT_CONFIGURED")
        override fun markGeminiInvalid(message: String?) = Unit
        override fun clearGeminiConfiguration() = Unit

        override fun saveImageProviderConfiguration(providerId: String, apiKey: String, model: String, accountId: String?) {
            val normalizedProviderId = providerId.trim().lowercase()
            providerRecords[normalizedProviderId] = ImageProviderCredentialConfiguration(
                providerId = normalizedProviderId,
                apiKey = apiKey,
                model = model,
                accountId = accountId
            )
        }

        override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? {
            return providerRecords[providerId.trim().lowercase()]
        }

        override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
            return AutomationCredentialStatusSnapshot("CONFIG_SAVED", providerId)
        }

        override fun markImageProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearImageProviderConfiguration(providerId: String) = Unit
        override fun setSelectedImageProviderId(providerId: String) = Unit
        override fun getSelectedImageProviderId(): String? = null
        override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) = Unit
        override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = null
        override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot = AutomationCredentialStatusSnapshot("NOT_CONFIGURED", providerId)
        override fun markVoiceProviderState(providerId: String, state: String, message: String?) = Unit
        override fun clearVoiceProviderConfiguration(providerId: String) = Unit
        override fun setSelectedVoiceProviderId(providerId: String) = Unit
        override fun getSelectedVoiceProviderId(): String? = null
    }
}
