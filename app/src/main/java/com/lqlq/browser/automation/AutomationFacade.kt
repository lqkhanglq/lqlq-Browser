package com.lqlq.browser.automation

import com.lqlq.browser.automation.artifact.AutomationArtifactStore
import com.lqlq.browser.automation.connector.AutomationConnectorRegistry
import com.lqlq.browser.automation.engine.AutomationEngine
import com.lqlq.browser.automation.model.AutomationFoundationStatus
import com.lqlq.browser.automation.repository.AutomationRepository

class AutomationFacade private constructor(
    private val engine: AutomationEngine
) {
    fun getFoundationStatus(): AutomationFoundationStatus = engine.getFoundationStatus()

    companion object {
        fun createDefault(): AutomationFacade {
            val registry = AutomationConnectorRegistry.empty()
            val repository = AutomationRepository.empty()
            val artifactStore = AutomationArtifactStore.empty()
            val engine = AutomationEngine(
                repository = repository,
                artifactStore = artifactStore,
                connectorRegistry = registry
            )
            return AutomationFacade(engine)
        }
    }
}
