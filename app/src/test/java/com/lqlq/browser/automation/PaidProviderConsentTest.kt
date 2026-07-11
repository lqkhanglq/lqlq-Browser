package com.lqlq.browser.automation

import com.lqlq.browser.automation.connector.image.AutomationImageProviders
import com.lqlq.browser.automation.connector.image.DefaultImageProviderRegistry
import com.lqlq.browser.automation.connector.image.ImageProviderCostType
import org.junit.Assert.assertEquals
import org.junit.Test

class PaidProviderConsentTest {

    @Test
    fun openAiRemainsExplicitPaidProviderInRegistry() {
        val registry = DefaultImageProviderRegistry(
            implementedProviderIds = setOf(
                AutomationImageProviders.OPENAI_IMAGES,
                AutomationImageProviders.CLOUDFLARE_WORKERS_AI
            )
        )

        assertEquals(
            ImageProviderCostType.PAID_PER_REQUEST,
            registry.getDefinition(AutomationImageProviders.OPENAI_IMAGES)?.costType
        )
        assertEquals(
            ImageProviderCostType.FREE_LIMITED,
            registry.getDefinition(AutomationImageProviders.CLOUDFLARE_WORKERS_AI)?.costType
        )
    }
}
