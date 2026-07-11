package com.lqlq.browser.automation.connector.image

enum class ImageProviderAuthType {
    API_KEY,
    ACCOUNT_ID_AND_API_TOKEN,
    NONE
}

enum class ImageProviderCostType {
    FREE_LIMITED,
    COMMUNITY_FREE,
    CREDIT_BASED,
    PAID_PER_REQUEST,
    STOCK_MEDIA,
    USER_ASSISTED_WEB
}

enum class ImageProviderHealth {
    AVAILABLE,
    NOT_CONFIGURED,
    NOT_IMPLEMENTED,
    UNAVAILABLE,
    USER_ACTION_REQUIRED,
    DEPRECATED
}

data class ImageProviderCapabilities(
    val supportedModels: List<String>,
    val defaultModel: String?,
    val syncOrAsync: String,
    val supportsPolling: Boolean,
    val supportsAspectRatio: Boolean,
    val supportsNegativePrompt: Boolean,
    val supportedOutputFormats: List<String>,
    val maxImagesPerJob: Int,
    val stabilityLevel: String
)

data class ImageProviderDefinition(
    val providerId: String,
    val displayName: String,
    val authType: ImageProviderAuthType,
    val costType: ImageProviderCostType,
    val capabilities: ImageProviderCapabilities,
    val health: ImageProviderHealth
)

interface ImageProviderRegistry {
    fun allDefinitions(): List<ImageProviderDefinition>

    fun getDefinition(providerId: String): ImageProviderDefinition?

    fun implementedProviderIds(): Set<String>
}

