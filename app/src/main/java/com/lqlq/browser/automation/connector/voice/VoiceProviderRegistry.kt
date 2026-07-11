package com.lqlq.browser.automation.connector.voice

enum class VoiceProviderAuthType {
    NONE,
    API_KEY,
    API_KEY_AND_REGION,
    SERVICE_ACCOUNT
}

enum class VoiceProviderCostType {
    FREE_LOCAL,
    FREE_LIMITED,
    CREDIT_BASED,
    PAID_PER_CHARACTER,
    PAID_PER_REQUEST,
    USER_ASSISTED_WEB
}

enum class VoiceProviderHealth {
    AVAILABLE,
    NOT_CONFIGURED,
    NOT_IMPLEMENTED,
    UNAVAILABLE,
    USER_ACTION_REQUIRED,
    LANGUAGE_NOT_SUPPORTED,
    VOICE_NOT_INSTALLED,
    DEPRECATED
}

data class VoiceProviderCapabilities(
    val supportedLocales: List<String>,
    val verifiedLocales: List<String>,
    val defaultLocale: String,
    val supportedOutputFormats: List<String>,
    val supportsPitch: Boolean,
    val supportsSpeechRate: Boolean,
    val supportsSamplePreview: Boolean,
    val supportsChunking: Boolean,
    val requiresCredentials: Boolean,
    val stabilityLevel: String
)

data class VoiceProviderDefinition(
    val providerId: String,
    val displayName: String,
    val authType: VoiceProviderAuthType,
    val costType: VoiceProviderCostType,
    val health: VoiceProviderHealth,
    val capabilities: VoiceProviderCapabilities
)

interface VoiceProviderRegistry {
    fun allDefinitions(): List<VoiceProviderDefinition>

    fun getDefinition(providerId: String): VoiceProviderDefinition?

    fun implementedProviderIds(): Set<String>
}

object AutomationVoiceProviders {
    const val ANDROID_SYSTEM_TTS: String = "android-system-tts"
    const val AZURE_SPEECH: String = "azure-speech"
    const val GOOGLE_CLOUD_TTS: String = "google-cloud-tts"
    const val ELEVENLABS: String = "elevenlabs"
    const val GEMINI_TTS: String = "gemini-tts"
    const val OPENAI_TTS: String = "openai-tts"
    const val FPT_AI_TTS: String = "fpt-ai-tts"
    const val VIETTEL_AI_TTS: String = "viettel-ai-tts"
    const val ZALO_AI_TTS: String = "zalo-ai-tts"
    const val VBEE_TTS: String = "vbee-tts"
    const val CLOUDFLARE_WORKERS_AI_TTS: String = "cloudflare-workers-ai-tts"
}

