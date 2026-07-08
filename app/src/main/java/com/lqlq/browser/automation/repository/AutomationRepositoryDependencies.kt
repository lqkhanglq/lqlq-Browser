package com.lqlq.browser.automation.repository

import java.util.UUID

fun interface AutomationClock {
    fun nowEpochMs(): Long
}

fun interface AutomationIdGenerator {
    fun newId(): String
}

data class AutomationRepositoryDependencies(
    val clock: AutomationClock = AutomationClock { System.currentTimeMillis() },
    val idGenerator: AutomationIdGenerator = AutomationIdGenerator { UUID.randomUUID().toString() }
)
