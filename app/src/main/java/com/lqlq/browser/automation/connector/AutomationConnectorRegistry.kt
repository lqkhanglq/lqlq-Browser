package com.lqlq.browser.automation.connector

class AutomationConnectorRegistry private constructor(
    private val connectorIds: List<String>
) {
    fun isFoundationReady(): Boolean = true

    fun registeredConnectorIds(): List<String> = connectorIds

    fun registeredConnectorCount(): Int = connectorIds.size

    companion object {
        fun empty(): AutomationConnectorRegistry = AutomationConnectorRegistry(emptyList())
    }
}