class DefaultVoiceProviderRegistry(
    implementedProviderIds: Set<String>
) : VoiceProviderRegistry {

    private val implemented = implementedProviderIds.map { it.trim().lowercase() }.toSet()

    private val definitions = listOf(
        definition(
            providerId = AutomationVoiceProviders.ANDROID_SYSTEM_TTS,
            displayName = "Google TTS tren dien thoai",
            authType = VoiceProviderAuthType.NONE,
            costType = VoiceProviderCostType.FREE_LOCAL,
            supportedLocales = listOf("vi-VN", "vi"),
            verifiedLocales = listOf("vi-VN"),
            defaultLocale = "vi-VN",
            supportedOutputFormats = listOf("wav"),
            supportsPitch = true,
            supportsSpeechRate = true,
            supportsSamplePreview = true,
            supportsChunking = true,
            requiresCredentials = false
        ),
        definition(
            AutomationVoiceProviders.FPT_AI_TTS,
            "FPT.AI Text to Speech",
            VoiceProviderAuthType.API_KEY,
            VoiceProviderCostType.FREE_LIMITED,
            listOf("vi-VN"),
            listOf(),
            "vi-VN",
            listOf("wav", "mp3"),
            false,
            true,
            true,
            true,
            true
        ),
        definition(
            providerId = AutomationVoiceProviders.VBEE_TTS,
            displayName = "VBEE",
            authType = VoiceProviderAuthType.API_KEY,
            costType = VoiceProviderCostType.PAID_PER_REQUEST,
            supportedLocales = listOf("vi-VN"),
            verifiedLocales = listOf(),
            defaultLocale = "vi-VN",
            supportedOutputFormats = listOf("mp3", "wav"),
            supportsPitch = false,
            supportsSpeechRate = true,
            supportsSamplePreview = true,
            supportsChunking = true,
            requiresCredentials = true
        ),
        definition(AutomationVoiceProviders.VIETTEL_AI_TTS, "Viettel AI", VoiceProviderAuthType.API_KEY, VoiceProviderCostType.PAID_PER_REQUEST, listOf("vi-VN"), listOf(), "vi-VN", listOf("mp3", "wav"), false, true, true, true, true, VoiceProviderHealth.NOT_IMPLEMENTED),
        definition(AutomationVoiceProviders.ZALO_AI_TTS, "Zalo AI", VoiceProviderAuthType.API_KEY, VoiceProviderCostType.PAID_PER_REQUEST, listOf("vi-VN"), listOf(), "vi-VN", listOf("mp3", "wav"), false, true, true, true, true, VoiceProviderHealth.NOT_IMPLEMENTED),
        definition(
            providerId = AutomationVoiceProviders.ELEVENLABS,
            displayName = "ElevenLabs",
            authType = VoiceProviderAuthType.API_KEY,
            costType = VoiceProviderCostType.FREE_LIMITED,
            supportedLocales = listOf("vi-VN"),
            verifiedLocales = listOf(),
            defaultLocale = "vi-VN",
            supportedOutputFormats = listOf("mp3"),
            supportsPitch = false,
            supportsSpeechRate = true,
            supportsSamplePreview = true,
            supportsChunking = true,
            requiresCredentials = true,
            healthOverride = VoiceProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationVoiceProviders.GOOGLE_CLOUD_TTS,
            displayName = "Google Cloud Text-to-Speech",
            authType = VoiceProviderAuthType.SERVICE_ACCOUNT,
            costType = VoiceProviderCostType.FREE_LIMITED,
            supportedLocales = listOf("vi-VN"),
            verifiedLocales = listOf(),
            defaultLocale = "vi-VN",
            supportedOutputFormats = listOf("wav", "mp3"),
            supportsPitch = true,
            supportsSpeechRate = true,
            supportsSamplePreview = true,
            supportsChunking = true,
            requiresCredentials = true,
            healthOverride = VoiceProviderHealth.NOT_IMPLEMENTED
        ),
        definition(AutomationVoiceProviders.OPENAI_TTS, "OpenAI TTS", VoiceProviderAuthType.API_KEY, VoiceProviderCostType.PAID_PER_CHARACTER, listOf("vi-VN"), listOf(), "vi-VN", listOf("mp3", "wav"), false, true, true, true, true, VoiceProviderHealth.NOT_IMPLEMENTED),
        definition(AutomationVoiceProviders.GEMINI_TTS, "Gemini TTS", VoiceProviderAuthType.API_KEY, VoiceProviderCostType.CREDIT_BASED, listOf("vi-VN"), listOf(), "vi-VN", listOf("wav"), false, true, true, true, true, VoiceProviderHealth.NOT_IMPLEMENTED),
        definition(
            providerId = AutomationVoiceProviders.AZURE_SPEECH,
            displayName = "Azure Speech",
            authType = VoiceProviderAuthType.API_KEY_AND_REGION,
            costType = VoiceProviderCostType.FREE_LIMITED,
            supportedLocales = listOf("vi-VN"),
            verifiedLocales = listOf(),
            defaultLocale = "vi-VN",
            supportedOutputFormats = listOf("wav", "mp3"),
            supportsPitch = true,
            supportsSpeechRate = true,
            supportsSamplePreview = true,
            supportsChunking = true,
            requiresCredentials = true,
            healthOverride = VoiceProviderHealth.NOT_IMPLEMENTED
        ),
        definition(AutomationVoiceProviders.CLOUDFLARE_WORKERS_AI_TTS, "Cloudflare Workers AI TTS", VoiceProviderAuthType.API_KEY, VoiceProviderCostType.CREDIT_BASED, listOf("vi-VN"), listOf(), "vi-VN", listOf("wav"), false, true, true, true, true, VoiceProviderHealth.NOT_IMPLEMENTED),
    )

    override fun allDefinitions(): List<VoiceProviderDefinition> = definitions

    override fun getDefinition(providerId: String): VoiceProviderDefinition? {
        val normalized = providerId.trim().lowercase()
        return definitions.firstOrNull { it.providerId == normalized }
    }

    override fun implementedProviderIds(): Set<String> = implemented

    private fun definition(
        providerId: String,
        displayName: String,
        authType: VoiceProviderAuthType,
        costType: VoiceProviderCostType,
        supportedLocales: List<String>,
        verifiedLocales: List<String>,
        defaultLocale: String,
        supportedOutputFormats: List<String>,
        supportsPitch: Boolean,
        supportsSpeechRate: Boolean,
        supportsSamplePreview: Boolean,
        supportsChunking: Boolean,
        requiresCredentials: Boolean,
        healthOverride: VoiceProviderHealth = if (implemented.contains(providerId)) VoiceProviderHealth.AVAILABLE else VoiceProviderHealth.NOT_IMPLEMENTED
    ): VoiceProviderDefinition {
        return VoiceProviderDefinition(
            providerId = providerId,
            displayName = displayName,
            authType = authType,
            costType = costType,
            health = healthOverride,
            capabilities = VoiceProviderCapabilities(
                supportedLocales = supportedLocales,
                verifiedLocales = verifiedLocales,
                defaultLocale = defaultLocale,
                supportedOutputFormats = supportedOutputFormats,
                supportsPitch = supportsPitch,
                supportsSpeechRate = supportsSpeechRate,
                supportsSamplePreview = supportsSamplePreview,
                supportsChunking = supportsChunking,
                requiresCredentials = requiresCredentials,
                stabilityLevel = if (implemented.contains(providerId)) "device-ready" else "planned"
            )
        )
    }
}
