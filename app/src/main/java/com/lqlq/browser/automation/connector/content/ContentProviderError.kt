package com.lqlq.browser.automation.connector.content

enum class ContentProviderErrorCode {
    NOT_CONFIGURED,
    AUTHENTICATION,
    QUOTA_EXCEEDED,
    NETWORK,
    TIMEOUT,
    CANCELLED,
    INVALID_RESPONSE,
    PROVIDER_FAILURE,
    VALIDATION
}

class ContentProviderException(
    val code: ContentProviderErrorCode,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)
