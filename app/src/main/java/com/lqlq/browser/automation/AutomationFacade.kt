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
        fun create(
            repository: AutomationRepository,
            artifactStore: AutomationArtifactStore = AutomationArtifactStore.empty(),
            connectorRegistry: AutomationConnectorRegistry = AutomationConnectorRegistry.empty()
        ): AutomationFacade {
            val engine = AutomationEngine(
                repository = repository,
                artifactStore = artifactStore,
                connectorRegistry = connectorRegistry
            )
            return AutomationFacade(engine)
        }

        fun createDefault(): AutomationFacade {
            return create(
                repository = AutomationRepository.empty(),
                artifactStore = AutomationArtifactStore.empty(),
                connectorRegistry = AutomationConnectorRegistry.empty()
            )
        }
    }
}
