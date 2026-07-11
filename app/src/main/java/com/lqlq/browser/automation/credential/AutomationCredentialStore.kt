package com.lqlq.browser.automation.credential

data class GeminiCredentialConfiguration(
    val providerId: String = AutomationCredentialStore.GEMINI_PROVIDER_ID,
    val apiKey: String,
    val model: String
)

data class ImageProviderCredentialConfiguration(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val accountId: String? = null
)

data class VoiceProviderCredentialConfiguration(
    val providerId: String,
    val locale: String,
    val voiceId: String? = null,
    val model: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val outputFormat: String = "wav",
    val engineName: String? = null,
    val apiKey: String? = null,
    val region: String? = null,
    val credentialJson: String? = null
)

data class AutomationCredentialStatusSnapshot(
    val state: String,
    val providerId: String = AutomationCredentialStore.GEMINI_PROVIDER_ID,
    val model: String? = null,
    val message: String? = null,
    val accountId: String? = null,
    val voiceId: String? = null,
    val locale: String? = null,
    val engineName: String? = null
)

interface AutomationCredentialStore {
    fun saveGeminiConfiguration(apiKey: String, model: String)

    fun getGeminiConfiguration(): GeminiCredentialConfiguration?

    fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot

    fun markGeminiInvalid(message: String? = null)

    fun clearGeminiConfiguration()

    fun saveImageProviderConfiguration(
        providerId: String,
        apiKey: String,
        model: String,
        accountId: String? = null
    )

    fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration?

    fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot

    fun markImageProviderState(
        providerId: String,
        state: String,
        message: String? = null
    )

    fun clearImageProviderConfiguration(providerId: String)

    fun setSelectedImageProviderId(providerId: String)

    fun getSelectedImageProviderId(): String?

    fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration)

    fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration?

    fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot

    fun markVoiceProviderState(providerId: String, state: String, message: String? = null)

    fun clearVoiceProviderConfiguration(providerId: String)

    fun setSelectedVoiceProviderId(providerId: String)

    fun getSelectedVoiceProviderId(): String?

    companion object {
        const val GEMINI_PROVIDER_ID: String = "gemini"
        const val OPENAI_IMAGE_PROVIDER_ID: String = "openai-images"
        const val CLOUDFLARE_WORKERS_AI_PROVIDER_ID: String = "cloudflare-workers-ai"

        const val STATE_CONNECTED: String = "CONNECTED"
        const val STATE_NOT_CONFIGURED: String = "NOT_CONFIGURED"
        const val STATE_INVALID: String = "INVALID"

        const val IMAGE_STATE_CONFIG_SAVED: String = "CONFIG_SAVED"
        const val IMAGE_STATE_CREDENTIAL_VALIDATED: String = "CREDENTIAL_VALIDATED"
        const val IMAGE_STATE_GENERATION_VERIFIED: String = "GENERATION_VERIFIED"

        const val VOICE_STATE_CONFIG_SAVED: String = "CONFIG_SAVED"
        const val VOICE_STATE_VOICE_LIST_LOADED: String = "VOICE_LIST_LOADED"
        const val VOICE_STATE_CREDENTIAL_VALIDATED: String = "CREDENTIAL_VALIDATED"
        const val VOICE_STATE_SAMPLE_VERIFIED: String = "SAMPLE_VERIFIED"
        const val VOICE_STATE_GENERATION_VERIFIED: String = "GENERATION_VERIFIED"

        fun empty(): AutomationCredentialStore = EmptyAutomationCredentialStore
    }
}

private object EmptyAutomationCredentialStore : AutomationCredentialStore {
    override fun saveGeminiConfiguration(apiKey: String, model: String) {
        throw UnsupportedOperationException("Empty credential store does not persist credentials.")
    }

    override fun getGeminiConfiguration(): GeminiCredentialConfiguration? = null

    override fun getGeminiConfigurationStatus(): AutomationCredentialStatusSnapshot {
        return AutomationCredentialStatusSnapshot(
            state = AutomationCredentialStore.STATE_NOT_CONFIGURED
        )
    }

    override fun markGeminiInvalid(message: String?) = Unit

    override fun clearGeminiConfiguration() = Unit

    override fun saveImageProviderConfiguration(
        providerId: String,
        apiKey: String,
        model: String,
        accountId: String?
    ) {
        throw UnsupportedOperationException("Empty credential store does not persist credentials.")
    }

    override fun getImageProviderConfiguration(providerId: String): ImageProviderCredentialConfiguration? = null

    override fun getImageProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
        return AutomationCredentialStatusSnapshot(
            state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
            providerId = providerId
        )
    }

    override fun markImageProviderState(providerId: String, state: String, message: String?) = Unit

    override fun clearImageProviderConfiguration(providerId: String) = Unit

    override fun setSelectedImageProviderId(providerId: String) = Unit

    override fun getSelectedImageProviderId(): String? = null

    override fun saveVoiceProviderConfiguration(configuration: VoiceProviderCredentialConfiguration) {
        throw UnsupportedOperationException("Empty credential store does not persist credentials.")
    }

    override fun getVoiceProviderConfiguration(providerId: String): VoiceProviderCredentialConfiguration? = null

    override fun getVoiceProviderConfigurationStatus(providerId: String): AutomationCredentialStatusSnapshot {
        return AutomationCredentialStatusSnapshot(
            state = AutomationCredentialStore.STATE_NOT_CONFIGURED,
            providerId = providerId
        )
    }

    override fun markVoiceProviderState(providerId: String, state: String, message: String?) = Unit

    override fun clearVoiceProviderConfiguration(providerId: String) = Unit

    override fun setSelectedVoiceProviderId(providerId: String) = Unit

    override fun getSelectedVoiceProviderId(): String? = null
}