class DefaultImageProviderRegistry(
    implementedProviderIds: Set<String>
) : ImageProviderRegistry {

    private val implemented = implementedProviderIds.map { it.trim().lowercase() }.toSet()

    private val definitions = listOf(
        definition(
            providerId = AutomationImageProviders.CLOUDFLARE_WORKERS_AI,
            displayName = "Cloudflare Workers AI",
            authType = ImageProviderAuthType.ACCOUNT_ID_AND_API_TOKEN,
            costType = ImageProviderCostType.FREE_LIMITED,
            defaultModel = CloudflareWorkersAiImageConnector.DEFAULT_MODEL,
            supportedModels = listOf(
                CloudflareWorkersAiImageConnector.DEFAULT_MODEL,
                "@cf/lykon/dreamshaper-8-lcm",
                "@cf/bytedance/stable-diffusion-xl-lightning"
            ),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = false,
            supportsNegativePrompt = false,
            supportedOutputFormats = listOf("png")
        ),
        definition(
            providerId = AutomationImageProviders.OPENAI_IMAGES,
            displayName = "OpenAI Images",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.PAID_PER_REQUEST,
            defaultModel = OpenAiImageConnector.DEFAULT_MODEL,
            supportedModels = listOf(
                OpenAiImageConnector.DEFAULT_MODEL,
                OpenAiImageConnector.MINI_MODEL,
                OpenAiImageConnector.LEGACY_MODEL
            ),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = true,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png", "jpeg", "webp"),
            healthOverride = if (implemented.contains(AutomationImageProviders.OPENAI_IMAGES)) {
                ImageProviderHealth.AVAILABLE
            } else {
                ImageProviderHealth.NOT_IMPLEMENTED
            }
        ),
        definition(
            providerId = AutomationImageProviders.HUGGINGFACE_INFERENCE,
            displayName = "Hugging Face Inference",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.CREDIT_BASED,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = false,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.AI_HORDE,
            displayName = "AI Horde",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.COMMUNITY_FREE,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "async",
            supportsPolling = true,
            supportsAspectRatio = true,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.GEMINI_IMAGE,
            displayName = "Gemini Image",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.CREDIT_BASED,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = false,
            supportsNegativePrompt = false,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.STABILITY_AI,
            displayName = "Stability AI",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.CREDIT_BASED,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = true,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.REPLICATE,
            displayName = "Replicate",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.PAID_PER_REQUEST,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "async",
            supportsPolling = true,
            supportsAspectRatio = true,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.BFL_FLUX,
            displayName = "BFL FLUX",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.PAID_PER_REQUEST,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = true,
            supportsNegativePrompt = true,
            supportedOutputFormats = listOf("png"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.PEXELS,
            displayName = "Pexels Stock Photos",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.STOCK_MEDIA,
            defaultModel = PexelsStockImageConnector.DEFAULT_MODEL,
            supportedModels = listOf(PexelsStockImageConnector.DEFAULT_MODEL),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = true,
            supportsNegativePrompt = false,
            supportedOutputFormats = listOf("jpeg", "png")
        ),
        definition(
            providerId = AutomationImageProviders.PIXABAY,
            displayName = "Pixabay",
            authType = ImageProviderAuthType.API_KEY,
            costType = ImageProviderCostType.STOCK_MEDIA,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = false,
            supportsNegativePrompt = false,
            supportedOutputFormats = listOf("jpeg"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        ),
        definition(
            providerId = AutomationImageProviders.COVERR,
            displayName = "Coverr",
            authType = ImageProviderAuthType.NONE,
            costType = ImageProviderCostType.STOCK_MEDIA,
            defaultModel = null,
            supportedModels = emptyList(),
            syncOrAsync = "sync",
            supportsPolling = false,
            supportsAspectRatio = false,
            supportsNegativePrompt = false,
            supportedOutputFormats = listOf("mp4", "jpeg"),
            healthOverride = ImageProviderHealth.NOT_IMPLEMENTED
        )
    )

    override fun allDefinitions(): List<ImageProviderDefinition> = definitions

    override fun getDefinition(providerId: String): ImageProviderDefinition? {
        val normalized = providerId.trim().lowercase()
        return definitions.firstOrNull { it.providerId == normalized }
    }

    override fun implementedProviderIds(): Set<String> = implemented

    private fun definition(
        providerId: String,
        displayName: String,
        authType: ImageProviderAuthType,
        costType: ImageProviderCostType,
        defaultModel: String?,
        supportedModels: List<String>,
        syncOrAsync: String,
        supportsPolling: Boolean,
        supportsAspectRatio: Boolean,
        supportsNegativePrompt: Boolean,
        supportedOutputFormats: List<String>,
        healthOverride: ImageProviderHealth = if (implemented.contains(providerId)) {
            ImageProviderHealth.AVAILABLE
        } else {
            ImageProviderHealth.NOT_IMPLEMENTED
        }
    ): ImageProviderDefinition {
        return ImageProviderDefinition(
            providerId = providerId,
            displayName = displayName,
            authType = authType,
            costType = costType,
            capabilities = ImageProviderCapabilities(
                supportedModels = supportedModels,
                defaultModel = defaultModel,
                syncOrAsync = syncOrAsync,
                supportsPolling = supportsPolling,
                supportsAspectRatio = supportsAspectRatio,
                supportsNegativePrompt = supportsNegativePrompt,
                supportedOutputFormats = supportedOutputFormats,
                maxImagesPerJob = 3,
                stabilityLevel = if (healthOverride == ImageProviderHealth.NOT_IMPLEMENTED) "planned" else "supported"
            ),
            health = healthOverride
        )
    }
}

object AutomationImageProviders {
    const val CLOUDFLARE_WORKERS_AI: String = "cloudflare-workers-ai"
    const val HUGGINGFACE_INFERENCE: String = "huggingface-inference"
    const val AI_HORDE: String = "ai-horde"
    const val GEMINI_IMAGE: String = "gemini-image"
    const val OPENAI_IMAGES: String = "openai-images"
    const val STABILITY_AI: String = "stability-ai"
    const val REPLICATE: String = "replicate"
    const val BFL_FLUX: String = "bfl-flux"
    const val PEXELS: String = "pexels"
    const val PIXABAY: String = "pixabay"
    const val COVERR: String = "coverr"
}
