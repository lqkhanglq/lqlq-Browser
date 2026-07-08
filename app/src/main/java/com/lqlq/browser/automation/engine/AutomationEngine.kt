package com.lqlq.browser.automation.engine

import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.model.AutomationFoundationStatus
import com.lqlq.browser.automation.repository.AutomationRepository

class AutomationEngine(
    private val repository: AutomationRepository,
    private val artifactStore: AutomationArtifactStore,
    private val connectorRegistry: AutomationConnectorRegistry
) {
    fun getFoundationStatus(): AutomationFoundationStatus {
        return AutomationFoundationStatus(
            initialized = true,
            repositoryReady = repository.isFoundationReady(),
            artifactStoreReady = artifactStore.isFoundationReady(),
            connectorRegistryReady = connectorRegistry.isFoundationReady(),
            registeredConnectorCount = connectorRegistry.registeredConnectorCount()
        )
    }
}
