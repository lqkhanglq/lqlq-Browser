package com.lqlq.browser.automation.connector.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProviderRegistryTest {

    @Test
    fun registryListsAllPlannedProvidersAndOnlyMarksImplementedOnesAvailable() {
        val registry = DefaultImageProviderRegistry(
            implementedProviderIds = setOf(
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationImageProviders.CLOUDFLARE_WORKERS_AI
            )
        )

        val definitions = registry.allDefinitions()
        val ids = definitions.map { it.providerId }.toSet()

        assertEquals(11, definitions.size)
        assertTrue(ids.contains(AutomationImageProviders.OPENAI_IMAGES))
        assertTrue(ids.contains(AutomationImageProviders.CLOUDFLARE_WORKERS_AI))
        assertTrue(ids.contains(AutomationImageProviders.HUGGINGFACE_INFERENCE))
        assertEquals(ImageProviderHealth.AVAILABLE, registry.getDefinition(AutomationImageProviders.OPENAI_IMAGES)?.health)
        assertEquals(ImageProviderHealth.AVAILABLE, registry.getDefinition(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)?.health)
        assertEquals(ImageProviderHealth.NOT_IMPLEMENTED, registry.getDefinition(AutomationImageProviders.HUGGINGFACE_INFERENCE)?.health)
        assertEquals(
            ImageProviderAuthType.ACCOUNT_ID_AND_API_TOKEN,
            registry.getDefinition(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)?.authType
        )
        assertFalse(
            registry.getDefinition(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)?.capabilities?.supportsNegativePrompt
                ?: true
        )
        assertFalse(registry.implementedProviderIds().contains(AutomationImageProviders.HUGGINGFACE_INFERENCE))
    }
}
