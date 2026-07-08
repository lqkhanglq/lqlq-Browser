package com.lqlq.browser.automation.artifact

interface AutomationArtifactStore {
    fun isFoundationReady(): Boolean

    companion object {
        fun empty(): AutomationArtifactStore = EmptyAutomationArtifactStore
    }
}

private object EmptyAutomationArtifactStore : AutomationArtifactStore {
    override fun isFoundationReady(): Boolean = true
}
