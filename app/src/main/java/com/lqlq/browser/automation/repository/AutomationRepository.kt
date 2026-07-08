package com.lqlq.browser.automation.repository

interface AutomationRepository {
    fun isFoundationReady(): Boolean

    companion object {
        fun empty(): AutomationRepository = EmptyAutomationRepository
    }
}

private object EmptyAutomationRepository : AutomationRepository {
    override fun isFoundationReady(): Boolean = true
}
