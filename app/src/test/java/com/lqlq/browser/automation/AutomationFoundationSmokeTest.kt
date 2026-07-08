package com.lqlq.browser.automation

import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.engine.AutomationEngine
import com.lqlq.browser.automation.repository.AutomationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationFoundationSmokeTest {
    @Test
    fun foundationSkeletonCanBeConstructedWithoutActivity() {
        val registry = AutomationConnectorRegistry.empty()
        val repository = AutomationRepository.empty()
        val artifactStore = AutomationArtifactStore.empty()
        val engine = AutomationEngine(
            repository = repository,
            artifactStore = artifactStore,
            connectorRegistry = registry
        )
        val facade = AutomationFacade.createDefault()

        val engineStatus = engine.getFoundationStatus()
        val facadeStatus = facade.getFoundationStatus()

        assertTrue(repository.isFoundationReady())
        assertTrue(artifactStore.isFoundationReady())
        assertTrue(registry.isFoundationReady())
        assertTrue(engineStatus.initialized)
        assertTrue(facadeStatus.initialized)
        assertEquals(0, registry.registeredConnectorCount())
        assertEquals(emptyList<String>(), registry.registeredConnectorIds())
        assertEquals(engineStatus, facadeStatus)
    }
}
