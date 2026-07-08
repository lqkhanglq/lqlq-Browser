package com.lqlq.browser.automation.repository

enum class AutomationRepositoryErrorCode {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    CONSTRAINT,
    STORAGE
}

class AutomationRepositoryException(
    val code: AutomationRepositoryErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)
