package com.lqlq.browser.automation.connector.content

import com.lqlq.browser.automation.script.StructuredScript

data class ContentGenerationResult(
    val generatedText: String,
    val providerId: String,
    val model: String,
    val requestId: String? = null,
    val usageMetadata: Map<String, Long> = emptyMap(),
    val structuredScript: StructuredScript? = null
)
